param([int]$Port = 18788)

$ErrorActionPreference = "Stop"

$token = [Environment]::GetEnvironmentVariable("HEALTH2609_AGY_TOKEN", "User")
if ([string]::IsNullOrWhiteSpace($token)) {
    $token = [Environment]::GetEnvironmentVariable("HEALTH2609_AGY_TOKEN", "Machine")
}
if ([string]::IsNullOrWhiteSpace($token)) {
    throw "HEALTH2609_AGY_TOKEN is not configured"
}

$env:HEALTH2609_AGY_TOKEN = $token
$env:HEALTH2609_AGY_PORT = [string]$Port
$env:Path = "C:\Users\meteo\AppData\Local\MixProcessProxy\bin;C:\Program Files\nodejs;" + $env:Path

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
Set-Location $repoRoot
& (Join-Path $PSScriptRoot "start.ps1") -Port $Port
exit $LASTEXITCODE
