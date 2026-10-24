# =============================================================================
# ViShop Platform - Start Containers (PowerShell)
# =============================================================================
[CmdletBinding()]
param(
    [ValidateSet("infra", "dev", "prod")]
    [string]$Env = "dev",
    [switch]$Build
)

$ErrorActionPreference = "Stop"
$infraDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

$buildFlag = if ($Build) { "--build" } else { "" }

Set-Location $infraDir

if ($Env -eq "infra") {
    Write-Host "==========================================================" -ForegroundColor Cyan
    Write-Host " Starting ViShop Core INFRASTRUCTURE" -ForegroundColor Cyan
    Write-Host " Running: Postgres 16, Kafka, Redis 7, Jaeger, Prometheus, Loki, Grafana" -ForegroundColor Cyan
    Write-Host " Compose: compose/docker-compose.infra.yml" -ForegroundColor Cyan
    Write-Host "==========================================================" -ForegroundColor Cyan

    docker compose -f compose/docker-compose.infra.yml up -d $buildFlag
}
elseif ($Env -eq "dev") {
    Write-Host "==========================================================" -ForegroundColor Cyan
    Write-Host " Starting ViShop Platform [DEVELOPMENT Environment]" -ForegroundColor Cyan
    Write-Host " Profile: dev | Remote Debug Ports: 5000-5003" -ForegroundColor Cyan
    Write-Host " Compose: compose/docker-compose.dev.yml" -ForegroundColor Cyan
    Write-Host "==========================================================" -ForegroundColor Cyan

    $envFile = if (Test-Path ".env.dev") { "--env-file .env.dev" } else { "" }
    docker compose $envFile -f compose/docker-compose.dev.yml up -d $buildFlag
}
elseif ($Env -eq "prod") {
    Write-Host "==========================================================" -ForegroundColor Yellow
    Write-Host " Starting ViShop Platform [PRODUCTION Environment]" -ForegroundColor Yellow
    Write-Host " Profile: prod | Security Hardened | Resource Constrained" -ForegroundColor Yellow
    Write-Host " Compose: compose/docker-compose.prod.yml" -ForegroundColor Yellow
    Write-Host "==========================================================" -ForegroundColor Yellow

    if (-not (Test-Path ".env.prod")) {
        Write-Warning "File .env.prod not found! Please create it from .env.prod.example first:"
        Write-Warning "Copy-Item infra\.env.prod.example infra\.env.prod"
        exit 1
    }

    docker compose --env-file .env.prod -f compose/docker-compose.prod.yml up -d $buildFlag
}

Write-Host "`n✓ ViShop Platform started successfully in [$Env] mode." -ForegroundColor Green
