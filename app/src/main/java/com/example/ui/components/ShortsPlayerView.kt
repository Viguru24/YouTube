package com.example.ui.components

import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.remote.YouTubeStreamExtractor
import com.example.service.MediaPlaybackService
import com.example.ui.theme.GoldStar
import com.example.ui.theme.YouTubeRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun ShortsPlayerView(
    videoId: String,
    videoTitle: String,
    channelName: String,
    isFavorite: Boolean = false,
    isWatchLater: Boolean = false,
    isDisliked: Boolean = false,
    isInPipMode: Boolean = false,
    onEnterPip: () -> Unit = {},
    playerCommandFlow: kotlinx.coroutines.flow.SharedFlow<String>? = null,
    onPlayingStateChanged: (Boolean) -> Unit = {},
    onBackClick: () -> Unit,
    onNextShort: () -> Unit,
    onPreviousShort: () -> Unit,
    onThumbsUp: () -> Unit = {},
    onThumbsDown: () -> Unit = {},
    onFavoriteToggle: () -> Unit = onThumbsUp,
    onWatchLaterToggle: () -> Unit,
    onPositionUpdate: (seconds: Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var streamUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isPlayingState by remember { mutableStateOf(true) }

    var showQualityDialog by remember { mutableStateOf(false) }
    var selectedQuality by remember { mutableStateOf("Auto") }

    var showDebugConsole by remember { mutableStateOf(false) }
    val logs = remember { mutableStateListOf<String>() }

    fun addLog(msg: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        logs.add(0, "[$time] $msg")
    }

    var isFirstFrameRendered by remember(videoId) { mutableStateOf(false) }
    var useWebPlayerFallback by remember(videoId) { mutableStateOf(false) }

    // Real-Time Closed Captions (CC) State
    var captionsEnabled by remember { mutableStateOf(false) }
    var captionSegments by remember(videoId) { mutableStateOf<List<com.example.util.TranscriptSegment>>(emptyList()) }
    var activeCaptionText by remember { mutableStateOf<String?>(null) }

    // Use a single persistent ExoPlayer instance across Shorts swipes to avoid hardware codec exhaustion
    val exoPlayer = remember {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 90_000,
                /* maxBufferMs = */ 240_000,
                /* bufferForPlaybackMs = */ 1_500,
                /* bufferForPlaybackAfterRebufferMs = */ 2_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(30_000, true)
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build().apply {
            playWhenReady = true
            setAudioAttributes(audioAttributes, true)
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_NETWORK)
            repeatMode = ExoPlayer.REPEAT_MODE_ONE // Shorts loop automatically
        }
    }

    DisposableEffect(Unit) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onRenderedFirstFrame() {
                isFirstFrameRendered = true
                isLoading = false
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == androidx.media3.common.Player.STATE_READY) {
                    isFirstFrameRendered = true
                    isLoading = false
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isPlayingState = isPlaying
                onPlayingStateChanged(isPlaying)
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            try {
                exoPlayer.removeListener(listener)
                val finalSec = (exoPlayer.currentPosition / 1000).toInt()
                if (finalSec > 0) {
                    onPositionUpdate(finalSec)
                }
            } catch (e: Exception) { }
            exoPlayer.release()
            MediaPlaybackService.stop(context)
        }
    }

    LaunchedEffect(playerCommandFlow) {
        playerCommandFlow?.collect { cmd ->
            when {
                cmd == "TOGGLE_PLAY_PAUSE" -> {
                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                }
                cmd.startsWith("SEEK_FORWARD_") -> {
                    val sec = cmd.substringAfter("SEEK_FORWARD_").toIntOrNull() ?: 10
                    exoPlayer.seekTo(exoPlayer.currentPosition + sec * 1000L)
                }
                cmd.startsWith("SEEK_BACKWARD_") -> {
                    val sec = cmd.substringAfter("SEEK_BACKWARD_").toIntOrNull() ?: 10
                    exoPlayer.seekTo((exoPlayer.currentPosition - sec * 1000L).coerceAtLeast(0L))
                }
            }
        }
    }

    LaunchedEffect(isPlayingState) {
        if (isPlayingState) {
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(exoPlayer) {
        var lastSavedSec = 0
        while (isActive) {
            try {
                if (!isScrubbing) {
                    currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                    val d = exoPlayer.duration
                    if (d > 0) totalDurationMs = d
                }
                if (exoPlayer.isPlaying) {
                    val posSec = (exoPlayer.currentPosition / 1000).toInt()
                    if (posSec > 0 && Math.abs(posSec - lastSavedSec) >= 3) {
                        lastSavedSec = posSec
                        onPositionUpdate(posSec)
                    }
                }
            } catch (e: Exception) { }
            delay(200)
        }
    }

    // Real-time position ticker to update Closed Captions in Shorts
    LaunchedEffect(exoPlayer, captionsEnabled, captionSegments) {
        while (isActive) {
            try {
                if (exoPlayer.isPlaying) {
                    val pos = exoPlayer.currentPosition
                    val currentSec = (pos / 1000).toInt()
                    if (captionsEnabled && captionSegments.isNotEmpty()) {
                        val matching = captionSegments
                            .filter { it.timestampSeconds <= currentSec }
                            .lastOrNull { (currentSec - it.timestampSeconds) <= 5 }
                        activeCaptionText = matching?.text?.trim()
                    } else {
                        activeCaptionText = null
                    }
                }
            } catch (e: Exception) { }
            delay(100)
        }
    }

    // Background fetch of real subtitles when CC is enabled
    LaunchedEffect(videoId, captionsEnabled) {
        if (captionsEnabled && captionSegments.isEmpty()) {
            try {
                val segments = com.example.data.remote.YouTubeCaptionService.fetchTimedCaptions(videoId)
                captionSegments = segments
                if (segments.isNotEmpty()) {
                    addLog("✅ CC Subtitles Enabled: Loaded ${segments.size} lines")
                }
            } catch (e: Exception) {
                addLog("⚠️ Subtitle fetch error: ${e.message}")
            }
        }
    }

    // Extract direct MP4 stream for vertical full-screen playback
    LaunchedEffect(videoId, selectedQuality) {
        isLoading = true
        isFirstFrameRendered = false
        useWebPlayerFallback = false
        streamUrl = null
        isPlayingState = true
        logs.clear()
        addLog("Extracting stream for videoId=$videoId")

        // Safely stop previous short before preparing next one
        try {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        } catch (e: Exception) { }

        try {
            // Check offline storage first
            val localFile = com.example.data.remote.VideoDownloadManager.getLocalVideoFile(context, videoId)
            if (localFile.exists() && localFile.length() > 1024 * 100) {
                val localUri = android.net.Uri.fromFile(localFile).toString()
                streamUrl = localUri
                val mediaItem = MediaItem.fromUri(localUri)
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.play()
                addLog("⚡ Playing from offline storage")
                return@LaunchedEffect
            }

            val result = kotlinx.coroutines.withTimeoutOrNull(6000L) {
                YouTubeStreamExtractor.extractVideoStreams(videoId)
            }
            val directUrl = if (selectedQuality != "Auto" && result?.qualityUrlMap?.containsKey(selectedQuality) == true) {
                result.qualityUrlMap[selectedQuality]
            } else {
                result?.primaryStreamUrl ?: YouTubeStreamExtractor.getDirectStreamUrl(videoId)
            }

            if (!directUrl.isNullOrEmpty()) {
                streamUrl = directUrl
                val audioUrl = result?.audioStreamUrl
                val isVideoOnly = result?.isVideoOnlyStream(directUrl, selectedQuality) == true ||
                        (!audioUrl.isNullOrBlank() && directUrl != result?.combinedMuxedUrl && !directUrl.contains(".m3u8") && !directUrl.startsWith("file://") && !directUrl.startsWith("/"))

                val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                    .setUserAgent("com.google.android.youtube/19.09.37 (Linux; U; Android 14; US) gzip")
                    .setDefaultRequestProperties(mapOf(
                        "Referer" to "https://www.youtube.com/",
                        "Origin" to "https://www.youtube.com",
                        "Sec-Fetch-Dest" to "video",
                        "Sec-Fetch-Mode" to "cors",
                        "Sec-Fetch-Site" to "cross-site"
                    ))
                    .setConnectTimeoutMs(15000)
                    .setReadTimeoutMs(30000)
                    .setAllowCrossProtocolRedirects(true)

                val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, httpDataSourceFactory)

                if (isVideoOnly && !audioUrl.isNullOrBlank()) {
                    val videoSource = androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(directUrl))
                    val audioSource = androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(audioUrl))
                    val mergingSource = androidx.media3.exoplayer.source.MergingMediaSource(true, true, videoSource, audioSource)
                    exoPlayer.setMediaSource(mergingSource)
                } else {
                    val mediaSource = androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(directUrl))
                    exoPlayer.setMediaSource(mediaSource)
                }

                exoPlayer.volume = 1.0f
                exoPlayer.prepare()
                exoPlayer.play()
                addLog("Stream extracted & ExoPlayer prepared (videoOnly=$isVideoOnly, audioMerged=${isVideoOnly && !audioUrl.isNullOrBlank()}): $directUrl")
            } else {
                addLog("⚠️ Stream extraction timed out -> activating Shorts Web Player fallback")
                useWebPlayerFallback = true
                isLoading = false
            }
        } catch (e: Exception) {
            addLog("❌ Exception: ${e.javaClass.simpleName}: ${e.message} -> activating fallback")
            useWebPlayerFallback = true
            isLoading = false
        }
    }

    var totalDragAmount by remember(videoId) { mutableFloatStateOf(0f) }
    var hasTriggered by remember(videoId) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Full-Screen Vertical Player Surface
        if (!useWebPlayerFallback) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        keepScreenOn = true
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                update = { view ->
                    if (view.player != exoPlayer) {
                        view.player = exoPlayer
                    }
                    view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    view.onResume()
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Automatic Fallback: Embedded YouTube Player WebView for Shorts HTML5 Video Playback
            AndroidView(
                factory = { ctx ->
                    android.webkit.WebView(ctx).apply {
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        // Desktop Mode User-Agent: Bypasses mobile restrictions automatically
                        settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

                        try {
                            val cookieManager = android.webkit.CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            cookieManager.setCookie("https://www.youtube.com", "PREF=f6=40000000&hl=en&gl=US; path=/; domain=.youtube.com; Secure")
                            cookieManager.setCookie("https://www.youtube-nocookie.com", "PREF=f6=40000000&hl=en&gl=US; path=/; domain=.youtube-nocookie.com; Secure")
                        } catch (e: Exception) { }

                        webChromeClient = android.webkit.WebChromeClient()
                        webViewClient = object : android.webkit.WebViewClient() {
                            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                if (url?.contains("youtube.com") == true) {
                                    view?.evaluateJavascript("""
                                        (function() {
                                            var style = document.createElement('style');
                                            style.innerHTML = 'header, ytm-header-bar, #header-bar, .mobile-topbar-header, ytm-pivot-bar-renderer, .pivot-bar, ytm-app-banner-renderer, #below, ytm-item-section-renderer, #comments, ytm-comment-section-renderer, #related, ytm-related-chip-cloud-renderer, ytm-compact-video-renderer, .ytp-chrome-top, .ytp-watermark, .ytp-youtube-button, .ytp-pause-overlay { display: none !important; } html, body { margin: 0 !important; padding: 0 !important; overflow: hidden !important; background: #000 !important; width: 100vw !important; height: 100vh !important; } .player-container, #player-container-id, .html5-video-player, ytm-player, video { position: fixed !important; top: 0 !important; left: 0 !important; width: 100vw !important; height: 100vh !important; max-width: 100vw !important; max-height: 100vh !important; z-index: 999999 !important; object-fit: contain !important; background: #000 !important; }';
                                            document.head.appendChild(style);
                                            var v = document.querySelector('video');
                                            if (v) { v.muted = false; v.play(); }
                                            var playBtn = document.querySelector('.ytp-play-button, .ytp-large-play-button, button.player-control-play-pause-icon');
                                            if (playBtn) playBtn.click();
                                        })();
                                    """.trimIndent(), null)
                                }
                            }
                        }
                        val embedHtml = """
                            <!DOCTYPE html>
                            <html>
                            <head>
                                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                                <style>
                                    * { margin: 0; padding: 0; box-sizing: border-box; }
                                    html, body { width: 100%; height: 100%; background: #000; overflow: hidden; }
                                    .iframe-container { position: absolute; top: 0; left: 0; width: 100%; height: 100%; }
                                    iframe, #player { width: 100%; height: 100%; border: none; }
                                </style>
                            </head>
                            <body>
                                <div class="iframe-container">
                                    <div id="player"></div>
                                </div>
                                <script src="https://www.youtube.com/iframe_api"></script>
                                <script>
                                    var player;
                                    function onYouTubeIframeAPIReady() {
                                        player = new YT.Player('player', {
                                            height: '100%',
                                            width: '100%',
                                            videoId: '$videoId',
                                            playerVars: {
                                                'autoplay': 1,
                                                'loop': 1,
                                                'playlist': '$videoId',
                                                'playsinline': 1,
                                                'controls': 0,
                                                'enablejsapi': 1,
                                                'rel': 0,
                                                'modestbranding': 1,
                                                'cc_load_policy': 0,
                                                'iv_load_policy': 3,
                                                'origin': 'https://www.youtube.com',
                                                'widget_referrer': 'https://www.youtube.com'
                                            },
                                            events: {
                                                'onReady': function(e) { e.target.playVideo(); },
                                                'onError': function(e) {
                                                    console.log('Shorts embed restricted -> Bypassing to direct watch page');
                                                    window.location.replace('https://www.youtube.com/watch?v=$videoId');
                                                }
                                            }
                                        });
                                    }
                                </script>
                            </body>
                            </html>
                        """.trimIndent()
                        loadDataWithBaseURL("https://www.youtube.com", embedHtml, "text/html", "UTF-8", null)
                    }
                },
                update = { view ->
                    // Keep loaded
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Gesture Layer: Intercepts Tap (Play/Pause), Double-Tap (Seek +/-5s), and Vertical Drag Swipes (Next/Previous Short)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(videoId) {
                    detectTapGestures(
                        onTap = {
                            isPlayingState = !isPlayingState
                            if (isPlayingState) {
                                exoPlayer.play()
                            } else {
                                exoPlayer.pause()
                            }
                        },
                        onDoubleTap = { offset ->
                            val w = size.width
                            if (offset.x < w / 2f) {
                                val target = (exoPlayer.currentPosition - 5000L).coerceAtLeast(0L)
                                exoPlayer.seekTo(target)
                            } else {
                                val dur = if (exoPlayer.duration > 0) exoPlayer.duration else Long.MAX_VALUE
                                val target = (exoPlayer.currentPosition + 5000L).coerceAtMost(dur)
                                exoPlayer.seekTo(target)
                            }
                        }
                    )
                }
                .pointerInput(videoId) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            totalDragAmount = 0f
                            hasTriggered = false
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            totalDragAmount += dragAmount
                            if (!hasTriggered) {
                                if (totalDragAmount < -35f) {
                                    hasTriggered = true
                                    onNextShort()
                                } else if (totalDragAmount > 35f) {
                                    hasTriggered = true
                                    onPreviousShort()
                                }
                            }
                        }
                    )
                }
        )

        // 2. Loading Overlay (Fades out cleanly when stream is ready)
        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = YouTubeRed, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Loading Short...", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (!isInPipMode) {
            // 3. Top Controls Row: Back arrow + PiP + Debug toggle
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 10.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                // Picture-in-Picture Pop-up Button
                IconButton(
                    onClick = onEnterPip,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PictureInPictureAlt,
                        contentDescription = "Floating Pop-up Window",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                // Debug console toggle button
                IconButton(
                    onClick = { showDebugConsole = !showDebugConsole },
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.BugReport,
                        contentDescription = "Debug",
                        tint = if (showDebugConsole) YouTubeRed else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Debug Console Overlay Panel
        if (showDebugConsole) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 56.dp, start = 10.dp, end = 10.dp)
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .background(Color.Black.copy(alpha = 0.88f), RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                androidx.compose.foundation.lazy.LazyColumn {
                    if (logs.isEmpty()) {
                        item {
                            Text("No logs yet. Open a Short to see extraction status.", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                    items(logs.size) { i ->
                        val line = logs[i]
                        val color = when {
                            line.contains("✅") -> Color(0xFF4CAF50)
                            line.contains("❌") -> Color(0xFFEF5350)
                            line.contains("⚠️") -> Color(0xFFFFB300)
                            else -> Color.White.copy(alpha = 0.8f)
                        }
                        Text(text = line, color = color, fontSize = 10.sp, lineHeight = 13.sp,
                            modifier = Modifier.padding(vertical = 1.dp))
                    }
                }
            }
        }

        // Real-Time Closed Caption (CC) Subtitle Overlay for Shorts
        if (captionsEnabled && !activeCaptionText.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 90.dp, start = 16.dp, end = 72.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = activeCaptionText!!,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 17.sp
                )
            }
        }

        if (!isInPipMode) {
            // 4. Bottom-Left Details Text & Interactive Timeline Scrubber
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(0.74f)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 28.dp, start = 14.dp, end = 10.dp)
            ) {
                Text(
                    text = channelName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = videoTitle,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Interactive YouTube Red scrubber with clear visibility
            val activePosMs = if (isScrubbing) scrubPositionMs.toLong() else currentPositionMs
            val durMs = if (totalDurationMs > 0) totalDurationMs else 1L
            Slider(
                value = activePosMs.toFloat().coerceIn(0f, durMs.toFloat()),
                onValueChange = { v ->
                    isScrubbing = true
                    scrubPositionMs = v
                },
                onValueChangeFinished = {
                    isScrubbing = false
                    exoPlayer.seekTo(scrubPositionMs.toLong())
                },
                valueRange = 0f..durMs.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = YouTubeRed,
                    activeTrackColor = YouTubeRed,
                    inactiveTrackColor = Color.White.copy(alpha = 0.35f),
                    disabledThumbColor = Color.Transparent,
                    disabledActiveTrackColor = Color.Transparent
                ),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 4.dp, start = 6.dp, end = 6.dp)
                    .height(20.dp)
            )

            // 5. Right-Side Action Controls: Like, Dislike, Resolution Selector & Watch Later
            var localIsFavorite by remember(videoId, isFavorite) { mutableStateOf(isFavorite) }
            var localIsDisliked by remember(videoId, isDisliked) { mutableStateOf(isDisliked) }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 28.dp, end = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Thumbs Up (Like) Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = {
                            localIsFavorite = !localIsFavorite
                            if (localIsFavorite) localIsDisliked = false
                            onThumbsUp()
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (localIsFavorite) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                            contentDescription = "Like",
                            tint = if (localIsFavorite) YouTubeRed else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text("Like", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                }

                // 2. Thumbs Down (Dislike) Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = {
                            localIsDisliked = !localIsDisliked
                            if (localIsDisliked) localIsFavorite = false
                            onThumbsDown()
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (localIsDisliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                            contentDescription = "Dislike",
                            tint = if (localIsDisliked) Color(0xFFE53935) else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text("Dislike", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                }

                // 3. Star / Favorite Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = {
                            localIsFavorite = !localIsFavorite
                            onFavoriteToggle()
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (localIsFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = "Star",
                            tint = if (localIsFavorite) Color(0xFFFFD700) else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text("Star", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                }

                // 4. Closed Captions (CC) Subtitles Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = {
                            val next = !captionsEnabled
                            captionsEnabled = next
                            android.widget.Toast.makeText(
                                context,
                                if (next) "Subtitles (CC) Enabled 💬" else "Subtitles (CC) Turned Off",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ClosedCaption,
                            contentDescription = "Subtitles",
                            tint = if (captionsEnabled) YouTubeRed else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text("CC", color = if (captionsEnabled) YouTubeRed else Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                // 4. HD Quality Selector Badge Button
                Surface(
                    shape = CircleShape,
                    onClick = { showQualityDialog = true },
                    color = Color.Black.copy(alpha = 0.45f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (selectedQuality == "Auto") "HD" else selectedQuality.replace("p", ""),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                // 5. Watch Later Toggle
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onWatchLaterToggle,
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isWatchLater) Icons.Filled.WatchLater else Icons.Outlined.WatchLater,
                            contentDescription = "Watch Later",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text("Later", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                }

                // 6. Share Short ↗️
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = {
                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_SUBJECT, videoTitle)
                                putExtra(android.content.Intent.EXTRA_TEXT, "$videoTitle\nhttps://youtube.com/shorts/$videoId")
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Short"))
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Share",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text("Share", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    // Quality Selector Modal Dialog
    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = {
                Text(
                    text = "Short Resolution",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column {
                    listOf("Auto", "1080p", "720p", "480p", "360p").forEach { quality ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedQuality = quality
                                    showQualityDialog = false

                                    val maxH = when (quality) {
                                        "1080p" -> 1080
                                        "720p" -> 720
                                        "480p" -> 480
                                        "360p" -> 360
                                        else -> Int.MAX_VALUE
                                    }
                                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                        .buildUpon()
                                        .setMaxVideoSize(Int.MAX_VALUE, maxH)
                                        .build()

                                    Toast.makeText(context, "Quality set to $quality", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = quality,
                                fontSize = 14.sp,
                                fontWeight = if (selectedQuality == quality) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (selectedQuality == quality) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = YouTubeRed,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showQualityDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
