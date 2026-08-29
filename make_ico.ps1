Add-Type -AssemblyName System.Drawing
$imgPath = "E:\Documents\GitHub\Youtube\windows\VixzDesktop\App.png"
$bmp = [System.Drawing.Bitmap]::FromFile($imgPath)
$resized = New-Object System.Drawing.Bitmap($bmp, 256, 256)
$hIcon = $resized.GetHicon()
$icon = [System.Drawing.Icon]::FromHandle($hIcon)

$destIco1 = "E:\Documents\GitHub\Youtube\windows\VixzDesktop\App.ico"
$stream1 = [System.IO.File]::Create($destIco1)
$icon.Save($stream1)
$stream1.Close()
$bmp.Dispose()
$resized.Dispose()

Write-Output "App.ico created successfully!"
