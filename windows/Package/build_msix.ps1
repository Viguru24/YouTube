$ErrorActionPreference = "Stop"

$RepoRoot = Resolve-Path "$PSScriptRoot\..\.."
$ProjectDir = "$RepoRoot\windows\VixzDesktop"
$PackageDir = "$RepoRoot\windows\Package"
$PayloadDir = "$PackageDir\Payload"
$ImagesDir = "$PayloadDir\Images"
$PublishDir = "$RepoRoot\windows\Publish"

$MakeAppx = "C:\Program Files (x86)\Windows Kits\10\bin\10.0.26100.0\x64\makeappx.exe"
$SignTool = "C:\Program Files (x86)\Windows Kits\10\bin\10.0.26100.0\x64\signtool.exe"

$CertPass = "CosmoDev123!"
$CertSubject = "CN=E79B034B-3211-490A-96BE-648E426FE339"

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "         Building Vixz Desktop MSIX Package             " -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan

# 1. Clean Directories
if (Test-Path $PayloadDir) { Remove-Item $PayloadDir -Recurse -Force }
if (-not (Test-Path $PublishDir)) { New-Item -ItemType Directory -Force -Path $PublishDir | Out-Null }
New-Item -ItemType Directory -Force -Path $ImagesDir | Out-Null

# 2. Publish Self-Contained .NET 9 App
Write-Host "[1/5] Publishing .NET 9 Self-Contained Win-x64 Application..." -ForegroundColor Yellow
dotnet publish "$ProjectDir\VixzDesktop.csproj" -c Release -r win-x64 --self-contained true -o $PayloadDir
if ($LASTEXITCODE -ne 0) { throw "Dotnet publish failed!" }

# 3. Copy Manifest
Copy-Item "$PackageDir\Package.appxmanifest" "$PayloadDir\AppxManifest.xml"

# 4. Generate Visual Asset Logos
Write-Host "[2/5] Generating High-Resolution Application Assets..." -ForegroundColor Yellow
Add-Type -AssemblyName System.Drawing

