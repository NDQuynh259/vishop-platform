# =============================================================================
# ViShop Platform - Stop Containers (PowerShell)
# =============================================================================
[CmdletBinding()]
param(
    [ValidateSet("infra", "dev", "prod", "all")]
    [string]$Env = "dev",
    [switch]$Volumes
)

$ErrorActionPreference = "Stop"
$infraDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

$volumeFlag = if ($Volumes) { "-v" } else { "" }
Set-Location $infraDir

if ($Env -eq "infra" -or $Env -eq "all") {
    Write-Host ">>> Stopping INFRASTRUCTURE stack..." -ForegroundColor Yellow
    docker compose -f compose/docker-compose.infra.yml down $volumeFlag 2>$null
}

if ($Env -eq "dev" -or $Env -eq "all") {
    Write-Host ">>> Stopping DEVELOPMENT stack..." -ForegroundColor Yellow
    docker compose -f compose/docker-compose.dev.yml down $volumeFlag 2>$null
}

if ($Env -eq "prod" -or $Env -eq "all") {
    Write-Host ">>> Stopping PRODUCTION stack..." -ForegroundColor Yellow
    $envFile = if (Test-Path ".env.prod") { "--env-file .env.prod" } else { "" }
    docker compose $envFile -f compose/docker-compose.prod.yml down $volumeFlag 2>$null
}

Write-Host "`n✓ Services stopped for environment: $Env" -ForegroundColor Green
