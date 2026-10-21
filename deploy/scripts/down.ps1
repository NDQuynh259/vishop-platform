# =============================================================================
# ViShop Platform - Stop Containers (PowerShell)
# =============================================================================
[CmdletBinding()]
param(
    [ValidateSet("dev", "prod", "all")]
    [string]$Env = "dev",
    [switch]$Volumes
)

$ErrorActionPreference = "Stop"
$rootDir = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$deployDir = Join-Path $rootDir "deploy"

$volumeFlag = if ($Volumes) { "-v" } else { "" }
Set-Location $deployDir

if ($Env -eq "dev" -or $Env -eq "all") {
    Write-Host ">>> Stopping DEVELOPMENT stack..." -ForegroundColor Yellow
    docker compose -f docker-compose.dev.yml down $volumeFlag 2>$null
}

if ($Env -eq "prod" -or $Env -eq "all") {
    Write-Host ">>> Stopping PRODUCTION stack..." -ForegroundColor Yellow
    $envFile = if (Test-Path ".env.prod") { "--env-file .env.prod" } else { "" }
    docker compose $envFile -f docker-compose.prod.yml down $volumeFlag 2>$null
}

Write-Host "`n✓ Services stopped for environment: $Env" -ForegroundColor Green
