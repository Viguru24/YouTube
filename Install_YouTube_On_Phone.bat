@echo off
setlocal enabledelayedexpansion
title Install YouTube (Vixz) on Connected Devices
color 0B

echo ========================================================
echo      Install YouTube (Vixz) On Connected Devices
echo ========================================================
echo.

REM 1. Set repository root directory
set "REPO_DIR=E:\Documents\GitHub\Youtube\"
if not exist "%REPO_DIR%gradlew.bat" set "REPO_DIR=%~dp0"
cd /d "%REPO_DIR%"

REM 2. Check if ADB is available
where adb >nul 2>nul
if errorlevel 1 (
    if exist "%LOCALAPPDATA%\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe" (
        set "PATH=%PATH%;%LOCALAPPDATA%\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools"
    )
)

where adb >nul 2>nul
if errorlevel 1 (
    color 0C
    echo [ERROR] ADB is not found in your PATH.
    echo Please make sure Android SDK platform-tools is installed.
    echo.
    pause
    exit /b 1
)

REM 2. Enumerate all connected devices and models
echo [*] Scanning for connected phones and tablets...
set "DEVICE_COUNT=0"
for /f "skip=1 tokens=1,2" %%A in ('adb devices') do (
    if "%%B"=="device" (
        set /a DEVICE_COUNT+=1
        set "DEV_!DEVICE_COUNT!=%%A"
        set "MODEL=Unknown"
        for /f "delims=" %%M in ('adb -s %%A shell getprop ro.product.model 2^>nul') do (
            set "MODEL=%%M"
        )
        set "DEV_MODEL_!DEVICE_COUNT!=!MODEL!"
        echo     [!DEVICE_COUNT!] Found: !MODEL! [%%A]
    )
)

if %DEVICE_COUNT% EQU 0 (
    color 0C
    echo.
    echo [ERROR] No authorized phone or tablet detected!
    echo.
    echo If you are trying to run Vixz on this PC:
    echo   Use "Launch Vixz Desktop (Update & Run).bat" instead!
    echo.
    echo If you are trying to install on your Android phone/tablet:
    echo   1. Plug your phone into this PC using a USB cable.
    echo   2. On your phone: Go to Settings - Developer Options - Enable "USB Debugging".
    echo   3. Look at your phone screen: Tap "Allow USB Debugging" - check "Always allow".
    echo.
    pause
    exit /b 1
)

echo.
echo [*] Total connected devices ready to update: %DEVICE_COUNT%
echo.

REM 3. ALWAYS Compile fresh debug APK with latest code changes
echo [*] Clearing Kotlin compile cache to ensure latest code is compiled...
if exist "%REPO_DIR%app\build\tmp\kotlin-classes" (
    rmdir /s /q "%REPO_DIR%app\build\tmp\kotlin-classes"
)
if exist "%REPO_DIR%app\build\kotlin" (
    rmdir /s /q "%REPO_DIR%app\build\kotlin"
)
echo [*] Compiling latest Vixz Android changes (Debug APK)...
call "%REPO_DIR%gradlew.bat" assembleDebug
if errorlevel 1 (
    color 0C
    echo.
    echo [ERROR] Gradle build failed! Check compiler errors above.
    pause
    exit /b 1
)
echo [OK] Build succeeded.
echo.

set "APK_PATH="
for /f "delims=" %%f in ('dir /b /s /a-d /o-d "%REPO_DIR%app\build\outputs\apk\debug\*.apk" 2^>nul') do (
    if not defined APK_PATH set "APK_PATH=%%f"
)

if "%APK_PATH%"=="" (
    color 0C
    echo [ERROR] Unable to locate built APK.
    pause
    exit /b 1
)

echo [*] Target APK to install:
echo     %APK_PATH%
echo.

REM 4. Install APK and launch app on every connected device
for /l %%i in (1,1,%DEVICE_COUNT%) do (
    call set "TARGET_DEV=%%DEV_%%i%%"
    call set "TARGET_MODEL=%%DEV_MODEL_%%i%%"
    color 0E
    echo --------------------------------------------------------
    echo [*] [%%i/%DEVICE_COUNT%] Installing to !TARGET_MODEL! [!TARGET_DEV!]...
    adb -s !TARGET_DEV! install -r "%APK_PATH%"
    if errorlevel 1 (
        color 0C
        echo [ERROR] Failed to install to !TARGET_MODEL! [!TARGET_DEV!].
    ) else (
        color 0A
        echo [*] Launching YouTube on !TARGET_MODEL!...
        adb -s !TARGET_DEV! shell am start -n com.aistudio.youtubeplayer.vixz/com.example.MainActivity >nul 2>nul
        echo [*] Successfully updated and launched on !TARGET_MODEL!.
    )
    echo --------------------------------------------------------
    echo.
)

color 0A
echo ========================================================
echo  [SUCCESS] Finished updating all connected devices!
echo ========================================================
echo.
pause

