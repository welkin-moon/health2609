@echo off
setlocal
cd /d "%~dp0"
powershell -ExecutionPolicy Bypass -File "tools\start-tray.ps1"
