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
$cmd = "`"$trayExe`""
$res = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{ CommandLine = $cmd }

if ($res.ReturnValue -eq 0) {
    Write-Host "[Health2609] Tray service launched successfully (PID: $($res.ProcessId))." -ForegroundColor Green
    Write-Host "[Health2609] Check the system tray icon at the bottom-right corner of your taskbar." -ForegroundColor Green
} else {
    Write-Host "[Health2609] Failed to launch tray service via WMI. Falling back to Start-Process..." -ForegroundColor Yellow
    Start-Process -FilePath $trayExe -WorkingDirectory "$repoRoot\tools\agy-tray\publish"
}
