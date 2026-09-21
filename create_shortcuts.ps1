$repoRoot = $PSScriptRoot
$targetExe = Join-Path $repoRoot "windows\VixzDesktop\bin\Debug\net9.0-windows\VixzDesktop.exe"
$workingDir = Join-Path $repoRoot "windows\VixzDesktop\bin\Debug\net9.0-windows"
$iconPath = Join-Path $repoRoot "windows\VixzDesktop\App.ico"

$wscript = New-Object -ComObject WScript.Shell

$desktopPaths = @(
    [System.Environment]::GetFolderPath([System.Environment+SpecialFolder]::Desktop)
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

$repoLnk = Join-Path $repoRoot "Vixz Desktop.lnk"
$s2 = $wscript.CreateShortcut($repoLnk)
$s2.TargetPath = $targetExe
$s2.WorkingDirectory = $workingDir
$s2.IconLocation = "$iconPath,0"
$s2.Description = "Vixz YouTube Desktop Player"
$s2.Save()
Write-Host "Created Repo shortcut: $repoLnk"
