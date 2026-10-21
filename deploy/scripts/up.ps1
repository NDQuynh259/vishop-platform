# =============================================================================
# ViShop Platform - Start Containers (PowerShell)
# =============================================================================
[CmdletBinding()]
param(
    [ValidateSet("dev", "prod")]
    [string]$Env = "dev",
    [switch]$Build
)

$ErrorActionPreference = "Stop"
$rootDir = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$deployDir = Join-Path $rootDir "deploy"

$buildFlag = if ($Build) { "--build" } else { "" }

Set-Location $deployDir

if ($Env -eq "dev") {
    Write-Host "==========================================================" -ForegroundColor Cyan
    Write-Host " Starting ViShop Platform [DEVELOPMENT Environment]" -ForegroundColor Cyan
    Write-Host " Profile: dev | Remote Debug Ports: 5000-5003" -ForegroundColor Cyan
    Write-Host " Compose: docker-compose.dev.yml" -ForegroundColor Cyan
    Write-Host "==========================================================" -ForegroundColor Cyan

    $envFile = if (Test-Path ".env.dev") { "--env-file .env.dev" } else { "" }
    docker compose $envFile -f docker-compose.dev.yml up -d $buildFlag
}
elseif ($Env -eq "prod") {
    Write-Host "==========================================================" -ForegroundColor Yellow
    Write-Host " Starting ViShop Platform [PRODUCTION Environment]" -ForegroundColor Yellow
    Write-Host " Profile: prod | Security Hardened | Resource Constrained" -ForegroundColor Yellow
    Write-Host " Compose: docker-compose.prod.yml" -ForegroundColor Yellow
    Write-Host "==========================================================" -ForegroundColor Yellow

    if (-not (Test-Path ".env.prod")) {
        Write-Warning "File .env.prod not found! Please create it from .env.prod.example first:"
        Write-Warning "Copy-Item deploy\.env.prod.example deploy\.env.prod"
        exit 1
    }

    docker compose --env-file .env.prod -f docker-compose.prod.yml up -d $buildFlag
}

Write-Host "`n✓ ViShop Platform started successfully in [$Env] mode." -ForegroundColor Green
