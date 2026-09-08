package com.example.ui.components

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.remote.SponsorBlockService
import com.example.data.remote.SponsorSegment
import com.example.data.remote.StreamExtractionResult
import com.example.data.remote.VideoDownloadManager
import com.example.data.remote.YouTubeCaptionService
import com.example.data.remote.YouTubeStreamExtractor
import com.example.ui.components.player.*
import com.example.ui.theme.YouTubeRed
import com.example.util.ScreenshotManager
import com.example.util.TranscriptSegment
import com.example.util.YouTubeUtils
import com.example.util.findActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun YouTubePlayerView(
    videoId: String,
    startSeconds: Int = 0,
    areAdvertsEnabled: Boolean = false,
    showDebugConsole: Boolean = false,
    onToggleDebugConsole: () -> Unit = {},
    playerCommandFlow: SharedFlow<String>? = null,
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
    localFilePath: String = "",
    onDownloadClick: () -> Unit = {},
    onDeleteDownloadClick: () -> Unit = {},
    videoTitle: String = "Video",
    isFullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
    onEnterPip: () -> Unit = {},
    isInPipMode: Boolean = false,
    onRotate180: () -> Unit = {},
    onPositionUpdate: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    onPlayerReady: (Any) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var streamUrl by remember(videoId) { mutableStateOf<String?>(null) }
    var isPlayingLocalOffline by remember(videoId) { mutableStateOf(false) }
    var isLoading by remember(videoId) { mutableStateOf(true) }
    var isBuffering by remember(videoId) { mutableStateOf(false) }
    var isSwitchingQuality by remember(videoId) { mutableStateOf<String?>(null) }
    var isFirstFrameRendered by remember(videoId) { mutableStateOf(false) }
    var useWebPlayerFallback by remember(videoId) { mutableStateOf(false) }
    var webViewRef by remember(videoId) { mutableStateOf<android.webkit.WebView?>(null) }
    var statusLog by remember(videoId) { mutableStateOf("Initializing Native ExoPlayer Engine...") }
    val debugLogs = remember(videoId) { mutableStateListOf<String>() }

    // Pinch-to-Zoom & Pan State (1.0x to 5.0x zoom with smooth translation)
    var zoomScale by remember(videoId) { mutableFloatStateOf(1f) }
    var panOffsetX by remember(videoId) { mutableFloatStateOf(0f) }
    var panOffsetY by remember(videoId) { mutableFloatStateOf(0f) }

    var savedPositionMs by rememberSaveable(videoId) { mutableLongStateOf(-1L) }
    var hasPreparedMedia by rememberSaveable(videoId) { mutableStateOf(false) }

    // Stream extraction & dynamic quality state
    var streamResult by remember(videoId) { mutableStateOf<StreamExtractionResult?>(null) }
    var availableQualities by remember(videoId) { mutableStateOf<List<String>>(emptyList()) }
    var selectedQuality by remember(videoId) { mutableStateOf("Auto") }

    // Video Playback State
    var isPlayingState by remember { mutableStateOf(true) }
    var isMutedState by remember { mutableStateOf(false) }
    var currentPosMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }

    // SponsorBlock In-Video Sponsor Skip State
    var sponsorSegments by remember(videoId) { mutableStateOf<List<SponsorSegment>>(emptyList()) }
    val skippedSegmentIds = remember(videoId) { mutableSetOf<String>() }

    // Real-Time Closed Captions (CC) State
    var captionsEnabled by remember { mutableStateOf(false) }
    var captionSegments by remember(videoId) { mutableStateOf<List<TranscriptSegment>>(emptyList()) }
    var activeCaptionText by remember { mutableStateOf<String?>(null) }
    var isCaptionsLoading by remember { mutableStateOf(false) }

    // Gestures: Brightness (Left) & Volume (Right)
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager }
    val maxAudioVolume = remember(audioManager) { audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1) }

    var gestureBrightness by remember { mutableFloatStateOf(0.5f) }
    var isAdjustingBrightness by remember { mutableStateOf(false) }
    var gestureVolumeFraction by remember { mutableFloatStateOf(0.5f) }
    var isAdjustingVolume by remember { mutableStateOf(false) }

    var localIsFavorite by remember(videoId, isFavorite) { mutableStateOf(isFavorite) }
    var localIsDisliked by remember(videoId, isDisliked) { mutableStateOf(isDisliked) }

    // Autoplay Next Video State (persisted across sessions)
    val playerPrefs = remember(context) { context.getSharedPreferences("vixz_player_prefs", Context.MODE_PRIVATE) }
    var isAutoplayEnabled by remember { mutableStateOf(playerPrefs.getBoolean("autoplay_enabled", true)) }

    // Sleep Timer State
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var isSleepTimerActive by remember { mutableStateOf(false) }
    var sleepTimerMinutes by remember { mutableIntStateOf(30) }
    var lastSleepDurationMinutes by remember { mutableIntStateOf(30) }
    var sleepTimerRemainingSec by remember { mutableIntStateOf(0) }
    var sleepTimerEndOfVideo by remember { mutableStateOf(false) }
    var wasPausedBySleepTimer by remember { mutableStateOf(false) }

    // Screenshot & Custom Folder State
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var activeScreenshotFolder by remember { mutableStateOf(ScreenshotManager.getActiveFolder(context)) }
    var showScreenshotFolderDialog by remember { mutableStateOf(false) }

    // Transient HUD Feedback State
    var forwardRewindFeedback by remember { mutableStateOf<String?>(null) }
    var playPauseFeedbackState by remember { mutableStateOf<Boolean?>(null) }
    var speedFeedbackState by remember { mutableStateOf<String?>(null) }

    // Controls visibility & Scrubber dragging
    var areControlsVisible by remember { mutableStateOf(true) }
    var isDraggingScrubber by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var selectedSpeed by remember { mutableFloatStateOf(1.0f) }

    fun addLog(msg: String) {
        val entry = "[${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())}] $msg"
        debugLogs.add(entry)
    }

    fun takeScreenshot() {
        coroutineScope.launch {
            val bmp = ScreenshotManager.capturePlayerFrame(playerViewRef, activity)
            delay(100)
            if (bmp != null) {
                val (uri, _) = ScreenshotManager.saveScreenshot(
                    context = context,
                    bitmap = bmp,
                    videoTitle = videoTitle,
                    timestampMs = currentPosMs,
                    targetFolder = activeScreenshotFolder
                )
                if (uri != null) {
                    val folderDisplay = if (activeScreenshotFolder.equals("Default", ignoreCase = true)) "Pictures/Vixz" else "Pictures/Vixz/$activeScreenshotFolder"
                    Toast.makeText(context, "📸 Screenshot saved to $folderDisplay", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "⚠️ Failed to save screenshot", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "⚠️ Could not capture video frame", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Keep screen on during playback
    DisposableEffect(Unit) {
        val act = (context as? android.app.Activity)
        act?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            act?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Initialize ExoPlayer with optimized buffer parameters
    val exoPlayer = remember(videoId) {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 20_000,
                /* maxBufferMs = */ 60_000,
                /* bufferForPlaybackMs = */ 1_500,
                /* bufferForPlaybackAfterRebufferMs = */ 2_500
            )
            .setTargetBufferBytes(C.LENGTH_UNSET)
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

    // ExoPlayer Listener for playback events, buffering, and auto-resume
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                isFirstFrameRendered = true
            }

            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> {
                        isFirstFrameRendered = true
                        isBuffering = false
                        isSwitchingQuality = null
                    }
                    Player.STATE_BUFFERING -> {
                        if (isFirstFrameRendered) {
                            isBuffering = true
                        }
                    }
                    Player.STATE_ENDED -> {
                        isBuffering = false
                        isSwitchingQuality = null
                        playerPrefs.edit().putInt("resume_pos_sec_${videoId}", 0).apply()
                        onPositionUpdate(0)
                        if (isAutoplayEnabled) {
                            onNextVideo()
                            Toast.makeText(context, "Autoplay: Playing Next Video ⏭️", Toast.LENGTH_SHORT).show()
                        }
                    }
                    Player.STATE_IDLE -> {
                        isBuffering = false
                    }
                }
            }

            override fun onIsLoadingChanged(isLoadingNow: Boolean) {
                if (isFirstFrameRendered && !isLoading) {
                    isBuffering = isLoadingNow && (exoPlayer.playbackState == Player.STATE_BUFFERING)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isPlayingState = isPlaying
                onPlayingStateChanged(isPlaying)
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                addLog("⚠️ ExoPlayer Playback Error (${error.errorCodeName}): ${error.message}")
                val isOfflineSource = isPlayingLocalOffline || isDownloaded || (streamUrl?.startsWith("file://") == true)
                if (isOfflineSource) {
                    addLog("⚠️ Offline local playback error. Staying offline.")
                } else {
                    addLog("-> Activating Web Player Fallback")
                    useWebPlayerFallback = true
                }
                isLoading = false
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            try {
                exoPlayer.removeListener(listener)
                val pos = exoPlayer.currentPosition
                savedPositionMs = pos
                val sec = (pos / 1000).toInt().coerceAtLeast(0)
                playerPrefs.edit().putInt("resume_pos_sec_${videoId}", sec).apply()
                onPositionUpdate(sec)
            } catch (e: Exception) { }
            exoPlayer.release()
        }
    }

    // Remote PiP and external commands (Play/Pause, Seek)
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

    // SponsorBlock fetch
    LaunchedEffect(videoId) {
        val segments = SponsorBlockService.getSponsorSegments(videoId)
        if (segments.isNotEmpty()) {
            sponsorSegments = segments
            addLog("SponsorBlock: Loaded ${segments.size} in-video sponsor skip segment(s) ⏭️")
        }
    }

    // Auto-hide bottom utility controls: when actively playing, auto-hide after 3.5s
    LaunchedEffect(areControlsVisible, isPlayingState, isDraggingScrubber) {
        if (areControlsVisible && isPlayingState && !isDraggingScrubber) {
            delay(3500L)
            areControlsVisible = false
        }
    }

    // Position ticker: updates playback position, checks SponsorBlock, and matches CC lines
    LaunchedEffect(exoPlayer, hasPreparedMedia, sponsorSegments, isDraggingScrubber, captionsEnabled, captionSegments) {
        if (hasPreparedMedia) {
            var lastSavedSec = -1
            while (isActive) {
                try {
                    val pos = exoPlayer.currentPosition
                    if (pos > 0 && !isDraggingScrubber) {
                        savedPositionMs = pos
                        currentPosMs = pos

                        val currentSec = (pos / 1000).toInt()
                        if (currentSec > 0 && (currentSec - lastSavedSec >= 5 || lastSavedSec == -1)) {
                            lastSavedSec = currentSec
                            playerPrefs.edit().putInt("resume_pos_sec_${videoId}", currentSec).apply()
                            onPositionUpdate(currentSec)
                        }

                        // Real-time Closed Captions (CC) Matcher
                        if (captionsEnabled && captionSegments.isNotEmpty()) {
                            val matching = captionSegments
                                .filter { it.timestampSeconds <= currentSec }
                                .lastOrNull { (currentSec - it.timestampSeconds) <= 5 }
                            activeCaptionText = matching?.text?.trim()
                        } else {
                            activeCaptionText = null
                        }

                        // Automatic SponsorBlock In-Video Segment Skip
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
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    val dur = exoPlayer.duration
                    if (dur > 0) {
                        totalDurationMs = dur
                    }
                    isPlayingState = exoPlayer.isPlaying
                } catch (e: Exception) { }
                delay(100)
            }
        }
    }

    // Sleep Timer Countdown Engine
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
                        Toast.makeText(context, "🌙 Sleep Timer: End of video reached. Tap 🌙 to resume.", Toast.LENGTH_LONG).show()
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
                        Toast.makeText(context, "🌙 Sleep Timer finished. Tap 🌙 to resume for ${lastSleepDurationMinutes}m.", Toast.LENGTH_LONG).show()
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
                val segments = YouTubeCaptionService.fetchTimedCaptions(videoId)
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

    // Main Stream Extraction
    LaunchedEffect(videoId) {
        isLoading = true
        isFirstFrameRendered = false
        useWebPlayerFallback = false
        hasPreparedMedia = false
        isPlayingLocalOffline = false
        streamUrl = null
        zoomScale = 1f
        panOffsetX = 0f
        panOffsetY = 0f

        // 1. Check if video is downloaded locally
        val localUri = VideoDownloadManager.getLocalVideoUriString(
            context = context,
            youtubeId = videoId,
            knownPath = localFilePath.ifBlank { null }
        )
        if (!localUri.isNullOrBlank()) {
            isPlayingLocalOffline = true
            streamUrl = localUri
            availableQualities = listOf("Offline Ready")
            selectedQuality = "Offline Ready"
            isLoading = false
            addLog("⚡ Playing from Local Offline Storage - Offline Ready!")
            return@LaunchedEffect
        }

        // 2. Otherwise extract online stream & all available resolutions
        addLog("Extracting direct stream URL & available qualities for videoId: $videoId")
        val result = kotlinx.coroutines.withTimeoutOrNull(15000L) {
            YouTubeStreamExtractor.extractVideoStreams(videoId)
        }
        if (result != null && result.isMembersOnly) {
            streamResult = result
            isLoading = false
            useWebPlayerFallback = false
            statusLog = "🔒 Members-Only Video: Channel membership required."
            addLog("🔒 Members-Only Video: ${result.errorMessage}")
            return@LaunchedEffect
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
            addLog("Direct stream timeout -> Activating Web Player Fallback")
        }
    }

    // Seamless in-place switch to local offline file if download completes while watching
    LaunchedEffect(localFilePath) {
        if (localFilePath.isNotBlank() && !isPlayingLocalOffline) {
            val localUri = VideoDownloadManager.getLocalVideoUriString(
                context = context,
                youtubeId = videoId,
                knownPath = localFilePath
            )
            if (!localUri.isNullOrBlank()) {
                val currentPos = exoPlayer.currentPosition
                savedPositionMs = currentPos
                isPlayingLocalOffline = true
                streamUrl = localUri
                availableQualities = listOf("Offline Ready")
                selectedQuality = "Offline Ready"
                addLog("⚡ Seamless switch to downloaded offline file at ${currentPos / 1000}s")
            }
        }
    }

    // ExoPlayer MediaSource Preparation
    LaunchedEffect(streamUrl) {
        streamUrl?.let { url ->
            val isLocalFile = url.startsWith("file://") || url.startsWith("/") || url.startsWith("content://")

            if (isLocalFile) {
                val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context)
                val mediaSource = androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(url))
                exoPlayer.setMediaSource(mediaSource)
            } else {
                val audioUrl = streamResult?.audioStreamUrl
                val isVideoOnly = streamResult?.isVideoOnlyStream(url, selectedQuality) == true ||
                        (!audioUrl.isNullOrBlank() && url != streamResult?.combinedMuxedUrl && !url.contains(".m3u8"))

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
            }

            val cachedResumeSec = playerPrefs.getInt("resume_pos_sec_${videoId}", -1)
            val effectiveStartSec = if (startSeconds > 0) {
                startSeconds
            } else if (cachedResumeSec > 0) {
                cachedResumeSec
            } else {
                0
            }
            val targetSeekMs = if (savedPositionMs > 0) {
                savedPositionMs
            } else if (effectiveStartSec > 0) {
                (effectiveStartSec * 1000).toLong()
            } else 0L

            if (targetSeekMs > 0) {
                exoPlayer.seekTo(targetSeekMs)
            }
            exoPlayer.volume = if (isMutedState) 0f else 1.0f
            exoPlayer.prepare()
            exoPlayer.play()
            hasPreparedMedia = true
            onPlayerReady(exoPlayer)
            addLog("ExoPlayer Prepared & Playing (isLocal=$isLocalFile) at ${targetSeekMs / 1000}s")
        }
    }

    // Safety timeout for quality switching indicator
    LaunchedEffect(isSwitchingQuality) {
        if (isSwitchingQuality != null) {
            delay(5000L)
            isSwitchingQuality = null
        }
    }

    val shouldShowControls = !isInPipMode && (streamUrl != null && !useWebPlayerFallback && !isLoading) && areControlsVisible

    // Main Player Composables Tree
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clipToBounds()
            .playerGestureEngine(
                context = context,
                videoId = videoId,
                exoPlayer = exoPlayer,
                streamUrl = streamUrl,
                useWebPlayerFallback = useWebPlayerFallback,
                webViewRef = webViewRef,
                totalDurationMs = totalDurationMs,
                zoomScale = zoomScale,
                panOffsetX = panOffsetX,
                panOffsetY = panOffsetY,
                onZoomChange = { scale, x, y ->
                    zoomScale = scale
                    panOffsetX = x
                    panOffsetY = y
                },
                onToggleFullscreen = onToggleFullscreen,
                isPlayingState = isPlayingState,
                onPlayingStateChange = { isPlayingState = it },
                areControlsVisible = areControlsVisible,
                onControlsVisibilityChange = { areControlsVisible = it },
                onPlayPauseFeedback = { playPauseFeedbackState = it },
                onSeekFeedback = { forwardRewindFeedback = it },
                onAdjustingBrightness = { isAdjustingBrightness = it },
                onBrightnessChange = { gestureBrightness = it },
                onAdjustingVolume = { isAdjustingVolume = it },
                onVolumeFractionChange = { gestureVolumeFraction = it },
                activity = activity,
                audioManager = audioManager,
                maxAudioVolume = maxAudioVolume,
                coroutineScope = coroutineScope
            )
    ) {
        // 1. Video Surface Layer (ExoPlayer texture or fallback WebView)
        if (streamUrl != null && !useWebPlayerFallback) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = exoPlayer
                        useController = false
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        playerViewRef = this
                    }
                },
                update = { pv ->
                    pv.player = exoPlayer
                    playerViewRef = pv
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = zoomScale,
                        scaleY = zoomScale,
                        translationX = panOffsetX,
                        translationY = panOffsetY
                    )
            )
        } else if (useWebPlayerFallback || (streamUrl == null && !isLoading)) {
            AndroidView(
                factory = { ctx ->
                    android.webkit.WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        webViewClient = android.webkit.WebViewClient()
                        loadUrl("https://www.youtube-nocookie.com/embed/$videoId?autoplay=1&playsinline=1")
                        webViewRef = this
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = zoomScale,
                        scaleY = zoomScale,
                        translationX = panOffsetX,
                        translationY = panOffsetY
                    )
                    .testTag("fallback_webview_player")
            )
        }

        // 2. Initial Preview Thumbnail Poster (prevents initial black flash)
        if ((isLoading || (!isFirstFrameRendered && !useWebPlayerFallback && streamUrl != null)) && streamResult?.isMembersOnly != true) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = YouTubeUtils.getThumbnailUrl(videoId),
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

        // 3. Mid-Playback Buffering & Quality Switching Glassmorphic HUD
        PlayerBufferingOverlay(
            isBuffering = isBuffering,
            isFirstFrameRendered = isFirstFrameRendered,
            isLoading = isLoading,
            useWebPlayerFallback = useWebPlayerFallback,
            isSwitchingQuality = isSwitchingQuality
        )

        // 4. Top-Right Sleep Timer Countdown Badge
        PlayerSleepTimerBadge(
            isActive = isSleepTimerActive,
            isInPipMode = isInPipMode,
            endOfVideo = sleepTimerEndOfVideo,
            remainingSec = sleepTimerRemainingSec,
            onClick = { showSleepTimerDialog = true },
            modifier = Modifier.align(Alignment.TopEnd)
        )

        // 5. Fullscreen Top Header Bar (Back button + Video title)
        PlayerTopHeader(
            isFullscreen = isFullscreen,
            shouldShowControls = shouldShowControls,
            videoTitle = videoTitle,
            onToggleFullscreen = onToggleFullscreen,
            modifier = Modifier.align(Alignment.TopStart)
        )

        // 6. Real-Time Closed Captions (CC) Subtitle Overlay
        PlayerCaptionsOverlay(
            captionsEnabled = captionsEnabled,
            activeCaptionText = activeCaptionText,
            isInPipMode = isInPipMode,
            shouldShowControls = shouldShowControls,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // 7. Center Play / Pause Animated Bubble
        PlayerPlayPauseBubble(
            state = playPauseFeedbackState,
            modifier = Modifier.align(Alignment.Center)
        )

        // 8. White Options Pill when Paused: 👍 | 👎 | ↗️ | ✨ | ⬇️
        PlayerPauseActionStrip(
            visible = !isPlayingState && !isInPipMode,
            isFullscreen = isFullscreen,
            context = context,
            videoId = videoId,
            videoTitle = videoTitle,
            isFavorite = localIsFavorite,
            isDisliked = localIsDisliked,
            isDownloaded = isDownloaded,
            downloadProgress = downloadProgress,
            onFavoriteToggle = {
                localIsFavorite = !localIsFavorite
                if (localIsFavorite) localIsDisliked = false
                onFavoriteToggle()
            },
            onDislikeToggle = {
                localIsDisliked = true
                localIsFavorite = false
                onDislikeToggle()
                onNextVideo()
            },
            onAiSummaryClick = onAiSummaryClick,
            onDownloadClick = onDownloadClick,
            onDeleteDownloadClick = onDeleteDownloadClick,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = if (isFullscreen) 0.dp else (-34).dp)
        )

        // 9. Bottom Utility Bar: Scrubber + Time + Action Buttons + Settings Anchor
        PlayerBottomBar(
            shouldShowControls = shouldShowControls,
            exoPlayer = exoPlayer,
            context = context,
            videoId = videoId,
            videoTitle = videoTitle,
            totalDurationMs = totalDurationMs,
            currentPosMs = currentPosMs,
            isDraggingScrubber = isDraggingScrubber,
            dragFraction = dragFraction,
            onScrubberDragChange = { fraction ->
                isDraggingScrubber = true
                dragFraction = fraction
                currentPosMs = (fraction * totalDurationMs).toLong()
            },
            onScrubberDragFinished = { targetMs ->
                exoPlayer.seekTo(targetMs)
                isDraggingScrubber = false
                val sec = (targetMs / 1000).toInt()
                if (sec >= 0) {
                    playerPrefs.edit().putInt("resume_pos_sec_${videoId}", sec).apply()
                    onPositionUpdate(sec)
                }
            },
            isPlayingState = isPlayingState,
            onPlayPauseClick = {
                if (exoPlayer.isPlaying) {
                    exoPlayer.pause()
                    isPlayingState = false
                    playPauseFeedbackState = false
                } else {
                    exoPlayer.play()
                    isPlayingState = true
                    playPauseFeedbackState = true
                }
                coroutineScope.launch {
                    delay(650)
                    playPauseFeedbackState = null
                }
            },
            isFavorite = isFavorite,
            onFavoriteToggle = onFavoriteToggle,
            onSaveToSubject = onSaveToSubject,
            isWatchLater = isWatchLater,
            onWatchLaterToggle = onWatchLaterToggle,
            selectedSpeed = selectedSpeed,
            onSpeedChange = { s ->
                selectedSpeed = s
                exoPlayer.playbackParameters = PlaybackParameters(s)
            },
            onSpeedFeedback = { speedFeedbackState = it },
            onTakeScreenshot = { takeScreenshot() },
            onOpenScreenshotFolder = { showScreenshotFolderDialog = true },
            isAutoplayEnabled = isAutoplayEnabled,
            onToggleAutoplay = {
                val next = !isAutoplayEnabled
                isAutoplayEnabled = next
                playerPrefs.edit().putBoolean("autoplay_enabled", next).apply()
                val msg = if (next) "▶️ Autoplay is ON" else "⏸️ Autoplay is OFF"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(context, "🌙 Resumed for ${lastSleepDurationMinutes}m", Toast.LENGTH_SHORT).show()
                } else {
                    showSleepTimerDialog = !showSleepTimerDialog
                }
            },
            captionsEnabled = captionsEnabled,
            onToggleCaptions = {
                val next = !captionsEnabled
                captionsEnabled = next
                val msg = if (next) "Subtitles (CC) Enabled 💬" else "Subtitles (CC) Turned Off"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            },
            streamResult = streamResult,
            availableQualities = availableQualities,
            selectedQuality = selectedQuality,
            onQualitySelected = { selectedQuality = it },
            onEnterPip = onEnterPip,
            onRotate180 = onRotate180,
            onToggleDebugConsole = onToggleDebugConsole,
            onSwitchStreamUrl = { targetUrl, quality ->
                val currentPos = exoPlayer.currentPosition
                savedPositionMs = currentPos
                isSwitchingQuality = quality
                streamUrl = targetUrl
            },
            isFullscreen = isFullscreen,
            onToggleFullscreen = onToggleFullscreen,
            coroutineScope = coroutineScope,
            modifier = Modifier.align(Alignment.BottomCenter)
        )


        // 12. Left Side Brightness HUD
        PlayerBrightnessHUD(
            isAdjustingBrightness = isAdjustingBrightness,
            brightnessFraction = gestureBrightness,
            modifier = Modifier.align(Alignment.CenterStart)
        )

        // 13. Right Side Volume HUD
        PlayerVolumeHUD(
            isAdjustingVolume = isAdjustingVolume,
            volumeFraction = gestureVolumeFraction,
            modifier = Modifier.align(Alignment.CenterEnd)
        )

        // 13. Center Seek / Scrub Feedback Pill (or Speed feedback)
        PlayerSeekFeedbackHUD(
            feedback = forwardRewindFeedback ?: speedFeedbackState,
            modifier = Modifier.align(Alignment.Center)
        )

        // 14. Native Extractor Debug Console Overlay
        PlayerDebugConsole(
            visible = showDebugConsole,
            debugLogs = debugLogs,
            onClose = onToggleDebugConsole,
            context = context
        )

        // 15. Screenshot Folder Chooser Dialog
        PlayerScreenshotFolderDialog(
            visible = showScreenshotFolderDialog,
            onDismissRequest = { showScreenshotFolderDialog = false },
            context = context,
            activeFolder = activeScreenshotFolder,
            onFolderSelected = { activeScreenshotFolder = it }
        )

        // 16. Sleep Timer Setup Dialog
        PlayerSleepTimerDialog(
            visible = showSleepTimerDialog,
            onDismissRequest = { showSleepTimerDialog = false },
            context = context,
            sleepTimerMinutes = sleepTimerMinutes,
            sleepTimerEndOfVideo = sleepTimerEndOfVideo,
            isSleepTimerActive = isSleepTimerActive,
            onStartTimer = { mins, endOfVideo ->
                sleepTimerMinutes = mins
                lastSleepDurationMinutes = mins
                sleepTimerEndOfVideo = endOfVideo
                if (endOfVideo) {
                    isSleepTimerActive = true
                } else {
                    sleepTimerRemainingSec = mins * 60
                    isSleepTimerActive = true
                }
                showSleepTimerDialog = false
            },
            onTurnOffTimer = {
                isSleepTimerActive = false
                sleepTimerRemainingSec = 0
                sleepTimerEndOfVideo = false
                showSleepTimerDialog = false
            }
        )
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSec = ms / 1000
    val mins = totalSec / 60
    val secs = totalSec % 60
    return String.format("%02d:%02d", mins, secs)
}
