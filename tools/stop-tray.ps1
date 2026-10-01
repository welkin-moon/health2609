$ErrorActionPreference = "SilentlyContinue"

Write-Host "[Health2609] Stopping Health2609AgyTray and bridge processes..." -ForegroundColor Cyan

Get-Process -Name "Health2609AgyTray" | ForEach-Object {
    Write-Host "Stopping Health2609AgyTray (PID: $($_.Id))..."
    Stop-Process -Id $_.Id -Force
}

# Kill any orphaned node server.mjs running on 18788
$conns = Get-NetTCPConnection -LocalPort 18788 -ErrorAction SilentlyContinue
foreach ($conn in $conns) {
    if ($conn.OwningProcess) {
        Write-Host "Killing process on port 18788 (PID: $($conn.OwningProcess))..."
        Stop-Process -Id $conn.OwningProcess -Force -ErrorAction SilentlyContinue
    }
}

Write-Host "[Health2609] Stopped." -ForegroundColor Green
