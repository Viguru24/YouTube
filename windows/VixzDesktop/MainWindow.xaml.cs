using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media.Animation;
using System.Windows.Threading;
using Microsoft.Web.WebView2.Core;
using VixzDesktop.Models;
using VixzDesktop.Services;

namespace VixzDesktop
{
    public partial class MainWindow : Window
    {
        // Shared WebView2 environment — reused by PopOutPlayerWindow to avoid the
        // "Class not registered" COM error that occurs when two environments point
        // at the same user-data folder simultaneously.
        public static CoreWebView2Environment? SharedWebView2Environment { get; private set; }

        private List<VideoItem> _currentFeed = new List<VideoItem>();
        private List<VideoItem> _rawUnfilteredFeed = new List<VideoItem>();
        private VideoItem? _currentVideo = null;
        private int _currentVideoIndex = -1;
        private PopOutPlayerWindow? _popOutWindow = null;

        private DispatcherTimer? _sleepTimer = null;
        private int _sleepRemainingSeconds = 0;
        private int _lastSleepDurationMinutes = 30;

        private DispatcherTimer? _sponsorBlockTimer = null;
        private List<SponsorSegment> _activeSponsorSegments = new List<SponsorSegment>();

        private bool _isAlwaysOnTop = false;
        private bool _isCustomFullscreen = false;

        public MainWindow()
        {
            InitializeComponent();
            Loaded += MainWindow_Loaded;
        }

        private async void MainWindow_Loaded(object sender, RoutedEventArgs e)
        {
            UpdateFolderUi();
            UpdateAutoplayUi();
            UpdateAccountUi();
            UpdateQualityButtonText(StorageService.Settings.PreferredQuality);
            SubscribedChannelsList.ItemsSource = WillRyanProfileData.SubscribedChannels;
            SubscribersHeader.Text = $"👤 Subscriptions ({WillRyanProfileData.SubscribedChannels.Count})";

            SetupBottomBarAutoFade();
            ApplySidebarState();
            ApplyAmbientGlowState();
            UpdateFolderChipHighlights();

            await InitializeWebViewAsync();
            await LoadFeedAsync("Recommended Feed", () => YouTubeService.GetHomeFeedAsync());
        }

        private async Task InitializeWebViewAsync()
        {
            try
            {
                var appData = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "VixzDesktop");
                var userDataFolder = Path.Combine(appData, "WebView2Profile");
                var chromiumFlags = "--autoplay-policy=no-user-gesture-required " +
                                    "--force_high_performance_gpu " +
                                    "--gpu-preference=2 " +
                                    "--enable-gpu-rasterization " +
                                    "--force-gpu-rasterization " +
                                    "--enable-zero-copy " +
                                    "--use-angle=d3d11 " +
                                    "--enable-accelerated-video-decode " +
                                    "--enable-accelerated-mjpeg-decode " +
                                    "--enable-accelerated-2d-canvas " +
                                    "--enable-features=VaapiVideoDecoder,D3D11VideoDecoder,PlatformHEVCDecoderSupport,DirectCompositionVideoOverlays,HardwareMediaKeyHandling " +
                                    "--disable-features=PreloadMediaEngagementData,TrackingPrevention " +
                                    "--disable-web-security " +
                                    "--allow-running-insecure-content";

                var options = new CoreWebView2EnvironmentOptions(chromiumFlags);
                var env = await CoreWebView2Environment.CreateAsync(userDataFolder: userDataFolder, options: options);
                SharedWebView2Environment = env; // expose for reuse by PopOutPlayerWindow
                await VideoWebView.EnsureCoreWebView2Async(env);

                // Enable F12 DevTools and modern desktop capabilities
                VideoWebView.CoreWebView2.Settings.AreDevToolsEnabled = true;
                VideoWebView.CoreWebView2.Settings.IsStatusBarEnabled = false;
                VideoWebView.CoreWebView2.Settings.IsWebMessageEnabled = true;
                VideoWebView.CoreWebView2.Settings.UserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";

                // Map virtual host https://vixz.app to local WebAssets for valid secure origin
                var webAssets = Path.Combine(appData, "WebAssets");
                Directory.CreateDirectory(webAssets);

                var playerHtmlPath = Path.Combine(webAssets, "player.html");
                var htmlContent = @"<!DOCTYPE html>
<html lang=""en"">
<head>
    <meta charset=""utf-8"">
    <meta name=""viewport"" content=""width=device-width, initial-scale=1.0"">
    <link rel=""icon"" href=""data:,"">
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        html, body { width: 100vw; height: 100vh; background: #000; overflow: hidden; position: relative; display: flex; align-items: center; justify-content: center; }
        
        #ambient-glow-container {
            position: absolute;
            top: -12%;
            left: -12%;
            width: 124%;
            height: 124%;
            pointer-events: none;
            z-index: 1;
            opacity: 0.9;
            transition: opacity 0.5s ease;
            filter: blur(60px) saturate(2.5) brightness(1.3);
            display: flex;
            align-items: center;
            justify-content: center;
        }

        .ambient-orb {
            position: absolute;
            width: 75%;
            height: 75%;
            border-radius: 50%;
            background: radial-gradient(circle, var(--glow-color, #FF0055) 0%, rgba(255, 120, 0, 0.45) 45%, transparent 75%);
            animation: pulseGlow 5s ease-in-out infinite alternate;
        }

        @keyframes pulseGlow {
            0% { transform: scale(0.92); opacity: 0.75; }
            100% { transform: scale(1.10); opacity: 1.0; }
        }

        #player-wrapper {
            position: relative;
            z-index: 2;
            width: 100%;
            height: 100%;
            display: flex;
            align-items: center;
            justify-content: center;
            box-shadow: 0 0 60px rgba(0,0,0,0.85);
        }

        #player { width: 100%; height: 100%; position: absolute; top: 0; left: 0; border: none; }
    </style>
</head>
<body>
    <div id=""ambient-glow-container"">
        <div class=""ambient-orb""></div>
    </div>
    <div id=""player-wrapper"">
        <div id=""player""></div>
    </div>
    <script>
        var tag = document.createElement('script');
        tag.src = 'https://www.youtube.com/iframe_api';
        var firstScriptTag = document.getElementsByTagName('script')[0];
        firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

        var player;
        var urlParams = new URLSearchParams(window.location.search);
        var currentVideoId = urlParams.get('v') || '';
        var startSec = parseFloat(urlParams.get('t') || '0') || 0;
        var preferredQuality = urlParams.get('vq') || 'hd1080';

        function applyHighQuality() {
            try {
                if (player) {
                    if (typeof player.setPlaybackQuality === 'function') {
                        player.setPlaybackQuality(preferredQuality);
                    }
                    if (typeof player.setPlaybackQualityRange === 'function') {
                        player.setPlaybackQualityRange(preferredQuality);
                    }
                }
            } catch(e) {}
        }

        function onYouTubeIframeAPIReady() {
            if (!currentVideoId) return;
            player = new YT.Player('player', {
                videoId: currentVideoId,
                host: 'https://www.youtube.com',
                playerVars: {
                    'autoplay': 1,
                    'playsinline': 1,
                    'controls': 1,
                    'rel': 0,
                    'fs': 1,
                    'modestbranding': 1,
                    'iv_load_policy': 3,
                    'enablejsapi': 1,
                    'origin': window.location.origin,
                    'widget_referrer': window.location.origin,
                    'start': Math.floor(startSec),
                    'vq': preferredQuality,
                    'hd': 1
                },
                events: {
                    'onReady': function(e) {
                        try { e.target.unMute(); } catch(err) {}
                        if (startSec > 3) {
                            try { e.target.seekTo(startSec, true); } catch(err) {}
                        }
                        applyHighQuality();
                        try { e.target.playVideo(); } catch(err) {}
                        setTimeout(function() { try { e.target.unMute(); applyHighQuality(); e.target.playVideo(); } catch(err) {} }, 250);
                        setTimeout(function() { try { e.target.unMute(); applyHighQuality(); if (e.target.getPlayerState() !== 1) e.target.playVideo(); } catch(err) {} }, 750);
                        setTimeout(applyHighQuality, 1500);
                        setTimeout(applyHighQuality, 3000);
                    },
                    'onStateChange': onPlayerStateChange,
                    'onPlaybackQualityChange': function(e) {
                        if (window.chrome && window.chrome.webview) {
                            window.chrome.webview.postMessage('QUALITY:' + (e.data || ''));
                        }
                    }
                }
            });
        }

        function onPlayerStateChange(event) {
            if (event.data === 1) { // Playing
                applyHighQuality();
                setTimeout(applyHighQuality, 400);
                try {
                    if (player && typeof player.getPlaybackQuality === 'function') {
                        var q = player.getPlaybackQuality();
                        if (window.chrome && window.chrome.webview) {
                            window.chrome.webview.postMessage('QUALITY:' + q);
                        }
                    }
                } catch(e) {}
            }
            if (event.data === 0) { // Ended
                if (window.chrome && window.chrome.webview) {
                    window.chrome.webview.postMessage('VIDEO_ENDED');
                }
            }
        }

        function loadVideo(vid, seekTime, quality) {
            currentVideoId = vid;
            if (quality) preferredQuality = quality;
            var targetSec = parseFloat(seekTime || '0') || 0;
            if (player && typeof player.loadVideoById === 'function') {
                player.loadVideoById({
                    videoId: vid,
                    startSeconds: targetSec,
                    suggestedQuality: preferredQuality
                });
                applyHighQuality();
                setTimeout(applyHighQuality, 400);
                setTimeout(applyHighQuality, 1200);
            } else {
                window.location.href = 'https://vixz.app/player.html?v=' + vid + '&t=' + targetSec + '&vq=' + preferredQuality;
            }
        }

        function setQuality(quality) {
            preferredQuality = quality || 'hd1080';
            applyHighQuality();
            setTimeout(applyHighQuality, 300);
            if (window.chrome && window.chrome.webview) {
                window.chrome.webview.postMessage('QUALITY:' + preferredQuality);
            }
        }

        function setAmbientGlow(isEnabled, colorHex) {
            try {
                var container = document.getElementById('ambient-glow-container');
                if (!container) return;
                if (isEnabled) {
                    container.style.opacity = '0.9';
                    if (colorHex) {
                        document.documentElement.style.setProperty('--glow-color', colorHex);
                    }
                } else {
                    container.style.opacity = '0.0';
                }
            } catch(e) {}
        }

        var sponsorSegments = [];
        function setSponsorSegments(segs) {
            sponsorSegments = segs || [];
        }

        // High-precision SponsorBlock In-Video Ad Skipper (200ms tick)
        setInterval(() => {
            try {
                if (player && typeof player.getCurrentTime === 'function' && typeof player.getPlayerState === 'function') {
                    if (player.getPlayerState() === 1) { // Playing
                        var cur = player.getCurrentTime();
                        for (var i = 0; i < sponsorSegments.length; i++) {
                            var seg = sponsorSegments[i];
                            if (cur >= seg.start && cur < (seg.end - 0.25)) {
                                player.seekTo(seg.end + 0.1, true);
                                if (window.chrome && window.chrome.webview) {
                                    window.chrome.webview.postMessage('SPONSOR_SKIPPED:' + (seg.category || 'sponsor'));
                                }
                                break;
                            }
                        }
                    }
                }
            } catch(e) {}
        }, 200);

        // Progress memory tracker - sends current position every 1.5s
        setInterval(() => {
            try {
                if (player && typeof player.getCurrentTime === 'function' && typeof player.getPlayerState === 'function') {
                    if (player.getPlayerState() === 1) { // Playing
                        var cur = player.getCurrentTime();
                        if (cur > 2 && currentVideoId && window.chrome && window.chrome.webview) {
                            window.chrome.webview.postMessage('POS:' + currentVideoId + ':' + cur.toFixed(1));
                        }
                    }
                }
            } catch(e) {}
        }, 1500);

        function stopVideo() {
            try {
                if (player && typeof player.stopVideo === 'function') player.stopVideo();
                if (player && typeof player.pauseVideo === 'function') player.pauseVideo();
            } catch(e) {}
        }

        function unMuteVideo() {
            try {
                if (player && typeof player.unMute === 'function') player.unMute();
            } catch(e) {}
        }

        function pauseVideo() {
            if (player && typeof player.pauseVideo === 'function') {
                player.pauseVideo();
            }
        }

        function playVideo() {
            if (player && typeof player.playVideo === 'function') {
                try { player.unMute(); } catch(e) {}
                player.playVideo();
            }
        }

        function togglePlay() {
            if (!player || typeof player.getPlayerState !== 'function') return;
            var s = player.getPlayerState();
            if (s === 1) pauseVideo();
            else playVideo();
        }

        function seek(sec) {
            if (!player || typeof player.getCurrentTime !== 'function') return;
            var cur = player.getCurrentTime();
            player.seekTo(cur + sec, true);
        }

        function seekTo(sec) {
            if (!player || typeof player.seekTo !== 'function') return;
            player.seekTo(sec, true);
        }

        function toggleMute() {
            if (!player || typeof player.isMuted !== 'function') return;
            if (player.isMuted()) player.unMute();
            else player.mute();
        }

        function getCurrentTime() {
            if (!player || typeof player.getCurrentTime !== 'function') return 0;
            return player.getCurrentTime() || 0;
        }

        async function fetchTranscript(vid) {
            try {
                var targetId = vid || currentVideoId;
                if (!targetId) return '';
                var res = await fetch('https://www.youtube.com/watch?v=' + targetId);
                var html = await res.text();
                var match = html.match(/""captionTracks"":\s*(\[.*?\])/);
                if (!match) return '';

                var tracks = JSON.parse(match[1]);
                if (!tracks || tracks.length === 0) return '';

                var enTrack = tracks.find(function(t) { return t.languageCode === 'en' || (t.vssId && t.vssId.indexOf('en') !== -1); }) || tracks[0];
                if (!enTrack || !enTrack.baseUrl) return '';

                var capRes = await fetch(enTrack.baseUrl + '&fmt=json3');
                var capJson = await capRes.json();
                if (!capJson.events) return '';

                var fullText = '';
                for (var i = 0; i < capJson.events.length; i++) {
                    var ev = capJson.events[i];
                    if (ev.segs) {
                        for (var j = 0; j < ev.segs.length; j++) {
                            var seg = ev.segs[j];
                            if (seg.utf8) {
                                fullText += seg.utf8 + ' ';
                            }
                        }
                    }
                }
                return fullText.trim();
            } catch (e) {
                return '';
            }
        }

        // Double-click to maximize / restore window
        document.addEventListener('dblclick', function(e) {
            if (window.chrome && window.chrome.webview) {
                window.chrome.webview.postMessage('DOUBLE_CLICK_VIDEO');
            }
        });
    </script>
</body>
</html>";
                File.WriteAllText(playerHtmlPath, htmlContent);

                VideoWebView.CoreWebView2.SetVirtualHostNameToFolderMapping(
                    "vixz.app",
                    webAssets,
                    CoreWebView2HostResourceAccessKind.Allow
                );

                // Intercept and mock ad requests with 200 OK to prevent ERR_CONNECTION_REFUSED
                VideoWebView.CoreWebView2.AddWebResourceRequestedFilter("*", CoreWebView2WebResourceContext.All);

                VideoWebView.CoreWebView2.WebResourceRequested += (s, args) =>
                {
                    try
                    {
                        var uri = args.Request.Uri.ToLowerInvariant();
                        if (uri.Contains("doubleclick") || uri.Contains("googleads") || uri.Contains("/pagead/") || uri.Contains("ad_status") || uri.Contains("favicon.ico") || uri.Contains("viewthroughconversion"))
                        {
                            string origin = "*";
                            try
                            {
                                if (args.Request.Headers.Contains("Origin"))
                                {
                                    origin = args.Request.Headers.GetHeader("Origin");
                                    if (string.IsNullOrWhiteSpace(origin)) origin = "*";
                                }
                            }
                            catch { }

                            string contentType = "text/plain";
                            byte[] bodyBytes = Array.Empty<byte>();

                            if (uri.Contains(".js") || uri.Contains("ad_status"))
                            {
                                contentType = "application/javascript";
                                bodyBytes = System.Text.Encoding.UTF8.GetBytes("/* mock ad script */\nwindow.google_ad_status = 1;\n");
                            }
                            else if (uri.Contains("favicon.ico"))
                            {
                                contentType = "image/x-icon";
                                bodyBytes = new byte[] { 0, 0, 1, 0, 1, 0, 1, 1, 0, 0, 1, 0, 32, 0, 68, 0, 0, 0, 22, 0, 0, 0, 40, 0, 0, 0, 1, 0, 0, 0, 2, 0, 0, 0, 1, 0, 32, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
                            }
                            else
                            {
                                contentType = "application/json";
                                bodyBytes = System.Text.Encoding.UTF8.GetBytes("{\"status\":\"ok\",\"id\":\"0\"}");
                            }

                            var headers = $"Content-Type: {contentType}\r\nAccess-Control-Allow-Origin: {origin}\r\nAccess-Control-Allow-Credentials: true\r\nAccess-Control-Allow-Methods: GET, POST, OPTIONS, PUT, DELETE\r\nAccess-Control-Allow-Headers: *";
                            var emptyStream = new MemoryStream(bodyBytes);
                            args.Response = VideoWebView.CoreWebView2.Environment.CreateWebResourceResponse(
                                emptyStream,
                                200,
                                "OK",
                                headers
                            );
                        }
                    }
                    catch { }
                };

                VideoWebView.CoreWebView2.WebMessageReceived += CoreWebView2_WebMessageReceived;
                VideoWebView.PreviewKeyDown += Window_PreviewKeyDown;

                // Intercept 'More videos' and external links so they play inside Vixz instead of opening external browsers
                VideoWebView.CoreWebView2.NewWindowRequested += async (s, args) =>
                {
                    args.Handled = true;
                    var vid = ExtractYouTubeVideoId(args.Uri);
                    if (!string.IsNullOrEmpty(vid))
                    {
                        var video = await YouTubeService.GetVideoDetailsAsync(vid) ?? new VideoItem
                        {
                            Id = vid,
                            Title = "YouTube Video",
                            ChannelTitle = "YouTube",
                            ThumbnailUrl = $"https://i.ytimg.com/vi/{vid}/hqdefault.jpg"
                        };
                        await PlayVideoAsync(video);
                    }
                };

                VideoWebView.CoreWebView2.NavigationStarting += async (s, args) =>
                {
                    if (args.Uri.StartsWith("https://vixz.app", StringComparison.OrdinalIgnoreCase))
                    {
                        return;
                    }

                    args.Cancel = true;
                    var vid = ExtractYouTubeVideoId(args.Uri);
                    if (!string.IsNullOrEmpty(vid))
                    {
                        var video = await YouTubeService.GetVideoDetailsAsync(vid) ?? new VideoItem
                        {
                            Id = vid,
                            Title = "YouTube Video",
                            ChannelTitle = "YouTube",
                            ThumbnailUrl = $"https://i.ytimg.com/vi/{vid}/hqdefault.jpg"
                        };
                        await PlayVideoAsync(video);
                    }
                };
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"WebView2 init error: {ex.Message}");
            }
        }

