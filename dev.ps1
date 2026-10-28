param(
    [Parameter(Mandatory, Position = 0)]
    [ValidateSet('infra', 'user', 'journal', 'local', 'hub', 'down', 'reset', 'logs', 'test', 'publish')]
    [string]$Mode,
    [string]$Tag = ''
)

Set-Location $PSScriptRoot
$base  = @('-f', 'docker-compose.yml')
$local = @('-f', 'docker-compose.yml', '-f', 'docker-compose.local.yml')
$hubUser = 'sahilsheikhdev'

function Import-DotEnv {
    Get-Content .env | Where-Object { $_ -match '^\s*[^#\s][^=]*=' } | ForEach-Object {
        $k, $v = $_ -split '=', 2
        [Environment]::SetEnvironmentVariable($k.Trim(), $v.Trim(), 'Process')
    }
}

function Set-HostEnv {
    Import-DotEnv
    $env:DB_PASSWORD     = $env:MYSQL_APP_PASSWORD
    $env:DB_URI          = "mongodb://journalapp:$($env:MONGO_APP_PASSWORD)@localhost:27018/journaldb?authSource=journaldb"
    $env:ADMIN_EMAIL     = $env:JOURNAL_ADMIN_EMAIL
    $env:ADMIN_PASSWORD  = $env:JOURNAL_ADMIN_PASSWORD
    $env:SWAGGER_ENABLED = 'true'
}

switch ($Mode) {
    'infra' {
        # Dev mode: only Kafka + databases in Docker; apps run on the host
        docker compose @local stop user-service journal-service
        docker compose @local up -d kafka mysql-user mongodb-journal
        docker compose @local ps
        Write-Host "`nNow run in two terminals:  .\dev.ps1 user   and   .\dev.ps1 journal"
    }
    'user'    { Set-HostEnv; Push-Location user-service;    mvn spring-boot:run; Pop-Location }
    'journal' { Set-HostEnv; Push-Location journal-service; mvn spring-boot:run; Pop-Location }
    'local' {
        docker compose @local down
        docker compose @local up --build -d
        docker compose @local ps
    }
    'hub' {
        docker compose @local down
        docker compose @base pull
        docker compose @base up -d
        docker compose @base images
    }
    'down'  { docker compose @local down }
    'logs'  { docker compose @local logs -f --tail 100 }
    'test'  {
        foreach ($d in 'user-service', 'journal-service') {
            Push-Location $d; mvn -B test; Pop-Location
        }
    }
    'reset' {
        $a = Read-Host 'This DELETES all database and Kafka data. Type yes to continue'
        if ($a -eq 'yes') { docker compose @local down -v }
    }
    'publish' {
        if (-not $Tag) { throw 'Usage: .\dev.ps1 publish -Tag 1.0.0' }
        foreach ($s in 'user-service', 'journal-service') {
            docker buildx build --platform linux/amd64 `
                -t "${hubUser}/${s}:$Tag" -t "${hubUser}/${s}:latest" --push "./$s"
        }
    }
}