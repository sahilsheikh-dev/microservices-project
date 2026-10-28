#!/usr/bin/env bash
set -uo pipefail
set -a; . ./.env; set +a
U=http://localhost:8081; J=http://localhost:8082
EMAIL="smoke$(date +%s)@example.com"; PW="Sm0ke-test-pass-123"; FAILED=0
H='Content-Type: application/json'
code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
check() { if [ "$2" = "$3" ]; then echo "PASS $1"; else echo "FAIL $1 (got $2, want $3)"; FAILED=1; fi; }
login() { curl -s -X POST $U/api/auth/login -H "$H" -d "{\"email\":\"$1\",\"password\":\"$2\"}"; }

REG=$(curl -s -X POST $U/api/users -H "$H" -d "{\"name\":\"Smoke\",\"email\":\"$EMAIL\",\"password\":\"$PW\",\"roles\":\"ADMIN\"}")
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
check "journal anonymous"            "$(code $J/api/journals)" 401
check "journal as USER"              "$(code -H "$A" $J/api/journals)" 403
check "journal as ADMIN"             "$(code -H "$AA" $J/api/journals)" 200

sleep 4
JR=$(curl -s -H "$AA" "$J/api/journals?size=50")
check "journal has the new user"     "$(echo "$JR" | jq --arg e "$EMAIL" '[.content[]|select(.userEmail==$e)]|length>0')" true
check "no array-typed dates"         "$(echo "$JR" | jq '[.content[0]|to_entries[]|select(.value|type=="array")]|length')" 0
N=$(docker compose exec -T kafka kafka-console-consumer --bootstrap-server localhost:9092 \
    --topic user-events --from-beginning --timeout-ms 5000 2>/dev/null | grep -ci password || true)
check "no password in Kafka topic"   "$N" 0

for i in 1 2 3 4 5; do code -X POST $U/api/auth/login -H "$H" -d "{\"email\":\"$EMAIL\",\"password\":\"bad-bad-bad-1\"}" >/dev/null; done
check "rate limit"                   "$(code -X POST $U/api/auth/login -H "$H" -d "{\"email\":\"$EMAIL\",\"password\":\"bad-bad-bad-1\"}")" 429

echo "stack traces in logs: $(docker compose logs --no-color user-service journal-service | grep -cE '^\s+at (com|org)\.' || true)"
exit $FAILED