        private void CoreWebView2_WebMessageReceived(object? sender, CoreWebView2WebMessageReceivedEventArgs e)
        {
            try
            {
                var msg = e.TryGetWebMessageAsString();
                if (string.IsNullOrEmpty(msg)) return;

                if (msg == "VIDEO_ENDED")
                {
                    if (StorageService.Settings.IsAutoplayEnabled)
                    {
                        PlayNextVideo();
                    }
                }
                else if (msg.StartsWith("SPONSOR_SKIPPED:"))
                {
                    var cat = msg.Substring("SPONSOR_SKIPPED:".Length);
                    ShowToast($"⏭️ Skipped {cat} (in-video ad)");
                }
                else if (msg.StartsWith("QUALITY:"))
                {
                    var q = msg.Substring("QUALITY:".Length);
                    Dispatcher.Invoke(() => UpdateQualityButtonText(q));
                }
                else if (msg == "DOUBLE_CLICK_VIDEO")
                {
                    Dispatcher.Invoke(() =>
                    {
                        MaximizeToggle();
                    });
                }
                else if (msg.StartsWith("POS:"))
                {
                    var parts = msg.Split(':');
                    if (parts.Length == 3 && double.TryParse(parts[2], System.Globalization.NumberStyles.Any, System.Globalization.CultureInfo.InvariantCulture, out double pos))
                    {
                        StorageService.SavePlaybackPosition(parts[1], pos);
                    }
                }
            }
            catch { }
        }

        public void UpdateQualityButtonText(string? quality)
        {
            if (QualityBtn == null) return;
            var label = quality switch
            {
                "highres" or "hd1080" => "HD 1080p",
                "hd720" => "HD 720p",
                "large" => "SD 480p",
                "medium" => "360p",
                "small" or "tiny" => "240p",
                _ => "HD 1080p"
            };
            QualityBtn.Content = label;
        }

        public void UpdateAccountUi()
        {
            var account = StorageService.Settings.UserAccount;
            if (account != null && account.IsSignedIn && !string.IsNullOrWhiteSpace(account.DisplayName))
            {
                AccountBtn.Content = $"👤 {account.DisplayName}";
                AccountBtn.Foreground = (System.Windows.Media.Brush)FindResource("AccentGold");
                AccountProfileName.Text = account.DisplayName;
                AccountStatusSubtext.Text = "Google Account Connected 🟢";
                AccountAvatarInitial.Text = account.DisplayName.Length > 0 ? account.DisplayName[0].ToString().ToUpper() : "👤";
            }
            else
            {
                AccountBtn.Content = "👤 Sign In";
                AccountBtn.Foreground = System.Windows.Media.Brushes.White;
                AccountProfileName.Text = "Not Signed In";
                AccountStatusSubtext.Text = "Sign in to sync YouTube data";
                AccountAvatarInitial.Text = "👤";
            }
        }

        #region Feed & Navigation

        private async Task LoadFeedAsync(string title, Func<Task<List<VideoItem>>> fetcher)
        {
            FeedTitleText.Text = title;
            LoadingSpinner.Visibility = Visibility.Visible;
            VideoItemsControl.ItemsSource = null;

            try
            {
                _rawUnfilteredFeed = await fetcher();
                ApplyCurrentFilters();
            }
            catch (Exception ex)
            {
                ShowToast($"Error loading feed: {ex.Message}");
            }
            finally
            {
                LoadingSpinner.Visibility = Visibility.Collapsed;
            }
        }

        private void ApplyCurrentFilters()
        {
            if (DateFilterCombo == null || DurationFilterCombo == null || SortByFilterCombo == null || VideoItemsControl == null)
            {
                return;
            }

            if (_rawUnfilteredFeed == null || _rawUnfilteredFeed.Count == 0)
            {
                _currentFeed = new List<VideoItem>();
                VideoItemsControl.ItemsSource = _currentFeed;
                return;
            }

            var dateTag = (DateFilterCombo.SelectedItem as ComboBoxItem)?.Tag?.ToString();
            var durationTag = (DurationFilterCombo.SelectedItem as ComboBoxItem)?.Tag?.ToString();
            var sortByTag = (SortByFilterCombo.SelectedItem as ComboBoxItem)?.Tag?.ToString();

            _currentFeed = YouTubeService.ApplyLocalFilters(_rawUnfilteredFeed, dateTag, durationTag, sortByTag);
            VideoItemsControl.ItemsSource = _currentFeed;
        }

        private void FilterOrSort_Changed(object sender, SelectionChangedEventArgs e)
        {
            ApplyCurrentFilters();
        }

        private async void ApplyFiltersBtn_Click(object sender, RoutedEventArgs e)
        {
            var query = SearchBox.Text.Trim();
            if (!string.IsNullOrWhiteSpace(query))
            {
                await PerformSearchWithFiltersAsync();
                return;
            }

            var dateTag = (DateFilterCombo.SelectedItem as ComboBoxItem)?.Tag?.ToString();
            var durationTag = (DurationFilterCombo.SelectedItem as ComboBoxItem)?.Tag?.ToString();
            var sortByTag = (SortByFilterCombo.SelectedItem as ComboBoxItem)?.Tag?.ToString();

            if (!string.IsNullOrWhiteSpace(dateTag) || !string.IsNullOrWhiteSpace(durationTag) || sortByTag == "latest" || sortByTag == "views")
            {
                LoadingSpinner.Visibility = Visibility.Visible;
                SwitchToFeedView();
                var label = dateTag == "today" ? "Today's Newest Videos" : "Filtered Feed";
                FeedTitleText.Text = $"⚡ {label}";

                var results = await YouTubeService.GetDeepFilteredFeedAsync(dateTag, durationTag, sortByTag);
                _rawUnfilteredFeed = results;
                _currentFeed = results;
                VideoItemsControl.ItemsSource = _currentFeed;
                LoadingSpinner.Visibility = Visibility.Collapsed;
                ShowToast($"⚡ Found {_currentFeed.Count} videos matching filters");
            }
            else
            {
                ApplyCurrentFilters();
            }
        }

        private void ResetFiltersBtn_Click(object sender, RoutedEventArgs e)
        {
            DateFilterCombo.SelectedIndex = 0;
            DurationFilterCombo.SelectedIndex = 0;
            SortByFilterCombo.SelectedIndex = 0;
            _currentFeed = _rawUnfilteredFeed.ToList();
            VideoItemsControl.ItemsSource = _currentFeed;
            ShowToast("Filters reset");
        }

        private async void NavHome_Click(object sender, RoutedEventArgs e)
        {
            SwitchToFeedView();
            await LoadFeedAsync("Recommended Feed", () => YouTubeService.GetHomeFeedAsync());
        }

        private async void NavSubscriptions_Click(object sender, RoutedEventArgs e)
        {
            SwitchToFeedView();
            await LoadFeedAsync("🔔 Subscriptions Feed", () => YouTubeService.GetSubscribedFeedAsync());
        }

        private async void ChannelFilter_Click(object sender, RoutedEventArgs e)
        {
            if (sender is Button btn && btn.Content is string channelName)
            {
                SwitchToFeedView();
                await LoadFeedAsync($"🔔 {channelName}", () => YouTubeService.GetSubscribedFeedAsync(channelName));
            }
        }

        private void RefreshSubscribedChannelsUi()
        {
            SubscribedChannelsList.ItemsSource = null;
            SubscribedChannelsList.ItemsSource = WillRyanProfileData.SubscribedChannels;
            SubscribersHeader.Text = $"👤 Subscriptions ({WillRyanProfileData.SubscribedChannels.Count})";
            UpdateSubscribeToggleBtn();
        }

        private void DeleteChannel_Click(object sender, RoutedEventArgs e)
        {
            if (sender is FrameworkElement elem && elem.Tag is string channelName)
            {
                WillRyanProfileData.RemoveSubscribedChannel(channelName);
                RefreshSubscribedChannelsUi();
                ShowToast($"Unsubscribed from {channelName}");
            }
        }

        private void AddChannelBtn_Click(object sender, RoutedEventArgs e)
        {
            var prompt = new Window
            {
                Title = "➕ Add Subscribed Channel",
                Width = 420,
                Height = 220,
                WindowStartupLocation = WindowStartupLocation.CenterOwner,
                Owner = this,
                Background = (System.Windows.Media.Brush)FindResource("BgDarkPrimary"),
                Foreground = System.Windows.Media.Brushes.White,
                WindowStyle = WindowStyle.ToolWindow,
                ResizeMode = ResizeMode.NoResize
            };

            var sp = new StackPanel { Margin = new Thickness(18) };
            var heading = new TextBlock
            {
                Text = "➕ Add Channel / Creator",
                FontSize = 14,
                FontWeight = FontWeights.Bold,
                Foreground = (System.Windows.Media.Brush)FindResource("AccentGold"),
                Margin = new Thickness(0, 0, 0, 8)
            };
            var desc = new TextBlock
            {
                Text = "Enter any YouTube channel name or handle to add to your custom subscriptions feed:",
                FontSize = 11.5,
                Foreground = (System.Windows.Media.Brush)FindResource("TextSecondary"),
                TextWrapping = TextWrapping.Wrap,
                Margin = new Thickness(0, 0, 0, 10)
            };

            var txtBox = new TextBox
            {
                Background = (System.Windows.Media.Brush)FindResource("BgDarkTertiary"),
                Foreground = System.Windows.Media.Brushes.White,
                BorderBrush = (System.Windows.Media.Brush)FindResource("BorderSubtle"),
                FontSize = 12,
                Padding = new Thickness(8, 6, 8, 6),
                Margin = new Thickness(0, 0, 0, 14)
            };

            var btnRow = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right };
            var cancelBtn = new Button
            {
                Content = "Cancel",
                Style = (Style)FindResource("GlassButton"),
                Padding = new Thickness(12, 6, 12, 6),
                Margin = new Thickness(0, 0, 8, 0)
            };
            cancelBtn.Click += (s, ev) => prompt.Close();

            var addBtn = new Button
            {
                Content = "Add Channel",
                Style = (Style)FindResource("GlassButton"),
                Background = (System.Windows.Media.Brush)FindResource("AccentGold"),
                Foreground = System.Windows.Media.Brushes.Black,
                FontWeight = FontWeights.Bold,
                Padding = new Thickness(14, 6, 14, 6)
            };

            Action doAdd = () =>
            {
                var val = txtBox.Text.Trim();
                if (!string.IsNullOrWhiteSpace(val))
                {
                    WillRyanProfileData.AddSubscribedChannel(val);
                    RefreshSubscribedChannelsUi();
                    ShowToast($"Subscribed to {val}");
                    prompt.Close();
                }
            };

            addBtn.Click += (s, ev) => doAdd();
            txtBox.KeyDown += (s, ev) =>
            {
                if (ev.Key == System.Windows.Input.Key.Enter) doAdd();
            };

            btnRow.Children.Add(cancelBtn);
            btnRow.Children.Add(addBtn);

            sp.Children.Add(heading);
            sp.Children.Add(desc);
            sp.Children.Add(txtBox);
            sp.Children.Add(btnRow);

