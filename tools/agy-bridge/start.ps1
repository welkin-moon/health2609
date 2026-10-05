param(
  [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path,
  [int]$Port = 18788,
  [string]$Token = [Environment]::GetEnvironmentVariable('HEALTH2609_AGY_TOKEN', 'User')
)

$ErrorActionPreference = "Stop"
if (-not (Get-Command node -ErrorAction SilentlyContinue)) { throw "node is not on PATH" }
if (-not $Token) { $Token = $env:HEALTH2609_AGY_TOKEN }
if (-not $Token) { throw 'HEALTH2609_AGY_TOKEN is not configured' }
foreach ($name in @('HEALTH2609_AGY_MODEL','HEALTH2609_AGY_EFFORT')) {
  $configured = [Environment]::GetEnvironmentVariable($name, 'User')
  if ($configured) { [Environment]::SetEnvironmentVariable($name, $configured, 'Process') }
}

$env:HEALTH2609_REPO_ROOT = $RepoRoot
$env:HEALTH2609_AGY_PORT = [string]$Port
if ($Token) { $env:HEALTH2609_AGY_TOKEN = $Token }

Set-Location $RepoRoot
Write-Host "health2609 AGY bridge -> 127.0.0.1:$Port"
Write-Host "The bridge will start one resident AGY stream session and preload the task contract."
node (Join-Path $RepoRoot "tools\agy-bridge\server.mjs")
