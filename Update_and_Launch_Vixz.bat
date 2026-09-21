@echo off
title Vixz Desktop - Update and Launch
color 0B

echo ========================================================
echo        Vixz Desktop - Update and Launch
echo ========================================================
echo.

set "REPO_DIR=e:\Documents\GitHub\Youtube"
cd /d "%REPO_DIR%"

REM 1. Terminate any running Vixz and WebView2 processes
echo [*] Stopping any running Vixz instances...
taskkill /f /t /im VixzDesktop.exe >nul 2>&1
taskkill /f /t /im msedgewebview2.exe >nul 2>&1
ping 127.0.0.1 -n 2 >nul 2>&1

REM 3. Pull latest from GitHub
echo [*] Pulling latest from GitHub...
git pull origin main
if errorlevel 1 (
    color 0E
    echo [WARN] Git pull failed - continuing with local version.
    color 0B
)
echo.

REM 4. Build
echo [*] Building...
dotnet build "%REPO_DIR%\windows\VixzDesktop\VixzDesktop.csproj" -c Release --nologo -v quiet
if errorlevel 1 (
    color 0C
    echo.
    echo [ERROR] Build failed! Check errors above.
    pause
    exit /b 1
)
echo [OK] Build succeeded.
echo.

REM 5. Sync to release\app_bin if it exists
set "BIN_DIR=%REPO_DIR%\windows\VixzDesktop\bin\Release\net9.0-windows"
set "APP_EXE=%BIN_DIR%\VixzDesktop.exe"

if exist "%REPO_DIR%\release\app_bin" (
    xcopy /y /q /s "%BIN_DIR%\*" "%REPO_DIR%\release\app_bin\" >nul 2>&1
)

REM 6. Delete cached player.html so updated JS is always picked up on next launch
if exist "%APPDATA%\VixzDesktop\WebAssets\player.html" (
    del /f /q "%APPDATA%\VixzDesktop\WebAssets\player.html" >nul 2>&1
    echo [*] Cleared cached player.html.
)

REM 7. Launch
echo [*] Launching Vixz Desktop...
start "" "%APP_EXE%"

echo [OK] Done!
ping 127.0.0.1 -n 3 >nul 2>&1
exit /b 0