            prompt.Content = sp;
            txtBox.Focus();
            prompt.ShowDialog();
        }

        private void ManageChannelsBtn_Click(object sender, RoutedEventArgs e)
        {
            var prompt = new Window
            {
                Title = "⚙️ Manage Subscriptions",
                Width = 480,
                Height = 440,
                WindowStartupLocation = WindowStartupLocation.CenterOwner,
                Owner = this,
                Background = (System.Windows.Media.Brush)FindResource("BgDarkPrimary"),
                Foreground = System.Windows.Media.Brushes.White,
                WindowStyle = WindowStyle.ToolWindow,
                ResizeMode = ResizeMode.NoResize
            };

            var grid = new Grid { Margin = new Thickness(18) };
            grid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            grid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
            grid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });

            var heading = new TextBlock
            {
                Text = "⚙️ Manage Your Subscribed Channels",
                FontSize = 14,
                FontWeight = FontWeights.Bold,
                Foreground = (System.Windows.Media.Brush)FindResource("AccentGold"),
                Margin = new Thickness(0, 0, 0, 8)
            };
            Grid.SetRow(heading, 0);

            var scroll = new ScrollViewer { VerticalScrollBarVisibility = ScrollBarVisibility.Auto, Margin = new Thickness(0, 0, 0, 12) };
            var listStack = new StackPanel();

            void PopulateList()
            {
                listStack.Children.Clear();
                if (WillRyanProfileData.SubscribedChannels.Count == 0)
                {
                    listStack.Children.Add(new TextBlock
                    {
                        Text = "No subscribed channels yet. Click '➕' in the sidebar to add your favorites!",
                        Foreground = (System.Windows.Media.Brush)FindResource("TextSecondary"),
                        FontSize = 12,
                        Margin = new Thickness(0, 30, 0, 0),
                        HorizontalAlignment = HorizontalAlignment.Center
                    });
                    return;
                }

                foreach (var ch in WillRyanProfileData.SubscribedChannels.ToList())
                {
                    var row = new Grid { Margin = new Thickness(0, 2, 0, 2) };
                    row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
                    row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

                    var chText = new TextBlock
                    {
                        Text = "▶ " + ch,
                        Foreground = System.Windows.Media.Brushes.White,
                        FontSize = 12,
                        VerticalAlignment = VerticalAlignment.Center
                    };
                    Grid.SetColumn(chText, 0);

                    var del = new Button
                    {
                        Content = "Remove ✕",
                        Style = (Style)FindResource("GlassButton"),
                        FontSize = 10,
                        Padding = new Thickness(8, 3, 8, 3),
                        Tag = ch
                    };
                    Grid.SetColumn(del, 1);
                    del.Click += (s, ev) =>
                    {
                        if (s is FrameworkElement fe && fe.Tag is string c)
                        {
                            WillRyanProfileData.RemoveSubscribedChannel(c);
                            RefreshSubscribedChannelsUi();
                            PopulateList();
                        }
                    };

                    row.Children.Add(chText);
                    row.Children.Add(del);
                    listStack.Children.Add(row);
                }
            }

            PopulateList();
            scroll.Content = listStack;
            Grid.SetRow(scroll, 1);

            var bottomBar = new Grid();
            bottomBar.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            bottomBar.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            var leftBtns = new StackPanel { Orientation = Orientation.Horizontal };
            var clearAllBtn = new Button
            {
                Content = "Clear All Channels",
                Style = (Style)FindResource("GlassButton"),
                Foreground = (System.Windows.Media.Brush)FindResource("AccentRed"),
                FontSize = 11,
                Padding = new Thickness(10, 6, 10, 6),
                Margin = new Thickness(0, 0, 8, 0)
            };
            clearAllBtn.Click += (s, ev) =>
            {
                WillRyanProfileData.ClearAllSubscribedChannels();
                RefreshSubscribedChannelsUi();
                PopulateList();
                ShowToast("Cleared all subscriptions");
            };

            var restoreBtn = new Button
            {
                Content = "Reset Defaults",
                Style = (Style)FindResource("GlassButton"),
                FontSize = 11,
                Padding = new Thickness(10, 6, 10, 6)
            };
            restoreBtn.Click += (s, ev) =>
            {
                WillRyanProfileData.RestoreDefaultChannels();
                RefreshSubscribedChannelsUi();
                PopulateList();
                ShowToast("Restored default channels");
            };

            leftBtns.Children.Add(clearAllBtn);
            leftBtns.Children.Add(restoreBtn);
            Grid.SetColumn(leftBtns, 0);

            var closeBtn = new Button
            {
                Content = "Done",
                Style = (Style)FindResource("GlassButton"),
                Background = (System.Windows.Media.Brush)FindResource("AccentGold"),
                Foreground = System.Windows.Media.Brushes.Black,
                FontWeight = FontWeights.Bold,
                FontSize = 11,
                Padding = new Thickness(16, 6, 16, 6)
            };
            closeBtn.Click += (s, ev) => prompt.Close();
            Grid.SetColumn(closeBtn, 1);

            bottomBar.Children.Add(leftBtns);
            bottomBar.Children.Add(closeBtn);
            Grid.SetRow(bottomBar, 2);

            grid.Children.Add(heading);
            grid.Children.Add(scroll);
            grid.Children.Add(bottomBar);

            prompt.Content = grid;
            prompt.ShowDialog();
        }

        private void SubscribeToggleBtn_Click(object sender, RoutedEventArgs e)
        {
            if (_currentVideo == null || string.IsNullOrWhiteSpace(_currentVideo.ChannelTitle)) return;
            var channel = _currentVideo.ChannelTitle.Trim();

            if (WillRyanProfileData.IsSubscribed(channel))
            {
                WillRyanProfileData.RemoveSubscribedChannel(channel);
                ShowToast($"Unsubscribed from {channel}");
            }
            else
            {
                WillRyanProfileData.AddSubscribedChannel(channel);
                ShowToast($"Subscribed to {channel}!");
            }
            RefreshSubscribedChannelsUi();
        }

        private void UpdateSubscribeToggleBtn()
        {
            if (_currentVideo == null || string.IsNullOrWhiteSpace(_currentVideo.ChannelTitle))
            {
                SubscribeToggleBtn.Visibility = Visibility.Collapsed;
                return;
            }

            SubscribeToggleBtn.Visibility = Visibility.Visible;
            bool isSubbed = WillRyanProfileData.IsSubscribed(_currentVideo.ChannelTitle);
            if (isSubbed)
            {
                SubscribeToggleBtn.Content = "✓ Subscribed";
                SubscribeToggleBtn.Foreground = (System.Windows.Media.Brush)FindResource("AccentGold");
            }
            else
            {
                SubscribeToggleBtn.Content = "+ Subscribe";
                SubscribeToggleBtn.Foreground = System.Windows.Media.Brushes.White;
            }
        }

        private async void NavTrending_Click(object sender, RoutedEventArgs e)
        {
            SwitchToFeedView();
            await LoadFeedAsync("🔥 Trending Videos", () => YouTubeService.SearchVideosAsync("Trending Worldwide", 30));
        }

        private void NavFavorites_Click(object sender, RoutedEventArgs e)
        {
            SwitchToFeedView();
            FeedTitleText.Text = "⭐ Favorite Videos";
            _rawUnfilteredFeed = StorageService.Settings.Favorites.ToList();
            ApplyCurrentFilters();
        }

        private void NavWatchLater_Click(object sender, RoutedEventArgs e)
        {
            SwitchToFeedView();
            FeedTitleText.Text = "🕒 Watch Later Queue";
            _rawUnfilteredFeed = StorageService.Settings.WatchLater.ToList();
            ApplyCurrentFilters();
        }

        private void NavHistory_Click(object sender, RoutedEventArgs e)
        {
            SwitchToFeedView();
            FeedTitleText.Text = "📜 Watch History";
            _rawUnfilteredFeed = StorageService.Settings.WatchHistory.ToList();
            ApplyCurrentFilters();
        }

        private async void SwitchToFeedView()
        {
            PlayerView.Visibility = Visibility.Collapsed;
            FeedView.Visibility = Visibility.Visible;
            _sponsorBlockTimer?.Stop();

            try
            {
                if (VideoWebView?.CoreWebView2 != null)
                {
                    // Save playback position
                    if (_currentVideo != null)
                    {
                        var timeStr = await VideoWebView.ExecuteScriptAsync("getCurrentTime()");
                        if (double.TryParse(timeStr, System.Globalization.NumberStyles.Any, System.Globalization.CultureInfo.InvariantCulture, out double sec) && sec > 1)
                        {
                            StorageService.SavePlaybackPosition(_currentVideo.Id, sec);
                        }
                    }

                    // Immediately pause video and all media elements so it stops playing in the background
                    await VideoWebView.ExecuteScriptAsync("document.querySelectorAll('video, audio').forEach(m => { m.pause(); }); if (window.player && typeof window.player.pauseVideo === 'function') window.player.pauseVideo();");
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"Error pausing video on SwitchToFeedView: {ex.Message}");
            }
        }

        private void SwitchToPlayerView()
        {
            FeedView.Visibility = Visibility.Collapsed;
            PlayerView.Visibility = Visibility.Visible;
        }

        private async void SearchButton_Click(object sender, RoutedEventArgs e)
        {
            await PerformSearchAsync();
        }

        private void ClearSearchBtn_Click(object sender, RoutedEventArgs e)
        {
            SearchBox.Text = "";
            SearchBox.Focus();
        }

        private void SearchHistoryToggle_Click(object sender, RoutedEventArgs e)
        {
            ToggleSearchHistoryPopup();
        }

        private void SearchBox_GotFocus(object sender, RoutedEventArgs e)
        {
            OpenSearchHistoryPopupIfAvailable();
        }

        private void SearchBox_PreviewMouseDown(object sender, MouseButtonEventArgs e)
        {
            if (SearchHistoryPopup != null && !SearchHistoryPopup.IsOpen)
            {
                OpenSearchHistoryPopupIfAvailable();
            }
        }

        private void OpenSearchHistoryPopupIfAvailable()
        {
            if (SearchHistoryPopup == null) return;
            UpdateSearchHistoryList();
            SearchHistoryPopup.IsOpen = true;
        }

        private void ToggleSearchHistoryPopup()
        {
            if (SearchHistoryPopup == null) return;
            if (SearchHistoryPopup.IsOpen)
            {
                SearchHistoryPopup.IsOpen = false;
            }
            else
            {
                UpdateSearchHistoryList();
                SearchHistoryPopup.IsOpen = true;
            }
        }

        private void UpdateSearchHistoryList()
        {
            if (SearchHistoryItemsControl == null) return;
            var history = StorageService.Settings.SearchHistory;
            SearchHistoryItemsControl.ItemsSource = null;
            SearchHistoryItemsControl.ItemsSource = history.ToList();

            if (SearchHistoryEmptyText != null)
            {
                SearchHistoryEmptyText.Visibility = history.Count == 0 ? Visibility.Visible : Visibility.Collapsed;
            }
        }

        private async void SearchHistoryItem_MouseDown(object sender, MouseButtonEventArgs e)
        {
            if (sender is FrameworkElement elem && elem.DataContext is string query)
            {
                if (SearchHistoryPopup != null) SearchHistoryPopup.IsOpen = false;
                SearchBox.Text = query;
                await PerformSearchAsync();
            }
        }

        private void RemoveSearchHistoryItem_Click(object sender, RoutedEventArgs e)
        {
            e.Handled = true;
            if (sender is Button btn && btn.Tag is string query)
            {
                StorageService.RemoveSearchHistoryItem(query);
                UpdateSearchHistoryList();
            }
        }

        private void ClearAllSearchHistory_Click(object sender, RoutedEventArgs e)
        {
            StorageService.ClearSearchHistory();
            UpdateSearchHistoryList();
            ShowToast("🗑️ Search history cleared");
        }

        private async void SearchBox_KeyDown(object sender, KeyEventArgs e)
        {
            if (e.Key == Key.Enter)
            {
                await PerformSearchAsync();
            }
        }

        private async Task PerformSearchAsync()
        {
            var query = SearchBox.Text.Trim();
            if (string.IsNullOrWhiteSpace(query)) return;

            if (SearchHistoryPopup != null) SearchHistoryPopup.IsOpen = false;
            StorageService.AddSearchHistory(query);

            // Direct YouTube URL or Video ID detection -> play instantly!
            var extractedId = ExtractYouTubeVideoId(query);
            if (!string.IsNullOrEmpty(extractedId))
            {
                var video = await YouTubeService.GetVideoDetailsAsync(extractedId) ?? new VideoItem
                {
                    Id = extractedId,
                    Title = "YouTube Video (" + extractedId + ")",
                    ChannelTitle = "YouTube",
                    ThumbnailUrl = $"https://i.ytimg.com/vi/{extractedId}/hqdefault.jpg"
                };
                await PlayVideoAsync(video);
                return;
            }

            await PerformSearchWithFiltersAsync();
        }

        private string? _currentSearchQuery = null;
        private bool _isLoadingMore = false;

        private async Task PerformSearchWithFiltersAsync()
        {
            var query = SearchBox.Text.Trim();
            if (string.IsNullOrWhiteSpace(query)) return;

            _currentSearchQuery = query;

            var dateTag = (DateFilterCombo.SelectedItem as ComboBoxItem)?.Tag?.ToString();
            var durationTag = (DurationFilterCombo.SelectedItem as ComboBoxItem)?.Tag?.ToString();
            var sortByTag = (SortByFilterCombo.SelectedItem as ComboBoxItem)?.Tag?.ToString();

            string? spParam = null;
            if (sortByTag == "latest") spParam = "CAI%3D";
            else if (sortByTag == "views") spParam = "CAM%3D";
            else if (dateTag == "today") spParam = "EgIIAg%3D%3D";
            else if (dateTag == "week") spParam = "EgIIAw%3D%3D";
            else if (dateTag == "month") spParam = "EgIIBA%3D%3D";
            else if (durationTag == "short") spParam = "EgQQARgB";
            else if (durationTag == "medium") spParam = "EgQQARgD";
            else if (durationTag == "long") spParam = "EgQQARgC";

            LoadingSpinner.Visibility = Visibility.Visible;
            SwitchToFeedView();
            FeedTitleText.Text = $"🔍 Search: \"{query}\"";

            var results = await YouTubeService.SearchVideosAsync(query, 50, spFilter: spParam);
            _rawUnfilteredFeed = results;
            _currentFeed = YouTubeService.ApplyLocalFilters(results, dateTag, durationTag, sortByTag);
            VideoItemsControl.ItemsSource = _currentFeed;
            LoadingSpinner.Visibility = Visibility.Collapsed;
        }

        private async void FeedScrollViewer_ScrollChanged(object sender, ScrollChangedEventArgs e)
        {
            if (FeedView.Visibility != Visibility.Visible || _isLoadingMore) return;

            // Trigger when scrolled to bottom 85%
            if (e.VerticalChange > 0 && e.VerticalOffset >= e.ExtentHeight - e.ViewportHeight - 350)
            {
                await LoadNextPageOfVideosAsync();
            }
        }

        private async void LoadMoreVideosBtn_Click(object sender, RoutedEventArgs e)
        {
            await LoadNextPageOfVideosAsync();
        }

        private async Task LoadNextPageOfVideosAsync()
        {
            if (_isLoadingMore || _rawUnfilteredFeed == null || _rawUnfilteredFeed.Count == 0) return;
            _isLoadingMore = true;

            if (InfiniteScrollSpinner != null) InfiniteScrollSpinner.Visibility = Visibility.Visible;
            if (LoadMoreVideosBtn != null) LoadMoreVideosBtn.IsEnabled = false;

            try
            {
                var existingIds = new HashSet<string>(_rawUnfilteredFeed.Select(v => v.Id));
                List<VideoItem> moreVideos = new List<VideoItem>();

                if (!string.IsNullOrWhiteSpace(_currentSearchQuery))
                {
                    moreVideos = await YouTubeService.FetchNextSearchBatchAsync(_currentSearchQuery, existingIds, 35);
                }
                else
                {
                    moreVideos = await YouTubeService.FetchNextSearchBatchAsync("Trending Worldwide", existingIds, 35);
                }

                if (moreVideos.Count > 0)
                {
                    _rawUnfilteredFeed.AddRange(moreVideos);
                    ApplyCurrentFilters();
                    ShowToast($"✨ Loaded {moreVideos.Count} more videos (Total: {_currentFeed.Count})");
                }
                else
                {
                    ShowToast("ℹ️ All available videos loaded");
                }
            }
            catch (Exception ex)
            {
                ShowToast($"⚠️ Could not load more: {ex.Message}");
            }
            finally
            {
                _isLoadingMore = false;
                if (InfiniteScrollSpinner != null) InfiniteScrollSpinner.Visibility = Visibility.Collapsed;
                if (LoadMoreVideosBtn != null) LoadMoreVideosBtn.IsEnabled = true;
            }
        }

        public static string? ExtractYouTubeVideoId(string input)
        {
            if (string.IsNullOrWhiteSpace(input)) return null;

            input = input.Trim();

            // 1. Check if it's already an 11-character video ID
            if (System.Text.RegularExpressions.Regex.IsMatch(input, @"^[a-zA-Z0-9_-]{11}$"))
            {
                return input;
            }

            // 2. youtu.be/ID (e.g. https://youtu.be/u0JVCVOIePo?si=...)
            var matchShort = System.Text.RegularExpressions.Regex.Match(input, @"youtu\.be\/([a-zA-Z0-9_-]{11})");
            if (matchShort.Success)
            {
                return matchShort.Groups[1].Value;
            }

            // 3. youtube.com/watch?v=ID or /embed/ID or /shorts/ID or /live/ID
            var matchStandard = System.Text.RegularExpressions.Regex.Match(input, @"(?:v=|embed\/|shorts\/|live\/)([a-zA-Z0-9_-]{11})");
            if (matchStandard.Success)
            {
                return matchStandard.Groups[1].Value;
            }

            return null;
        }

        #endregion

        #region Video Player

        private async void VideoCard_MouseDown(object sender, MouseButtonEventArgs e)
        {
            if (sender is FrameworkElement elem && elem.DataContext is VideoItem video)
            {
                _currentVideoIndex = _currentFeed.IndexOf(video);
                await PlayVideoAsync(video);
            }
        }

        private async Task PlayVideoAsync(VideoItem video, double? resumePos = null)
        {
            _currentVideo = video;
            StorageService.AddHistory(video);

            CurrentVideoTitle.Text = video.Title;
            CurrentVideoChannel.Text = video.ChannelTitle;
            CurrentVideoDate.Text = !string.IsNullOrWhiteSpace(video.UploadDateText) ? $" • {video.UploadDateText}" : "";
            CurrentVideoViews.Text = !string.IsNullOrWhiteSpace(video.ViewCountText) ? $" • {video.ViewCountText}" : "";
            UpdateSubscribeToggleBtn();

            // If date is missing, fetch full details asynchronously
            if (string.IsNullOrWhiteSpace(video.UploadDateText))
            {
                _ = Task.Run(async () =>
                {
                    var details = await YouTubeService.GetVideoDetailsAsync(video.Id);
                    if (details != null && !string.IsNullOrWhiteSpace(details.UploadDateText))
                    {
                        Dispatcher.Invoke(() =>
                        {
                            if (_currentVideo?.Id == video.Id)
                            {
                                video.UploadDateText = details.UploadDateText;
                                CurrentVideoDate.Text = $" • {details.UploadDateText}";
                            }
                        });
                    }
                });
            }

            var isDisliked = StorageService.IsDisliked(video.Id);
            ThumbsUpBtn.Foreground = video.IsFavorite ? (System.Windows.Media.Brush)FindResource("AccentGold") : System.Windows.Media.Brushes.White;
            ThumbsDownBtn.Foreground = isDisliked ? (System.Windows.Media.Brush)FindResource("AccentRed") : System.Windows.Media.Brushes.White;
            FavBtn.Foreground = video.IsFavorite ? (System.Windows.Media.Brush)FindResource("AccentGold") : System.Windows.Media.Brushes.White;
            WatchLaterBtn.Foreground = video.IsWatchLater ? (System.Windows.Media.Brush)FindResource("AccentRed") : System.Windows.Media.Brushes.White;

            FeedView.Visibility = Visibility.Collapsed;
            PlayerView.Visibility = Visibility.Visible;
            UpdateAmbientGlowFromVideo(video);
            AnimateBottomBar(1.0);
            _bottomBarHideTimer?.Stop();
            _bottomBarHideTimer?.Start();

            if (VideoWebView.CoreWebView2 == null)
            {
                await InitializeWebViewAsync();
            }

            if (VideoWebView.CoreWebView2 != null)
            {
                VideoWebView.CoreWebView2.IsMuted = false;
            }

            // Load SponsorBlock segments in background
            _activeSponsorSegments = await SponsorBlockService.GetSegmentsAsync(video.Id);

            var savedPos = resumePos ?? StorageService.GetPlaybackPosition(video.Id);
            if (savedPos > 3)
            {
                var ts = TimeSpan.FromSeconds(savedPos);
                var timeFormatted = ts.Hours > 0 ? $"{ts.Hours}:{ts.Minutes:D2}:{ts.Seconds:D2}" : $"{ts.Minutes:D2}:{ts.Seconds:D2}";
                ShowToast($"▶ Resuming from {timeFormatted}");
            }

            var prefQuality = StorageService.Settings.PreferredQuality ?? "hd1080";
            UpdateQualityButtonText(prefQuality);

            var currentSrc = VideoWebView.Source?.ToString() ?? "";
            if (currentSrc.Contains("vixz.app/player.html"))
            {
                await VideoWebView.ExecuteScriptAsync($"loadVideo('{video.Id}', {savedPos.ToString(System.Globalization.CultureInfo.InvariantCulture)}, '{prefQuality}')");
            }
            else
            {
                VideoWebView.CoreWebView2?.Navigate($"https://vixz.app/player.html?v={video.Id}&t={savedPos.ToString(System.Globalization.CultureInfo.InvariantCulture)}&vq={prefQuality}");
            }

            // Inject SponsorBlock segments directly into player engine
            if (_activeSponsorSegments != null && _activeSponsorSegments.Count > 0)
            {
                var segsList = _activeSponsorSegments.Select(s => new { start = s.StartTime, end = s.EndTime, category = s.Category }).ToList();
                var json = Newtonsoft.Json.JsonConvert.SerializeObject(segsList);
                await VideoWebView.ExecuteScriptAsync($"setSponsorSegments({json})");
            }
            else
            {
                await VideoWebView.ExecuteScriptAsync("setSponsorSegments([])");
            }

            // Start SponsorBlock monitor
            StartSponsorBlockMonitor();
        }

        private async void SeekMinus10_Click(object sender, RoutedEventArgs e)
        {
            await VideoWebView.ExecuteScriptAsync("seek(-10);");
            ShowToast("⏪ -10s");
        }

        private async void SeekMinus5_Click(object sender, RoutedEventArgs e)
        {
            await VideoWebView.ExecuteScriptAsync("seek(-5);");
            ShowToast("⏪ -5s");
        }

        private async void SeekPlus5_Click(object sender, RoutedEventArgs e)
        {
            await VideoWebView.ExecuteScriptAsync("seek(5);");
            ShowToast("⏩ +5s");
        }

        private async void SeekPlus10_Click(object sender, RoutedEventArgs e)
        {
            await VideoWebView.ExecuteScriptAsync("seek(10);");
            ShowToast("⏩ +10s");
        }

        private async void PopOutPlayer_Click(object sender, RoutedEventArgs e)
        {
            if (_currentVideo == null)
            {
                ShowToast("No video currently loaded");
                return;
            }

            // 1. Synchronously mute main window WebView2 audio stream on line 1 before any async calls
            if (VideoWebView.CoreWebView2 != null)
            {
                VideoWebView.CoreWebView2.IsMuted = true;
            }

            double curSec = 0;
            try
            {
                var timeStr = await VideoWebView.ExecuteScriptAsync("getCurrentTime()");
                double.TryParse(timeStr, System.Globalization.NumberStyles.Any, System.Globalization.CultureInfo.InvariantCulture, out curSec);
                await VideoWebView.ExecuteScriptAsync("document.querySelectorAll('video, audio').forEach(m => { m.pause(); m.muted = true; }); if (window.player && typeof window.player.pauseVideo === 'function') window.player.pauseVideo();");
            }
            catch { }

            // Mute Main WebView2 natively at Chromium host level to guarantee 0 duplicate sound
            if (VideoWebView.CoreWebView2 != null)
            {
                VideoWebView.CoreWebView2.IsMuted = true;
            }

            _popOutWindow?.Close();
            _popOutWindow = new PopOutPlayerWindow(this, _currentVideo, curSec);

            var workArea = SystemParameters.WorkArea;
            _popOutWindow.Left = workArea.Right - _popOutWindow.Width - 30;
            _popOutWindow.Top = workArea.Bottom - _popOutWindow.Height - 30;

            _popOutWindow.Show();
            SwitchToFeedView();
            ShowToast("⧉ Floating Pop-Out Player Launched (Always on Top)");
        }

        public void ReturnFromPopOut(VideoItem video, double positionSeconds)
        {
            _popOutWindow = null;
            if (VideoWebView.CoreWebView2 != null)
            {
                VideoWebView.CoreWebView2.IsMuted = false;
            }
            VideoWebView.Visibility = Visibility.Visible;
            SwitchToPlayerView();
            _ = PlayVideoAsync(video, positionSeconds);
            ShowToast($"⧉ Restored to main player at {TimeSpan.FromSeconds(positionSeconds):mm\\:ss}");
        }

        private void StartSponsorBlockMonitor()
        {
            _sponsorBlockTimer?.Stop();
            if (_activeSponsorSegments == null || _activeSponsorSegments.Count == 0) return;

            _sponsorBlockTimer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(500) };
            _sponsorBlockTimer.Tick += async (s, e) =>
            {
                try
                {
                    if (VideoWebView.CoreWebView2 != null && _activeSponsorSegments.Count > 0)
                    {
                        var timeStr = await VideoWebView.ExecuteScriptAsync("getCurrentTime()");
                        if (double.TryParse(timeStr, System.Globalization.NumberStyles.Any, System.Globalization.CultureInfo.InvariantCulture, out double currentSec))
                        {
                            var segment = _activeSponsorSegments.FirstOrDefault(seg => currentSec >= seg.StartTime && currentSec < (seg.EndTime - 0.5));
                            if (segment != null)
                            {
                                await VideoWebView.ExecuteScriptAsync($"seek({segment.EndTime - currentSec + 0.1})");
                                ShowToast($"⏭️ Skipped {segment.Category}");
                            }
                        }
                    }
                }
                catch { }
            };
            _sponsorBlockTimer.Start();
        }

        public void PlayNextVideo()
        {
            if (_currentFeed.Count == 0) return;

            _currentVideoIndex++;
            if (_currentVideoIndex >= _currentFeed.Count)
            {
                _currentVideoIndex = 0;
            }

            var next = _currentFeed[_currentVideoIndex];
            _ = PlayVideoAsync(next);
            ShowToast("Autoplay: Playing Next Video ⏭️");
        }

        public void PlayPreviousVideo()
        {
            if (_currentFeed.Count == 0) return;

            _currentVideoIndex--;
            if (_currentVideoIndex < 0)
            {
                _currentVideoIndex = _currentFeed.Count - 1;
            }

            var prev = _currentFeed[_currentVideoIndex];
            _ = PlayVideoAsync(prev);
            ShowToast("Playing Previous Video ⏮️");
        }

        private void BackToFeed_Click(object sender, RoutedEventArgs e)
        {
            SwitchToFeedView();
        }

        private void NextVideoBtn_Click(object sender, RoutedEventArgs e)
        {
            PlayNextVideo();
        }

        private void ThumbsUpBtn_Click(object sender, RoutedEventArgs e)
        {
            if (_currentVideo != null)
            {
                if (StorageService.IsDisliked(_currentVideo.Id))
                {
                    StorageService.RemoveDislike(_currentVideo.Id);
                    ThumbsDownBtn.Foreground = System.Windows.Media.Brushes.White;
                }

                StorageService.ToggleFavorite(_currentVideo);
                ThumbsUpBtn.Foreground = _currentVideo.IsFavorite ? (System.Windows.Media.Brush)FindResource("AccentGold") : System.Windows.Media.Brushes.White;
                FavBtn.Foreground = _currentVideo.IsFavorite ? (System.Windows.Media.Brush)FindResource("AccentGold") : System.Windows.Media.Brushes.White;
                ShowToast(_currentVideo.IsFavorite ? "👍 Liked! Algorithm boosted for this creator." : "Removed Like");
            }
        }

        private void ThumbsDownBtn_Click(object sender, RoutedEventArgs e)
        {
            if (_currentVideo != null)
            {
                var targetVideo = _currentVideo;
                StorageService.AddDislike(targetVideo);

                ThumbsUpBtn.Foreground = System.Windows.Media.Brushes.White;
                FavBtn.Foreground = System.Windows.Media.Brushes.White;
                ThumbsDownBtn.Foreground = (System.Windows.Media.Brush)FindResource("AccentRed");

                // Completely remove from active feeds
                _currentFeed.RemoveAll(v => v.Id == targetVideo.Id);
                _rawUnfilteredFeed.RemoveAll(v => v.Id == targetVideo.Id);
                VideoItemsControl.ItemsSource = null;
                VideoItemsControl.ItemsSource = _currentFeed;

                ShowToast("👎 Disliked! Video removed & assigned negative score.");

                if (_currentFeed.Count > 0)
                {
                    PlayNextVideo();
                }
                else
                {
                    SwitchToFeedView();
                }
            }
        }

        private void FavBtn_Click(object sender, RoutedEventArgs e)
        {
            if (_currentVideo != null)
            {
                if (StorageService.IsDisliked(_currentVideo.Id))
                {
                    StorageService.RemoveDislike(_currentVideo.Id);
                    if (ThumbsDownBtn != null) ThumbsDownBtn.Foreground = System.Windows.Media.Brushes.White;
                }
                StorageService.ToggleFavorite(_currentVideo);
                FavBtn.Foreground = _currentVideo.IsFavorite ? (System.Windows.Media.Brush)FindResource("AccentGold") : System.Windows.Media.Brushes.White;
                if (ThumbsUpBtn != null) ThumbsUpBtn.Foreground = _currentVideo.IsFavorite ? (System.Windows.Media.Brush)FindResource("AccentGold") : System.Windows.Media.Brushes.White;
                ShowToast(_currentVideo.IsFavorite ? "Added to Favorites ⭐" : "Removed from Favorites");
            }
        }

        #endregion

        #region Video Card Context Menu Handlers

        private void ContextMenuPlay_Click(object sender, RoutedEventArgs e)
        {
            if (sender is MenuItem menuItem && menuItem.DataContext is VideoItem video)
            {
                _ = PlayVideoAsync(video);
            }
        }

        private void ContextMenuLike_Click(object sender, RoutedEventArgs e)
        {
            if (sender is MenuItem menuItem && menuItem.DataContext is VideoItem video)
            {
                if (StorageService.IsDisliked(video.Id))
                {
                    StorageService.RemoveDislike(video.Id);
                }
                StorageService.ToggleFavorite(video);
                ShowToast(video.IsFavorite ? "👍 Liked! Boosted in algorithm." : "Removed Like");
            }
        }

        private void ContextMenuDislike_Click(object sender, RoutedEventArgs e)
        {
            if (sender is MenuItem menuItem && menuItem.DataContext is VideoItem video)
            {
                StorageService.AddDislike(video);
                _currentFeed.RemoveAll(v => v.Id == video.Id);
                _rawUnfilteredFeed.RemoveAll(v => v.Id == video.Id);
                VideoItemsControl.ItemsSource = null;
                VideoItemsControl.ItemsSource = _currentFeed;
                ShowToast("👎 Disliked & removed from feed! Algorithm updated.");
            }
        }

        private void ContextMenuFavorite_Click(object sender, RoutedEventArgs e)
        {
            if (sender is MenuItem menuItem && menuItem.DataContext is VideoItem video)
            {
                StorageService.ToggleFavorite(video);
                ShowToast(video.IsFavorite ? "⭐ Added to Favorites" : "Removed from Favorites");
            }
        }

        private void ContextMenuWatchLater_Click(object sender, RoutedEventArgs e)
        {
            if (sender is MenuItem menuItem && menuItem.DataContext is VideoItem video)
            {
                StorageService.ToggleWatchLater(video);
                ShowToast(video.IsWatchLater ? "🕒 Added to Watch Later" : "Removed from Watch Later");
            }
        }

        private void ContextMenuShare_Click(object sender, RoutedEventArgs e)
        {
            if (sender is MenuItem menuItem && menuItem.DataContext is VideoItem video)
            {
                OpenShareModal(video, 0);
            }
        }

        private void ContextMenuCopyLink_Click(object sender, RoutedEventArgs e)
        {
            if (sender is MenuItem menuItem && menuItem.DataContext is VideoItem video)
            {
                var url = $"https://youtu.be/{video.Id}";
                Clipboard.SetText(url);
                ShowToast("Video link copied to clipboard! 📋✨");
            }
        }

        #endregion

        #region Share Modal & Social Handlers

        private int _shareCurrentSeconds = 0;
        private VideoItem? _shareTargetVideo = null;

        private async void ShareBtn_Click(object sender, RoutedEventArgs e)
        {
            if (_currentVideo == null)
            {
                ShowToast("No video currently playing ⚠️");
                return;
            }

            int currentSec = 0;
            try
            {
                if (VideoWebView?.CoreWebView2 != null)
                {
                    var timeStr = await VideoWebView.ExecuteScriptAsync("getCurrentTime()");
                    if (double.TryParse(timeStr, System.Globalization.NumberStyles.Any, System.Globalization.CultureInfo.InvariantCulture, out double parsedSec))
                    {
                        currentSec = (int)parsedSec;
                    }
                }
            }
            catch {}

            OpenShareModal(_currentVideo, currentSec);
        }

        private void OpenShareModal(VideoItem video, int startSeconds)
        {
            // Close other popups
            if (SleepTimerPopup != null) SleepTimerPopup.IsOpen = false;
            if (FolderPopup != null) FolderPopup.IsOpen = false;
            if (QualityPopup != null) QualityPopup.IsOpen = false;
            if (AccountPopup != null) AccountPopup.IsOpen = false;

            _shareTargetVideo = video;
            _shareCurrentSeconds = Math.Max(0, startSeconds);

            if (SharePopupVideoTitle != null) SharePopupVideoTitle.Text = video.Title;
            if (SharePopupVideoChannel != null) SharePopupVideoChannel.Text = video.ChannelTitle;

            if (ShareAtCurrentTimeCheckBox != null)
            {
                ShareAtCurrentTimeCheckBox.IsChecked = false;
                ShareAtCurrentTimeCheckBox.Content = $"Start at current time ({FormatPlaybackTime(_shareCurrentSeconds)})";
                ShareAtCurrentTimeCheckBox.Visibility = _shareCurrentSeconds > 0 ? Visibility.Visible : Visibility.Collapsed;
            }

            UpdateShareUrl();

            if (SharePopup != null)
            {
                SharePopup.IsOpen = true;
            }
        }

        private void UpdateShareUrl()
        {
            if (_shareTargetVideo == null) return;

            string baseUrl = $"https://youtu.be/{_shareTargetVideo.Id}";
            if (ShareAtCurrentTimeCheckBox?.IsChecked == true && _shareCurrentSeconds > 0)
            {
                baseUrl += $"?t={_shareCurrentSeconds}";
            }

            if (ShareUrlTextBox != null)
            {
                ShareUrlTextBox.Text = baseUrl;
            }
        }

        private string FormatPlaybackTime(int seconds)
        {
            var ts = TimeSpan.FromSeconds(Math.Max(0, seconds));
            return ts.Hours > 0 ? ts.ToString(@"h\:mm\:ss") : ts.ToString(@"m\:ss");
        }

        private void CloseShareModal_Click(object sender, RoutedEventArgs e)
        {
            if (SharePopup != null) SharePopup.IsOpen = false;
        }

        private void ShareTimestamp_Changed(object sender, RoutedEventArgs e)
        {
            UpdateShareUrl();
        }

        private async void CopyShareLink_Click(object sender, RoutedEventArgs e)
        {
            var url = ShareUrlTextBox?.Text;
            if (!string.IsNullOrWhiteSpace(url))
            {
                Clipboard.SetText(url);
                ShowToast("Link copied to clipboard! 📋✨");

                if (CopyShareLinkBtn != null)
                {
                    var orig = CopyShareLinkBtn.Content;
                    CopyShareLinkBtn.Content = "✓ Copied!";
                    await System.Threading.Tasks.Task.Delay(1500);
                    CopyShareLinkBtn.Content = orig;
                }
            }
        }

        private void ShareWhatsApp_Click(object sender, RoutedEventArgs e)
        {
            if (_shareTargetVideo == null) return;
            var url = ShareUrlTextBox?.Text ?? $"https://youtu.be/{_shareTargetVideo.Id}";
            var text = $"Check out \"{_shareTargetVideo.Title}\" by {_shareTargetVideo.ChannelTitle}: {url}";
            var waUrl = $"https://api.whatsapp.com/send?text={Uri.EscapeDataString(text)}";
            LaunchBrowserUrl(waUrl);
            ShowToast("Opening WhatsApp... 💬");
            if (SharePopup != null) SharePopup.IsOpen = false;
        }

        private void ShareTelegram_Click(object sender, RoutedEventArgs e)
        {
            if (_shareTargetVideo == null) return;
            var url = ShareUrlTextBox?.Text ?? $"https://youtu.be/{_shareTargetVideo.Id}";
            var title = _shareTargetVideo.Title;
            var tgUrl = $"https://t.me/share/url?url={Uri.EscapeDataString(url)}&text={Uri.EscapeDataString(title)}";
            LaunchBrowserUrl(tgUrl);
            ShowToast("Opening Telegram... ✈️");
            if (SharePopup != null) SharePopup.IsOpen = false;
        }

        private void ShareTwitter_Click(object sender, RoutedEventArgs e)
        {
            if (_shareTargetVideo == null) return;
            var url = ShareUrlTextBox?.Text ?? $"https://youtu.be/{_shareTargetVideo.Id}";
            var text = $"Watching \"{_shareTargetVideo.Title}\" on Vixz:";
            var twUrl = $"https://twitter.com/intent/tweet?text={Uri.EscapeDataString(text)}&url={Uri.EscapeDataString(url)}";
            LaunchBrowserUrl(twUrl);
            ShowToast("Opening X (Twitter)... 🐦");
            if (SharePopup != null) SharePopup.IsOpen = false;
        }

        private void ShareFacebook_Click(object sender, RoutedEventArgs e)
        {
            if (_shareTargetVideo == null) return;
            var url = ShareUrlTextBox?.Text ?? $"https://youtu.be/{_shareTargetVideo.Id}";
            var fbUrl = $"https://www.facebook.com/sharer/sharer.php?u={Uri.EscapeDataString(url)}";
            LaunchBrowserUrl(fbUrl);
            ShowToast("Opening Facebook... 📘");
            if (SharePopup != null) SharePopup.IsOpen = false;
        }

        private void ShareEmail_Click(object sender, RoutedEventArgs e)
        {
            if (_shareTargetVideo == null) return;
            var url = ShareUrlTextBox?.Text ?? $"https://youtu.be/{_shareTargetVideo.Id}";
            var subject = $"Video: {_shareTargetVideo.Title}";
            var body = $"Hey, check out this video:\n\n{_shareTargetVideo.Title}\nBy: {_shareTargetVideo.ChannelTitle}\n\n{url}";
            var mailUrl = $"mailto:?subject={Uri.EscapeDataString(subject)}&body={Uri.EscapeDataString(body)}";
            LaunchBrowserUrl(mailUrl);
            ShowToast("Opening Email Client... ✉️");
            if (SharePopup != null) SharePopup.IsOpen = false;
        }

        private void ShareMore_Click(object sender, RoutedEventArgs e)
        {
            if (_shareTargetVideo == null) return;
            var url = ShareUrlTextBox?.Text ?? $"https://youtu.be/{_shareTargetVideo.Id}";
            Clipboard.SetText(url);
            LaunchBrowserUrl(url);
            ShowToast("Opened in browser & copied link! 🌐📋");
            if (SharePopup != null) SharePopup.IsOpen = false;
        }

        private void CopyEmbedCode_Click(object sender, RoutedEventArgs e)
        {
            if (_shareTargetVideo == null) return;
            var embed = $"<iframe width=\"560\" height=\"315\" src=\"https://www.youtube.com/embed/{_shareTargetVideo.Id}\" title=\"YouTube video player\" frameborder=\"0\" allow=\"accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share\" allowfullscreen></iframe>";
            Clipboard.SetText(embed);
            ShowToast("HTML Embed code copied to clipboard! ‹/›✨");
        }

        private void LaunchBrowserUrl(string url)
        {
            try
            {
                System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo
                {
                    FileName = url,
                    UseShellExecute = true
                });
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"Failed to open URL: {ex.Message}");
            }
        }

        #endregion

        private void WatchLaterBtn_Click(object sender, RoutedEventArgs e)
        {
            if (_currentVideo != null)
            {
                StorageService.ToggleWatchLater(_currentVideo);
                WatchLaterBtn.Foreground = _currentVideo.IsWatchLater ? (System.Windows.Media.Brush)FindResource("AccentRed") : System.Windows.Media.Brushes.White;
                ShowToast(_currentVideo.IsWatchLater ? "Added to Watch Later 🕒" : "Removed from Watch Later");
            }
        }

        private void AutoplayBtn_Click(object sender, RoutedEventArgs e)
        {
            StorageService.Settings.IsAutoplayEnabled = !StorageService.Settings.IsAutoplayEnabled;
            StorageService.Save();
            UpdateAutoplayUi();
            ShowToast(StorageService.Settings.IsAutoplayEnabled ? "▶️ Autoplay is ON" : "⏸️ Autoplay is OFF");
        }

        private void UpdateAutoplayUi()
        {
            if (AutoplayBtn != null)
            {
                AutoplayBtn.Content = StorageService.Settings.IsAutoplayEnabled ? "▶️" : "⏸️";
                AutoplayBtn.Foreground = StorageService.Settings.IsAutoplayEnabled ? (System.Windows.Media.Brush)FindResource("AccentGold") : System.Windows.Media.Brushes.Gray;
            }
        }

        #region Video & Audio Download & Screenshot

        private void DownloadVideoBtn_Click(object sender, RoutedEventArgs e)
        {
            if (_currentVideo == null)
            {
                ShowToast("⚠️ No video is currently playing");
                return;
            }
            if (DownloadChoicePopup != null)
            {
                DownloadChoicePopup.IsOpen = !DownloadChoicePopup.IsOpen;
            }
        }

        private void CloseDownloadPopup_Click(object sender, RoutedEventArgs e)
        {
            if (DownloadChoicePopup != null) DownloadChoicePopup.IsOpen = false;
        }

        private void OpenDownloadsFolder_Click(object sender, RoutedEventArgs e)
        {
            if (DownloadChoicePopup != null) DownloadChoicePopup.IsOpen = false;
            try
            {
                var downloadsFolder = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "Downloads", "Vixz");
                if (!Directory.Exists(downloadsFolder))
                {
                    Directory.CreateDirectory(downloadsFolder);
                }
                System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo
                {
                    FileName = "explorer.exe",
                    Arguments = $"\"{downloadsFolder}\"",
                    UseShellExecute = true
                });
                ShowToast("📂 Opening Downloads folder...");
            }
            catch (Exception ex)
            {
                ShowToast($"⚠️ Could not open folder: {ex.Message}");
            }
        }

        private async void DownloadMp4Choice_Click(object sender, RoutedEventArgs e)
        {
            if (DownloadChoicePopup != null) DownloadChoicePopup.IsOpen = false;
            await DownloadCurrentVideoAsync(isVideo: true);
        }

        private async void DownloadMp3Choice_Click(object sender, RoutedEventArgs e)
        {
            if (DownloadChoicePopup != null) DownloadChoicePopup.IsOpen = false;
            await DownloadCurrentVideoAsync(isVideo: false);
        }

        private bool _isDownloading = false;

        private async Task DownloadCurrentVideoAsync(bool isVideo = true)
        {
            if (_currentVideo == null)
            {
                ShowToast("⚠️ No video is currently playing");
                return;
            }

            if (_isDownloading)
            {
                ShowToast("⏳ Download already in progress...");
                return;
            }

            _isDownloading = true;
            DownloadVideoBtn.Foreground = (System.Windows.Media.Brush)FindResource("AccentGold");
            ShowToast(isVideo ? "📥 Fetching video streams..." : "🎵 Extracting HQ audio...");

            try
            {
                var progressHandler = new Progress<double>(p =>
                {
                    var percent = (int)(p * 100);
                    if (percent % 10 == 0 || percent == 100)
                    {
                        Dispatcher.Invoke(() => ShowToast(isVideo ? $"📥 Downloading MP4: {percent}%" : $"🎵 Downloading Audio: {percent}%"));
                    }
                });

                var filePath = isVideo 
                    ? await DownloadService.DownloadVideoAsync(_currentVideo.Id, _currentVideo.Title, progressHandler)
                    : await DownloadService.DownloadAudioAsync(_currentVideo.Id, _currentVideo.Title, progressHandler);

                ShowToast(isVideo ? "✅ Video Download Complete!" : "✅ Audio Download Complete!");

                var typeName = isVideo ? "Video" : "Audio";
                var result = MessageBox.Show($"{typeName} downloaded successfully!\n\nSaved to: {filePath}\n\nWould you like to open the folder?", "Download Complete", MessageBoxButton.YesNo, MessageBoxImage.Information);
                if (result == MessageBoxResult.Yes)
                {
                    System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo
                    {
                        FileName = "explorer.exe",
                        Arguments = $"/select,\"{filePath}\"",
                        UseShellExecute = true
                    });
                }
            }
            catch (Exception ex)
            {
                ShowToast($"⚠️ Download failed: {ex.Message}");
            }
            finally
            {
                _isDownloading = false;
                DownloadVideoBtn.Foreground = System.Windows.Media.Brushes.White;
            }
        }

        private async void ScreenshotBtn_Click(object sender, RoutedEventArgs e)
        {
            await CaptureScreenshotAsync();
        }

        private async Task CaptureScreenshotAsync()
        {
            // Shutter Flash Animation
            TriggerShutterFlash();

            double currentSec = 0;
            try
            {
                var timeStr = await VideoWebView.ExecuteScriptAsync("getCurrentTime()");
                double.TryParse(timeStr, System.Globalization.NumberStyles.Any, System.Globalization.CultureInfo.InvariantCulture, out currentSec);
            }
            catch { }

            var path = await ScreenshotService.CaptureAndSaveAsync(
                VideoWebView,
                PlayerContainer,
                _currentVideo?.Title ?? "Video",
                currentSec,
                StorageService.Settings.ActiveScreenshotFolder
            );

            if (path != null)
            {
                var folderDisplay = System.IO.Path.GetDirectoryName(path) ?? ScreenshotService.GetTargetDirectory(StorageService.Settings.ActiveScreenshotFolder);
                ShowToast($"📸 Saved to {folderDisplay}");
            }
            else
            {
                ShowToast("⚠️ Failed to capture screenshot");
            }
        }

        private void TriggerShutterFlash()
        {
            var anim = new DoubleAnimation(0.85, 0.0, TimeSpan.FromMilliseconds(200));
            ShutterFlash.BeginAnimation(OpacityProperty, anim);
        }

        private void ChangeFolder_Click(object sender, RoutedEventArgs e)
        {
            UpdateFolderUi();
            FolderListBox.ItemsSource = StorageService.Settings.ScreenshotFolders.ToList();
            FolderListBox.SelectedItem = StorageService.Settings.ActiveScreenshotFolder;
            FolderPopup.IsOpen = true;
        }

        private void CloseFolderModal_Click(object sender, RoutedEventArgs e)
        {
            FolderPopup.IsOpen = false;
        }

        private void BrowseAndSelectFolder_Click(object sender, RoutedEventArgs e)
        {
            try
            {
                var dialog = new Microsoft.Win32.OpenFolderDialog
                {
                    Title = "Select Screenshot Destination Folder",
                    InitialDirectory = ScreenshotService.GetTargetDirectory(),
                    Multiselect = false
                };

                if (dialog.ShowDialog() == true)
                {
                    var selectedPath = dialog.FolderName;
                    if (!string.IsNullOrWhiteSpace(selectedPath))
                    {
                        var folderName = System.IO.Path.GetFileName(selectedPath);
                        if (string.IsNullOrWhiteSpace(folderName)) folderName = selectedPath;

                        StorageService.Settings.CustomScreenshotPath = selectedPath;
                        StorageService.Settings.CustomFolderPaths[folderName] = selectedPath;

                        if (!StorageService.Settings.ScreenshotFolders.Contains(folderName))
                        {
                            StorageService.Settings.ScreenshotFolders.Insert(0, folderName);
                        }

                        StorageService.Settings.ActiveScreenshotFolder = folderName;
                        StorageService.Save();
                        UpdateFolderUi();
                        FolderListBox.ItemsSource = StorageService.Settings.ScreenshotFolders.ToList();
                        FolderListBox.SelectedItem = folderName;
                        FolderPopup.IsOpen = false;
                        ShowToast($"📸 Screenshot folder set to {folderName}");
                    }
                }
            }
            catch (Exception ex)
            {
                ShowToast($"Error opening folder picker: {ex.Message}");
            }
        }

        private void FolderListBox_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            if (FolderListBox.SelectedItem is string selected)
            {
                StorageService.Settings.ActiveScreenshotFolder = selected;
                if (StorageService.Settings.CustomFolderPaths.TryGetValue(selected, out var customPath) && !string.IsNullOrWhiteSpace(customPath))
                {
                    StorageService.Settings.CustomScreenshotPath = customPath;
                }
                else if (selected == "Default" || selected == "Screenshots" || selected == "Favorites" || selected == "Recipes" || selected == "Notes" || selected == "Tutorials")
                {
                    StorageService.Settings.CustomScreenshotPath = null;
                }
                StorageService.Save();
                UpdateFolderUi();
                ShowToast($"Active folder: {selected}");
            }
        }

        private void AddNewFolder_Click(object sender, RoutedEventArgs e)
        {
            var newName = NewFolderInput.Text.Trim();
            if (!string.IsNullOrWhiteSpace(newName))
            {
                if (!StorageService.Settings.ScreenshotFolders.Contains(newName))
                {
                    StorageService.Settings.ScreenshotFolders.Add(newName);
                }
                StorageService.Settings.ActiveScreenshotFolder = newName;
                StorageService.Settings.CustomScreenshotPath = null;
                StorageService.Save();
                UpdateFolderUi();
                FolderListBox.ItemsSource = StorageService.Settings.ScreenshotFolders.ToList();
                FolderListBox.SelectedItem = newName;
                NewFolderInput.Text = "";
                ShowToast($"Created & Selected: {newName}");
            }
        }

        private void OpenFolder_Click(object sender, RoutedEventArgs e)
        {
            ScreenshotService.OpenFolderInExplorer();
        }

        private void UpdateFolderUi()
        {
            var targetDir = ScreenshotService.GetTargetDirectory();
            if (CurrentFolderText != null) CurrentFolderText.Text = targetDir;
            if (PopupCurrentFolderText != null) PopupCurrentFolderText.Text = targetDir;
        }

        #endregion

        #region Sleep Timer

        private void SleepTimerBtn_Click(object sender, RoutedEventArgs e)
        {
            SleepTimerPopup.IsOpen = true;
        }

        private void CloseSleepModal_Click(object sender, RoutedEventArgs e)
        {
            SleepTimerPopup.IsOpen = false;
        }

        private void SleepSlider_ValueChanged(object sender, RoutedPropertyChangedEventArgs<double> e)
        {
            if (SleepSliderValueText != null)
            {
                SleepSliderValueText.Text = $"{(int)e.NewValue} minutes";
            }
        }

        private void Preset15_Click(object sender, RoutedEventArgs e) { SleepSlider.Value = 15; }
        private void Preset30_Click(object sender, RoutedEventArgs e) { SleepSlider.Value = 30; }
        private void Preset45_Click(object sender, RoutedEventArgs e) { SleepSlider.Value = 45; }
        private void Preset60_Click(object sender, RoutedEventArgs e) { SleepSlider.Value = 60; }

        private void StartSleepTimer_Click(object sender, RoutedEventArgs e)
        {
            int minutes = (int)SleepSlider.Value;
            _lastSleepDurationMinutes = minutes;
            _sleepRemainingSeconds = minutes * 60;

            _sleepTimer?.Stop();
            _sleepTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(1) };
            _sleepTimer.Tick += SleepTimer_Tick;
            _sleepTimer.Start();

            SleepCountdownBadge.Visibility = Visibility.Visible;
            CancelSleepBtn.Visibility = Visibility.Visible;
            SleepTimerPopup.IsOpen = false;

            ShowToast($"🌙 Sleep Timer set for {minutes}m");
        }

        private void CancelSleepTimer_Click(object sender, RoutedEventArgs e)
        {
            _sleepTimer?.Stop();
            SleepCountdownBadge.Visibility = Visibility.Collapsed;
            CancelSleepBtn.Visibility = Visibility.Collapsed;
            SleepTimerPopup.IsOpen = false;
            ShowToast("🌙 Sleep Timer Cancelled");
        }

        private void SleepTimer_Tick(object? sender, EventArgs e)
        {
            _sleepRemainingSeconds--;
            if (_sleepRemainingSeconds > 0)
            {
                var ts = TimeSpan.FromSeconds(_sleepRemainingSeconds);
                SleepCountdownText.Text = $"{ts.Minutes:D2}:{ts.Seconds:D2}";
            }
            else
            {
                _sleepTimer?.Stop();
                SleepCountdownBadge.Visibility = Visibility.Collapsed;

                // Auto-pause video
                _ = VideoWebView.ExecuteScriptAsync("pauseVideo()");

                // Show 1-Click Resume Pill
                ResumeSleepText.Text = $"Resume for {_lastSleepDurationMinutes}m 🌙";
                ResumeSleepPill.Visibility = Visibility.Visible;
            }
        }

        private void ResumeSleepPill_MouseDown(object sender, MouseButtonEventArgs e)
        {
            ResumeSleepPill.Visibility = Visibility.Collapsed;
            _ = VideoWebView.ExecuteScriptAsync("playVideo()");
            
            // Re-arm timer
            _sleepRemainingSeconds = _lastSleepDurationMinutes * 60;
            _sleepTimer?.Start();
            SleepCountdownBadge.Visibility = Visibility.Visible;

            ShowToast($"🌙 Resumed for {_lastSleepDurationMinutes}m");
        }

        #endregion

        #region Window Controls & Keyboard Shortcuts

        private void TitleBar_MouseDown(object sender, MouseButtonEventArgs e)
        {
            if (e.ChangedButton == MouseButton.Left)
            {
                if (e.ClickCount == 2)
                {
                    MaximizeToggle();
                }
                else
                {
                    DragMove();
                }
            }
        }

        private void PinButton_Click(object sender, RoutedEventArgs e)
        {
            _isAlwaysOnTop = !_isAlwaysOnTop;
            Topmost = _isAlwaysOnTop;
            PinButton.Foreground = _isAlwaysOnTop ? (System.Windows.Media.Brush)FindResource("AccentGold") : System.Windows.Media.Brushes.White;
            ShowToast(_isAlwaysOnTop ? "📌 Always-on-Top Pinned" : "Unpinned");
        }

        protected override void OnStateChanged(EventArgs e)
        {
            base.OnStateChanged(e);
            if (MaximizeBtn != null)
            {
                MaximizeBtn.Content = WindowState == WindowState.Maximized ? "❐" : "▢";
            }
            if (WindowState == WindowState.Maximized)
            {
                if (MainBorder != null)
                {
                    MainBorder.CornerRadius = new CornerRadius(0);
                    MainBorder.BorderThickness = new Thickness(0);
                }
                if (TitleBarBorder != null)
                {
                    TitleBarBorder.CornerRadius = new CornerRadius(0);
                }
            }
            else
            {
                if (MainBorder != null)
                {
                    MainBorder.CornerRadius = new CornerRadius(14);
                    MainBorder.BorderThickness = new Thickness(1);
                }
                if (TitleBarBorder != null)
                {
                    TitleBarBorder.CornerRadius = new CornerRadius(14, 14, 0, 0);
                }
            }
        }

        private void MinimizeButton_Click(object sender, RoutedEventArgs e)
        {
            WindowState = WindowState.Minimized;
        }

        private void MaximizeButton_Click(object sender, RoutedEventArgs e)
        {
            MaximizeToggle();
        }

        private void MaximizeToggle()
        {
            WindowState = WindowState == WindowState.Maximized ? WindowState.Normal : WindowState.Maximized;
        }

        private void PlayerContainer_MouseDown(object sender, MouseButtonEventArgs e)
        {
            if (e.ClickCount == 2)
            {
                MaximizeToggle();
            }
        }

        private void CloseButton_Click(object sender, RoutedEventArgs e)
        {
            Close();
        }

        private void DevToolsBtn_Click(object sender, RoutedEventArgs e)
        {
            VideoWebView.CoreWebView2?.OpenDevToolsWindow();
        }

        private void FullscreenBtn_Click(object sender, RoutedEventArgs e)
        {
            ToggleFullscreen();
        }

        private void ToggleFullscreen()
        {
            _isCustomFullscreen = !_isCustomFullscreen;
            if (_isCustomFullscreen)
            {
                WindowState = WindowState.Maximized;
                SidebarCol.Width = new GridLength(0);
                TitleBarRow.Height = new GridLength(0);
            }
            else
            {
                WindowState = WindowState.Normal;
                SidebarCol.Width = new GridLength(210);
                TitleBarRow.Height = new GridLength(46);
            }
        }

        private async void Window_PreviewKeyDown(object sender, KeyEventArgs e)
        {
            // Do not intercept hotkeys if typing inside any input text box
            if (Keyboard.FocusedElement is TextBox || 
                Keyboard.FocusedElement is PasswordBox || 
                SearchBox.IsFocused || 
                (AiPromptBox != null && AiPromptBox.IsFocused) || 
                (NewFolderInput != null && NewFolderInput.IsFocused))
            {
                return;
            }

            switch (e.Key)
            {
                case Key.Space:
                    e.Handled = true;
                    await VideoWebView.ExecuteScriptAsync("togglePlay();");
                    break;
                case Key.K:
                    e.Handled = true;
                    await VideoWebView.ExecuteScriptAsync("togglePlay();");
                    break;
                case Key.Left:
                    e.Handled = true;
                    await VideoWebView.ExecuteScriptAsync("seek(-5);");
                    ShowToast("⏪ -5s");
                    break;
                case Key.Right:
                    e.Handled = true;
                    await VideoWebView.ExecuteScriptAsync("seek(5);");
                    ShowToast("⏩ +5s");
                    break;
                case Key.J:
                    e.Handled = true;
                    await VideoWebView.ExecuteScriptAsync("seek(-10);");
                    ShowToast("⏪ -10s");
                    break;
                case Key.L:
                    e.Handled = true;
                    await VideoWebView.ExecuteScriptAsync("seek(10);");
                    ShowToast("⏩ +10s");
                    break;
                case Key.M:
                    e.Handled = true;
                    await VideoWebView.ExecuteScriptAsync("toggleMute();");
                    break;
                case Key.S:
                    e.Handled = true;
                    await CaptureScreenshotAsync();
                    break;
                case Key.D:
                    e.Handled = true;
                    await DownloadCurrentVideoAsync();
                    break;
                case Key.F:
                    e.Handled = true;
                    ToggleFullscreen();
                    break;
                case Key.T:
                    e.Handled = true;
                    PinButton_Click(this, new RoutedEventArgs());
                    break;
                case Key.N:
                    e.Handled = true;
                    PlayNextVideo();
                    break;
                case Key.P:
                    e.Handled = true;
                    PlayPreviousVideo();
                    break;
                case Key.F12:
                    e.Handled = true;
                    VideoWebView.CoreWebView2?.OpenDevToolsWindow();
                    break;
                case Key.Escape:
                    e.Handled = true;
                    if (SleepTimerPopup.IsOpen) SleepTimerPopup.IsOpen = false;
                    if (FolderPopup.IsOpen) FolderPopup.IsOpen = false;
                    if (_isCustomFullscreen) ToggleFullscreen();
                    break;
            }
        }

        private DispatcherTimer? _toastTimer;

        public void ShowToast(string message)
        {
            if (BottomToastText == null || BottomToastPill == null) return;

            BottomToastText.Text = message;
            BottomToastPill.Visibility = Visibility.Visible;

            _toastTimer?.Stop();
            _toastTimer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(2000) };
            _toastTimer.Tick += (s, e) =>
            {
                BottomToastPill.Visibility = Visibility.Collapsed;
                _toastTimer.Stop();
            };
            _toastTimer.Start();
        }

        #endregion

        #region User Account & Google Profile

        private void AccountBtn_Click(object sender, RoutedEventArgs e)
        {
            var account = StorageService.Settings.UserAccount;
            if (account != null && account.IsSignedIn)
            {
                AccountPopup.IsOpen = !AccountPopup.IsOpen;
            }
            else
            {
                OpenSignInWindow_Click(sender, e);
            }
        }

        private void CloseAccountModal_Click(object sender, RoutedEventArgs e)
        {
            AccountPopup.IsOpen = false;
        }

        private void OpenSignInWindow_Click(object sender, RoutedEventArgs e)
        {
            AccountPopup.IsOpen = false;
            var win = new SignInWindow { Owner = this };
            if (win.ShowDialog() == true || win.IsSuccess)
            {
                UpdateAccountUi();
                ShowToast($"👤 Signed in as {StorageService.Settings.UserAccount.DisplayName}!");
            }
        }

        private async void SyncAccountSubscriptions_Click(object sender, RoutedEventArgs e)
        {
            AccountPopup.IsOpen = false;
            ShowToast("🔄 Syncing subscriptions & feed...");
            await LoadFeedAsync("Recommended Feed", () => YouTubeService.GetHomeFeedAsync());
            ShowToast("✅ Subscriptions & feed synced!");
        }

        private void SignOutAccount_Click(object sender, RoutedEventArgs e)
        {
            AccountPopup.IsOpen = false;
            StorageService.SignOutUser();
            UpdateAccountUi();
            ShowToast("🚪 Signed out of Google Account");
        }

        #endregion

        #region Auto-Fading Bottom Player Bar

        private DispatcherTimer? _bottomBarHideTimer;
        private bool _isMouseOverBottomBar = false;

        private void SetupBottomBarAutoFade()
        {
            _bottomBarHideTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(2.5) };
            _bottomBarHideTimer.Tick += (s, e) =>
            {
                if (PlayerView.Visibility == Visibility.Visible && !_isMouseOverBottomBar)
                {
                    // If any popup is open, don't hide the bar
                    if ((SharePopup != null && SharePopup.IsOpen) ||
                        (DownloadChoicePopup != null && DownloadChoicePopup.IsOpen) ||
                        (QualityPopup != null && QualityPopup.IsOpen) ||
                        (SleepTimerPopup != null && SleepTimerPopup.IsOpen) ||
                        (FolderPopup != null && FolderPopup.IsOpen) ||
                        (AccountPopup != null && AccountPopup.IsOpen))
                    {
                        return;
                    }

                    AnimateBottomBar(0.0);
                }
            };
        }

        private void PlayerView_MouseMove(object sender, MouseEventArgs e)
        {
            if (PlayerView.Visibility == Visibility.Visible)
            {
                AnimateBottomBar(1.0);
                _bottomBarHideTimer?.Stop();
                if (!_isMouseOverBottomBar)
                {
                    _bottomBarHideTimer?.Start();
                }
            }
        }

        private void PlayerBottomBar_MouseEnter(object sender, MouseEventArgs e)
        {
            _isMouseOverBottomBar = true;
            _bottomBarHideTimer?.Stop();
            AnimateBottomBar(1.0);
        }

        private void PlayerBottomBar_MouseLeave(object sender, MouseEventArgs e)
        {
            _isMouseOverBottomBar = false;
            _bottomBarHideTimer?.Stop();
            _bottomBarHideTimer?.Start();
        }

        private void AnimateBottomBar(double targetOpacity)
        {
            if (PlayerBottomBar == null) return;
            if (Math.Abs(PlayerBottomBar.Opacity - targetOpacity) < 0.01) return;

            var anim = new DoubleAnimation(targetOpacity, TimeSpan.FromMilliseconds(220));
            PlayerBottomBar.BeginAnimation(OpacityProperty, anim);
        }

        #endregion

        #region Collapsible Sidebar

        private void SidebarToggle_Click(object sender, RoutedEventArgs e)
        {
            StorageService.Settings.IsSidebarCollapsed = !StorageService.Settings.IsSidebarCollapsed;
            StorageService.Save();
            ApplySidebarState();
            ShowToast(StorageService.Settings.IsSidebarCollapsed ? "◀ Sidebar Folded" : "☰ Sidebar Expanded");
        }

        private void ApplySidebarState()
        {
            if (SidebarCol == null) return;
            double targetWidth = StorageService.Settings.IsSidebarCollapsed ? 0 : 210;

            if (SidebarToggleBtn != null)
            {
                SidebarToggleBtn.Content = StorageService.Settings.IsSidebarCollapsed ? "▶" : "☰";
                SidebarToggleBtn.ToolTip = StorageService.Settings.IsSidebarCollapsed ? "Expand Sidebar Menu" : "Fold Sidebar Menu";
                SidebarToggleBtn.Foreground = StorageService.Settings.IsSidebarCollapsed ? (System.Windows.Media.Brush)FindResource("AccentGold") : System.Windows.Media.Brushes.White;
            }

            SidebarCol.Width = new GridLength(targetWidth);
        }

        #endregion

        #region Dynamic Ambient Lighting Glow

        private void AmbientGlowBtn_Click(object sender, RoutedEventArgs e)
        {
            StorageService.Settings.IsAmbientGlowEnabled = !StorageService.Settings.IsAmbientGlowEnabled;
            StorageService.Save();
            ApplyAmbientGlowState();
            ShowToast(StorageService.Settings.IsAmbientGlowEnabled ? "💡 Ambient Glow: ON" : "💡 Ambient Glow: OFF");
        }

        private void ApplyAmbientGlowState()
        {
            if (AmbientGlowBorder != null)
            {
                AmbientGlowBorder.Visibility = StorageService.Settings.IsAmbientGlowEnabled ? Visibility.Visible : Visibility.Collapsed;
            }
            if (AmbientGlowBtn != null)
            {
                AmbientGlowBtn.Foreground = StorageService.Settings.IsAmbientGlowEnabled ? (System.Windows.Media.Brush)FindResource("AccentGold") : System.Windows.Media.Brushes.Gray;
            }
            UpdateAmbientGlowFromVideo(_currentVideo);
        }

        private void UpdateAmbientGlowFromVideo(VideoItem? video)
        {
            if (video == null) return;

            // Generate an adaptive cinematic color palette for the ambient glow
            var hash = Math.Abs((video.Title + video.ChannelTitle).GetHashCode());
            byte r = (byte)(80 + (hash % 160));
            byte g = (byte)(35 + ((hash / 13) % 130));
            byte b = (byte)(90 + ((hash / 17) % 150));
            var colorHex = $"#{r:X2}{g:X2}{b:X2}";

            if (AmbientGlowBorder != null)
            {
                AmbientGlowBorder.Background = new System.Windows.Media.SolidColorBrush(System.Windows.Media.Color.FromArgb(95, r, g, b));
            }

            if (VideoWebView?.CoreWebView2 != null)
            {
                _ = VideoWebView.ExecuteScriptAsync($"setAmbientGlow({StorageService.Settings.IsAmbientGlowEnabled.ToString().ToLower()}, '{colorHex}');");
            }

            if (PlayerView != null)
            {
                PlayerView.Background = StorageService.Settings.IsAmbientGlowEnabled 
                    ? new System.Windows.Media.SolidColorBrush(System.Windows.Media.Color.FromArgb(50, r, g, b))
                    : new System.Windows.Media.SolidColorBrush(System.Windows.Media.Color.FromRgb(9, 9, 12));
            }
        }

        #endregion

        #region Subscription Folder Groupings

        private string _activeSubscriptionFolder = "All";

        private void SubscriptionFolderChip_Click(object sender, RoutedEventArgs e)
        {
            if (sender is Button btn && btn.Tag is string folderName)
            {
                _activeSubscriptionFolder = folderName;
                UpdateFolderChipHighlights();

                if (folderName == "All")
                {
                    SubscribedChannelsList.ItemsSource = WillRyanProfileData.SubscribedChannels;
                }
                else if (StorageService.Settings.SubscriptionFolders.TryGetValue(folderName, out var channels))
                {
                    var matched = WillRyanProfileData.SubscribedChannels
                        .Where(c => channels.Any(ch => c.IndexOf(ch, StringComparison.OrdinalIgnoreCase) >= 0 || ch.IndexOf(c, StringComparison.OrdinalIgnoreCase) >= 0))
                        .ToList();
                    
                    if (matched.Count == 0)
                    {
                        matched = channels;
                    }
                    SubscribedChannelsList.ItemsSource = matched;
                }
                ShowToast($"📁 Folder: {folderName}");
            }
        }

        private void UpdateFolderChipHighlights()
        {
            var gold = (System.Windows.Media.Brush)FindResource("AccentGold");
            var normal = (System.Windows.Media.Brush)FindResource("TextSecondary");

            if (FolderChipAll != null) FolderChipAll.Foreground = _activeSubscriptionFolder == "All" ? gold : normal;
            if (FolderChipAi != null) FolderChipAi.Foreground = _activeSubscriptionFolder == "AI & Tech" ? gold : normal;
            if (FolderChipPodcasts != null) FolderChipPodcasts.Foreground = _activeSubscriptionFolder == "Podcasts" ? gold : normal;
            if (FolderChipGaming != null) FolderChipGaming.Foreground = _activeSubscriptionFolder == "Gaming" ? gold : normal;
            if (FolderChipMusic != null) FolderChipMusic.Foreground = _activeSubscriptionFolder == "Music" ? gold : normal;
        }

        #endregion

        #region Video Quality

        private void QualityBtn_Click(object sender, RoutedEventArgs e)
        {
            QualityPopup.IsOpen = !QualityPopup.IsOpen;
        }

        private async void QualityOption_Click(object sender, RoutedEventArgs e)
        {
            if (sender is Button btn && btn.Tag is string q)
            {
                QualityPopup.IsOpen = false;
                StorageService.SetPreferredQuality(q);
                UpdateQualityButtonText(q);
                await VideoWebView.ExecuteScriptAsync($"setQuality('{q}')");
                ShowToast($"📺 Quality set to {QualityBtn.Content}");
            }
        }

        #endregion

        #region AI Copilot & Assistant

        private void AiCopilotBtn_Click(object sender, RoutedEventArgs e)
        {
            ToggleAiCopilotDrawer();
        }

        private double _savedAiDrawerWidth = 390;

        private void CloseAiCopilot_Click(object sender, RoutedEventArgs e)
        {
            if (AiDrawerCol.ActualWidth > 200)
            {
                _savedAiDrawerWidth = AiDrawerCol.ActualWidth;
            }
            AiCopilotPanel.Visibility = Visibility.Collapsed;
            AiGridSplitter.Visibility = Visibility.Collapsed;
            AiDrawerCol.Width = new GridLength(0);
        }

        private void ToggleAiCopilotDrawer()
        {
            if (AiCopilotPanel.Visibility == Visibility.Visible)
            {
                CloseAiCopilot_Click(null!, null!);
            }
            else
            {
                AiCopilotPanel.Visibility = Visibility.Visible;
                AiGridSplitter.Visibility = Visibility.Visible;
                AiDrawerCol.Width = new GridLength(Math.Max(320, _savedAiDrawerWidth));
                AiPromptBox.Focus();
            }
        }

        private void AiSettingsBtn_Click(object sender, RoutedEventArgs e)
        {
            var currentKey = StorageService.Settings.GeminiApiKey ?? "";
            var prompt = new Window
            {
                Title = "⚙️ Vixz AI Brain Settings",
                Width = 470,
                Height = 280,
                WindowStartupLocation = WindowStartupLocation.CenterOwner,
                Owner = this,
                Background = (System.Windows.Media.Brush)FindResource("BgDarkPrimary"),
                Foreground = System.Windows.Media.Brushes.White,
                WindowStyle = WindowStyle.ToolWindow,
                ResizeMode = ResizeMode.NoResize
            };

            var sp = new StackPanel { Margin = new Thickness(18) };
            var heading = new TextBlock
            {
                Text = "⚡ Connect Real AI (Gemini / Groq / OpenAI)",
                FontSize = 14,
                FontWeight = FontWeights.Bold,
                Foreground = (System.Windows.Media.Brush)FindResource("AccentGold"),
                Margin = new Thickness(0, 0, 0, 8)
            };
            var desc = new TextBlock
            {
                Text = "Paste your free API key from Google AI Studio (Gemini 2.0 / 1.5 Flash), Groq, or OpenAI to enable full conversational ChatGPT-level intelligence and video reasoning:",
                FontSize = 11.5,
                Foreground = (System.Windows.Media.Brush)FindResource("TextSecondary"),
                TextWrapping = TextWrapping.Wrap,
                Margin = new Thickness(0, 0, 0, 12)
            };

            var txtBox = new TextBox
            {
                Text = currentKey,
                Background = (System.Windows.Media.Brush)FindResource("BgDarkTertiary"),
                Foreground = System.Windows.Media.Brushes.White,
                BorderBrush = (System.Windows.Media.Brush)FindResource("BorderSubtle"),
                FontSize = 12,
                Padding = new Thickness(8, 6, 8, 6),
                Margin = new Thickness(0, 0, 0, 14)
            };

            var btnRow = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right };
            var clearBtn = new Button
            {
                Content = "Clear Key",
                Style = (Style)FindResource("GlassButton"),
                Padding = new Thickness(12, 6, 12, 6),
                Margin = new Thickness(0, 0, 8, 0)
            };
            clearBtn.Click += (s, ev) =>
            {
                StorageService.Settings.GeminiApiKey = null;
                StorageService.Save();
                ShowToast("Cleared AI API Key");
                prompt.Close();
            };

            var saveBtn = new Button
            {
                Content = "Save & Activate",
                Style = (Style)FindResource("GlassButton"),
                Background = (System.Windows.Media.Brush)FindResource("AccentGold"),
                Foreground = System.Windows.Media.Brushes.Black,
                FontWeight = FontWeights.Bold,
                Padding = new Thickness(14, 6, 14, 6)
            };
            saveBtn.Click += (s, ev) =>
            {
                var val = txtBox.Text.Trim();
                StorageService.Settings.GeminiApiKey = string.IsNullOrWhiteSpace(val) ? null : val;
                StorageService.Save();
                ShowToast("✨ AI Brain Connected Successfully!");
                prompt.Close();
            };

            btnRow.Children.Add(clearBtn);
            btnRow.Children.Add(saveBtn);

            sp.Children.Add(heading);
            sp.Children.Add(desc);
            sp.Children.Add(txtBox);
            sp.Children.Add(btnRow);

            prompt.Content = sp;
            prompt.ShowDialog();
        }

        private void AiChipSummarize_Click(object sender, RoutedEventArgs e)
        {
            _ = SubmitAiCommandAsync("Summarise this video");
        }

        private void AiChipBenny_Click(object sender, RoutedEventArgs e)
        {
            _ = SubmitAiCommandAsync("Play the latest Benny Johnson video");
        }

        private void AiChipTimer_Click(object sender, RoutedEventArgs e)
        {
            _ = SubmitAiCommandAsync("Set sleep timer for 30 minutes");
        }

        private void AiChipNews_Click(object sender, RoutedEventArgs e)
        {
            _ = SubmitAiCommandAsync("Find breaking news from today");
        }

        private async void AiPromptBox_KeyDown(object sender, KeyEventArgs e)
        {
            if (e.Key == Key.Enter)
            {
                await SubmitAiCommandAsync();
            }
        }

        private async void AiSendBtn_Click(object sender, RoutedEventArgs e)
        {
            await SubmitAiCommandAsync();
        }

        private async Task SubmitAiCommandAsync(string? explicitPrompt = null)
        {
            var prompt = explicitPrompt ?? AiPromptBox.Text.Trim();
            if (string.IsNullOrWhiteSpace(prompt)) return;

            if (AiCopilotPanel.Visibility != Visibility.Visible)
            {
                AiCopilotPanel.Visibility = Visibility.Visible;
                AiGridSplitter.Visibility = Visibility.Visible;
                AiDrawerCol.Width = new GridLength(Math.Max(320, _savedAiDrawerWidth));
            }

            AiPromptBox.Text = "";

            // 1. Add User Message Card
            AddUserMessageBubble(prompt);

            // 2. Add Thinking Indicator
            var thinkingCard = AddThinkingBubble();

            try
            {
                // 3. Process via AiCopilotService with WebView2 transcript fetcher delegate
                var result = await AiCopilotService.ProcessCommandAsync(prompt, _currentVideo, async (vid) =>
                {
                    try
                    {
                        if (VideoWebView.CoreWebView2 != null)
                        {
                            var rawJson = await VideoWebView.ExecuteScriptAsync($"fetchTranscript('{vid}')");
                            if (!string.IsNullOrWhiteSpace(rawJson) && rawJson != "null" && rawJson != "\"\"")
                            {
                                return Newtonsoft.Json.JsonConvert.DeserializeObject<string>(rawJson) ?? "";
                            }
                        }
                    }
                    catch { }
                    return "";
                });

                // Remove thinking indicator
                AiMessageStack.Children.Remove(thinkingCard);

                // 4. Render AI Response
                AddAiResponseBubble(result);

                // 5. Execute side effects
                if (result.Type == AiCommandType.PlayVideo && result.TargetVideo != null)
                {
                    await PlayVideoAsync(result.TargetVideo);
                    ShowToast($"▶ Playing {result.TargetVideo.Title}");
                }
                else if (result.Type == AiCommandType.ControlSeek && result.SeekSeconds.HasValue)
                {
                    await VideoWebView.ExecuteScriptAsync($"seek({result.SeekSeconds.Value.ToString(System.Globalization.CultureInfo.InvariantCulture)})");
                    ShowToast(result.SeekSeconds.Value > 0 ? $"⏩ +{result.SeekSeconds.Value}s" : $"⏪ {result.SeekSeconds.Value}s");
                }
                else if (result.Type == AiCommandType.ControlPause)
                {
                    await VideoWebView.ExecuteScriptAsync("pauseVideo()");
                    ShowToast("⏸️ Paused");
                }
                else if (result.Type == AiCommandType.ControlPlay)
                {
                    await VideoWebView.ExecuteScriptAsync("playVideo()");
                    ShowToast("▶ Resumed");
                }
                else if (result.Type == AiCommandType.SetSleepTimer && result.TimerMinutes.HasValue)
                {
                    _lastSleepDurationMinutes = result.TimerMinutes.Value;
                    _sleepRemainingSeconds = result.TimerMinutes.Value * 60;
                    _sleepTimer?.Stop();
                    _sleepTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(1) };
                    _sleepTimer.Tick += SleepTimer_Tick;
                    _sleepTimer.Start();
                    SleepCountdownBadge.Visibility = Visibility.Visible;
                    ShowToast($"🌙 Sleep Timer set for {result.TimerMinutes.Value}m");
                }
                else if (result.Type == AiCommandType.SearchFeed && !string.IsNullOrWhiteSpace(result.SearchQuery))
                {
                    SearchBox.Text = result.SearchQuery;
                    await PerformSearchWithFiltersAsync();
                }
            }
            catch (Exception ex)
            {
                AiMessageStack.Children.Remove(thinkingCard);
                AddSimpleAiText($"⚠️ Error: {ex.Message}");
            }

            AiChatScrollViewer.ScrollToEnd();
        }

        private void AddUserMessageBubble(string text)
        {
            var border = new Border
            {
                Background = new System.Windows.Media.SolidColorBrush((System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#3E2866")),
                BorderBrush = new System.Windows.Media.SolidColorBrush((System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#809355FF")),
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(12, 12, 2, 12),
                Padding = new Thickness(12, 8, 12, 8),
                Margin = new Thickness(30, 4, 0, 6),
                HorizontalAlignment = HorizontalAlignment.Right
            };

            var tb = new TextBlock
            {
                Text = text,
                Foreground = System.Windows.Media.Brushes.White,
                FontSize = 12.5,
                TextWrapping = TextWrapping.Wrap
            };

            border.Child = tb;
            AiMessageStack.Children.Add(border);
        }

        private Border AddThinkingBubble()
        {
            var border = new Border
            {
                Background = new System.Windows.Media.SolidColorBrush((System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#1C1C28")),
                CornerRadius = new CornerRadius(12),
                Padding = new Thickness(10, 6, 10, 6),
                Margin = new Thickness(0, 4, 30, 6),
                HorizontalAlignment = HorizontalAlignment.Left
            };
            var tb = new TextBlock
            {
                Text = "✨ AI is analyzing...",
                Foreground = (System.Windows.Media.Brush)FindResource("AccentGold"),
                FontSize = 12,
                FontWeight = FontWeights.SemiBold
            };
            border.Child = tb;
            AiMessageStack.Children.Add(border);
            AiChatScrollViewer.ScrollToEnd();
            return border;
        }

        private void AddSimpleAiText(string text)
        {
            var border = new Border
            {
                Background = new System.Windows.Media.SolidColorBrush((System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#181824")),
                BorderBrush = new System.Windows.Media.SolidColorBrush((System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#33FFFFFF")),
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(12, 12, 12, 2),
                Padding = new Thickness(12, 10, 12, 10),
                Margin = new Thickness(0, 4, 20, 6),
                HorizontalAlignment = HorizontalAlignment.Left
            };
            var tb = new TextBlock
            {
                Text = text,
                Foreground = System.Windows.Media.Brushes.White,
                FontSize = 12,
                TextWrapping = TextWrapping.Wrap
            };
            border.Child = tb;
            AiMessageStack.Children.Add(border);
        }

        private void AddAiResponseBubble(AiCommandResult result)
        {
            var mainContainer = new StackPanel
            {
                Margin = new Thickness(0, 4, 10, 8),
                HorizontalAlignment = HorizontalAlignment.Left
            };

            // 1. Text Message
            if (!string.IsNullOrWhiteSpace(result.ResponseMessage))
            {
                var textBorder = new Border
                {
                    Background = new System.Windows.Media.SolidColorBrush((System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#1C1C28")),
                    BorderBrush = new System.Windows.Media.SolidColorBrush((System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#33FFD700")),
                    BorderThickness = new Thickness(1),
                    CornerRadius = new CornerRadius(12, 12, 12, 2),
                    Padding = new Thickness(12, 10, 12, 10),
                    Margin = new Thickness(0, 0, 0, 6)
                };
                var tb = new TextBlock
                {
                    Text = result.ResponseMessage.Replace("**", "").Replace("*", ""),
                    Foreground = System.Windows.Media.Brushes.White,
                    FontSize = 12,
                    TextWrapping = TextWrapping.Wrap
                };
                textBorder.Child = tb;
                mainContainer.Children.Add(textBorder);
            }

            // 2. Rich Summary Card (if Summarize command)
            if (result.Summary != null)
            {
                var sum = result.Summary;
                var card = new Border
                {
                    Background = new System.Windows.Media.SolidColorBrush((System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#14141E")),
                    BorderBrush = (System.Windows.Media.Brush)FindResource("AccentGold"),
                    BorderThickness = new Thickness(1),
                    CornerRadius = new CornerRadius(12),
                    Padding = new Thickness(12),
                    Margin = new Thickness(0, 4, 0, 6)
                };

                var cardStack = new StackPanel();

                // TL;DR Header & Block
                var tldrHeader = new TextBlock
                {
                    Text = "📌 EXECUTIVE SUMMARY",
                    Foreground = (System.Windows.Media.Brush)FindResource("AccentGold"),
                    FontSize = 10.5,
                    FontWeight = FontWeights.Bold,
                    Margin = new Thickness(0, 0, 0, 4)
                };
                cardStack.Children.Add(tldrHeader);

                var tldrBody = new TextBlock
                {
                    Text = sum.Tldr,
                    Foreground = new System.Windows.Media.SolidColorBrush((System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#EEEEEE")),
                    FontSize = 12,
                    LineHeight = 18,
                    TextWrapping = TextWrapping.Wrap,
                    Margin = new Thickness(0, 0, 0, 10)
                };
                cardStack.Children.Add(tldrBody);

                // Key Takeaways Header
                if (sum.KeyTakeaways.Count > 0)
                {
                    var takeHeader = new TextBlock
                    {
                        Text = "🔑 KEY TAKEAWAYS",
                        Foreground = (System.Windows.Media.Brush)FindResource("AccentGold"),
                        FontSize = 10.5,
                        FontWeight = FontWeights.Bold,
                        Margin = new Thickness(0, 4, 0, 4)
                    };
                    cardStack.Children.Add(takeHeader);

                    foreach (var point in sum.KeyTakeaways)
                    {
                        var pointGrid = new Grid { Margin = new Thickness(0, 3, 0, 3) };
                        pointGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
                        pointGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });

                        var dot = new TextBlock 
                        { 
                            Text = "• ", 
                            Foreground = (System.Windows.Media.Brush)FindResource("AccentGold"), 
                            FontSize = 12,
                            Margin = new Thickness(0, 0, 4, 0)
                        };
                        Grid.SetColumn(dot, 0);

                        var pointText = new TextBlock 
                        { 
                            Text = point, 
                            Foreground = System.Windows.Media.Brushes.White, 
                            FontSize = 11.5, 
                            LineHeight = 16.5,
                            TextWrapping = TextWrapping.Wrap 
                        };
                        Grid.SetColumn(pointText, 1);

                        pointGrid.Children.Add(dot);
                        pointGrid.Children.Add(pointText);
                        cardStack.Children.Add(pointGrid);
                    }
                }

                // Interactive Timestamp Chapters
                if (sum.Chapters.Count > 0)
                {
                    var chapHeader = new TextBlock
                    {
                        Text = "⏱️ TIMELINE CHAPTERS (CLICK TO JUMP)",
                        Foreground = (System.Windows.Media.Brush)FindResource("AccentGold"),
                        FontSize = 10,
                        FontWeight = FontWeights.Bold,
                        Margin = new Thickness(0, 10, 0, 6)
                    };
                    cardStack.Children.Add(chapHeader);

                    var chapWrap = new WrapPanel();
                    foreach (var chap in sum.Chapters)
                    {
                        var btn = new Button
                        {
                            Content = $"▶ {chap.TimeFormatted} {chap.Title}",
                            Style = (Style)FindResource("GlassButton"),
                            FontSize = 10.5,
                            Padding = new Thickness(6, 3, 6, 3),
                            Margin = new Thickness(0, 0, 4, 4),
                            Tag = chap.Seconds
                        };
                        btn.Click += async (s, e) =>
                        {
                            if (s is Button b && b.Tag is double sec)
                            {
                                await VideoWebView.ExecuteScriptAsync($"seek({sec.ToString(System.Globalization.CultureInfo.InvariantCulture)})");
                                ShowToast($"▶ Jumped to {chap.TimeFormatted}");
                            }
                        };
                        chapWrap.Children.Add(btn);
                    }
                    cardStack.Children.Add(chapWrap);
                }

                card.Child = cardStack;
                mainContainer.Children.Add(card);
            }

            // 3. Web Facts / Live Knowledge Card
            if (result.WebFacts.Count > 0)
            {
                var factsCard = new Border
                {
                    Background = new System.Windows.Media.SolidColorBrush((System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#14141E")),
                    BorderBrush = new System.Windows.Media.SolidColorBrush((System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#3300E5FF")),
                    BorderThickness = new Thickness(1),
                    CornerRadius = new CornerRadius(12),
                    Padding = new Thickness(12),
                    Margin = new Thickness(0, 4, 0, 6)
                };

                var factStack = new StackPanel();
                var factHeader = new TextBlock
                {
                    Text = "🌐 LIVE WEB INTELLIGENCE & KEY FACTS",
                    Foreground = new System.Windows.Media.SolidColorBrush((System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#00E5FF")),
                    FontSize = 10,
                    FontWeight = FontWeights.Bold,
                    Margin = new Thickness(0, 0, 0, 6)
                };
                factStack.Children.Add(factHeader);

                foreach (var fact in result.WebFacts)
                {
                    var factGrid = new Grid { Margin = new Thickness(0, 2, 0, 2) };
                    factGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
                    factGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });

                    var bullet = new TextBlock
                    {
                        Text = "• ",
                        Foreground = new System.Windows.Media.SolidColorBrush((System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#00E5FF")),
                        FontSize = 11,
                        Margin = new Thickness(0, 0, 4, 0)
                    };
                    Grid.SetColumn(bullet, 0);

                    var factText = new TextBlock
                    {
                        Text = fact,
                        Foreground = new System.Windows.Media.SolidColorBrush((System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#CCCCCC")),
                        FontSize = 11,
                        LineHeight = 15.5,
                        TextWrapping = TextWrapping.Wrap
                    };
                    Grid.SetColumn(factText, 1);

                    factGrid.Children.Add(bullet);
                    factGrid.Children.Add(factText);
                    factStack.Children.Add(factGrid);
                }

                factsCard.Child = factStack;
                mainContainer.Children.Add(factsCard);
            }

            AiMessageStack.Children.Add(mainContainer);
            AiChatScrollViewer.ScrollToEnd();
        }

        #endregion
    }
}