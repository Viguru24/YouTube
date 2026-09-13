@echo off
title Vixz Desktop Auto-Updater and Launcher
color 0B

echo ========================================================
echo        Vixz Desktop - Auto-Updater and Launcher
echo ========================================================
echo.

set "REPO_DIR=e:\Documents\GitHub\Youtube"
cd /d "%REPO_DIR%"

REM 1. Stop any currently running instance of Vixz
echo [*] Closing running Vixz Desktop instances...
taskkill /f /im VixzDesktop.exe >nul 2>&1

REM 2. Pull latest version from GitHub
echo [*] Pulling latest updates from GitHub (origin/main)...
git pull origin main
echo.

REM 3. Always ensure release executable exists
set "APP_EXE=%REPO_DIR%\windows\VixzDesktop\bin\Release\net9.0-windows\VixzDesktop.exe"
if not exist "%APP_EXE%" (
    set "APP_EXE=%REPO_DIR%\release\app_bin\VixzDesktop.exe"
)

if not exist "%APP_EXE%" (
    echo [*] Compiling release binaries...
    dotnet build "%REPO_DIR%\windows\VixzDesktop\VixzDesktop.csproj" -c Release
    set "APP_EXE=%REPO_DIR%\windows\VixzDesktop\bin\Release\net9.0-windows\VixzDesktop.exe"
)

if not exist "%APP_EXE%" (
    color 0C
    echo [ERROR] Could not find or build VixzDesktop.exe!
    pause
    exit /b 1
)

REM 4. Clear any cached web assets to guarantee fresh player scripts
if exist "%APPDATA%\VixzDesktop\WebAssets\player.html" (
    del /f /q "%APPDATA%\VixzDesktop\WebAssets\player.html" >nul 2>&1
)

REM 5. Launch the latest Vixz Desktop
echo [*] Launching latest Vixz Desktop...
start "" "%APP_EXE%"

echo [OK] Vixz Desktop launched successfully!
timeout /t 2 >nul 2>&1
exit /b 0