function Generate-Logo {
    param($Width, $Height, $OutputPath, $IsWide = $false)
    $Bitmap = New-Object System.Drawing.Bitmap($Width, $Height)
    $Graphics = [System.Drawing.Graphics]::FromImage($Bitmap)
    $Graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $Graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $Graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

    # Background (#0B0B0E)
    $BgBrush = New-Object System.Drawing.SolidBrush([System.Drawing.ColorTranslator]::FromHtml("#0B0B0E"))
    $Graphics.FillRectangle($BgBrush, 0, 0, $Width, $Height)
    $BgBrush.Dispose()

    # Red Play Badge (#FF2A2A)
    $BadgeSize = [Math]::Min($Width, $Height) * 0.65
    $BadgeX = [int](($Width - $BadgeSize) / 2)
    $BadgeY = [int](($Height - $BadgeSize) / 2)

    # Draw rounded red rectangle
    $RedBrush = New-Object System.Drawing.SolidBrush([System.Drawing.ColorTranslator]::FromHtml("#FF2A2A"))
    $Radius = [int]($BadgeSize * 0.25)
    
    # Fill rounded badge area
    $Graphics.FillEllipse($RedBrush, $BadgeX, $BadgeY, $BadgeSize, $BadgeSize)
    $RedBrush.Dispose()

    # White Play Triangle
    $TriangleBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
    $CenterX = $Width / 2.0
    $CenterY = $Height / 2.0
    $TriW = $BadgeSize * 0.32
    $TriH = $BadgeSize * 0.38

    $Point1 = New-Object System.Drawing.PointF(($CenterX - $TriW/2 + ($TriW*0.1)), ($CenterY - $TriH/2))
    $Point2 = New-Object System.Drawing.PointF(($CenterX - $TriW/2 + ($TriW*0.1)), ($CenterY + $TriH/2))
    $Point3 = New-Object System.Drawing.PointF(($CenterX + $TriW/2 + ($TriW*0.1)), $CenterY)

    $Graphics.FillPolygon($TriangleBrush, @($Point1, $Point2, $Point3))
    $TriangleBrush.Dispose()

    if ($IsWide) {
        # Add Vixz Text to Splash / Wide tile
        $Font = New-Object System.Drawing.Font("Segoe UI", [float]($Height * 0.18), [System.Drawing.FontStyle]::Bold)
        $TextBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
        $StringFormat = New-Object System.Drawing.StringFormat
        $StringFormat.Alignment = [System.Drawing.StringAlignment]::Center
        $StringFormat.LineAlignment = [System.Drawing.StringAlignment]::Near
        $Graphics.DrawString("Vixz Desktop", $Font, $TextBrush, [float]($Width / 2.0), [float]($Height * 0.78), $StringFormat)
        $Font.Dispose()
        $TextBrush.Dispose()
    }

    $Bitmap.Save($OutputPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $Graphics.Dispose()
    $Bitmap.Dispose()
}

Generate-Logo 44 44 "$ImagesDir\Square44x44Logo.png"
Generate-Logo 150 150 "$ImagesDir\Square150x150Logo.png"
Generate-Logo 50 50 "$ImagesDir\StoreLogo.png"
Generate-Logo 310 150 "$ImagesDir\Wide310x150Logo.png" $true
Generate-Logo 620 300 "$ImagesDir\SplashScreen.png" $true

# 5. Pack MSIX
Write-Host "[3/5] Packaging MSIX Container..." -ForegroundColor Yellow
$MsixPath = "$PublishDir\VixzDesktop-v1.0.0.msix"
if (Test-Path $MsixPath) { Remove-Item $MsixPath -Force }

& $MakeAppx pack /d $PayloadDir /p $MsixPath /o
if ($LASTEXITCODE -ne 0) { throw "MakeAppx failed!" }

# 6. Use Machine-Trusted Certificate & Sign MSIX
Write-Host "[4/5] Signing MSIX with Machine-Trusted Certificate..." -ForegroundColor Yellow
$PfxPath = "$PackageDir\Vixz_Dev_Cert.pfx"
$SourcePfx = "E:\Onedrive\Documents\GitHub\CosmoWhisper-Native\CosmoWhisper-Package\CosmoWhisper_Key.pfx"

if (Test-Path $SourcePfx) {
    Copy-Item $SourcePfx $PfxPath -Force
} else {
    $Cert = New-SelfSignedCertificate -Type Custom -Subject $CertSubject `
        -KeyUsage DigitalSignature `
        -FriendlyName "Vixz Desktop Dev Certificate" `
        -CertStoreLocation "Cert:\CurrentUser\My" `
        -TextExtension @("2.5.29.37={text}1.3.6.1.5.5.7.3.3", "2.5.29.19={text}")

    $SecurePass = ConvertTo-SecureString -String $CertPass -Force -AsPlainText
    Export-PfxCertificate -Cert $Cert -FilePath $PfxPath -Password $SecurePass | Out-Null
}

# Sign the MSIX with trusted PFX
try {
    $CertObj = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2($PfxPath, $CertPass)
    $sig = Set-AuthenticodeSignature -FilePath $MsixPath -Certificate $CertObj -HashAlgorithm SHA256
    Write-Host "Signed with Set-AuthenticodeSignature: $($sig.Status)" -ForegroundColor Green
} catch {
    Write-Host "Signing error: $_" -ForegroundColor Red
}

# Copy MSIX to root for easy access
Copy-Item $MsixPath "$RepoRoot\VixzDesktop-v1.0.0.msix" -Force

Write-Host "========================================================" -ForegroundColor Green
Write-Host " ✅ MSIX Build Complete! " -ForegroundColor Green
Write-Host " 📦 Package: $RepoRoot\VixzDesktop-v1.0.0.msix" -ForegroundColor Green
Write-Host " 🔑 Certificate: $CerPath" -ForegroundColor Green
Write-Host "========================================================" -ForegroundColor Green
