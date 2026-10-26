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

$volumeArgs = if ($Volumes) { @("-v") } else { @() }
Set-Location $infraDir

if ($Env -eq "infra" -or $Env -eq "all") {
    Write-Host ">>> Stopping INFRASTRUCTURE stack..." -ForegroundColor Yellow
    & docker compose -f compose/docker-compose.infra.yml down @volumeArgs 2>$null
}

if ($Env -eq "dev" -or $Env -eq "all") {
    Write-Host ">>> Stopping DEVELOPMENT stack..." -ForegroundColor Yellow
    $envArgs = if (Test-Path ".env.dev") { @("--env-file", ".env.dev") } else { @() }
    & docker compose @envArgs -f compose/docker-compose.dev.yml down @volumeArgs 2>$null
}

if ($Env -eq "prod" -or $Env -eq "all") {
    Write-Host ">>> Stopping PRODUCTION stack..." -ForegroundColor Yellow
    $envArgs = if (Test-Path ".env.prod") { @("--env-file", ".env.prod") } else { @() }
    & docker compose @envArgs -f compose/docker-compose.prod.yml down @volumeArgs 2>$null
}

Write-Host "`n[OK] Services stopped for environment: $Env" -ForegroundColor Green
