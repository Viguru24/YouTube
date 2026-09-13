@echo off
setlocal enabledelayedexpansion
title Vixz Desktop - Auto Updater & Launcher
color 0B

echo ========================================================
echo        Vixz Desktop - Auto-Updater & Launcher
echo ========================================================
echo.

cd /d "%~dp0"

REM 1. Stop any currently running instance of Vixz
echo [*] Checking for running Vixz Desktop instances...
taskkill /f /im VixzDesktop.exe >nul 2>&1

REM 2. Pull latest version from GitHub
echo [*] Pulling latest updates from GitHub (origin/main)...
git pull origin main
if errorlevel 1 (
    echo [!] Notice: Git pull encountered a warning or offline state. Continuing with local build...
) else (
    echo [OK] Synced with latest GitHub version!
)
echo.

REM 3. Ensure latest binary is compiled and published
set "APP_EXE=%~dp0release\app_bin\VixzDesktop.exe"
if not exist "%APP_EXE%" (
    echo [*] Compiling latest release binaries...
    dotnet publish "%~dp0windows\VixzDesktop\VixzDesktop.csproj" -c Release -r win-x64 --self-contained false -o "%~dp0release\app_bin"
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

echo [OK] Vixz Desktop launched!
timeout /t 3 >nul
exit /b 0
