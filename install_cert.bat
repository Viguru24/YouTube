@echo off
title Install Vixz Desktop Certificate
color 0A

echo ========================================================
echo       Trust Vixz Desktop Certificate for MSIX
echo ========================================================
echo.
echo Installing developer certificate to Trusted Root store...
echo (You may see a Windows prompt asking to confirm trust)
echo.

certutil -addstore -user Root "%~dp0\windows\Publish\Vixz_Dev_Cert.cer"

if %ERRORLEVEL% EQU 0 (
    echo.
    echo [*] Certificate installed and trusted! You can now double-click VixzDesktop-v1.0.0.msix to install.
) else (
    color 0C
    echo [!] Failed to install certificate.
)

echo.
pause
