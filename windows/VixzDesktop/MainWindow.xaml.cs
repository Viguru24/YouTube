using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Media.Imaging;
using System.Windows.Threading;
using System.Text.RegularExpressions;
using Microsoft.Web.WebView2.Core;
using Newtonsoft.Json.Linq;
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
        private bool _isDiscoveryFeed = true; // true = Home/Subscriptions — filter out watched videos
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
        private readonly WebViewFrameTracker _frameTracker = new();
        private bool _isDirectWatchMode = false;
        private static readonly HashSet<string> _knownDirectWatchVideos = new(StringComparer.OrdinalIgnoreCase) { "kzrcf5ph0EI" };

        public const string WatchPageCleanerScript = PlayerScripts.WatchPageCleanerScript;

        public MainWindow()
        {
            InitializeComponent();
            Loaded += MainWindow_Loaded;

            // Global Win32 thread message hook for F12 — catches keypress anywhere (including inside WebView2 HWND)
            System.Windows.Interop.ComponentDispatcher.ThreadPreprocessMessage += (ref System.Windows.Interop.MSG msg, ref bool handled) =>
            {
                const int WM_KEYDOWN = 0x0100;
                const int WM_SYSKEYDOWN = 0x0104;
                const int VK_F12 = 0x7B;

                if (msg.message == WM_KEYDOWN || msg.message == WM_SYSKEYDOWN)
                {
                    if ((int)msg.wParam == VK_F12)
                    {
                        handled = true;
                        Dispatcher.Invoke(() =>
                        {
                            VideoWebView.CoreWebView2?.OpenDevToolsWindow();
                        });
                    }
                }
            };
        }

        private async void MainWindow_Loaded(object sender, RoutedEventArgs e)
        {
            UpdateFolderUi();
            UpdateAutoplayUi();
            UpdateAccountUi();
            UpdateJevButtonUi();
            UpdateQualityButtonText(StorageService.Settings.PreferredQuality);
            SubscribedChannelsList.ItemsSource = UserProfileData.SubscribedChannels;
            SubscribersHeader.Text = $"👤 Subscriptions ({UserProfileData.SubscribedChannels.Count})";

            DownloadQueueService.Instance.QueueChanged += () => Dispatcher.Invoke(UpdateDownloadQueueBadge);
            DownloadQueueService.Instance.TaskCompleted += (task) => Dispatcher.Invoke(() =>
            {
                UpdateDownloadQueueBadge();
                ShowToast($"✅ Download complete: {task.Title}");
            });
            UpdateDownloadQueueBadge();

            SetupBottomBarAutoFade();
            ApplySidebarState();
            ApplyAmbientGlowState();
            UpdateFolderChipHighlights();

            // Restore AI Copilot custom font size
            if (AiFontSizeSlider != null && StorageService.Settings.AiFontSize >= 11 && StorageService.Settings.AiFontSize <= 20)
            {
                AiFontSizeSlider.Value = StorageService.Settings.AiFontSize;
            }

            // Initialize VPS Cross-Device Cloud Sync
            VpsSyncService.InitializeAutoSync();
            VpsSyncService.OnSyncCompleted += () =>
            {
                var title = FeedTitleText?.Text ?? "";
                if (title != "📜 Watch History" && title != "💾 Downloaded Videos & Audio")
                {
                    var watchedIds = new HashSet<string>(StorageService.Settings.WatchHistory.Select(v => v.Id), StringComparer.OrdinalIgnoreCase);
                    _rawUnfilteredFeed?.RemoveAll(v => watchedIds.Contains(v.Id));
                    if (_currentFeed != null)
                    {
                        var removed = _currentFeed.RemoveAll(v => watchedIds.Contains(v.Id));
                        if (removed > 0 && VideoItemsControl != null)
                        {
                            VideoItemsControl.ItemsSource = null;
                            VideoItemsControl.ItemsSource = _currentFeed;
                        }
                    }
                }
            };

            await InitializeWebViewAsync();

            if (StorageService.Settings.UserAccount?.IsSignedIn == true)
            {
                _ = Task.Run(async () =>
                {
                    await Task.Delay(600);
                    await Dispatcher.InvokeAsync(async () => await SyncAccountProfileAsync(silent: true));
                });
            }

            await LoadFeedAsync("Recommended Feed", () => YouTubeService.GetHomeFeedAsync());
        }

        private async Task InitializeWebViewAsync()
        {
            try
            {
                var appData = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "VixzDesktop");
                var env = await WebViewManager.GetEnvironmentAsync();
                SharedWebView2Environment = env; // expose for backwards compatibility
                await VideoWebView.EnsureCoreWebView2Async(env);
                _frameTracker.Attach(VideoWebView.CoreWebView2);
                await WebViewManager.MaskWebViewIndicatorsAsync(VideoWebView.CoreWebView2);

                // Enable F12 DevTools and modern desktop capabilities
                VideoWebView.CoreWebView2.Settings.AreDevToolsEnabled = true;
                VideoWebView.CoreWebView2.Settings.IsStatusBarEnabled = false;
                VideoWebView.CoreWebView2.Settings.IsWebMessageEnabled = true;
                VideoWebView.CoreWebView2.Settings.UserAgent = WebViewManager.CommonUserAgent;

                // Inject GDPR/consent cookies so YouTube never shows "Before you continue to YouTube"
                // The sync log shows consent.youtube.com?gl=GB intercepting every navigation in this GB session.
                // SOCS=CAI = "consent accepted, no personalisation". Must be set on both youtube.com and google.com.
                WebViewManager.EnsureConsentCookiesAsync(VideoWebView.CoreWebView2);

                // Map virtual host https://vixz.app to local WebAssets for valid secure origin
                var webAssets = Path.Combine(appData, "WebAssets");
                Directory.CreateDirectory(webAssets);

                var playerHtmlPath = Path.Combine(webAssets, "player.html");
                var htmlContent = @"<!DOCTYPE html>
<html lang=""en"">
<head>
    <meta charset=""utf-8"">
    <meta name=""viewport"" content=""width=device-width, initial-scale=1.0"">
    <meta name=""referrer"" content=""strict-origin-when-cross-origin"">
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
        var _fallbackFired = false; // guard: only one fallback message per video load

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
            try {
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
                        'origin': 'https://www.youtube.com',
                        'widget_referrer': 'https://www.youtube.com/',
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
                            // Detect unstarted/blocked video (age-gate or sign-in wall): if still unstarted (-1) after 2.5s
                            var _readyVideoId = currentVideoId;
                            setTimeout(function() {
                                try {
                                    if (_fallbackFired) return; // onError already handled it
                                    var state = e.target.getPlayerState();
                                    // state -1=unstarted means age restriction or sign-in wall blocking.
                                    // 1=playing, 2=paused, 3=buffering, 5=cued are all OK.
                                    if (state === -1 && _readyVideoId && window.chrome && window.chrome.webview) {
                                        _fallbackFired = true;
                                        console.warn('[Vixz] Player stuck unstarted at 2.5s — age restriction or sign-in wall detected for', _readyVideoId);
                                        window.chrome.webview.postMessage('PLAYER_UNSTARTED:' + _readyVideoId);
                                    }
                                } catch(err) {}
                            }, 2500);
                        },
                        'onStateChange': onPlayerStateChange,
                        'onPlaybackQualityChange': function(e) {
                            if (window.chrome && window.chrome.webview) {
                                window.chrome.webview.postMessage('QUALITY:' + (e.data || ''));
                            }
                        },
                        'onError': function(e) {
                            if (_fallbackFired) return; // prevent looping
                            _fallbackFired = true;
                            console.warn('[Vixz Player] YT.Player error code:', e.data);
                            if (window.chrome && window.chrome.webview) {
                                window.chrome.webview.postMessage('PLAYER_ERROR:' + e.data);
                                window.chrome.webview.postMessage('PLAYER_STREAM_FALLBACK:' + (currentVideoId || ''));
                            }
                        }
                    }
                });
            } catch(e) {
                console.error('[Vixz Player] YT.Player init exception:', e);
                fallbackToDirectIframe(currentVideoId, startSec);
            }
        }

        function fallbackToDirectIframe(vid, sec) {
            if (_fallbackFired) return;
            _fallbackFired = true;
            if (window.chrome && window.chrome.webview) {
                window.chrome.webview.postMessage('PLAYER_STREAM_FALLBACK:' + (vid || currentVideoId || ''));
            }
        }

        // F12 key listener to open DevTools from within player iframe/page
        window.addEventListener('keydown', function(e) {
            if (e.key === 'F12' || e.keyCode === 123) {
                if (window.chrome && window.chrome.webview) {
                    window.chrome.webview.postMessage('OPEN_DEVTOOLS');
                }
            }
        });

        // Safe timeout: if API doesn't initialize player within 4s, fallback
        setTimeout(function() {
            if (!player && currentVideoId && !isLocalMode) {
                console.warn('[Vixz Player] YouTube iframe API timed out - activating fallback');
                fallbackToDirectIframe(currentVideoId, startSec);
            }
        }, 4000);




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

        var isLocalMode = false;
        var localVideoElement = null;

        function getLocalVideo() {
            if (!localVideoElement) {
                localVideoElement = document.getElementById('local-video-player');
            }
            return localVideoElement;
        }

        function loadLocalVideo(mediaUrl, seekTime, videoId) {
            isLocalMode = true;
            if (videoId) currentVideoId = videoId;
            var targetSec = parseFloat(seekTime || '0') || 0;

            // Pause and hide remote YouTube iframe
            try {
                if (player && typeof player.pauseVideo === 'function') player.pauseVideo();
            } catch(e) {}
            var playerDiv = document.getElementById('player');
            if (playerDiv) playerDiv.style.display = 'none';

            var vid = getLocalVideo();
            if (!vid) {
                vid = document.createElement('video');
                vid.id = 'local-video-player';
                vid.style.position = 'absolute';
                vid.style.top = '0';
                vid.style.left = '0';
                vid.style.width = '100%';
                vid.style.height = '100%';
                vid.style.objectFit = 'contain';
                vid.style.backgroundColor = '#000';
                vid.style.zIndex = '999';
                vid.controls = true;
                vid.autoplay = true;

                vid.addEventListener('loadedmetadata', function() {
                    if (window.chrome && window.chrome.webview && vid.duration > 0) {
                        window.chrome.webview.postMessage('DUR:' + vid.duration.toFixed(1));
                    }
                });

                vid.addEventListener('timeupdate', function() {
                    if (window.chrome && window.chrome.webview && currentVideoId) {
                        window.chrome.webview.postMessage('POS:' + currentVideoId + ':' + vid.currentTime.toFixed(1));
                        if (vid.duration > 0) {
                            window.chrome.webview.postMessage('DUR:' + vid.duration.toFixed(1));
                        }
                    }
                });

                vid.addEventListener('ended', function() {
                    if (window.chrome && window.chrome.webview) {
                        window.chrome.webview.postMessage('VIDEO_ENDED');
                    }
                });

                vid.addEventListener('play', function() {
                    if (window.chrome && window.chrome.webview) {
                        window.chrome.webview.postMessage('QUALITY:Offline MP4');
                    }
                });

                document.body.appendChild(vid);
                localVideoElement = vid;
            }

            vid.style.display = 'block';
            vid.src = mediaUrl;
            if (targetSec > 0) {
                vid.currentTime = targetSec;
            }
            vid.play().catch(function(e) { console.log('Autoplay error', e); });
            if (window.chrome && window.chrome.webview) {
                window.chrome.webview.postMessage('QUALITY:Offline MP4');
            }
        }

        function loadVideo(vid, seekTime, quality) {
            currentVideoId = vid;
            _fallbackFired = false; // reset for each new video load
            if (quality) preferredQuality = quality;
            var targetSec = parseFloat(seekTime || '0') || 0;

            if (isLocalMode) {
                isLocalMode = false;
                var lvid = getLocalVideo();
                if (lvid) { lvid.pause(); lvid.style.display = 'none'; lvid.src = ''; }
                var pdiv = document.getElementById('player');
                if (pdiv) pdiv.style.display = 'block';
            }

            if (player && typeof player.loadVideoById === 'function') {
                try {
                    player.loadVideoById({
                        videoId: vid,
                        startSeconds: targetSec,
                        suggestedQuality: preferredQuality
                    });
                    applyHighQuality();
                    setTimeout(applyHighQuality, 400);
                    setTimeout(applyHighQuality, 1200);
                } catch(e) {
                    fallbackToDirectIframe(vid, targetSec);
                }
            } else {
                fallbackToDirectIframe(vid, targetSec);
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

        // Progress memory tracker - sends current position and duration
        setInterval(() => {
            try {
                if (isLocalMode) return;
                if (player && typeof player.getCurrentTime === 'function' && typeof player.getPlayerState === 'function') {
                    if (player.getPlayerState() === 1) { // Playing
                        var cur = player.getCurrentTime();
                        if (typeof player.getDuration === 'function') {
                            var dur = player.getDuration();
                            if (dur > 0 && window.chrome && window.chrome.webview) {
                                window.chrome.webview.postMessage('DUR:' + dur.toFixed(1));
                            }
                        }
                        if (cur >= 0 && currentVideoId && window.chrome && window.chrome.webview) {
                            window.chrome.webview.postMessage('POS:' + currentVideoId + ':' + cur.toFixed(1));
                        }
                    }
                }
            } catch(e) {}
        }, 500);

        function stopVideo() {
            if (isLocalMode) {
                var vid = getLocalVideo();
                if (vid) { vid.pause(); vid.currentTime = 0; }
                return;
            }
            try {
                if (player && typeof player.stopVideo === 'function') player.stopVideo();
                if (player && typeof player.pauseVideo === 'function') player.pauseVideo();
            } catch(e) {}
        }

        function unMuteVideo() {
            if (isLocalMode) {
                var vid = getLocalVideo();
                if (vid) vid.muted = false;
                return;
            }
            try {
                if (player && typeof player.unMute === 'function') player.unMute();
            } catch(e) {}
        }

        function sendFrameCommand(func, args) {
            try {
                var f = document.getElementById('fallback-yt-frame') || document.querySelector('iframe');
                if (f && f.contentWindow) {
                    f.contentWindow.postMessage(JSON.stringify({
                        event: 'command',
                        func: func,
                        args: args || []
                    }), '*');
                }
            } catch(e) {}
        }

        function pauseVideo() {
            if (isLocalMode) {
                var vid = getLocalVideo();
                if (vid) vid.pause();
                return;
            }
            if (player && typeof player.pauseVideo === 'function') {
                try { player.pauseVideo(); } catch(e) {}
            }
            sendFrameCommand('pauseVideo');
        }

        function playVideo() {
            if (isLocalMode) {
                var vid = getLocalVideo();
                if (vid) { vid.muted = false; vid.play(); }
                return;
            }
            if (player && typeof player.playVideo === 'function') {
                try { player.unMute(); player.playVideo(); } catch(e) {}
            }
            sendFrameCommand('unMute');
            sendFrameCommand('playVideo');
        }

        function togglePlay() {
            if (isLocalMode) {
                var vid = getLocalVideo();
                if (vid) {
                    if (vid.paused) vid.play(); else vid.pause();
                }
                return;
            }
            if (!player || typeof player.getPlayerState !== 'function') return;
            var s = player.getPlayerState();
            if (s === 1) pauseVideo();
            else playVideo();
        }

        function seek(sec) {
            if (isLocalMode) {
                var vid = getLocalVideo();
                if (vid) vid.currentTime = Math.max(0, vid.currentTime + sec);
                return;
            }
            if (!player || typeof player.getCurrentTime !== 'function') return;
            var cur = player.getCurrentTime();
            player.seekTo(cur + sec, true);
        }

        function seekTo(sec) {
            if (isLocalMode) {
                var vid = getLocalVideo();
                if (vid) vid.currentTime = Math.max(0, sec);
                return;
            }
            if (!player || typeof player.seekTo !== 'function') return;
            player.seekTo(sec, true);
        }

        function toggleMute() {
            if (isLocalMode) {
                var vid = getLocalVideo();
                if (vid) vid.muted = !vid.muted;
                return;
            }
            if (!player || typeof player.isMuted !== 'function') return;
            if (player.isMuted()) player.unMute();
            else player.mute();
        }

        function getCurrentTime() {
            if (isLocalMode) {
                var vid = getLocalVideo();
                return vid ? vid.currentTime : 0;
            }
            if (!player || typeof player.getCurrentTime !== 'function') return 0;
            return player.getCurrentTime() || 0;
        }

        async function fetchTranscript(vid) {
            try {
                var targetId = vid || currentVideoId;
                if (!targetId) return '';

                function extractCaptions(evts) {
                    var t = '';
                    for (var i = 0; i < evts.length; i++) {
                        var ev = evts[i];
                        if (ev.segs) for (var j = 0; j < ev.segs.length; j++) {
                            var s = ev.segs[j].utf8;
                            if (s && s !== '\n') t += s + ' ';
                        }
                    }
                    return t.trim();
                }

                async function fetchCapUrl(baseUrl) {
                    var r = await fetch(baseUrl + '&fmt=json3', {credentials:'include'});
                    var j = await r.json();
                    return j && j.events ? extractCaptions(j.events) : '';
                }

                function pickEnTrack(tracks) {
                    return tracks.find(function(t){return t.languageCode&&t.languageCode.indexOf('en')===0;}) || tracks[0];
                }

                // Strategy 1: Innertube WEB player API (sends YouTube session cookies from WebView2)
                try {
                    var pr = await fetch('https://www.youtube.com/youtubei/v1/player', {
                        method: 'POST',
                        headers: {'Content-Type':'application/json','X-YouTube-Client-Name':'1','X-YouTube-Client-Version':'2.20240101.00.00'},
                        credentials: 'include',
                        body: JSON.stringify({context:{client:{clientName:'WEB',clientVersion:'2.20240101.00.00',hl:'en',gl:'US'}},videoId:targetId})
                    });
                    var pd = await pr.json();
                    var ct1 = pd&&pd.captions&&pd.captions.playerCaptionsTracklistRenderer&&pd.captions.playerCaptionsTracklistRenderer.captionTracks;
                    if (ct1 && ct1.length > 0) {
                        var t1 = await fetchCapUrl(pickEnTrack(ct1).baseUrl);
                        if (t1.length > 80) return t1;
                    }
                } catch(e1) {}

                // Strategy 2: Parse ytInitialPlayerResponse from watch page HTML (with auth cookies)
                try {
                    var res = await fetch('https://www.youtube.com/watch?v=' + targetId + '&hl=en', {credentials:'include'});
                    var html = await res.text();

                    // Brace-count parse of ytInitialPlayerResponse
                    var iprIdx = html.indexOf('ytInitialPlayerResponse');
                    if (iprIdx !== -1) {
                        var bStart = html.indexOf('{', iprIdx);
                        if (bStart !== -1) {
                            var depth = 0, bEnd = bStart;
                            for (var ci = bStart; ci < Math.min(bStart + 800000, html.length); ci++) {
                                if (html[ci] === '{') depth++; else if (html[ci] === '}') { depth--; if (depth === 0) { bEnd = ci; break; } }
                            }
                            try {
                                var ipr = JSON.parse(html.substring(bStart, bEnd + 1));
                                var ct2 = ipr&&ipr.captions&&ipr.captions.playerCaptionsTracklistRenderer&&ipr.captions.playerCaptionsTracklistRenderer.captionTracks;
                                if (ct2 && ct2.length > 0) {
                                    var t2 = await fetchCapUrl(pickEnTrack(ct2).baseUrl);
                                    if (t2.length > 80) return t2;
                                }
                            } catch(pe) {}
                        }
                    }

                    // Strategy 3: Bracket-count extraction of captionTracks array
                    var ctIdx = html.indexOf('""captionTracks""');
                    if (ctIdx !== -1) {
                        var aStart = html.indexOf('[', ctIdx);
                        if (aStart !== -1) {
                            var ad = 0, aEnd = aStart;
                            for (var ci2 = aStart; ci2 < Math.min(aStart + 80000, html.length); ci2++) {
                                if (html[ci2] === '[') ad++; else if (html[ci2] === ']') { ad--; if (ad === 0) { aEnd = ci2; break; } }
                            }
                            try {
                                var ct3 = JSON.parse(html.substring(aStart, aEnd + 1));
                                if (ct3 && ct3.length > 0) {
                                    var enT3 = ct3.find(function(t){return t.languageCode==='en'||(t.vssId&&t.vssId.indexOf('en')!==-1);}) || ct3[0];
                                    if (enT3 && enT3.baseUrl) {
                                        var t3 = await fetchCapUrl(enT3.baseUrl);
                                        if (t3.length > 80) return t3;
                                    }
                                }
                            } catch(pe3) {}
                        }
                    }
                } catch(e2) {}

                return '';
            } catch (e) {
                return '';
            }
        }

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
                        // Only intercept external tracking ad domains, never tamper with youtube.com internal integrity/pagead scripts
                        if (!uri.Contains("youtube.com") && (uri.Contains("doubleclick") || uri.Contains("googleads") || uri.Contains("viewthroughconversion") || uri.Contains("ad_status") || uri.Contains("favicon.ico")))
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
                        else if (uri.Contains("youtube.com"))
                        {
                            try
                            {
                                if (!args.Request.Headers.Contains("Referer") || string.IsNullOrWhiteSpace(args.Request.Headers.GetHeader("Referer")))
                                {
                                    args.Request.Headers.SetHeader("Referer", "https://www.youtube.com/");
                                }
                            }
                            catch { }
                        }
                    }
                    catch { }
                };

                VideoWebView.CoreWebView2.WebMessageReceived += CoreWebView2_WebMessageReceived;
                VideoWebView.PreviewKeyDown += Window_PreviewKeyDown;

                // Intercept 'More videos', external links, and in-player Sign In prompts
                VideoWebView.CoreWebView2.NewWindowRequested += async (s, args) =>
                {
                    args.Handled = true;
                    if (args.Uri.Contains("accounts.google.com") || args.Uri.Contains("/signin") || args.Uri.Contains("ServiceLogin"))
                    {
                        Dispatcher.Invoke(() => OpenSignInWindow_Click(this, new RoutedEventArgs()));
                        return;
                    }

                    // Intercept channel links inside the player → navigate within Vixz
                    var channelHandle = ExtractYouTubeChannelHandle(args.Uri);
                    if (!string.IsNullOrEmpty(channelHandle))
                    {
                        await Dispatcher.InvokeAsync(async () => await NavigateToChannelFeedAsync(channelHandle));
                        return;
                    }

                    var vid = ExtractYouTubeVideoId(args.Uri);
                    if (!string.IsNullOrEmpty(vid))
                    {
                        // If user clicked "Watch on YouTube" on the currently playing/blocked video, open direct watch page inside Vixz
                        if (_currentVideo != null && vid == _currentVideo.Id)
                        {
                            await Dispatcher.InvokeAsync(async () => await PlayDirectWatchPageAsync(vid, 0));
                            return;
                        }

                        var video = await YouTubeService.GetVideoDetailsAsync(vid) ?? new VideoItem
                        {
                            Id = vid,
                            Title = "YouTube Video",
                            ChannelTitle = "YouTube",
                            ThumbnailUrl = $"https://i.ytimg.com/vi/{vid}/hqdefault.jpg"
                        };
                        await PlayVideoAsync(video);
                        return;
                    }

                    // For external informational links (e.g. YouTube Learn More), open in system browser
                    if (args.Uri.StartsWith("http://", StringComparison.OrdinalIgnoreCase) || args.Uri.StartsWith("https://", StringComparison.OrdinalIgnoreCase))
                    {
                        try
                        {
                            System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo
                            {
                                FileName = args.Uri,
                                UseShellExecute = true
                            });
                        }
                        catch { }
                    }
                };

                VideoWebView.CoreWebView2.NavigationStarting += async (s, args) =>
                {
                    if (args.Uri.StartsWith("https://vixz.app", StringComparison.OrdinalIgnoreCase))
                    {
                        return;
                    }

                    if (_isDirectWatchMode && args.Uri.Contains("youtube.com/watch"))
                    {
                        var targetVid = ExtractYouTubeVideoId(args.Uri);
                        if (string.IsNullOrEmpty(targetVid) || targetVid == _currentVideo?.Id)
                        {
                            return; // Allow the current direct watch video to load!
                        }
                    }

                    args.Cancel = true;
                    if (args.Uri.Contains("accounts.google.com") || args.Uri.Contains("/signin") || args.Uri.Contains("ServiceLogin"))
                    {
                        Dispatcher.Invoke(() => OpenSignInWindow_Click(this, new RoutedEventArgs()));
                        return;
                    }

                    // Intercept channel links (e.g. clicking channel name/avatar in player)
                    var channelHandle = ExtractYouTubeChannelHandle(args.Uri);
                    if (!string.IsNullOrEmpty(channelHandle))
                    {
                        await Dispatcher.InvokeAsync(async () => await NavigateToChannelFeedAsync(channelHandle));
                        return;
                    }

                    var vid = ExtractYouTubeVideoId(args.Uri);
                    if (!string.IsNullOrEmpty(vid))
                    {
                        if (_currentVideo != null && vid == _currentVideo.Id)
                        {
                            await Dispatcher.InvokeAsync(async () => await PlayDirectWatchPageAsync(vid, 0));
                            return;
                        }

                        var video = await YouTubeService.GetVideoDetailsAsync(vid) ?? new VideoItem
                        {
                            Id = vid,
                            Title = "YouTube Video",
                            ChannelTitle = "YouTube",
                            ThumbnailUrl = $"https://i.ytimg.com/vi/{vid}/hqdefault.jpg"
                        };
                        await PlayVideoAsync(video);
                    }
                    // All other navigations are cancelled (args.Cancel = true above)
                };

                VideoWebView.CoreWebView2.DOMContentLoaded += async (s, args) =>
                {
                    if (_isDirectWatchMode && VideoWebView.Source?.ToString().Contains("youtube.com/watch") == true)
                    {
                        await VideoWebView.ExecuteScriptAsync(WatchPageCleanerScript);
                    }
                };

                VideoWebView.CoreWebView2.NavigationCompleted += async (s, args) =>
                {
                    if (_isDirectWatchMode && VideoWebView.Source?.ToString().Contains("youtube.com/watch") == true)
                    {
                        await VideoWebView.ExecuteScriptAsync(WatchPageCleanerScript);
                    }
                };

                // Sub-frame challenge interceptor — catches YouTube's bot-check / age-gate page
                // when it navigates INSIDE the embed iframe (cross-origin, so JS can't see it).
                // FrameNavigationStarting fires in C# before any content is rendered, giving us
                // a clean opportunity to trigger the stream fallback instead.
                VideoWebView.CoreWebView2.FrameCreated += (s, frameArgs) =>
                {
                    frameArgs.Frame.NavigationStarting += (frameSender, navArgs) =>
                    {
                        try
                        {
                            var uri = navArgs.Uri ?? string.Empty;
                            bool isChallenge =
                                uri.Contains("accounts.google.com") ||
                                uri.Contains("consent.youtube.com") ||
                                uri.Contains("/verify") ||
                                uri.Contains("challenge") ||
                                (uri.Contains("youtube.com") && uri.Contains("signin"));

                            if (isChallenge && _currentVideo != null)
                            {
                                var videoId = _currentVideo.Id;
                                System.Diagnostics.Debug.WriteLine($"[Vixz] Sub-frame challenge detected for {videoId}: {uri}");
                                Dispatcher.InvokeAsync(() =>
                                {
                                    VideoWebView.CoreWebView2?.PostWebMessageAsString($"PLAYER_STREAM_FALLBACK:{videoId}");
                                });
                            }
                        }
                        catch { }
                    };
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

                if (msg == "OPEN_DEVTOOLS")
                {
                    Dispatcher.Invoke(() => VideoWebView.CoreWebView2?.OpenDevToolsWindow());
                    return;
                }
                else if (msg.StartsWith("PLAYER_ERROR:"))
                {
                    var errCode = msg.Substring("PLAYER_ERROR:".Length);
                    System.Diagnostics.Debug.WriteLine($"[Vixz] Player error code: {errCode}");
                }
                else if (msg.StartsWith("PLAYER_STREAM_FALLBACK:") || msg.StartsWith("PLAYER_UNSTARTED:"))
                {
                    var vid = msg.StartsWith("PLAYER_STREAM_FALLBACK:") 
                        ? msg.Substring("PLAYER_STREAM_FALLBACK:".Length) 
                        : msg.Substring("PLAYER_UNSTARTED:".Length);
                    System.Diagnostics.Debug.WriteLine($"[Vixz] PLAYER_STREAM_FALLBACK / UNSTARTED triggered for: {vid}");
                    if (!string.IsNullOrEmpty(vid))
                    {
                        _ = Dispatcher.InvokeAsync(async () =>
                        {
                            try
                            {
                                ShowToast("⚙️ Embed restricted — authenticating stream engine…");

                                // 1. Pass current WebView2 session cookies to YouTubeService
                                List<System.Net.Cookie>? netCookies = null;
                                try
                                {
                                    if (VideoWebView?.CoreWebView2 != null)
                                    {
                                        var wvCookies = await VideoWebView.CoreWebView2.CookieManager.GetCookiesAsync("https://www.youtube.com");
                                        netCookies = wvCookies.Select(c => new System.Net.Cookie(c.Name, c.Value, c.Path, c.Domain)).ToList();
                                        YouTubeService.SetAuthCookies(netCookies);
                                    }
                                }
                                catch (Exception exCookie)
                                {
                                    System.Diagnostics.Debug.WriteLine($"[Vixz] Cookie extraction failed: {exCookie.Message}");
                                }

                                // 2. Try stream extraction with authenticated cookies
                                var streamUrl = await YouTubeService.GetStreamUrlAsync(vid, netCookies);
                                if (!string.IsNullOrEmpty(streamUrl))
                                {
                                    var timeStr = VideoWebView != null ? await VideoWebView.ExecuteScriptAsync("getCurrentTime()") : "0";
                                    double.TryParse(timeStr, System.Globalization.NumberStyles.Any, System.Globalization.CultureInfo.InvariantCulture, out double curSec);
                                    if (VideoWebView != null)
                                    {
                                        await VideoWebView.ExecuteScriptAsync($"loadLocalVideo('{streamUrl}', {curSec.ToString(System.Globalization.CultureInfo.InvariantCulture)}, '{vid}')");
                                    }
                                    ShowToast("🛡️ Restriction bypassed: Playing stream");
                                    return;
                                }

                                // 3. If stream engine cannot extract (YouTube hard age-gate on embeds, or DRM movie like Dune 1984):
                                // Seamlessly switch to First-Party Clean Watch Page Player directly inside Vixz!
                                var curSecStr = VideoWebView != null ? await VideoWebView.ExecuteScriptAsync("getCurrentTime()") : "0";
                                double.TryParse(curSecStr, System.Globalization.NumberStyles.Any, System.Globalization.CultureInfo.InvariantCulture, out double currentSec);
                                var secInt = Math.Max(0, (int)currentSec);

                                await PlayDirectWatchPageAsync(vid, secInt);
                            }
                            catch (Exception ex)
                            {
                                System.Diagnostics.Debug.WriteLine($"[Vixz] Stream fallback exception: {ex.Message}");
                                await PlayDirectWatchPageAsync(vid, 0);
                            }
                        });
                    }
                }
                else if (msg.StartsWith("PLAYER_SIGNINWALL:"))
                {
                    // 5-6s timeout detected sign-in wall (session expired, not an embed-disabled video).
                    var vid = msg.Substring("PLAYER_SIGNINWALL:".Length);
                    System.Diagnostics.Debug.WriteLine($"[Vixz] PLAYER_SIGNINWALL detected for: {vid}");
                    _ = Dispatcher.InvokeAsync(() =>
                    {
                        if (StorageService.Settings.UserAccount?.IsSignedIn == true)
                        {
                            // User is already signed in; this is an age restriction or embed block, not a missing login!
                            VideoWebView.CoreWebView2?.PostWebMessageAsString($"PLAYER_STREAM_FALLBACK:{vid}");
                            return;
                        }

                        ShowToast("⚠️ YouTube session expired — please sign in again");
                        var t = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(800) };
                        t.Tick += (ts, te) =>
                        {
                            t.Stop();
                            OpenSignInWindow_Click(this, new RoutedEventArgs());
                        };
                        t.Start();
                    });
                }
                else if (msg == "VIDEO_ENDED")
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
                else if (msg.StartsWith("DUR:"))
                {
                    if (double.TryParse(msg.Substring(4), System.Globalization.NumberStyles.Any, System.Globalization.CultureInfo.InvariantCulture, out double dur))
                    {
                        Dispatcher.Invoke(() => UpdateVideoDuration(dur));
                    }
                }
                else if (msg.StartsWith("POS:"))
                {
                    var parts = msg.Split(':');
                    if (parts.Length == 3 && double.TryParse(parts[2], System.Globalization.NumberStyles.Any, System.Globalization.CultureInfo.InvariantCulture, out double pos))
                    {
                        StorageService.SavePlaybackPosition(parts[1], pos);
                        Dispatcher.Invoke(() => UpdatePlaybackTimeline(pos));
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
            if (account != null && account.IsSignedIn)
            {
                var display = !string.IsNullOrWhiteSpace(account.DisplayName) && !account.DisplayName.Equals("Google User", StringComparison.OrdinalIgnoreCase)
                    ? account.DisplayName
                    : (!string.IsNullOrWhiteSpace(account.Email) ? account.Email : "Google Account");

                AccountBtn.Content = $"👤 {display}";
                AccountBtn.Foreground = (System.Windows.Media.Brush)FindResource("AccentGold");
                AccountProfileName.Text = display;

                if (!string.IsNullOrWhiteSpace(account.Email))
                {
                    AccountEmailText.Text = account.Email;
                    AccountEmailText.Visibility = Visibility.Visible;
                }
                else
                {
                    AccountEmailText.Visibility = Visibility.Collapsed;
                }

                AccountStatusSubtext.Text = "Google Account Connected 🟢";

                // Avatar rendering: real profile picture if available, fallback to gold initial
                if (!string.IsNullOrWhiteSpace(account.AvatarUrl))
                {
                    try
                    {
                        var bi = new BitmapImage();
                        bi.BeginInit();
                        bi.UriSource = new Uri(account.AvatarUrl, UriKind.Absolute);
                        bi.CacheOption = BitmapCacheOption.OnLoad;
                        bi.EndInit();
                        AccountAvatarImage.Source = bi;
                        AccountAvatarImage.Visibility = Visibility.Visible;
                        AccountAvatarInitial.Visibility = Visibility.Collapsed;
                    }
                    catch
                    {
                        AccountAvatarImage.Visibility = Visibility.Collapsed;
                        AccountAvatarInitial.Visibility = Visibility.Visible;
                        AccountAvatarInitial.Text = display.Length > 0 ? display[0].ToString().ToUpper() : "👤";
                    }
                }
                else
                {
                    AccountAvatarImage.Visibility = Visibility.Collapsed;
                    AccountAvatarInitial.Visibility = Visibility.Visible;
                    AccountAvatarInitial.Text = display.Length > 0 ? display[0].ToString().ToUpper() : "👤";
                }
            }
            else
            {
                AccountBtn.Content = "👤 Sign In";
                AccountBtn.Foreground = System.Windows.Media.Brushes.White;
                AccountProfileName.Text = "Not Signed In";
                AccountEmailText.Visibility = Visibility.Collapsed;
                AccountStatusSubtext.Text = "Sign in to sync YouTube data";
                AccountAvatarImage.Visibility = Visibility.Collapsed;
                AccountAvatarInitial.Visibility = Visibility.Visible;
                AccountAvatarInitial.Text = "👤";
            }

            if (ProfileItemsControl != null)
            {
                ProfileItemsControl.ItemsSource = null;
                ProfileItemsControl.ItemsSource = ProfileManagerService.Instance.GetProfilesList();
            }
        }


        protected override void OnClosed(EventArgs e)
        {
            try
            {
                SpeechService.Instance.StopSpeaking();
                SpeechService.Instance.StopListening();
            }
            catch { }
            base.OnClosed(e);
        }
    }
}