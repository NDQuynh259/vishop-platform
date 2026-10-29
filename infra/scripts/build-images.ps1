[CmdletBinding()]
param(
    [string]$Tag = "latest",
    [switch]$NoCache
)

$ErrorActionPreference = "Stop"
$rootDir = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$services = @("api-gateway", "iam-service", "audit-service", "subscription-service")

Push-Location $rootDir
try {
    foreach ($serviceName in $services) {
        $imageName = "vishop/${serviceName}:$Tag"
        $buildArgs = @("build", "-t", $imageName, "-f", "infra/docker/Dockerfile.$serviceName")
        if ($NoCache) { $buildArgs += "--no-cache" }
        $buildArgs += "."
        Write-Host "Building $imageName" -ForegroundColor Cyan
        & docker @buildArgs
        if ($LASTEXITCODE -ne 0) { throw "Failed to build $imageName" }
    }
    Write-Host "All ViShop images built successfully." -ForegroundColor Green
}
finally {
    Pop-Location
}
