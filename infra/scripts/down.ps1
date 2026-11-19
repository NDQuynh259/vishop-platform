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
. (Join-Path $PSScriptRoot "compose-env.ps1")
$infraDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

$volumeArgs = if ($Volumes) { @("-v") } else { @() }
$stacks = if ($Env -eq "all") { @("infra", "dev", "prod") } else { @($Env) }
$failedStacks = @()
Push-Location $infraDir
try {
    foreach ($stack in $stacks) {
        Write-Host ">>> Stopping $stack stack..." -ForegroundColor Yellow
        $envArgs = @(Get-ComposeEnvArgs -Stack $stack)
        & docker compose @envArgs -f "compose/docker-compose.$stack.yml" down @volumeArgs
        if ($LASTEXITCODE -ne 0) {
            $failedStacks += $stack
        }
    }
    if ($failedStacks.Count -gt 0) {
        throw "Docker Compose could not stop: $($failedStacks -join ', '). Check the Compose output and environment settings."
    }
}
finally {
    Pop-Location
}

Write-Host "`n[OK] Services stopped for environment: $Env" -ForegroundColor Green
