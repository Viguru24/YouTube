package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.remote.SponsorBlockService
import com.example.data.remote.SponsorSegment
import com.example.data.remote.YouTubeStreamExtractor
import com.example.ui.theme.YouTubeRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

@Composable
fun YouTubePlayerView(
    videoId: String,
    startSeconds: Int = 0,
    areAdvertsEnabled: Boolean = false,
    showDebugConsole: Boolean = false,
    onToggleDebugConsole: () -> Unit = {},
    playerCommandFlow: kotlinx.coroutines.flow.SharedFlow<String>? = null,
    onPlayingStateChanged: (Boolean) -> Unit = {},
    onNextVideo: () -> Unit = {},
    onPreviousVideo: () -> Unit = {},
    isFavorite: Boolean = false,
    isWatchLater: Boolean = false,
    onFavoriteToggle: () -> Unit = {},
    onWatchLaterToggle: () -> Unit = {},
    onSaveToSubject: () -> Unit = {},
    videoTitle: String = "Video",
    isFullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
    modifier: Modifier = Modifier,
    onPlayerReady: (Any) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var streamUrl by remember(videoId) { mutableStateOf<String?>(null) }
    var isLoading by remember(videoId) { mutableStateOf(true) }
    var isFirstFrameRendered by remember(videoId) { mutableStateOf(false) }
    var useWebPlayerFallback by remember(videoId) { mutableStateOf(false) }
    var statusLog by remember(videoId) { mutableStateOf("Initializing Native ExoPlayer Engine...") }
    val debugLogs = remember(videoId) { mutableStateListOf<String>() }

    // Pinch-to-Zoom & Pan State (1.0x to 5.0x zoom with smooth translation)
    var zoomScale by remember(videoId) { mutableFloatStateOf(1f) }
    var panOffsetX by remember(videoId) { mutableFloatStateOf(0f) }
    var panOffsetY by remember(videoId) { mutableFloatStateOf(0f) }

    var savedPositionMs by rememberSaveable(videoId) { mutableLongStateOf(-1L) }
    var hasPreparedMedia by rememberSaveable(videoId) { mutableStateOf(false) }

    // Stream extraction & dynamic quality state
    var streamResult by remember(videoId) { mutableStateOf<com.example.data.remote.StreamExtractionResult?>(null) }
    var availableQualities by remember(videoId) { mutableStateOf<List<String>>(emptyList()) }
    var selectedQuality by remember(videoId) { mutableStateOf("Auto") }

    // Video Playback State
    var isPlayingState by remember { mutableStateOf(true) }
    var isMutedState by remember { mutableStateOf(false) }
    var currentPosMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }

    // SponsorBlock In-Video Sponsor Skip State (Debounced single execution to prevent infinite seek freeze)
    var sponsorSegments by remember(videoId) { mutableStateOf<List<SponsorSegment>>(emptyList()) }
    val skippedSegmentIds = remember(videoId) { mutableSetOf<String>() }

    // Real-Time Closed Captions (CC) State
    var captionsEnabled by remember { mutableStateOf(false) }
    var captionSegments by remember(videoId) { mutableStateOf<List<com.example.util.TranscriptSegment>>(emptyList()) }
    var activeCaptionText by remember { mutableStateOf<String?>(null) }
    var isCaptionsLoading by remember { mutableStateOf(false) }
    // Gestures: Brightness (Left) & Volume (Right)
    val activity = remember(context) { context as? android.app.Activity }
    val audioManager = remember(context) { context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager }
    val maxAudioVolume = remember(audioManager) { audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1) }

    var gestureBrightness by remember { mutableFloatStateOf(0.5f) }
    var isAdjustingBrightness by remember { mutableStateOf(false) }

    var gestureVolumeFraction by remember { mutableFloatStateOf(0.5f) }
    var isAdjustingVolume by remember { mutableStateOf(false) }

    // Autoplay Next Video State (persisted across sessions)
    val playerPrefs = remember(context) { context.getSharedPreferences("vixz_player_prefs", Context.MODE_PRIVATE) }
    var isAutoplayEnabled by remember { mutableStateOf(playerPrefs.getBoolean("autoplay_enabled", true)) }

    // Sleep Timer State (5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60 mins slider & presets)
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var isSleepTimerActive by remember { mutableStateOf(false) }
    var sleepTimerMinutes by remember { mutableIntStateOf(30) }
    var lastSleepDurationMinutes by remember { mutableIntStateOf(30) }
    var sleepTimerRemainingSec by remember { mutableIntStateOf(0) }
    var sleepTimerEndOfVideo by remember { mutableStateOf(false) }
    var wasPausedBySleepTimer by remember { mutableStateOf(false) }

    // Screenshot & Custom Folder State
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var activeScreenshotFolder by remember { mutableStateOf(com.example.util.ScreenshotManager.getActiveFolder(context)) }
    var showScreenshotFolderDialog by remember { mutableStateOf(false) }
    var screenshotFlashTrigger by remember { mutableStateOf(false) }
    var screenshotFeedbackText by remember { mutableStateOf<String?>(null) }

    fun takeScreenshot() {
        coroutineScope.launch {
            screenshotFlashTrigger = true
            val bmp = com.example.util.ScreenshotManager.capturePlayerFrame(playerViewRef, activity)
            delay(100)
            screenshotFlashTrigger = false
            if (bmp != null) {
                val (uri, path) = com.example.util.ScreenshotManager.saveScreenshot(
                    context = context,
                    bitmap = bmp,
                    videoTitle = videoTitle ?: "Video",
                    timestampMs = currentPosMs,
                    targetFolder = activeScreenshotFolder
                )
                if (uri != null) {
                    val folderDisplay = if (activeScreenshotFolder.equals("Default", ignoreCase = true)) "Pictures/Vixz" else "Pictures/Vixz/$activeScreenshotFolder"
                    screenshotFeedbackText = "📸 Saved to $folderDisplay"
                    android.widget.Toast.makeText(context, "📸 Screenshot saved to $folderDisplay", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(context, "⚠️ Failed to save screenshot", android.widget.Toast.LENGTH_SHORT).show()
                }
            } else {
                android.widget.Toast.makeText(context, "⚠️ Could not capture video frame", android.widget.Toast.LENGTH_SHORT).show()
            }
            delay(2500)
            screenshotFeedbackText = null
        }
    }

    fun addLog(msg: String) {
        val entry = "[${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())}] $msg"
        debugLogs.add(entry)
    }

    // Keep screen on during playback
    DisposableEffect(Unit) {
        val activity = (context as? android.app.Activity)
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val exoPlayer = remember(videoId) {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 120_000,
                /* maxBufferMs = */ 360_000,
                /* bufferForPlaybackMs = */ 2_000,
                /* bufferForPlaybackAfterRebufferMs = */ 2_500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(60_000, true)
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setSeekForwardIncrementMs(10_000)
            .setSeekBackIncrementMs(10_000)
            .build().apply {
                playWhenReady = true
                setAudioAttributes(audioAttributes, true)
                setHandleAudioBecomingNoisy(true)
                setWakeMode(C.WAKE_MODE_NETWORK)
                volume = if (isMutedState) 0f else 1.0f
            }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onRenderedFirstFrame() {
                isFirstFrameRendered = true
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == androidx.media3.common.Player.STATE_READY) {
                    isFirstFrameRendered = true
                } else if (state == androidx.media3.common.Player.STATE_ENDED) {
                    if (isAutoplayEnabled) {
                        onNextVideo()
                        android.widget.Toast.makeText(context, "Autoplay: Playing Next Video ⏭️", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isPlayingState = isPlaying
                onPlayingStateChanged(isPlaying)
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                addLog("⚠️ ExoPlayer Playback Error (${error.errorCodeName}): ${error.message} -> Activating Web Player Fallback")
                useWebPlayerFallback = true
                isLoading = false
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            try {
                exoPlayer.removeListener(listener)
                val pos = exoPlayer.currentPosition
                if (pos > 0) {
                    savedPositionMs = pos
                }
            } catch (e: Exception) {
                // Ignore
            }
            exoPlayer.release()
        }
    }

    // Handle remote PiP and external commands (Play/Pause, Seek)
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

    // Fetch SponsorBlock skip segments for video
    LaunchedEffect(videoId) {
        val segments = SponsorBlockService.getSponsorSegments(videoId)
        if (segments.isNotEmpty()) {
            sponsorSegments = segments
            addLog("SponsorBlock: Loaded ${segments.size} in-video sponsor skip segment(s) ⏭️")
        }
    }

    // Scrubber drag state (smooth scrubbing without ticker fighting)
    var isDraggingScrubber by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    var areControlsVisible by remember { mutableStateOf(true) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showSpeedSubMenu by remember { mutableStateOf(false) }
    var showQualitySubMenu by remember { mutableStateOf(false) }
    var selectedSpeed by remember { mutableFloatStateOf(1.0f) }

    // Auto-hide bottom utility controls after 2.0 seconds of no interaction (both when playing and when paused)
    LaunchedEffect(areControlsVisible, isPlayingState, isDraggingScrubber, showSettingsMenu, showSpeedSubMenu, showQualitySubMenu) {
        if (areControlsVisible && !isDraggingScrubber && !showSettingsMenu && !showSpeedSubMenu && !showQualitySubMenu) {
            delay(2000L)
            areControlsVisible = false
        }
    }

    // Position ticker: saves current playback timestamp & automatically skips SponsorBlock segments & updates CC subtitles
    LaunchedEffect(exoPlayer, hasPreparedMedia, sponsorSegments, isDraggingScrubber, captionsEnabled, captionSegments) {
        if (hasPreparedMedia) {
            while (isActive) {
                try {
                    val pos = exoPlayer.currentPosition
                    if (pos > 0 && !isDraggingScrubber) {
                        savedPositionMs = pos
                        currentPosMs = pos

                        // Real-time Closed Captions (CC) Matcher
                        val currentSec = (pos / 1000).toInt()
                        if (captionsEnabled && captionSegments.isNotEmpty()) {
                            val matching = captionSegments
                                .filter { it.timestampSeconds <= currentSec }
                                .lastOrNull { (currentSec - it.timestampSeconds) <= 5 }
                            activeCaptionText = matching?.text?.trim()
                        } else {
                            activeCaptionText = null
                        }

                        // Automatic SponsorBlock In-Video Segment Skip (Strict single execution per segment)
                        if (sponsorSegments.isNotEmpty()) {
                            val segment = sponsorSegments.firstOrNull { seg ->
                                val key = "${seg.startMs}_${seg.endMs}"
                                key !in skippedSegmentIds && pos >= seg.startMs && pos < (seg.endMs - 500)
                            }
                            if (segment != null) {
                                val key = "${segment.startMs}_${segment.endMs}"
                                skippedSegmentIds.add(key)
                                val targetSeek = (segment.endMs + 100).coerceAtMost(if (totalDurationMs > 0) totalDurationMs else (segment.endMs + 100))
                                exoPlayer.seekTo(targetSeek)
                                val startFormatted = formatMs(segment.startMs)
                                val endFormatted = formatMs(segment.endMs)
                                val message = "⏭️ Skipped ${segment.category.replaceFirstChar { it.uppercase() }} ($startFormatted → $endFormatted)"
                                addLog(message)
                                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    val dur = exoPlayer.duration
                    if (dur > 0) {
                        totalDurationMs = dur
                    }
                    isPlayingState = exoPlayer.isPlaying
                } catch (e: Exception) {
                    // Ignore
                }
                delay(100)
            }
        }
    }

    // Sleep Timer Countdown Engine (Automatically pauses playback when countdown expires or video ends)
    LaunchedEffect(isSleepTimerActive, sleepTimerEndOfVideo) {
        if (isSleepTimerActive) {
            if (sleepTimerEndOfVideo) {
                while (isSleepTimerActive && isActive) {
                    if (totalDurationMs > 0 && currentPosMs >= (totalDurationMs - 1500L)) {
                        exoPlayer.pause()
                        isPlayingState = false
                        isSleepTimerActive = false
                        sleepTimerEndOfVideo = false
                        wasPausedBySleepTimer = true
                        android.widget.Toast.makeText(context, "🌙 Sleep Timer: End of video reached. Tap 🌙 to resume.", android.widget.Toast.LENGTH_LONG).show()
                        break
                    }
                    delay(500L)
                }
            } else {
                while (isSleepTimerActive && sleepTimerRemainingSec > 0 && isActive) {
                    delay(1000L)
                    sleepTimerRemainingSec -= 1
                    if (sleepTimerRemainingSec <= 0) {
                        exoPlayer.pause()
                        isPlayingState = false
                        isSleepTimerActive = false
                        wasPausedBySleepTimer = true
                        android.widget.Toast.makeText(context, "🌙 Sleep Timer finished. Tap 🌙 to resume for ${lastSleepDurationMinutes}m.", android.widget.Toast.LENGTH_LONG).show()
                        break
                    }
                }
            }
        }
    }

    // Background fetch of real subtitles when CC is enabled
    LaunchedEffect(videoId, captionsEnabled) {
        if (captionsEnabled && captionSegments.isEmpty()) {
            isCaptionsLoading = true
            try {
                val segments = com.example.data.remote.YouTubeCaptionService.fetchTimedCaptions(videoId)
                captionSegments = segments
                if (segments.isEmpty()) {
                    addLog("ℹ️ No English captions found for this video.")
                } else {
                    addLog("✅ CC Subtitles Enabled: Loaded ${segments.size} timed lines")
                }
            } catch (e: Exception) {
                addLog("⚠️ Subtitle fetch error: ${e.message}")
            } finally {
                isCaptionsLoading = false
            }
        }
    }

    LaunchedEffect(videoId) {
        isLoading = true
        isFirstFrameRendered = false
        useWebPlayerFallback = false
        hasPreparedMedia = false
        streamUrl = null

        // 1. Check if video is downloaded locally (Offline / Airplane Mode Playback)
        val localFile = com.example.data.remote.VideoDownloadManager.getLocalVideoFile(context, videoId)
        if (localFile.exists() && localFile.length() > 1024 * 100) {
            val localUri = android.net.Uri.fromFile(localFile).toString()
            streamUrl = localUri
            availableQualities = listOf("Offline Ready")
            selectedQuality = "Offline Ready"
            isLoading = false
            addLog("⚡ Playing from Local Offline Storage (${localFile.length() / (1024 * 1024)}MB) - Offline / Airplane Mode Ready!")
            return@LaunchedEffect
        }

        // 2. Otherwise extract online stream & all available resolutions
        addLog("Extracting direct MP4 stream URL & available qualities for videoId: $videoId")
        val result = kotlinx.coroutines.withTimeoutOrNull(15000L) {
            YouTubeStreamExtractor.extractVideoStreams(videoId)
        }
        if (result != null && !result.primaryStreamUrl.isNullOrEmpty()) {
            streamResult = result
            availableQualities = result.availableQualities
            selectedQuality = "Auto"
            streamUrl = result.primaryStreamUrl
            isLoading = false
            addLog("Streams Extracted! Available: ${result.availableQualities.joinToString(", ")}")
        } else {
            isLoading = false
            useWebPlayerFallback = true
            statusLog = "Direct stream timed out or restricted. Activating Web Player."
            addLog("Direct stream timeout (15.0s) -> Activating Web Player Fallback")
        }
    }

    LaunchedEffect(isMutedState) {
        exoPlayer.volume = if (isMutedState) 0f else 1.0f
    }

    LaunchedEffect(streamUrl) {
        streamUrl?.let { url ->
            val audioUrl = streamResult?.audioStreamUrl
            val isVideoOnly = streamResult?.isVideoOnlyStream(url, selectedQuality) == true ||
                    (!audioUrl.isNullOrBlank() && url != streamResult?.combinedMuxedUrl && !url.contains(".m3u8") && !url.startsWith("file://") && !url.startsWith("/"))

            val liveCookies = try {
                android.webkit.CookieManager.getInstance().getCookie("https://www.youtube.com") ?: ""
            } catch (e: Throwable) { "" }
            val savedCookies = playerPrefs.getString("youtube_cookies", "") ?: ""
            val effectiveCookies = if (liveCookies.isNotBlank() && (liveCookies.contains("LOGIN_INFO") || liveCookies.contains("SID") || liveCookies.contains("SAPISID"))) liveCookies else savedCookies

            val requestProps = mutableMapOf(
                "Referer" to "https://www.youtube.com/",
                "Origin" to "https://www.youtube.com",
                "Sec-Fetch-Dest" to "video",
                "Sec-Fetch-Mode" to "cors",
                "Sec-Fetch-Site" to "cross-site"
            )
            if (effectiveCookies.isNotBlank()) {
                requestProps["Cookie"] = effectiveCookies
            }

            val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                .setUserAgent("com.google.android.youtube/19.09.37 (Linux; U; Android 14; US) gzip")
                .setDefaultRequestProperties(requestProps)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(30000)
                .setAllowCrossProtocolRedirects(true)

            val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, httpDataSourceFactory)

            val isHls = url.contains(".m3u8") || url.contains("manifest/hls_variant") || selectedQuality == "HLS"
            if (isHls) {
                val hlsSource = androidx.media3.exoplayer.hls.HlsMediaSource.Factory(dataSourceFactory)
                    .setAllowChunklessPreparation(true)
                    .createMediaSource(MediaItem.fromUri(url))
                exoPlayer.setMediaSource(hlsSource)
            } else if (isVideoOnly && !audioUrl.isNullOrBlank()) {
                val videoSource = androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(url))
                val audioSource = androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(audioUrl))
                val mergingSource = androidx.media3.exoplayer.source.MergingMediaSource(false, false, videoSource, audioSource)
                exoPlayer.setMediaSource(mergingSource)
            } else {
                val mediaSource = androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(url))
                exoPlayer.setMediaSource(mediaSource)
            }

            val targetSeekMs = if (savedPositionMs > 0) {
                savedPositionMs
            } else if (startSeconds > 0) {
                (startSeconds * 1000).toLong()
            } else 0L

            if (targetSeekMs > 0) {
                exoPlayer.seekTo(targetSeekMs)
            }
            exoPlayer.volume = if (isMutedState) 0f else 1.0f
            exoPlayer.prepare()
            exoPlayer.play()
            hasPreparedMedia = true
            onPlayerReady(exoPlayer)
            addLog("ExoPlayer Prepared & Playing (videoOnly=$isVideoOnly, audioMerged=${isVideoOnly && !audioUrl.isNullOrBlank()}) at ${targetSeekMs / 1000}s")
        }
    }

    var forwardRewindFeedback by remember { mutableStateOf<String?>(null) }
    var swipeVideoFeedback by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clipToBounds()
            .pointerInput(videoId) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (zoom != 1f || zoomScale > 1f) {
                        val newScale = (zoomScale * zoom).coerceIn(1f, 5f)
                        zoomScale = newScale
                        if (newScale > 1f) {
                            val maxPanX = (size.width * (newScale - 1f)) / 2f
                            val maxPanY = (size.height * (newScale - 1f)) / 2f
                            panOffsetX = (panOffsetX + pan.x * newScale).coerceIn(-maxPanX, maxPanX)
                            panOffsetY = (panOffsetY + pan.y * newScale).coerceIn(-maxPanY, maxPanY)
                        } else {
                            panOffsetX = 0f
                            panOffsetY = 0f
                            zoomScale = 1f
                        }
                    }
                }
            }
            .pointerInput(videoId, zoomScale) {
                detectTapGestures(
                    onTap = {
                        if (streamUrl != null && !useWebPlayerFallback && !isLoading) {
                            if (exoPlayer.isPlaying) {
                                exoPlayer.pause()
                                isPlayingState = false
                                areControlsVisible = true // Always reveal controls when pausing
                            } else {
                                exoPlayer.play()
                                isPlayingState = true
                                areControlsVisible = true // Reveal controls when playing
                            }
                        }
                    },
                    onDoubleTap = { offset: Offset ->
                        if (zoomScale > 1.05f) {
                            // Quick reset zoom on double tap
                            zoomScale = 1f
                            panOffsetX = 0f
                            panOffsetY = 0f
                        } else if (streamUrl != null && !useWebPlayerFallback && !isLoading) {
                            areControlsVisible = true
                            val w = size.width
                            if (offset.x < w / 2f) {
                                val target = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                                exoPlayer.seekTo(target)
                                forwardRewindFeedback = "-10s ⏪"
                            } else {
                                val dur = if (exoPlayer.duration > 0) exoPlayer.duration else Long.MAX_VALUE
                                val target = (exoPlayer.currentPosition + 10000L).coerceAtMost(dur)
                                exoPlayer.seekTo(target)
                                forwardRewindFeedback = "+10s ⏩"
                            }
                            coroutineScope.launch {
                                kotlinx.coroutines.delay(750)
                                forwardRewindFeedback = null
                            }
                        }
                    }
                )
            }
            .pointerInput(videoId, zoomScale) {
                if (zoomScale <= 1.05f) {
                    var dragZone = 0 // 1 = Left (Brightness), 2 = Right (Volume), 3 = Middle (Next/Prev Video)
                    var middleTotalDragY = 0f
                    val swipeThresholdPx = 70f * density

                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            val w = size.width.toFloat()
                            middleTotalDragY = 0f
                            if (offset.x < w * 0.28f) {
                                // Far Left (Brightness)
                                dragZone = 1
                                val currentLpBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
                                gestureBrightness = if (currentLpBrightness >= 0f) {
                                    currentLpBrightness
                                } else {
                                    try {
                                        android.provider.Settings.System.getInt(
                                            context.contentResolver,
                                            android.provider.Settings.System.SCREEN_BRIGHTNESS,
                                            128
                                        ) / 255f
                                    } catch (e: Exception) { 0.5f }
                                }
                                isAdjustingBrightness = true
                                isAdjustingVolume = false
                            } else if (offset.x > w * 0.72f) {
                                // Far Right (Volume)
                                dragZone = 2
                                val currentVol = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                                gestureVolumeFraction = currentVol.toFloat() / maxAudioVolume
                                isAdjustingVolume = true
                                isAdjustingBrightness = false
                            } else {
                                // Middle (Next / Previous Video)
                                dragZone = 3
                                isAdjustingBrightness = false
                                isAdjustingVolume = false
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            when (dragZone) {
                                1 -> {
                                    val delta = -dragAmount / (size.height * 0.55f)
                                    val newBrightness = (gestureBrightness + delta).coerceIn(0.01f, 1.0f)
                                    gestureBrightness = newBrightness
                                    activity?.let { act ->
                                        val lp = act.window.attributes
                                        lp.screenBrightness = newBrightness
                                        act.window.attributes = lp
                                    }
                                }
                                2 -> {
                                    val delta = -dragAmount / (size.height * 0.55f)
                                    val newVolFraction = (gestureVolumeFraction + delta).coerceIn(0f, 1f)
                                    gestureVolumeFraction = newVolFraction
                                    val targetVol = (newVolFraction * maxAudioVolume).toInt().coerceIn(0, maxAudioVolume)
                                    try {
                                        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, targetVol, 0)
                                    } catch (e: Exception) { }
                                }
                                3 -> {
                                    middleTotalDragY += dragAmount
                                }
                            }
                        },
                        onDragEnd = {
                            if (dragZone == 3) {
                                if (middleTotalDragY < -swipeThresholdPx) {
                                    swipeVideoFeedback = "Next Video ⏭️"
                                    onNextVideo()
                                } else if (middleTotalDragY > swipeThresholdPx) {
                                    swipeVideoFeedback = "Previous Video ⏮️"
                                    onPreviousVideo()
                                }
                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(900)
                                    swipeVideoFeedback = null
                                }
                            } else {
                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(1200)
                                    isAdjustingBrightness = false
                                    isAdjustingVolume = false
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                kotlinx.coroutines.delay(800)
                                isAdjustingBrightness = false
                                isAdjustingVolume = false
                            }
                        }
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Video Surface Container with Pinch-to-Zoom and Pan Graphics Layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoomScale
                    scaleY = zoomScale
                    translationX = panOffsetX
                    translationY = panOffsetY
                },
            contentAlignment = Alignment.Center
        ) {
            if (streamUrl != null && !useWebPlayerFallback) {
                AndroidView(
                    factory = { ctx ->
                        val inflater = android.view.LayoutInflater.from(ctx)
                        (inflater.inflate(com.example.R.layout.exo_texture_player_view, null) as PlayerView).apply {
                            player = exoPlayer
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setOnTouchListener { _, _ -> false }
                            playerViewRef = this
                        }
                    },
                    update = { view ->
                        if (view.player != exoPlayer) {
                            view.player = exoPlayer
                        }
                        view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        playerViewRef = view
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("native_exoplayer_view")
                )
            } else if (useWebPlayerFallback || (streamUrl == null && !isLoading)) {
                // Automatic Fallback: Embedded YouTube Player WebView with WebChromeClient for HTML5 Video Playback
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
                            // Desktop Mode User-Agent: Bypasses mobile browser embed restrictions automatically
                            settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

                            // Set Comprehensive Desktop, EU/UK Consent & Privacy Bypass Cookies
                            try {
                                val cookieManager = android.webkit.CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)
                                val domains = listOf(
                                    "https://www.youtube.com",
                                    "https://m.youtube.com",
                                    "https://youtube.com",
                                    ".youtube.com",
                                    "https://consent.youtube.com",
                                    "https://consent.google.com",
                                    ".google.com"
                                )
                                val consentCookies = listOf(
                                    "SOCS=CAESEwgDEgk2OTg5OTk5OTkaAmVuIAEaBgiA_LyaBg; path=/; domain=.youtube.com; Secure; SameSite=None",
                                    "CONSENT=YES+cb.20230531-04-p0.en+FX+999; path=/; domain=.youtube.com; Secure; SameSite=None",
                                    "PREF=f6=40000000&hl=en&gl=GB; path=/; domain=.youtube.com; Secure; SameSite=None",
                                    "SOCS=CAESEwgDEgk2OTg5OTk5OTkaAmVuIAEaBgiA_LyaBg; path=/; domain=.google.com; Secure; SameSite=None",
                                    "CONSENT=YES+cb.20230531-04-p0.en+FX+999; path=/; domain=.google.com; Secure; SameSite=None"
                                )
                                domains.forEach { domain ->
                                    consentCookies.forEach { cookie ->
                                        cookieManager.setCookie(domain, cookie)
                                    }
                                }
                                cookieManager.flush()
                            } catch (e: Exception) { }

                            webChromeClient = object : android.webkit.WebChromeClient() {
                                override fun onShowCustomView(view: android.view.View?, callback: CustomViewCallback?) {
                                    super.onShowCustomView(view, callback)
                                    onToggleFullscreen()
                                }
                                override fun onHideCustomView() {
                                    super.onHideCustomView()
                                    onToggleFullscreen()
                                }
                            }
                        webViewClient = object : android.webkit.WebViewClient() {
                            private fun runBypassScript(view: android.webkit.WebView?) {
                                view?.evaluateJavascript("""
                                    (function() {
                                        // 1. Auto-dismiss any Google / YouTube consent dialogues & channel creation prompts immediately
                                        var dismissButtons = document.querySelectorAll(
                                            'button[aria-label*="Accept"], button[aria-label*="Agree"], button[aria-label*="Reject"], ' +
                                            'button[aria-label*="Dismiss"], button[aria-label*="No thanks"], button[aria-label*="Cancel"], ' +
                                            'button[aria-label*="Close"], button[aria-label*="Skip"], .yt-spec-button-shape-next--tonal, ' +
                                            'form[action*="consent"] button, ytm-consent-bump-v2-renderer button, .eom-button-row button, ' +
                                            'button.VfPpkd-LgbsSe, button.c3-material-button, ytd-consent-bump-v2-renderer button, ' +
                                            'button[aria-label*="I agree"], button[aria-label*="accept all"], ytm-button-renderer button, ' +
                                            '#cancel-button button, yt-button-renderer#cancel-button button'
                                        );
                                        for (var i = 0; i < dismissButtons.length; i++) {
                                            try { dismissButtons[i].click(); } catch(e) {}
                                        }

                                        // 2. Hide channel creation forms, consent popups, dialogs, and all YouTube web clutter
                                        var style = document.createElement('style');
                                        style.innerHTML = 'ytm-consent-bump-v2-renderer, ytd-consent-bump-v2-renderer, #consent-bump, ' +
                                            '.eom-dialog-wrapper, .upsell-dialog, #dialog, .dialog-container, ytm-channel-creation-form, ' +
                                            '#channel-creation, ytd-channel-creation-form-renderer, tp-yt-paper-dialog, ytm-dialog-renderer, ' +
                                            'header, ytm-header-bar, #header-bar, .mobile-topbar-header, ytm-pivot-bar-renderer, .pivot-bar, ' +
                                            'ytm-app-banner-renderer, #below, ytm-item-section-renderer, #comments, ytm-comment-section-renderer, ' +
                                            '#related, ytm-related-chip-cloud-renderer, ytm-compact-video-renderer, .ytp-chrome-top, ' +
                                            '.ytp-watermark, .ytp-youtube-button, .ytp-pause-overlay, ytd-masthead, #masthead, ' +
                                            'ytd-watch-next-secondary-results-renderer, #secondary, #comments-entry-point { display: none !important; } ' +
                                            'html, body { margin: 0 !important; padding: 0 !important; overflow: hidden !important; background: #000 !important; width: 100vw !important; height: 100vh !important; } ' +
                                            '.player-container, #player-container-id, .html5-video-player, ytm-player, video { position: fixed !important; top: 0 !important; left: 0 !important; width: 100vw !important; height: 100vh !important; max-width: 100vw !important; max-height: 100vh !important; z-index: 999999 !important; object-fit: contain !important; background: #000 !important; }';
                                        document.head.appendChild(style);

                                        // 3. Trigger video playback
                                        var v = document.querySelector('video');
                                        if (v) { v.muted = false; v.play(); }
                                        var playBtn = document.querySelector('.ytp-play-button, .ytp-large-play-button, button.player-control-play-pause-icon');
                                        if (playBtn) playBtn.click();
                                    })();
                                """.trimIndent(), null)
                            }

                            override fun onPageStarted(view: android.webkit.WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                runBypassScript(view)
                            }

                            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isFirstFrameRendered = true
                                runBypassScript(view)
                            }
                        }
                        
                        val extraHeaders = mapOf(
                            "Accept-Language" to "en-US,en;q=0.9",
                            "Sec-Fetch-Site" to "none",
                            "Sec-Fetch-Mode" to "navigate",
                            "Sec-Fetch-User" to "?1",
                            "Sec-Fetch-Dest" to "document"
                        )
                        loadUrl("https://m.youtube.com/watch?v=$videoId", extraHeaders)
                        onPlayerReady(this)
                    }
                },
                update = { webView ->
                    // Keep loaded
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("fallback_webview_player")
            )
        }
        }

        // Preview thumbnail poster while buffering / preparing (prevents initial black screen)
        if (isLoading || (!isFirstFrameRendered && !useWebPlayerFallback && streamUrl != null)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = com.example.util.YouTubeUtils.getThumbnailUrl(videoId),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = YouTubeRed)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isLoading) "Extracting Video Stream..." else "Loading Video...",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        val shouldShowControls = (streamUrl != null && !useWebPlayerFallback && !isLoading) && (areControlsVisible || showSettingsMenu || isDraggingScrubber)

        // Top-Right Corner Tiny Translucent Sleep Timer Countdown Badge
        if (isSleepTimerActive) {
            val countdownText = if (sleepTimerEndOfVideo) {
                "End"
            } else {
                val m = sleepTimerRemainingSec / 60
                val s = sleepTimerRemainingSec % 60
                String.format("%02d:%02d", m, s)
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.35f),
                border = androidx.compose.foundation.BorderStroke(0.75.dp, Color.White.copy(alpha = 0.25f)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
                    .clickable { showSleepTimerDialog = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bedtime,
                        contentDescription = "Sleep Countdown",
                        tint = com.example.ui.theme.GoldStar.copy(alpha = 0.85f),
                        modifier = Modifier.size(10.dp)
                    )
                    Text(
                        text = countdownText,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }
        }

        // Real-Time Closed Caption (CC) Subtitle Overlay
        if (captionsEnabled && !activeCaptionText.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        bottom = if (shouldShowControls) 50.dp else 16.dp,
                        start = 20.dp,
                        end = 20.dp
                    )
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = activeCaptionText!!,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 18.sp
                )
            }
        }

        // Sleek YouTube Bottom Utility Bar (Scrubber + Volume + Timestamp + CC + Settings) - Auto-vanishes & Reappears on Touch
        androidx.compose.animation.AnimatedVisibility(
            visible = shouldShowControls,
            enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(200)),
            exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val isLiveStream = exoPlayer.isCurrentMediaItemLive

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 1.dp)
            ) {
                // 1. YouTube Red Scrubber Slider
                if (!isLiveStream && totalDurationMs > 0) {
                    val activeSliderValue = if (isDraggingScrubber) {
                        dragFraction
                    } else {
                        (currentPosMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                    }

                    Slider(
                        value = activeSliderValue,
                        onValueChange = { fraction ->
                            isDraggingScrubber = true
                            dragFraction = fraction
                            currentPosMs = (fraction * totalDurationMs).toLong()
                        },
                        onValueChangeFinished = {
                            val targetMs = (dragFraction * totalDurationMs).toLong()
                            exoPlayer.seekTo(targetMs)
                            isDraggingScrubber = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = YouTubeRed,
                            activeTrackColor = YouTubeRed,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                    )
                }

                // 2. Utility Row: [🔊 Volume] [08:28 / 16:52] ---------- [[CC] Captions] [⚙️ Settings]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left side: Volume + Timestamp
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // On-Screen Direct Star (Favorite) Button
                        IconButton(
                            onClick = { onFavoriteToggle() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarOutline,
                                contentDescription = "Favorite",
                                tint = if (isFavorite) com.example.ui.theme.GoldStar else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // On-Screen Direct Save to Subject Button
                        IconButton(
                            onClick = { onSaveToSubject() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Folder,
                                contentDescription = "Save to Subject",
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // On-Screen Direct Watch Later Button
                        IconButton(
                            onClick = { onWatchLaterToggle() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isWatchLater) Icons.Filled.WatchLater else Icons.Filled.AccessTime,
                                contentDescription = "Watch Later",
                                tint = if (isWatchLater) YouTubeRed else Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        if (isLiveStream) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(YouTubeRed, RoundedCornerShape(3.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color.White, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "LIVE",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else if (totalDurationMs > 0) {
                            Text(
                                text = "${formatMs(currentPosMs)} / ${formatMs(totalDurationMs)}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Right side: Screenshot + Folder + Sleep Timer + CC + Settings Gear
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        // 1. Screenshot Button [📸]
                        IconButton(
                            onClick = { takeScreenshot() },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CameraAlt,
                                contentDescription = "Screenshot",
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // 2. Screenshot Folder Switcher [📁]
                        IconButton(
                            onClick = { showScreenshotFolderDialog = true },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FolderOpen,
                                contentDescription = "Screenshot Folder",
                                tint = com.example.ui.theme.GoldStar,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // 3. Autoplay Toggle [▶️ / ⏸️] (Auto-advances next video)
                        IconButton(
                            onClick = {
                                val next = !isAutoplayEnabled
                                isAutoplayEnabled = next
                                playerPrefs.edit().putBoolean("autoplay_enabled", next).apply()
                                val msg = if (next) "▶️ Autoplay is ON (Auto-advance next video)" else "⏸️ Autoplay is OFF"
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(19.dp)
                                        .clip(CircleShape)
                                        .background(if (isAutoplayEnabled) com.example.ui.theme.GoldStar.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isAutoplayEnabled) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                        contentDescription = if (isAutoplayEnabled) "Autoplay is ON" else "Autoplay is OFF",
                                        tint = if (isAutoplayEnabled) com.example.ui.theme.GoldStar else Color.LightGray,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                if (isAutoplayEnabled) {
                                    Box(
                                        modifier = Modifier
                                            .width(10.dp)
                                            .height(2.dp)
                                            .background(com.example.ui.theme.GoldStar)
                                    )
                                }
                            }
                        }

                        // 4. Sleep Timer [🌙] (1-tap repeat when paused by sleep, or toggle mini-popup)
                        IconButton(
                            onClick = {
                                if (wasPausedBySleepTimer) {
                                    wasPausedBySleepTimer = false
                                    sleepTimerRemainingSec = lastSleepDurationMinutes * 60
                                    isSleepTimerActive = true
                                    exoPlayer.play()
                                    isPlayingState = true
                                    android.widget.Toast.makeText(context, "🌙 Resumed for ${lastSleepDurationMinutes}m", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    showSleepTimerDialog = !showSleepTimerDialog
                                }
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Filled.Bedtime,
                                    contentDescription = "Sleep Timer",
                                    tint = if (isSleepTimerActive || wasPausedBySleepTimer) com.example.ui.theme.GoldStar else Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                                if (isSleepTimerActive || wasPausedBySleepTimer) {
                                    Box(
                                        modifier = Modifier
                                            .width(12.dp)
                                            .height(2.dp)
                                            .background(com.example.ui.theme.GoldStar)
                                    )
                                }
                            }
                        }

                        // Subtitles [CC]
                        IconButton(
                            onClick = {
                                val next = !captionsEnabled
                                captionsEnabled = next
                                val msg = if (next) "Subtitles (CC) Enabled 💬" else "Subtitles (CC) Turned Off"
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Filled.ClosedCaption,
                                    contentDescription = "Subtitles",
                                    tint = if (captionsEnabled) YouTubeRed else Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                                if (captionsEnabled) {
                                    Box(
                                        modifier = Modifier
                                            .width(12.dp)
                                            .height(2.dp)
                                            .background(YouTubeRed)
                                    )
                                }
                            }
                        }

                        // Settings Gear [⚙️]
                        Box {
                            IconButton(
                                onClick = {
                                    showSettingsMenu = true
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = "Settings",
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showSettingsMenu,
                                onDismissRequest = {
                                    showSettingsMenu = false
                                    showSpeedSubMenu = false
                                    showQualitySubMenu = false
                                }
                            ) {
                                if (!showSpeedSubMenu && !showQualitySubMenu) {
                                    DropdownMenuItem(
                                        text = { Text("Quality: $selectedQuality", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                                        leadingIcon = { Icon(Icons.Filled.HighQuality, contentDescription = null, tint = YouTubeRed) },
                                        onClick = { showQualitySubMenu = true }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Playback Speed: ${selectedSpeed}x", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                                        leadingIcon = { Icon(Icons.Filled.Speed, contentDescription = null, tint = YouTubeRed) },
                                        onClick = { showSpeedSubMenu = true }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Stats & Debug Console", fontSize = 13.sp) },
                                        leadingIcon = { Icon(Icons.Filled.BugReport, contentDescription = null) },
                                        onClick = {
                                            showSettingsMenu = false
                                            onToggleDebugConsole()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Open in Browser 🌐", fontSize = 13.sp) },
                                        leadingIcon = { Icon(Icons.Filled.OpenInBrowser, contentDescription = null) },
                                        onClick = {
                                            showSettingsMenu = false
                                            try {
                                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/watch?v=$videoId"))
                                                context.startActivity(intent)
                                            } catch (e: Exception) { }
                                        }
                                    )
                                } else if (showQualitySubMenu) {
                                    DropdownMenuItem(
                                        text = { Text("⬅ Back to Settings", fontWeight = FontWeight.Bold) },
                                        onClick = { showQualitySubMenu = false }
                                    )
                                    availableQualities.forEach { q ->
                                        val isCurrent = q.equals(selectedQuality, ignoreCase = true)
                                        val label = when (q) {
                                            "2160p" -> "4K Ultra HD (2160p)"
                                            "1440p" -> "Quad HD (1440p)"
                                            "1080p" -> "1080p (Full HD)"
                                            "720p"  -> "720p (HD)"
                                            "480p"  -> "480p (Standard)"
                                            "360p"  -> "360p (Data Saver)"
                                            "240p"  -> "240p (Low)"
                                            "144p"  -> "144p (Lowest)"
                                            "Auto"  -> "Auto (Best Quality)"
                                            else    -> q
                                        }
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = if (isCurrent) "✓ $label" else label,
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isCurrent) YouTubeRed else MaterialTheme.colorScheme.onSurface,
                                                    fontSize = 13.sp
                                                )
                                            },
                                            onClick = {
                                                selectedQuality = q
                                                showQualitySubMenu = false
                                                showSettingsMenu = false

                                                val (maxW, maxH) = when (q) {
                                                    "2160p" -> Pair(3840, 2160)
                                                    "1440p" -> Pair(2560, 1440)
                                                    "1080p" -> Pair(1920, 1080)
                                                    "720p"  -> Pair(1280, 720)
                                                    "480p"  -> Pair(854, 480)
                                                    "360p"  -> Pair(640, 360)
                                                    else    -> Pair(Int.MAX_VALUE, Int.MAX_VALUE)
                                                }
                                                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                .buildUpon().setMaxVideoSize(maxW, maxH).build()

                                                coroutineScope.launch {
                                                    val targetUrl = streamResult?.qualityUrlMap?.get(q)
                                                        ?: com.example.data.remote.YouTubeStreamExtractor.getDirectStreamUrl(videoId, q)
                                                    if (!targetUrl.isNullOrEmpty()) {
                                                        val currentPos = exoPlayer.currentPosition
                                                        savedPositionMs = currentPos
                                                        streamUrl = targetUrl
                                                        android.widget.Toast.makeText(context, "Quality switched to $q", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        )
                                    }
                                } else if (showSpeedSubMenu) {
                                    DropdownMenuItem(
                                        text = { Text("⬅ Back to Settings", fontWeight = FontWeight.Bold) },
                                        onClick = { showSpeedSubMenu = false }
                                    )
                                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { s ->
                                        val isCurrent = s == selectedSpeed
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = if (isCurrent) "✓ ${s}x (Normal)" else "${s}x",
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isCurrent) YouTubeRed else MaterialTheme.colorScheme.onSurface,
                                                    fontSize = 13.sp
                                                )
                                            },
                                            onClick = {
                                                selectedSpeed = s
                                                showSpeedSubMenu = false
                                                showSettingsMenu = false
                                                exoPlayer.playbackParameters = androidx.media3.common.PlaybackParameters(s)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Fullscreen / Maximize & Minimize Button [⤢ / ⤡]
                        IconButton(
                            onClick = { onToggleFullscreen() },
                            modifier = Modifier.size(30.dp).testTag("fullscreen_toggle_btn")
                        ) {
                            Icon(
                                imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                contentDescription = if (isFullscreen) "Exit Fullscreen" else "Maximize / Fullscreen",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }

        // Floating Zoom Level & Quick Reset Badge
        if (zoomScale > 1.05f) {
            Surface(
                onClick = {
                    zoomScale = 1f
                    panOffsetX = 0f
                    panOffsetY = 0f
                },
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.8f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🔍 ${(zoomScale * 10).toInt() / 10f}x Zoom",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "• Tap to Reset",
                        color = YouTubeRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Overlay Debug Logs Overlay Panel
        AnimatedVisibility(
            visible = showDebugConsole,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .padding(12.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Native Extractor Debug Console",
                        color = Color.Green,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Row {
                        IconButton(onClick = {
                            val textToCopy = debugLogs.joinToString("\n")
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Debug Logs", textToCopy))
                            Toast.makeText(context, "Logs Copied to Clipboard 📋", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = "Copy", tint = Color.White)
                        }
                        IconButton(onClick = { onToggleDebugConsole() }) {
                            Icon(imageVector = Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                }
                HorizontalDivider(color = Color.DarkGray)
                Spacer(modifier = Modifier.height(6.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(debugLogs) { log ->
                        Text(
                            text = log,
                            color = if (log.contains("Successfully")) Color.Green else if (log.contains("Failed")) Color.Red else Color.LightGray,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // Left Side Screen Brightness Gesture HUD Overlay
        androidx.compose.animation.AnimatedVisibility(
            visible = isAdjustingBrightness,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 24.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.82f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 18.dp)
                ) {
                    Icon(
                        imageVector = if (gestureBrightness > 0.6f) Icons.Filled.BrightnessHigh else if (gestureBrightness > 0.25f) Icons.Filled.BrightnessMedium else Icons.Filled.BrightnessLow,
                        contentDescription = "Brightness",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .width(8.dp)
                            .height(100.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(gestureBrightness.coerceIn(0f, 1f))
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "${(gestureBrightness * 100).toInt()}%",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Right Side Device Volume Gesture HUD Overlay
        androidx.compose.animation.AnimatedVisibility(
            visible = isAdjustingVolume,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 24.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.82f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 18.dp)
                ) {
                    Icon(
                        imageVector = if (gestureVolumeFraction > 0.5f) Icons.Filled.VolumeUp else if (gestureVolumeFraction > 0.05f) Icons.Filled.VolumeDown else Icons.Filled.VolumeMute,
                        contentDescription = "Volume",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .width(8.dp)
                            .height(100.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(gestureVolumeFraction.coerceIn(0f, 1f))
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "${(gestureVolumeFraction * 100).toInt()}%",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Center Fast-Forward / Rewind Gesture HUD Overlay
        androidx.compose.animation.AnimatedVisibility(
            visible = forwardRewindFeedback != null,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.80f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
            ) {
                Text(
                    text = forwardRewindFeedback ?: "",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp)
                )
            }
        }

        // Center Swipe Next / Previous Video Gesture HUD Overlay
        androidx.compose.animation.AnimatedVisibility(
            visible = swipeVideoFeedback != null,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = YouTubeRed.copy(alpha = 0.90f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
            ) {
                Text(
                    text = swipeVideoFeedback ?: "",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp)
                )
            }
        }

        // 1-Click Center Resume & Repeat Sleep Timer Pill (shows when video was paused by sleep timer)
        if (wasPausedBySleepTimer && !isPlayingState) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Black.copy(alpha = 0.88f),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, com.example.ui.theme.GoldStar),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
                    .clickable {
                        wasPausedBySleepTimer = false
                        sleepTimerRemainingSec = lastSleepDurationMinutes * 60
                        isSleepTimerActive = true
                        exoPlayer.play()
                        isPlayingState = true
                        android.widget.Toast.makeText(context, "🌙 Resumed with ${lastSleepDurationMinutes}m timer", android.widget.Toast.LENGTH_SHORT).show()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bedtime,
                        contentDescription = "Resume Sleep Timer",
                        tint = com.example.ui.theme.GoldStar,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Resume for ${lastSleepDurationMinutes}m 🌙",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }

        // Camera Shutter Flash Effect (150ms white flash fade)
        androidx.compose.animation.AnimatedVisibility(
            visible = screenshotFlashTrigger,
            enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(30)),
            exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(150)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.85f))
            )
        }

        // Screenshot Saved Feedback Toast Chip
        androidx.compose.animation.AnimatedVisibility(
            visible = screenshotFeedbackText != null,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 45.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.85f),
                border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.GoldStar.copy(alpha = 0.7f)),
                shadowElevation = 6.dp,
                modifier = Modifier.clickable { showScreenshotFolderDialog = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = "Screenshot",
                        tint = com.example.ui.theme.GoldStar,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = screenshotFeedbackText ?: "",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Screenshot Folder Manager Dialog
        if (showScreenshotFolderDialog) {
            var newFolderInput by remember { mutableStateOf("") }
            var isCreatingFolder by remember { mutableStateOf(false) }
            val folders = remember(showScreenshotFolderDialog) { mutableStateListOf(*com.example.util.ScreenshotManager.getFolders(context).toTypedArray()) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { showScreenshotFolderDialog = false },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF18181A).copy(alpha = 0.96f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .width(290.dp)
                        .clickable(enabled = false) {}
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Folder,
                                    contentDescription = "Folder",
                                    tint = com.example.ui.theme.GoldStar,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Screenshot Folder",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            IconButton(
                                onClick = { showScreenshotFolderDialog = false },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Close",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Text(
                            text = "Save in: Pictures/Vixz/$activeScreenshotFolder",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .align(Alignment.Start)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Folder List
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(folders) { fName ->
                                val isSelected = fName == activeScreenshotFolder
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) com.example.ui.theme.GoldStar.copy(alpha = 0.2f) else Color(0xFF242426),
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.GoldStar) else null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            com.example.util.ScreenshotManager.setActiveFolder(context, fName)
                                            activeScreenshotFolder = fName
                                            android.widget.Toast.makeText(context, "Active folder: $fName", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.Folder,
                                                contentDescription = null,
                                                tint = if (isSelected) com.example.ui.theme.GoldStar else Color.LightGray,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = fName,
                                                color = if (isSelected) Color.White else Color.LightGray,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 13.sp
                                            )
                                        }

                                        if (fName != "Default" && fName != "Screenshots") {
                                            IconButton(
                                                onClick = {
                                                    com.example.util.ScreenshotManager.deleteFolder(context, fName)
                                                    folders.remove(fName)
                                                    if (activeScreenshotFolder == fName) {
                                                        activeScreenshotFolder = "Default"
                                                    }
                                                },
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Delete,
                                                    contentDescription = "Delete",
                                                    tint = Color.Gray,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Add Custom Folder Section
                        if (isCreatingFolder) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedTextField(
                                    value = newFolderInput,
                                    onValueChange = { newFolderInput = it },
                                    placeholder = { Text("Folder Name", fontSize = 12.sp, color = Color.Gray) },
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 12.sp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = com.example.ui.theme.GoldStar,
                                        unfocusedBorderColor = Color.Gray,
                                        cursorColor = com.example.ui.theme.GoldStar
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                )
                                Button(
                                    onClick = {
                                        val trimmed = newFolderInput.trim()
                                        if (trimmed.isNotBlank()) {
                                            com.example.util.ScreenshotManager.addFolder(context, trimmed)
                                            com.example.util.ScreenshotManager.setActiveFolder(context, trimmed)
                                            activeScreenshotFolder = trimmed
                                            if (!folders.contains(trimmed)) folders.add(trimmed)
                                            newFolderInput = ""
                                            isCreatingFolder = false
                                            android.widget.Toast.makeText(context, "Created folder: $trimmed", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.GoldStar),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier.height(44.dp)
                                ) {
                                    Text("Add", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = { isCreatingFolder = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = com.example.ui.theme.GoldStar),
                                border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.GoldStar.copy(alpha = 0.6f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = "Add", modifier = Modifier.size(14.dp))
                                    Text("New Folder", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Tiny, Sleek Floating Sleep Timer Mini-Card
        if (showSleepTimerDialog) {
            var tempMinutes by remember { mutableFloatStateOf(if (sleepTimerMinutes in 5..60) sleepTimerMinutes.toFloat() else 30f) }
            var tempEndOfVideo by remember { mutableStateOf(sleepTimerEndOfVideo) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { showSleepTimerDialog = false },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF18181A).copy(alpha = 0.95f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .width(270.dp)
                        .clickable(enabled = false) {}
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Header: Title + Close X
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Bedtime,
                                    contentDescription = "Sleep",
                                    tint = com.example.ui.theme.GoldStar,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Sleep Timer",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            IconButton(
                                onClick = { showSleepTimerDialog = false },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Close",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        if (isSleepTimerActive) {
                            val remM = sleepTimerRemainingSec / 60
                            val remS = sleepTimerRemainingSec % 60
                            val statusText = if (sleepTimerEndOfVideo) "Active: Stops at video end" else "Active: ${remM}m ${remS}s left"
                            Text(
                                text = statusText,
                                color = Color(0xFF81C784),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }

                        // Duration Readout
                        val chosenMins = ((tempMinutes / 5f).toInt() * 5).coerceIn(5, 60)
                        val readoutText = if (tempEndOfVideo) "End of Video" else if (chosenMins == 60) "1 Hour" else "$chosenMins Min"

                        Text(
                            text = readoutText,
                            color = if (tempEndOfVideo) com.example.ui.theme.GoldStar else Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp
                        )

                        // Compact Step Slider
                        if (!tempEndOfVideo) {
                            Slider(
                                value = tempMinutes,
                                onValueChange = { raw ->
                                    tempMinutes = ((raw / 5f).toInt() * 5).toFloat().coerceIn(5f, 60f)
                                },
                                valueRange = 5f..60f,
                                steps = 10,
                                colors = SliderDefaults.colors(
                                    thumbColor = com.example.ui.theme.GoldStar,
                                    activeTrackColor = com.example.ui.theme.GoldStar,
                                    inactiveTrackColor = Color.DarkGray
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(28.dp)
                            )
                        }

                        // Preset Chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(15, 30, 45, 60).forEach { p ->
                                val sel = !tempEndOfVideo && chosenMins == p
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (sel) com.example.ui.theme.GoldStar else Color(0xFF2A2A2C),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            tempMinutes = p.toFloat()
                                            tempEndOfVideo = false
                                        }
                                ) {
                                    Text(
                                        text = if (p == 60) "1h" else "${p}m",
                                        color = if (sel) Color.Black else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isSleepTimerActive) {
                                Button(
                                    onClick = {
                                        isSleepTimerActive = false
                                        sleepTimerRemainingSec = 0
                                        sleepTimerEndOfVideo = false
                                        showSleepTimerDialog = false
                                        android.widget.Toast.makeText(context, "Timer Off ⏹️", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333336)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                ) {
                                    Text("Turn Off", color = Color(0xFFFF5252), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Button(
                                onClick = {
                                    val finalMins = ((tempMinutes / 5f).toInt() * 5).coerceIn(5, 60)
                                    sleepTimerMinutes = finalMins
                                    lastSleepDurationMinutes = finalMins
                                    sleepTimerEndOfVideo = tempEndOfVideo
                                    if (tempEndOfVideo) {
                                        isSleepTimerActive = true
                                        android.widget.Toast.makeText(context, "🌙 Sleep: End of Video", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        sleepTimerRemainingSec = finalMins * 60
                                        isSleepTimerActive = true
                                        android.widget.Toast.makeText(context, "🌙 Sleep set for $finalMins min", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                    showSleepTimerDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.GoldStar),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                            ) {
                                Text(
                                    text = if (isSleepTimerActive) "Reset (${readoutText})" else "Start (${readoutText})",
                                    color = Color.Black,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSec = ms / 1000
    val mins = totalSec / 60
    val secs = totalSec % 60
    return String.format("%02d:%02d", mins, secs)
}
