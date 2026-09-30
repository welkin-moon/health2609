param(
  [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path,
  [int]$Port = 18788,
  [string]$Token = $env:HEALTH2609_AGY_TOKEN
)

$ErrorActionPreference = "Stop"
if (-not (Get-Command node -ErrorAction SilentlyContinue)) { throw "node is not on PATH" }
if (-not (Get-Command agy -ErrorAction SilentlyContinue)) { throw "agy is not on PATH" }

$env:HEALTH2609_REPO_ROOT = $RepoRoot
$env:HEALTH2609_AGY_PORT = [string]$Port
if ($Token) { $env:HEALTH2609_AGY_TOKEN = $Token }

Set-Location $RepoRoot
Write-Host "health2609 AGY bridge -> 127.0.0.1:$Port"
Write-Host "The bridge will start one resident AGY stream session and preload the task contract."
node (Join-Path $RepoRoot "tools\agy-bridge\server.mjs")
