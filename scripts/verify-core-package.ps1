$sourceRoot = Join-Path $PSScriptRoot "..\platform-common"
$legacyPackage = Get-ChildItem -Path $sourceRoot -Recurse -File -Filter "*.java" |
    Select-String -Pattern "com\.vishop"

if ($legacyPackage) {
    Write-Output "Found $($legacyPackage.Count) legacy package references in platform-common."
    $legacyPackage | Select-Object -First 10 | ForEach-Object {
        Write-Output "$($_.Path):$($_.LineNumber): $($_.Line.Trim())"
    }
    exit 1
}

Write-Output "No legacy com.vishop package references found in platform-common."
