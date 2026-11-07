@echo off
title Vixz Download Analytics Monitor
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0check-stats.ps1"
echo.
pause
