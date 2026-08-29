@echo off
title Vixz YouTube Desktop [DEV MODE]
color 0A

echo ========================================================
echo        Vixz - Native YouTube Desktop [DEV MODE]
echo ========================================================
echo.

cd /d "%~dp0\windows\VixzDesktop"

echo [*] Restoring and Building Vixz Desktop...
dotnet build -c Debug
if %ERRORLEVEL% NEQ 0 (
    color 0C
    echo.
    echo [!] Build Failed! Please check the compiler errors above.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [*] Launching Vixz YouTube Desktop...
start "" "%~dp0\windows\VixzDesktop\bin\Debug\net9.0-windows\VixzDesktop.exe"

echo [*] App launched successfully.
timeout /t 3 >nul
exit
