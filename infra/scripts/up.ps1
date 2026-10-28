# ViShop Platform - Start Containers (PowerShell)
[CmdletBinding()]
param(
    [ValidateSet("infra", "dev", "prod")]
    [string]$Env = "dev",
    [switch]$Build,
    [ValidateRange(1, 3600)]
    [int]$WaitTimeout = 180
)

$ErrorActionPreference = "Stop"
$infraDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$composeFile = "compose/docker-compose.$Env.yml"
$buildArgs = @()
if ($Build) {
    $buildArgs = @("--build")
}

Push-Location $infraDir
try {
    $envArgs = @()
    if ($Env -eq "prod") {
        if (-not (Test-Path -LiteralPath ".env.prod")) {
            throw "File infra/.env.prod not found. Configure production environment variables in this file before starting."
        }
        $envArgs = @("--env-file", ".env.prod")
    }
    elseif ($Env -eq "dev" -and (Test-Path -LiteralPath ".env.dev")) {
        $envArgs = @("--env-file", ".env.dev")
    }

    Write-Host "Starting ViShop Platform [$Env] using $composeFile" -ForegroundColor Cyan
    if ($Env -ne "infra") {
        Write-Host "Run IAM and audit migrations against this environment's databases before starting."
    }

    & docker compose @envArgs -f $composeFile up -d @buildArgs --wait --wait-timeout $WaitTimeout
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose failed (exit code $LASTEXITCODE). Check container logs and healthchecks; IAM/audit databases must be migrated before startup."
    }

    Write-Host "`n[OK] ViShop Platform started successfully in [$Env] mode; Compose healthchecks passed." -ForegroundColor Green
}
finally {
    Pop-Location
}
