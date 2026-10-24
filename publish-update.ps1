# publish-update.ps1
# Automates compiling and publishing OTA builds to the Sovereign VPS update server for YouTube for Android (Vixz).

param(
    [string]$Notes = ""
)

$AdminKey = "test_secret_4666"
$AppSlug = "youtube"
$UploadUrl = "https://api.cosmowhisper.com/updates/$AppSlug/upload"

Set-Location $PSScriptRoot

Write-Host ""
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "  YouTube (Vixz) Sovereign OTA Publisher" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host ""

# 1. Automatically increment versionCode in build.gradle.kts so phones recognize the new build
$GradleFile = Join-Path $PSScriptRoot "app\build.gradle.kts"
if (Test-Path $GradleFile) {
    $content = Get-Content -Path $GradleFile -Raw
    if ($content -match 'versionCode\s*=\s*(\d+)') {
        $oldCode = [int]$matches[1]
        $newCode = $oldCode + 1
        $content = $content -replace "versionCode\s*=\s*$oldCode", "versionCode = $newCode"
        Set-Content -Path $GradleFile -Value $content -NoNewline
        Write-Host "[+] Auto-incremented versionCode: $oldCode -> $newCode" -ForegroundColor Green
    }
}

Write-Host "[*] Compiling latest APK build with Gradle..." -ForegroundColor Yellow
& .\gradlew.bat assembleDebug

if ($LASTEXITCODE -ne 0) {
    Write-Error "[!] Compilation failed! Build abandoned."
    exit $LASTEXITCODE
}

$BuildOutputsJson = Join-Path $PSScriptRoot "app\build\outputs\apk\debug\output-metadata.json"
$ApkPath = $null

if (Test-Path $BuildOutputsJson) {
    try {
        $Metadata = Get-Content $BuildOutputsJson | ConvertFrom-Json
        $outFile = $Metadata.elements[0].outputFile
        $candidate = Join-Path $PSScriptRoot "app\build\outputs\apk\debug\$outFile"
        if (Test-Path $candidate) {
            $ApkPath = $candidate
        }
        $VersionCode = $Metadata.elements[0].versionCode
        $VersionName = $Metadata.elements[0].versionName
    } catch {
        Write-Warning "Could not parse output-metadata.json, falling back to directory search."
    }
}

if (-not $ApkPath -or -not (Test-Path $ApkPath)) {
    $found = Get-ChildItem -Path (Join-Path $PSScriptRoot "app\build\outputs\apk\debug") -Filter "*.apk" -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($found) {
        $ApkPath = $found.FullName
    }
}

if (-not $ApkPath -or -not (Test-Path $ApkPath)) {
    Write-Error "[!] Could not locate compiled APK!"
    exit 1
}

if (-not $VersionCode) {
    $VersionCode = 39
}
if (-not $VersionName) {
    $VersionName = "2.0.0"
}

if ([string]::IsNullOrWhiteSpace($Notes)) {
    Write-Host ""
    Write-Host "Release Notes (or press Enter for default 'Sovereign OTA updates and performance improvements'):" -ForegroundColor Cyan
    $InputNotes = Read-Host
    if ([string]::IsNullOrWhiteSpace($InputNotes)) {
        $ReleaseNotes = "Sovereign Over-The-Air (OTA) updates, background sync, and performance improvements."
    } else {
        $ReleaseNotes = $InputNotes
    }
} else {
    $ReleaseNotes = $Notes
}

Write-Host ""
Write-Host "[*] Build Ready:" -ForegroundColor Green
Write-Host "    Version: $VersionName (code: $VersionCode)"
Write-Host "    File:    $ApkPath"
Write-Host "    Notes:   $ReleaseNotes"
Write-Host ""

Write-Host "[*] Uploading to VPS ($UploadUrl)..." -ForegroundColor Yellow

try {
    $curlOut = & curl.exe -s -S -X POST -H "x-admin-key: $AdminKey" -F "apk=@$ApkPath" -F "appId=$AppSlug" -F "versionCode=$VersionCode" -F "versionName=$VersionName" -F "releaseNotes=$ReleaseNotes" $UploadUrl
    $ServerResponse = $curlOut | ConvertFrom-Json
    if ($ServerResponse.success) {
        Write-Host ""
        Write-Host "[OK] OTA DEPLOYMENT SUCCESSFUL!" -ForegroundColor Green
        Write-Host "     Message: $($ServerResponse.message)" -ForegroundColor Cyan
        Write-Host "     VPS has published the new APK package." -ForegroundColor Green
        Write-Host "     Android devices will automatically detect and prompt to update." -ForegroundColor White
        Write-Host ""
    } else {
        Write-Error "[!] Upload rejected by server: $curlOut"
    }
} catch {
    Write-Warning ""
    Write-Warning "Note: Verify that api.cosmowhisper.com /updates endpoint is active."
    Write-Error "[!] Server deployment upload failed: $_"
}
