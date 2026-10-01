$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Resolve-Path "$scriptDir\.."
$trayExe = "$repoRoot\tools\agy-tray\publish\Health2609AgyTray.exe"

if (-not (Test-Path $trayExe)) {
    Write-Host "[Health2609] Building tray application..." -ForegroundColor Cyan
    dotnet publish "$repoRoot\tools\agy-tray\Health2609AgyTray.csproj" -c Release -r win-x64 --self-contained false -o "$repoRoot\tools\agy-tray\publish"
}

$runKey = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Run"
$name = "Health2609AgyTray"
$value = "`"$trayExe`""

Set-ItemProperty -Path $runKey -Name $name -Value $value
Write-Host "[Health2609] Successfully registered auto-start in registry: $runKey -> $name = $value" -ForegroundColor Green
