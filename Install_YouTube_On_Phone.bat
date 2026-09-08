@echo off
setlocal enabledelayedexpansion
title Install YouTube (Vixz) on Connected Devices
color 0B

echo ========================================================
echo      Install YouTube (Vixz) On Connected Devices
echo ========================================================
echo.

REM 1. Check if ADB is available
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
    echo [!] No authorized phone or tablet detected!
    echo.
    echo Please make sure:
    echo   1. Your devices are plugged into your PC via USB.
    echo   2. USB Debugging is turned ON in Developer Options on each device.
    echo   3. You tapped "Allow" on the USB debugging prompt on your device screens.
    echo.
    pause
    exit /b 1
)

echo.
echo [*] Total connected devices ready to update: %DEVICE_COUNT%
echo.

REM 3. Locate the latest compiled APK
set "APK_PATH=%~dp0app\build\outputs\apk\debug\Vixz-YouTube-Player-v1.9.7.apk"

if not exist "%APK_PATH%" (
    for /f "delims=" %%f in ('dir /b /s /o-d "%~dp0app\build\outputs\apk\debug\*.apk" 2^>nul') do (
        set "APK_PATH=%%f"
        goto :found_apk
    )
)

:found_apk
if not exist "%APK_PATH%" (
    echo [*] APK not found. Building YouTube debug APK now...
    call "%~dp0gradlew.bat" assembleDebug
    if errorlevel 1 (
        color 0C
        echo [!] Gradle build failed!
        pause
        exit /b 1
    )
    for /f "delims=" %%f in ('dir /b /s /o-d "%~dp0app\build\outputs\apk\debug\*.apk" 2^>nul') do (
        set "APK_PATH=%%f"
        goto :install_step
    )
)

:install_step
if "%APK_PATH%"=="" (
    color 0C
    echo [!] Unable to locate built APK.
    pause
    exit /b 1
)

echo [*] Target APK to install:
echo     %APK_PATH%
echo.

REM 4. Install APK and launch app on every connected device
for /l %%i in (1,1,%DEVICE_COUNT%) do (
    set "TARGET_DEV=!DEV_%%i!"
    set "TARGET_MODEL=!DEV_MODEL_%%i!"
    color 0E
    echo --------------------------------------------------------
    echo [*] [%%i/%DEVICE_COUNT%] Installing to !TARGET_MODEL! [!TARGET_DEV!]...
    adb -s !TARGET_DEV! install -r "%APK_PATH%"
    if errorlevel 1 (
        color 0C
        echo [!] Failed to install to !TARGET_MODEL! [!TARGET_DEV!].
    ) else (
        color 0A
        echo [*] Launching YouTube on !TARGET_MODEL!...
        adb -s !TARGET_DEV! shell am start -n com.aistudio.youtubeplayer.vixz/com.example.MainActivity >nul 2>nul
        echo [*] Successfully updated and launched on !TARGET_MODEL!!
    )
    echo --------------------------------------------------------
    echo.
)

color 0A
echo ========================================================
echo  [SUCCESS] Finished updating all connected device[s]!
echo ========================================================
echo.
pause
