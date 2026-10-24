@echo off
setlocal
title Publish YouTube (Vixz) Sovereign OTA Update
color 0C
echo ========================================================
echo   Publishing YouTube (Vixz) Over-The-Air Update
echo ========================================================
echo.
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File .\publish-update.ps1
echo.
pause
