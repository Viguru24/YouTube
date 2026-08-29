@echo off
title Build Vixz Desktop MSIX Installer
color 0B

echo ========================================================
echo         Building Vixz Desktop Windows MSIX Package
echo ========================================================
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0\windows\Package\build_msix.ps1"

if %ERRORLEVEL% NEQ 0 (
    color 0C
    echo.
    echo [!] MSIX Packaging Failed!
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [*] MSIX Package ready at: %~dp0VixzDesktop-v1.0.0.msix
echo.
pause
