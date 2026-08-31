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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import com.example.util.findActivity
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
import androidx.compose.ui.input.pointer.positionChange
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
    isDisliked: Boolean = false,
    onDislikeToggle: () -> Unit = {},
    onAiSummaryClick: () -> Unit = {},
    isDownloaded: Boolean = false,
    downloadProgress: Int = 0,
    onDownloadClick: () -> Unit = {},
    onDeleteDownloadClick: () -> Unit = {},
    videoTitle: String = "Video",
    isFullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
    onBackClick: () -> Unit = {},
    onEnterPip: () -> Unit = {},
    onPositionUpdate: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    onPlayerReady: (Any) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600 || configuration.smallestScreenWidthDp >= 600
    val bottomBtnSize = if (isTablet) 44.dp else 36.dp
    val bottomIconSize = if (isTablet) 24.dp else 20.dp
    val actionBtnSize = if (isTablet) 46.dp else 38.dp
    val actionIconSize = if (isTablet) 24.dp else 20.dp
    val timeFontSize = if (isTablet) 14.sp else 12.sp
    val scrubberHeight = if (isTablet) 18.dp else 14.dp
    var streamUrl by remember(videoId) { mutableStateOf<String?>(null) }
    var isLoading by remember(videoId) { mutableStateOf(true) }
    var isFirstFrameRendered by remember(videoId) { mutableStateOf(false) }

    var statusLog by remember(videoId) { mutableStateOf("Initializing Native ExoPlayer Engine...") }
    val debugLogs = remember(videoId) { mutableStateListOf<String>() }

    // Pinch-to-Zoom & Pan State (1.0x to 5.0x zoom with smooth translation)
    var zoomScale by remember(videoId) { mutableFloatStateOf(1f) }
    var playerRotationAngle by remember(videoId) { mutableFloatStateOf(0f) }
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
    val activity = remember(context) {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is android.app.Activity) return@remember ctx
            ctx = ctx.baseContext
        }
        null
    }
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
        val activity = context.findActivity()
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
                /* minBufferMs = */ 50_000,
                /* maxBufferMs = */ 120_000,
                /* bufferForPlaybackMs = */ 500,
                /* bufferForPlaybackAfterRebufferMs = */ 1_000
            )
            .setTargetBufferBytes(androidx.media3.common.C.LENGTH_UNSET)
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(30_000, true)
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
                    val finalSec = (pos / 1000).toInt()
                    val rPrefs = context.getSharedPreferences("vixz_resume_positions", Context.MODE_PRIVATE)
                    rPrefs.edit().putInt("pos_$videoId", finalSec).apply()
                    onPositionUpdate(finalSec)
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
    var showWebSignInModal by remember { mutableStateOf(false) }
    var extractionRetryTrigger by remember { mutableIntStateOf(0) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    var areControlsVisible by remember { mutableStateOf(true) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showSpeedSubMenu by remember { mutableStateOf(false) }
    var showQualitySubMenu by remember { mutableStateOf(false) }
    var selectedSpeed by remember { mutableFloatStateOf(1.0f) }

    // Auto-hide bottom utility controls after 2.0 seconds — only while PLAYING (paused = stays visible)
    LaunchedEffect(areControlsVisible, isPlayingState, isDraggingScrubber, showSettingsMenu, showSpeedSubMenu, showQualitySubMenu) {
        if (areControlsVisible && isPlayingState && !isDraggingScrubber && !showSettingsMenu && !showSpeedSubMenu && !showQualitySubMenu) {
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
                        val curSec = (pos / 1000).toInt()
                        if (curSec > 0) {
                            val rPrefs = context.getSharedPreferences("vixz_resume_positions", Context.MODE_PRIVATE)
                            rPrefs.edit().putInt("pos_$videoId", curSec).apply()
                            onPositionUpdate(curSec)
                        }

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

            val okHttpClient = com.example.data.remote.NetworkClient.client
            val httpDataSourceFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(okHttpClient)
                .setUserAgent("com.google.android.youtube/19.09.37 (Linux; U; Android 14; US) gzip")
                .setDefaultRequestProperties(requestProps)

            val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, httpDataSourceFactory)

            val isHls = url.contains(".m3u8") || url.contains("manifest/hls_variant") || selectedQuality == "HLS" || url == streamResult?.qualityUrlMap?.get("HLS")
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
                val mergingSource = androidx.media3.exoplayer.source.MergingMediaSource(
                    /* adjustPeriodTimeOffsets = */ true,
                    /* clipDurations = */ true,
                    videoSource,
                    audioSource
                )
                exoPlayer.setMediaSource(mergingSource)
            } else {
                val mediaSource = androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(url))
                exoPlayer.setMediaSource(mediaSource)
            }

            val resumePrefs = context.getSharedPreferences("vixz_resume_positions", Context.MODE_PRIVATE)
            val savedPrefsSec = resumePrefs.getInt("pos_$videoId", 0)

            val targetSeekMs = if (savedPositionMs > 0) {
                savedPositionMs
            } else if (savedPrefsSec > 0) {
                savedPrefsSec * 1000L
            } else if (startSeconds > 0) {
                startSeconds * 1000L
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
    var playPauseFeedbackState by remember { mutableStateOf<Boolean?>(null) }
    var speedFeedbackState by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        // Video Surface Container with Pinch-to-Zoom and Pan Graphics Layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = playerRotationAngle
                    scaleX = zoomScale
                    scaleY = zoomScale
                    translationX = panOffsetX
                    translationY = panOffsetY
                },
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
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
        }

        // Preview thumbnail poster while buffering / preparing (prevents initial black screen)
        if (isLoading || (!isFirstFrameRendered && streamUrl != null)) {
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

        // Master Unified Touch Coordinator (1-Finger: Left=Brightness, Right=Volume, Center Swipe=Next/Prev Video, Tap=Play/Pause, Double-Tap=Seek | 2-Fingers: Pinch-to-Zoom & Pan)
        var lastTapTime by remember { mutableLongStateOf(0L) }
        var lastTapX by remember { mutableFloatStateOf(0f) }
        var singleTapJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(videoId) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val startTime = System.currentTimeMillis()
                            val startX = down.position.x
                            val startY = down.position.y
                            var lastY = startY
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()

                            var isDrag = false
                            var isPinch = false
                            val dragZone = when {
                                startX < w * 0.22f -> 1 // Left: Brightness
                                startX > w * 0.78f -> 2 // Right: Volume
                                else -> 3 // Center: Next/Prev Video Swipe or Pan
                            }
                            var prevPinchDist = 0f
                            var prevCenter = Offset.Zero

                            do {
                                val event = awaitPointerEvent()
                                val pointers = event.changes
                                val activePointers = pointers.filter { it.pressed }

                                if (activePointers.size >= 2) {
                                    // 2-FINGER PINCH & PAN
                                    isPinch = true
                                    isDrag = false
                                    isAdjustingBrightness = false
                                    isAdjustingVolume = false

                                    val p1 = activePointers[0].position
                                    val p2 = activePointers[1].position
                                    val dist = kotlin.math.hypot((p1.x - p2.x).toDouble(), (p1.y - p2.y).toDouble()).toFloat()
                                    val center = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)

                                    if (prevPinchDist > 0f) {
                                        val scale = dist / prevPinchDist
                                        val newZoom = (zoomScale * scale).coerceIn(1.0f, 5.0f)
                                        zoomScale = newZoom

                                        if (newZoom > 1.02f) {
                                            val panX = center.x - prevCenter.x
                                            val panY = center.y - prevCenter.y
                                            val maxPanX = (w * (newZoom - 1f)) / 2f
                                            val maxPanY = (h * (newZoom - 1f)) / 2f
                                            panOffsetX = (panOffsetX + panX).coerceIn(-maxPanX, maxPanX)
                                            panOffsetY = (panOffsetY + panY).coerceIn(-maxPanY, maxPanY)
                                        } else {
                                            panOffsetX = 0f
                                            panOffsetY = 0f
                                            zoomScale = 1.0f
                                        }
                                    }
                                    prevPinchDist = dist
                                    prevCenter = center
                                    pointers.forEach { it.consume() }
                                } else if (activePointers.size == 1 && !isPinch) {
                                    val p = activePointers[0]
                                    val dx = p.position.x - startX
                                    val dy = p.position.y - startY
                                    lastY = p.position.y

                                    if (!isDrag && (java.lang.Math.abs(dy) > 18f || java.lang.Math.abs(dx) > 18f)) {
                                        isDrag = true
                                        if (dragZone == 1) {
                                            // Brightness works in normal AND zoomed-in mode
                                            isAdjustingBrightness = true
                                            isAdjustingVolume = false
                                            val currentLp = activity?.window?.attributes?.screenBrightness ?: -1f
                                            gestureBrightness = if (currentLp in 0.01f..1.0f) currentLp else 0.5f
                                        } else if (dragZone == 2) {
                                            // Volume works in normal AND zoomed-in mode
                                            isAdjustingVolume = true
                                            isAdjustingBrightness = false
                                            val curVol = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                                            gestureVolumeFraction = curVol.toFloat() / maxAudioVolume.toFloat()
                                        }
                                    }

                                    if (isDrag) {
                                        val deltaY = -(p.position.y - p.previousPosition.y)
                                        val deltaX = p.position.x - p.previousPosition.x
                                        p.consume()

                                        if (dragZone == 1) {
                                            val delta = deltaY / (h * 0.40f)
                                            val newB = (gestureBrightness + delta).coerceIn(0.01f, 1.0f)
                                            gestureBrightness = newB
                                            activity?.let { act ->
                                                val lp = act.window.attributes
                                                lp.screenBrightness = newB
                                                act.window.attributes = lp
                                            }
                                        } else if (dragZone == 2) {
                                            val delta = deltaY / (h * 0.40f)
                                            val newV = (gestureVolumeFraction + delta).coerceIn(0f, 1f)
                                            gestureVolumeFraction = newV
                                            val targetVol = kotlin.math.round(newV * maxAudioVolume).toInt().coerceIn(0, maxAudioVolume)
                                            try {
                                                audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, targetVol, 0)
                                            } catch (e: Exception) { }
                                        } else if (zoomScale > 1.02f) {
                                            // Pan zoomed video with 1 finger in center
                                            val maxPanX = (w * (zoomScale - 1f)) / 2f
                                            val maxPanY = (h * (zoomScale - 1f)) / 2f
                                            panOffsetX = (panOffsetX + deltaX).coerceIn(-maxPanX, maxPanX)
                                            panOffsetY = (panOffsetY + deltaY).coerceIn(-maxPanY, maxPanY)
                                        }
                                    }
                                }
                            } while (activePointers.isNotEmpty())

                            // Touch released: Tap or Swipe Completion
                            val duration = System.currentTimeMillis() - startTime
                            if (!isDrag && !isPinch && duration < 320) {
                                val now = System.currentTimeMillis()
                                val isDouble = (now - lastTapTime < 280L) && (java.lang.Math.abs(startX - lastTapX) < 120f)
                                if (isDouble) {
                                    singleTapJob?.cancel()
                                    lastTapTime = 0L
                                    if (startX < w * 0.35f) {
                                        val currentPos = exoPlayer.currentPosition
                                        exoPlayer.seekTo((currentPos - 10_000L).coerceAtLeast(0L))
                                        forwardRewindFeedback = "⏪ -10s"
                                        coroutineScope.launch {
                                            kotlinx.coroutines.delay(750)
                                            forwardRewindFeedback = null
                                        }
                                    } else if (startX > w * 0.65f) {
                                        val currentPos = exoPlayer.currentPosition
                                        val dur = if (totalDurationMs > 0) totalDurationMs else Long.MAX_VALUE
                                        exoPlayer.seekTo((currentPos + 10_000L).coerceAtMost(dur))
                                        forwardRewindFeedback = "⏩ +10s"
                                        coroutineScope.launch {
                                            kotlinx.coroutines.delay(750)
                                            forwardRewindFeedback = null
                                        }
                                    } else {
                                        onToggleFullscreen()
                                    }
                                } else {
                                    lastTapTime = now
                                    lastTapX = startX
                                    singleTapJob?.cancel()
                                    singleTapJob = coroutineScope.launch {
                                        kotlinx.coroutines.delay(240)
                                        if (streamUrl != null && !isLoading) {
                                            val willPlay = !exoPlayer.isPlaying
                                            if (willPlay) {
                                                exoPlayer.play()
                                                isPlayingState = true
                                                areControlsVisible = false
                                            } else {
                                                exoPlayer.pause()
                                                isPlayingState = false
                                                areControlsVisible = true
                                            }
                                        }
                                    }
                                }
                            }

                            if (isDrag) {
                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(1200)
                                    isAdjustingBrightness = false
                                    isAdjustingVolume = false
                                }
                            }
                        }
                    }
                }
        )

        val shouldShowControls = (streamUrl != null && !isLoading) && (areControlsVisible || showSettingsMenu || isDraggingScrubber)

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


        // Modular Paused Action Strip: ⏮️ | 👍 | 👎 | ↗️ Share | ✨ AI | ⬇️ Download | ⏭️
        com.example.ui.components.player.PlayerPauseActionStrip(
            visible = !isPlayingState && !isLoading && shouldShowControls,
            isTablet = isTablet,
            videoId = videoId,
            videoTitle = videoTitle,
            isFavorite = isFavorite,
            isDisliked = isDisliked,
            isDownloaded = isDownloaded,
            downloadProgress = downloadProgress,
            onFavoriteToggle = onFavoriteToggle,
            onDislikeToggle = onDislikeToggle,
            onAiSummaryClick = onAiSummaryClick,
            onDownloadClick = onDownloadClick,
            onDeleteDownloadClick = onDeleteDownloadClick,
            onPreviousVideo = { onPreviousVideo() },
            onNextVideo = { onNextVideo() },
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 12.dp)
        )

        // Modular Bottom Utility Bar (Scrubber + Live Timestamps + Favorites + Folders + Watch Later + Speed Pill + Screenshots + Autoplay + Sleep Timer + CC + Settings + Rotate Screen)
        com.example.ui.components.player.PlayerBottomBar(
            visible = shouldShowControls,
            isTablet = isTablet,
            isLiveStream = exoPlayer.isCurrentMediaItemLive,
            currentPosMs = currentPosMs,
            totalDurationMs = totalDurationMs,
            isDraggingScrubber = isDraggingScrubber,
            dragFraction = dragFraction,
            onScrubberDrag = { fraction ->
                isDraggingScrubber = true
                dragFraction = fraction
                currentPosMs = (fraction * totalDurationMs).toLong()
            },
            onScrubberRelease = { fraction ->
                val targetMs = (fraction * totalDurationMs).toLong()
                exoPlayer.seekTo(targetMs)
                currentPosMs = targetMs
                isDraggingScrubber = false
            },
            isFavorite = isFavorite,
            onFavoriteToggle = { onFavoriteToggle() },
            onSaveToSubject = { onSaveToSubject() },
            isWatchLater = isWatchLater,
            onWatchLaterToggle = { onWatchLaterToggle() },
            selectedSpeed = selectedSpeed,
            onSpeedChange = { newSpeed ->
                selectedSpeed = newSpeed
                exoPlayer.playbackParameters = androidx.media3.common.PlaybackParameters(newSpeed)
            },
            onSpeedFeedback = { feedback ->
                speedFeedbackState = feedback
                coroutineScope.launch {
                    kotlinx.coroutines.delay(750)
                    speedFeedbackState = null
                }
            },
            onTakeScreenshot = { takeScreenshot() },
            onOpenScreenshotFolder = { showScreenshotFolderDialog = true },
            isAutoplayEnabled = isAutoplayEnabled,
            onToggleAutoplay = {
                val next = !isAutoplayEnabled
                isAutoplayEnabled = next
                playerPrefs.edit().putBoolean("autoplay_enabled", next).apply()
                val msg = if (next) "▶️ Autoplay is ON" else "⏸️ Autoplay is OFF"
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            },
            isSleepTimerActive = isSleepTimerActive,
            wasPausedBySleepTimer = wasPausedBySleepTimer,
            onSleepTimerClick = {
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
            captionsEnabled = captionsEnabled,
            onToggleCaptions = {
                val next = !captionsEnabled
                captionsEnabled = next
                val msg = if (next) "Subtitles (CC) Enabled 💬" else "Subtitles (CC) Turned Off"
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            },
            videoId = videoId,
            videoTitle = videoTitle,
            availableQualities = availableQualities,
            selectedQuality = selectedQuality,
            onSelectQuality = { q ->
                selectedQuality = q
                val (maxW, maxH) = when {
                    q.contains("1080") -> 1920 to 1080
                    q.contains("720") -> 1280 to 720
                    q.contains("480") -> 854 to 480
                    q.contains("360") -> 640 to 360
                    else -> Int.MAX_VALUE to Int.MAX_VALUE
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
            },
            onToggleDebugConsole = { onToggleDebugConsole() },
            onBackClick = { onBackClick() },
            onEnterPip = { onEnterPip() },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Tiny Out-of-the-Way Bottom-Right Floating Zoom Reset Button
        if (zoomScale > 1.05f) {
            Surface(
                onClick = {
                    zoomScale = 1f
                    panOffsetX = 0f
                    panOffsetY = 0f
                },
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.75f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = if (shouldShowControls) 72.dp else 20.dp, end = 16.dp)
                    .size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.ZoomOutMap,
                        contentDescription = "Reset Zoom",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
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
                        modifier = Modifier.size(bottomBtnSize)
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
                        modifier = Modifier.size(bottomBtnSize)
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

        // Center Speed Adjustment 2-Finger Swipe Gesture HUD Overlay
        androidx.compose.animation.AnimatedVisibility(
            visible = speedFeedbackState != null,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1E1E1E).copy(alpha = 0.92f),
                border = androidx.compose.foundation.BorderStroke(1.dp, YouTubeRed.copy(alpha = 0.75f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp)
                ) {
                    Icon(
                        Icons.Filled.Speed,
                        contentDescription = null,
                        tint = YouTubeRed,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = speedFeedbackState ?: "",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
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
                                                modifier = Modifier.size(bottomIconSize)
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
