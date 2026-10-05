$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Resolve-Path "$scriptDir\.."
$trayExe = "$repoRoot\tools\agy-tray\publish\Health2609AgyTray.exe"

if (-not (Test-Path $trayExe)) {
    Write-Host "[Health2609] Building tray application..." -ForegroundColor Cyan
    dotnet publish "$repoRoot\tools\agy-tray\Health2609AgyTray.csproj" -c Release -r win-x64 --self-contained false -o "$repoRoot\tools\agy-tray\publish"
}

$running = Get-Process -Name "Health2609AgyTray" -ErrorAction SilentlyContinue
if ($running) {
    Write-Host "[Health2609] Health2609AgyTray is already running (PID: $($running.Id))." -ForegroundColor Green
    exit 0
}

Write-Host "[Health2609] Launching AGY Bridge Tray Service..." -ForegroundColor Cyan
$env:HEALTH2609_REPO_ROOT = [string]$repoRoot
Start-Process -FilePath $trayExe -WorkingDirectory ([string]$repoRoot) -WindowStyle Hidden
Write-Host "[Health2609] Tray started. Verify http://127.0.0.1:18788/healthz before using photo recognition."
