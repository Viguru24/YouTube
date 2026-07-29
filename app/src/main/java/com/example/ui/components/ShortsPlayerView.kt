package com.example.ui.components

import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarBorder
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

@Composable
fun ShortsPlayerView(
    videoId: String,
    videoTitle: String,
    channelName: String,
    isFavorite: Boolean = false,
    isWatchLater: Boolean = false,
    onBackClick: () -> Unit,
    onNextShort: () -> Unit,
    onPreviousShort: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onWatchLaterToggle: () -> Unit,
    onPositionUpdate: (seconds: Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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

    val exoPlayer = remember(context) {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            setAudioAttributes(audioAttributes, true)
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_NETWORK)
            repeatMode = ExoPlayer.REPEAT_MODE_ONE // Shorts loop automatically
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            try {
                val finalSec = (exoPlayer.currentPosition / 1000).toInt()
                if (finalSec > 0) {
                    onPositionUpdate(finalSec)
                }
            } catch (e: Exception) { }
            exoPlayer.release()
            MediaPlaybackService.stop(context)
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

    // Extract direct MP4 stream for vertical full-screen playback
    LaunchedEffect(videoId) {
        isLoading = true
        streamUrl = null
        isPlayingState = true
        logs.clear()
        addLog("Extracting stream for videoId=$videoId")

        try {
            val url = YouTubeStreamExtractor.getDirectStreamUrl(videoId)
            if (!url.isNullOrEmpty()) {
                streamUrl = url
                val mediaItem = MediaItem.fromUri(url)
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.play()
                isLoading = false
            } else {
                addLog("⚠️ Native extraction FAILED → falling back to WebView embed")
                isLoading = false
            }
        } catch (e: Exception) {
            addLog("❌ Exception: ${e.javaClass.simpleName}: ${e.message}")
            isLoading = false
        }
    }


    var totalDragAmount by remember(videoId) { mutableFloatStateOf(0f) }
    var hasTriggered by remember(videoId) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) {
                // Tap anywhere to toggle play/pause silently (0 pop-up buttons!)
                isPlayingState = !isPlayingState
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
                            if (totalDragAmount < -25f) {
                                hasTriggered = true
                                onNextShort()
                            } else if (totalDragAmount > 25f) {
                                hasTriggered = true
                                onPreviousShort()
                            }
                        }
                    }
                )
            }
    ) {
        // 1. Full-Screen Vertical Player Surface (ExoPlayer Native or WebView Fallback)
        if (streamUrl != null || !isLoading) {
            if (streamUrl != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            keepScreenOn = true
                            setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT // Fit full vertical frame without cropping
                        }
                    },
                    update = { view ->
                        if (view.player != exoPlayer) {
                            view.player = exoPlayer
                        }
                        view.onResume()
                        if (isPlayingState) exoPlayer.playWhenReady = true
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.allowFileAccess = true
                            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            webChromeClient = WebChromeClient()
                            webViewClient = WebViewClient()
                            val html = """
                                <!DOCTYPE html>
                                <html>
                                <head>
                                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                                    <style>
                                        body, html { margin:0; padding:0; width:100%; height:100%; background-color:#000; overflow:hidden; }
                                        iframe { width:100%; height:100%; border:0; }
                                    </style>
                                </head>
                                <body>
                                    <iframe id="player" src="https://www.youtube.com/embed/$videoId?autoplay=1&controls=0&rel=0&loop=1&playlist=$videoId" allow="autoplay; encrypted-media; picture-in-picture" allowfullscreen></iframe>
                                </body>
                                </html>
                            """.trimIndent()
                            loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "utf-8", null)
                        }
                    },
                    update = { view -> view.onResume() },
                    modifier = Modifier.fillMaxSize()
                )

            }
        }

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

        // 3. Top Controls Row: Back arrow + Debug toggle
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
            // Debug console toggle button
            IconButton(
                onClick = { showDebugConsole = !showDebugConsole },
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            ) {
                Icon(
                    imageVector = if (showDebugConsole) Icons.Filled.BugReport else Icons.Filled.BugReport,
                    contentDescription = "Debug",
                    tint = if (showDebugConsole) YouTubeRed else Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
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

        // 4. Bottom-Left Details Text & Interactive Timeline Scrubber
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.78f)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 6.dp, start = 14.dp, end = 10.dp)
        ) {
            Text(
                text = channelName,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = videoTitle,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Interactive Timeline Scrubber displaying Elapsed & Remaining Time
            val activePosMs = if (isScrubbing) scrubPositionMs.toLong() else currentPositionMs
            val durMs = if (totalDurationMs > 0) totalDurationMs else 1L
            val elapsedSec = (activePosMs / 1000).toInt()
            val remainingSec = ((totalDurationMs - activePosMs).coerceAtLeast(0L) / 1000).toInt()

            val elapsedStr = String.format("%d:%02d", elapsedSec / 60, elapsedSec % 60)
            val remainingStr = String.format("-%d:%02d", remainingSec / 60, remainingSec % 60)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = elapsedStr,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Slider(
                    value = activePosMs.toFloat().coerceIn(0f, durMs.toFloat()),
                    onValueChange = { newValue ->
                        isScrubbing = true
                        scrubPositionMs = newValue
                    },
                    onValueChangeFinished = {
                        isScrubbing = false
                        exoPlayer.seekTo(scrubPositionMs.toLong())
                    },
                    valueRange = 0f..durMs.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = YouTubeRed,
                        activeTrackColor = YouTubeRed,
                        inactiveTrackColor = Color.White.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(20.dp)
                        .padding(horizontal = 4.dp)
                )

                Text(
                    text = remainingStr,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }

        // 5. Right-Side Action Controls: Resolution Selector & Watch Later
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 12.dp, end = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Quality / Resolution Selector Button (Replaces Star Button!)
            Surface(
                onClick = { showQualityDialog = true },
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.45f),
                modifier = Modifier.size(42.dp)
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

            // Watch Later Toggle (With Clear "Later" Label)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onWatchLaterToggle,
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isWatchLater) Icons.Filled.WatchLater else Icons.Outlined.WatchLater,
                        contentDescription = "Watch Later",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text("Later", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Medium)
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
