$targetExe = "E:\Documents\GitHub\Youtube\windows\VixzDesktop\bin\Debug\net9.0-windows\VixzDesktop.exe"
$workingDir = "E:\Documents\GitHub\Youtube\windows\VixzDesktop\bin\Debug\net9.0-windows"
$iconPath = "E:\Documents\GitHub\Youtube\windows\VixzDesktop\App.ico"

$wscript = New-Object -ComObject WScript.Shell

$desktopPaths = @(
    [System.Environment]::GetFolderPath([System.Environment+SpecialFolder]::Desktop),
    "C:\Users\louis\Desktop"
) | Select-Object -Unique | Where-Object { Test-Path $_ }

foreach ($d in $desktopPaths) {
    # Remove any old/broken Vixz shortcuts
    Get-ChildItem -Path $d -Filter "*Vixz*" | Remove-Item -Force -ErrorAction SilentlyContinue

    $desktopLnk = [System.IO.Path]::Combine($d, "Vixz.lnk")
    $s1 = $wscript.CreateShortcut($desktopLnk)
    $s1.TargetPath = $targetExe
    $s1.WorkingDirectory = $workingDir
    $s1.IconLocation = "$iconPath,0"
    $s1.Description = "Vixz YouTube Desktop Player"
    $s1.Save()
    Write-Host "Created Desktop shortcut: $desktopLnk"
}

# Repo root shortcut
$repoLnk = "E:\Documents\GitHub\Youtube\Vixz Desktop.lnk"
$s2 = $wscript.CreateShortcut($repoLnk)
$s2.TargetPath = $targetExe
$s2.WorkingDirectory = $workingDir
$s2.IconLocation = "$iconPath,0"
$s2.Description = "Vixz YouTube Desktop Player"
$s2.Save()
Write-Host "Created Repo shortcut: $repoLnk"
