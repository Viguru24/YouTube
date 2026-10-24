@echo off
setlocal enabledelayedexpansion
title Vixz Desktop - Quick Launch
color 0A

echo ========================================================
echo             Vixz Desktop - Quick Launch
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

REM 2. Locate compiled executable
set "BIN_DIR=%REPO_DIR%\windows\VixzDesktop\bin\Release\net9.0-windows"
set "APP_EXE=%BIN_DIR%\VixzDesktop.exe"

if not exist "%APP_EXE%" (
    set "BIN_DIR=%REPO_DIR%\windows\VixzDesktop\bin\Debug\net9.0-windows"
    set "APP_EXE=%BIN_DIR%\VixzDesktop.exe"
)

if not exist "%APP_EXE%" (
    set "BIN_DIR=%REPO_DIR%\release\app_bin"
    set "APP_EXE=%BIN_DIR%\VixzDesktop.exe"
)

if not exist "%APP_EXE%" (
    echo [WARN] Compiled binary not found. Performing one-time build...
    dotnet build "%REPO_DIR%\windows\VixzDesktop\VixzDesktop.csproj" -c Release --nologo -v minimal
    set "BIN_DIR=%REPO_DIR%\windows\VixzDesktop\bin\Release\net9.0-windows"
    set "APP_EXE=%BIN_DIR%\VixzDesktop.exe"
)

if not exist "%APP_EXE%" (
    color 0C
    echo [ERROR] Could not find or build VixzDesktop.exe.
    pause
    exit /b 1
)

REM 3. Launch the app directly
echo [*] Launching Vixz Desktop...
start "" /d "%BIN_DIR%" "%APP_EXE%"

echo [OK] Vixz Desktop launched!
timeout /t 2 /nobreak >nul 2>&1
exit /b 0
