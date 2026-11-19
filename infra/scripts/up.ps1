# ViShop Platform - Start Containers (PowerShell)
[CmdletBinding()]
param(
    [ValidateSet("infra", "dev", "prod")]
    [string]$Env = "dev",
    [switch]$Build,
    [string[]]$Service = @(),
    [ValidateRange(1, 3600)]
    [int]$WaitTimeout = 180
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "compose-env.ps1")
$infraDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$composeFile = "compose/docker-compose.$Env.yml"
$buildArgs = @()
if ($Build) {
    $buildArgs = @("--build")
}

Push-Location $infraDir
try {
    $envArgs = @(Get-ComposeEnvArgs -Stack $Env)

    Write-Host "Starting ViShop Platform [$Env] using $composeFile" -ForegroundColor Cyan
    if ($Env -ne "infra") {
        Write-Host "Run IAM, audit and subscription migrations against this environment's databases before starting."
    }

    & docker compose @envArgs -f $composeFile up -d @buildArgs --wait --wait-timeout $WaitTimeout @Service
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose failed (exit code $LASTEXITCODE). Check container logs and healthchecks; IAM/audit/subscription databases must be migrated before startup."
    }

    Write-Host "`n[OK] ViShop Platform started successfully in [$Env] mode; Compose healthchecks passed." -ForegroundColor Green
}
finally {
    Pop-Location
}
