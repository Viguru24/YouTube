# check-stats.ps1
# Unified download monitor for YouTube (Vixz): GitHub Releases + Sovereign VPS OTA Downloads

$ErrorActionPreference = "SilentlyContinue"
$ProgressPreference = "SilentlyContinue"

Write-Host ""
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "  Vixz YouTube Player -- Unified Download Analytics     " -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host ""

# 1. Fetch Sovereign VPS OTA Metrics
Write-Host "[*] Querying Sovereign VPS OTA telemetry (api.cosmowhisper.com)..." -ForegroundColor Yellow
$VpsStatsUrl = "https://api.cosmowhisper.com/updates/youtube/stats"
$VpsStats = $null
try {
    $VpsStats = Invoke-RestMethod -Uri $VpsStatsUrl -TimeoutSec 6 -Headers @{"User-Agent"="VixzAnalytics"}
} catch {
    Write-Warning "Could not reach Sovereign VPS stats endpoint."
}

# 2. Fetch GitHub Releases Metrics
Write-Host "[*] Querying GitHub Releases API (Viguru24/YouTube)..." -ForegroundColor Yellow
$GhUrl = "https://api.github.com/repos/Viguru24/YouTube/releases"
$GhReleases = $null
try {
    $GhReleases = Invoke-RestMethod -Uri $GhUrl -TimeoutSec 6 -Headers @{"User-Agent"="Antigravity"}
} catch {
    Write-Warning "Could not reach GitHub Releases API."
}

# Process GitHub Totals
$ghTotal = 0
$ghRows = @()
if ($GhReleases) {
    foreach ($rel in $GhReleases) {
        $tag = $rel.tag_name
        $published = $rel.published_at
        if ($published -and $published.Length -ge 10) {
            $published = $published.Substring(0, 10)
        }
        foreach ($asset in $rel.assets) {
            $count = [int]$asset.download_count
            $ghTotal += $count
            $ghRows += [PSCustomObject]@{
                Channel    = "GitHub"
                Release    = $tag
                Date       = $published
                Downloads  = $count
                File       = $asset.name
            }
        }
    }
}

# Process VPS Totals
$vpsTotal = 0
$vpsRows = @()
if ($VpsStats) {
    $vpsTotal = [int]$VpsStats.totalDownloads
    if ($VpsStats.byVersion) {
        foreach ($prop in $VpsStats.byVersion.PSObject.Properties) {
            $vpsRows += [PSCustomObject]@{
                Channel    = "VPS (OTA)"
                Release    = "v$($prop.Name)"
                Date       = "OTA"
                Downloads  = [int]$prop.Value
                File       = "app-latest.apk"
            }
        }
    }
}

$grandTotal = $ghTotal + $vpsTotal

Write-Host ""
Write-Host "--------------------------------------------------------" -ForegroundColor DarkGray
Write-Host "  GRAND TOTAL DOWNLOADS:  " -NoNewline
Write-Host "$grandTotal" -ForegroundColor Green -NoNewline
Write-Host "  (GitHub: $ghTotal | Sovereign VPS: $vpsTotal)" -ForegroundColor Cyan
Write-Host "--------------------------------------------------------" -ForegroundColor DarkGray
Write-Host ""

# Show combined breakdown
$allRows = @()
$allRows += $vpsRows
$allRows += $ghRows

if ($allRows.Count -gt 0) {
    Write-Host "Detailed Breakdown by Channel and Release:" -ForegroundColor White
    $allRows | Format-Table -Property Channel, Release, Downloads, Date, File -AutoSize
}

# Show recent VPS OTA installs
if ($VpsStats -and $VpsStats.recentDownloads -and $VpsStats.recentDownloads.Count -gt 0) {
    Write-Host "Recent Sovereign VPS OTA Download Log:" -ForegroundColor White
    $VpsStats.recentDownloads | Select-Object -First 10 | ForEach-Object {
        $time = $_.timestamp
        $ver = $_.version
        $ip = $_.ip
        $ua = $_.userAgent
        if ($ua -and $ua.Length -gt 60) {
            $ua = $ua.Substring(0, 57) + "..."
        }
        [PSCustomObject]@{
            Timestamp = $time
            Version   = "v$ver"
            ClientIP  = $ip
            ClientUA  = $ua
        }
    } | Format-Table -AutoSize
}

Write-Host "Live Endpoints:" -ForegroundColor DarkGray
Write-Host "  - VPS Stats API:  https://api.cosmowhisper.com/updates/youtube/stats" -ForegroundColor Gray
Write-Host "  - VPS Hub Status: https://api.cosmowhisper.com/updates/status" -ForegroundColor Gray
Write-Host "  - GitHub Repo:    https://github.com/Viguru24/YouTube" -ForegroundColor Gray
Write-Host ""
