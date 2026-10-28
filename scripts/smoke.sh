#!/usr/bin/env bash
# Needs: stack running, jq, curl. Run: bash scripts/smoke.sh
set -uo pipefail
cd "$(dirname "$0")/.."
set -a; . <(tr -d '\r' < .env); set +a
U=http://localhost:8081; J=http://localhost:8082
EMAIL="smoke$(date +%s)@example.com"; PW="Sm0ke-test-pass-123"; NEWPW="N3w-smoke-pass-456"
H='Content-Type: application/json'; FAILED=0
code()  { curl -s -o /dev/null -w '%{http_code}' "$@"; }
check() { if [ "$2" = "$3" ]; then echo "PASS $1"; else echo "FAIL $1 (got $2, want $3)"; FAILED=1; fi; }
login() { curl -s -X POST $U/api/auth/login -H "$H" -d "{\"email\":\"$1\",\"password\":\"$2\"}"; }
uuid()  { cat /proc/sys/kernel/random/uuid 2>/dev/null || uuidgen 2>/dev/null || powershell.exe -NoProfile -Command '[guid]::NewGuid().ToString()' | tr -d '\r'; }
kafka() { docker compose exec -T kafka "$@"; }

PARTS=$(kafka kafka-topics --bootstrap-server localhost:9092 --describe --topic user-events 2>/dev/null | grep -o 'PartitionCount: *[0-9]*' | grep -o '[0-9]*$')
check "user-events has 3 partitions" "$PARTS" 3

REG=$(curl -s -X POST $U/api/users -H "$H" -d "{\"name\":\"Smoke\",\"email\":\"$EMAIL\",\"password\":\"$PW\",\"roles\":\"ADMIN\"}")
ID=$(echo "$REG" | jq -r .id)
check "register ignores roles=ADMIN" "$(echo "$REG" | jq -r .roles)" "USER"
check "response has no password"     "$(echo "$REG" | jq 'has("password")')" "false"
check "duplicate email"              "$(code -X POST $U/api/users -H "$H" -d "{\"name\":\"S\",\"email\":\"$EMAIL\",\"password\":\"$PW\"}")" 409
check "malformed JSON"               "$(code -X POST $U/api/users -H "$H" -d '{bad')" 400

TOKEN=$(login "$EMAIL" "$PW" | jq -r .accessToken)
check "login returns token"          "$([ "$TOKEN" != null ] && [ -n "$TOKEN" ] && echo ok)" ok
A="Authorization: Bearer $TOKEN"
check "GET /me"                      "$(code -H "$A" $U/api/users/me)" 200
check "wrong password"               "$(code -X POST $U/api/auth/login -H "$H" -d "{\"email\":\"$EMAIL\",\"password\":\"nope-nope-1\"}")" 401
check "anonymous list users"         "$(code $U/api/users)" 401
check "USER list users"              "$(code -H "$A" $U/api/users)" 403
check "unknown route"                "$(code -H "$A" $U/api/nope)" 404

ATOKEN=$(login "$ADMIN_EMAIL" "$ADMIN_PASSWORD" | jq -r .accessToken)
AA="Authorization: Bearer $ATOKEN"
check "admin list users"             "$(code -H "$AA" "$U/api/users?size=5")" 200
check "partial update (name only)"   "$(curl -s -X PUT $U/api/users/$ID -H "$AA" -H "$H" -d '{"name":"Renamed"}' | jq -r .name)" Renamed
check "admin changes password"       "$(code -X PUT $U/api/users/$ID -H "$AA" -H "$H" -d "{\"password\":\"$NEWPW\"}")" 200
check "old password rejected"        "$(code -X POST $U/api/auth/login -H "$H" -d "{\"email\":\"$EMAIL\",\"password\":\"$PW\"}")" 401
check "new password works"           "$([ "$(login "$EMAIL" "$NEWPW" | jq -r .accessToken)" != null ] && echo ok)" ok

check "journal anonymous"            "$(code $J/api/journals)" 401
check "journal as USER"              "$(code -H "$A" $J/api/journals)" 403
check "journal as ADMIN"             "$(code -H "$AA" $J/api/journals)" 200

sleep 4
JR=$(curl -s -H "$AA" "$J/api/journals?size=50")
check "journal has the new user"     "$(echo "$JR" | jq --arg e "$EMAIL" '[.content[]|select(.userEmail==$e)]|length>0')" true
check "no array-typed dates"         "$(echo "$JR" | jq '[.content[0]|to_entries[]|select(.value|type=="array")]|length')" 0
N=$(kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic user-events --from-beginning --timeout-ms 5000 2>/dev/null | grep -ci password || true)
check "no password in Kafka topic"   "${N:-0}" 0

# Idempotency, dead-letter routing, and the Long userId repository query.
EID=$(uuid); DUP="dup-$EID@example.com"; NOW=$(date -u +%Y-%m-%dT%H:%M:%SZ)
MSG="{\"schemaVersion\":2,\"eventId\":\"$EID\",\"occurredAt\":\"$NOW\",\"operation\":\"CREATE\",\"status\":\"SUCCESS\",\"comment\":\"smoke\",\"userId\":999999,\"userName\":\"Dup\",\"userEmail\":\"$DUP\",\"userRole\":\"USER\"}"
printf '%s\n%s\n' "$MSG" "$MSG" | kafka kafka-console-producer --bootstrap-server localhost:9092 --topic user-events >/dev/null 2>&1
printf '%s\n' "not-json-$EID" | kafka kafka-console-producer --bootstrap-server localhost:9092 --topic user-events >/dev/null 2>&1
sleep 6
check "duplicate eventId stored once (and findByUserId works)" \
  "$(curl -s -H "$AA" "$J/api/journals/user/999999?size=100" | jq --arg e "$DUP" '[.content[]|select(.userEmail==$e)]|length')" 1
DLT=$(kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic user-operation-failed --from-beginning --timeout-ms 8000 2>/dev/null | grep -c "not-json-$EID" || true)
check "malformed event reaches dead-letter topic" "$([ "${DLT:-0}" -ge 1 ] && echo yes)" yes

for i in 1 2 3 4 5; do
  code -X POST $U/api/auth/login -H "$H" -d "{\"email\":\"$EMAIL\",\"password\":\"bad-bad-bad-1\"}" >/dev/null
done
RATE_LIMIT=$(code -X POST $U/api/auth/login -H "$H" -d "{\"email\":\"$EMAIL\",\"password\":\"bad-bad-bad-1\"}")
check "rate limit"                   "$RATE_LIMIT" 429
echo "stack traces in logs: $(docker compose logs --no-color user-service journal-service | grep -cE '^\s+at (com|org)\.' || true)"
exit $FAILED
