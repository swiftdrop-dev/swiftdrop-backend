# Loads only webhook connection settings; never executes .env content.
param([switch]$DatabaseCheckOnly, [switch]$LocalDevelopment)
if ($DatabaseCheckOnly -and $LocalDevelopment) { throw 'Choose one mode only.' }
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $repoRoot '.env'
if (!(Test-Path -LiteralPath $envFile)) { throw 'Create the backend root .env file first.' }
$allowed = @('PGHOST', 'PGPORT', 'PGDATABASE', 'PGUSER', 'PGPASSWORD', 'PGSSLMODE', 'PGCHANNELBINDING', 'WEBHOOK_SERVICE_PORT', 'WEBHOOK_LOCAL_MERCHANT_ID', 'WEBHOOK_LOCAL_SIGNING_SECRET', 'WEBHOOK_BROKER_ENABLED', 'RABBITMQ_HOST', 'RABBITMQ_PORT', 'RABBITMQ_USER', 'RABBITMQ_PASSWORD')
$previous = @{}
try {
    foreach ($key in $allowed) { $previous[$key] = [Environment]::GetEnvironmentVariable($key, 'Process') }
    foreach ($line in Get-Content -LiteralPath $envFile) {
        if ($line -match '^\s*([A-Z_]+)\s*=\s*(.*?)\s*$' -and $allowed -contains $matches[1]) {
            $key = $matches[1]
            $value = $matches[2]
            if ($value.Length -ge 2 -and (($value.StartsWith("'") -and $value.EndsWith("'")) -or ($value.StartsWith('"') -and $value.EndsWith('"')))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            [Environment]::SetEnvironmentVariable($key, $value, 'Process')
        }
    }
    foreach ($key in @('PGHOST', 'PGDATABASE', 'PGUSER', 'PGPASSWORD')) {
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($key, 'Process'))) { throw "Missing setting: $key" }
    }
    Push-Location (Join-Path $repoRoot 'services/webhook-service')
    try {
        $maven = if (Get-Command mvn.cmd -ErrorAction SilentlyContinue) { 'mvn.cmd' } else { '.\mvnw.cmd' }
        if ($DatabaseCheckOnly) {
            # Database-only health check; no RabbitMQ server is needed for this step.
            & $maven spring-boot:run '-Dspring-boot.run.arguments=--management.health.rabbit.enabled=false --spring.datasource.hikari.read-only=true'
        } elseif ($LocalDevelopment) {
            & $maven spring-boot:run '-Dspring-boot.run.profiles=local-webhooks'
        } else {
            & $maven spring-boot:run
        }
        if ($LASTEXITCODE -ne 0) { throw "Webhook service exited with code $LASTEXITCODE" }
    } finally { Pop-Location }
} finally {
    foreach ($key in $allowed) { [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process') }
}
