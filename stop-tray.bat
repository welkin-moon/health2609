@echo off
setlocal
cd /d "%~dp0"
powershell -ExecutionPolicy Bypass -File "tools\stop-tray.ps1"
