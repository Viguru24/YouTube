Add-Type -AssemblyName System.Drawing
$imgPath = "C:\Users\louis\.gemini\antigravity\brain\f44dd137-f808-48fd-8036-3a5abcd8108e\vixz_logo_concept_1_1787214285873.jpg"
$bmp = [System.Drawing.Bitmap]::FromFile($imgPath)

$destPng1 = "e:\Onedrive\Documents\GitHub\Youtube\windows\VixzDesktop\App.png"
$destPng2 = "e:\Onedrive\Documents\GitHub\VixzDesktop\src\VixzDesktop\App.png"

$bmp.Save($destPng1, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Save($destPng2, [System.Drawing.Imaging.ImageFormat]::Png)

Write-Output "App.png saved successfully!"
