@echo off
setlocal enabledelayedexpansion
title Vixz Desktop - Update and Launch
color 0B

echo ========================================================
echo        Vixz Desktop - Update, Build and Launch
echo ========================================================
echo.

set "REPO_DIR=%~dp0"
if "%REPO_DIR:~-1%"=="\" set "REPO_DIR=%REPO_DIR:~0,-1%"
cd /d "%REPO_DIR%"

REM 1. Terminate any running VixzDesktop / sync processes and kill open ports (8088, 31415)
echo [*] Freeing open ports (8088, 31415) and stopping running instances...

powershell -NoProfile -ExecutionPolicy Bypass -Command "& { param([int[]]$ports) foreach ($p in $ports) { Get-NetTCPConnection -LocalPort $p -ErrorAction SilentlyContinue | ForEach-Object { try { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue } catch {} } } }" -ports 8088,31415 >nul 2>&1

for /f "tokens=5" %%a in ('netstat -aon 2^>nul ^| findstr /r /c:":8088 " /c:":31415 "') do (
    taskkill /f /pid %%a >nul 2>&1
)

taskkill /f /t /im VixzDesktop.exe >nul 2>&1
powershell -NoProfile -Command "Get-Process -Name VixzDesktop -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue; Get-CimInstance Win32_Process -Filter \"Name = 'msedgewebview2.exe'\" | Where-Object { $_.CommandLine -like '*VixzDesktop*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }; Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -like '*sync_server.py*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }" >nul 2>&1
timeout /t 1 /nobreak >nul 2>&1
echo [OK] Ports and existing instances cleared.
echo.

REM 2. Pull latest from GitHub
echo [*] Checking for updates from GitHub...
git pull origin main
if errorlevel 1 (
    color 0E
    echo [WARN] Git pull skipped or failed - continuing with local code.
    color 0B
)
echo.

REM 3. ALWAYS Compile fresh Release binaries with latest code changes
echo [*] Compiling latest Vixz Desktop changes (Release)...
dotnet build "%REPO_DIR%\windows\VixzDesktop\VixzDesktop.csproj" -c Release --nologo -v minimal
if errorlevel 1 (
    color 0C
    echo.
    echo [ERROR] Build failed! Check compiler errors above.
    pause
    exit /b 1
)
echo [OK] Build succeeded.
echo.

REM 4. Sync binaries to release\app_bin
set "BIN_DIR=%REPO_DIR%\windows\VixzDesktop\bin\Release\net9.0-windows"
set "APP_EXE=%BIN_DIR%\VixzDesktop.exe"

if exist "%REPO_DIR%\release\app_bin" (
    xcopy /y /q /s "%BIN_DIR%\*" "%REPO_DIR%\release\app_bin\" >nul 2>&1
)

REM 5. Delete cached player.html so updated scripts load fresh
if exist "%APPDATA%\VixzDesktop\WebAssets\player.html" (
    del /f /q "%APPDATA%\VixzDesktop\WebAssets\player.html" >nul 2>&1
    echo [*] Cleared cached player assets.
)

REM 6. Launch the newly built app
echo [*] Launching updated Vixz Desktop...
start "" /d "%BIN_DIR%" "%APP_EXE%"

echo [OK] Vixz Desktop launched!
timeout /t 2 /nobreak >nul 2>&1
exit /b 0
