@echo off
title Vixz - Rollback OTA to Stable
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0publish-update.ps1" -RollbackToStable
pause
