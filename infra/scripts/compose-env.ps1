function Get-ComposeEnvArgs {
    param([ValidateSet("infra", "dev", "prod")][string]$Stack)

    $infraDir = Split-Path -Parent $PSScriptRoot
    $profile = if ($Stack -eq "prod") { "prod" } else { "dev" }
    $files = @(Join-Path $infraDir ".env.$profile")
    if ($Stack -ne "infra") {
        foreach ($service in @("iam-service", "api-gateway", "audit-service", "subscription-service")) {
            $files += Join-Path $infraDir "../services/$service/.env.$profile"
        }
    }

    foreach ($file in $files) {
        if (Test-Path -LiteralPath $file -PathType Leaf) {
            "--env-file"
            (Resolve-Path -LiteralPath $file).Path
        }
    }
}
