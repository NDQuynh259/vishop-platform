# =============================================================================
# ViShop Platform - Build All Docker Images (PowerShell)
# =============================================================================
[CmdletBinding()]
param(
    [string]$Tag = "latest",
    [switch]$NoCache
)

$ErrorActionPreference = "Stop"
$rootDir = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Building ViShop Platform Docker Images (Tag: $Tag)" -ForegroundColor Cyan
Write-Host " Context root: $rootDir" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$services = @(
    @{ Name = "api-gateway"; Dockerfile = "infra/docker/Dockerfile.api-gateway" },
    @{ Name = "identity-service"; Dockerfile = "infra/docker/Dockerfile.identity-service" },
    @{ Name = "tenant-service"; Dockerfile = "infra/docker/Dockerfile.tenant-service" },
    @{ Name = "audit-service"; Dockerfile = "infra/docker/Dockerfile.audit-service" }
)

$cacheArg = if ($NoCache) { "--no-cache" } else { "" }

foreach ($svc in $services) {
    $imageName = "vishop/$($svc.Name):$Tag"
    Write-Host "`n>>> Building $imageName using $($svc.Dockerfile)..." -ForegroundColor Yellow
    
    $dockerCmd = "docker build $cacheArg -t $imageName -f $($svc.Dockerfile) ."
    Push-Location $rootDir
    try {
        Invoke-Expression $dockerCmd
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to build image $imageName"
        }
        Write-Host "✓ Successfully built $imageName" -ForegroundColor Green
    }
    finally {
        Pop-Location
    }
}

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host " All ViShop images built successfully!" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan
