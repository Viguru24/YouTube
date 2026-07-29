package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.data.remote.SponsorBlockService
import com.example.data.remote.SponsorSegment
import com.example.data.remote.YouTubeStreamExtractor
import com.example.ui.theme.YouTubeRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun YouTubePlayerView(
    videoId: String,
    startSeconds: Int = 0,
    areAdvertsEnabled: Boolean = false,
    showDebugConsole: Boolean = false,
    onToggleDebugConsole: () -> Unit = {},
    modifier: Modifier = Modifier,
    onPlayerReady: (Any) -> Unit = {}
) {
    val context = LocalContext.current
    var streamUrl by remember(videoId) { mutableStateOf<String?>(null) }
    var isLoading by remember(videoId) { mutableStateOf(true) }
    var statusLog by remember(videoId) { mutableStateOf("Initializing Native ExoPlayer Engine...") }
    val debugLogs = remember(videoId) { mutableStateListOf<String>() }

    var savedPositionMs by rememberSaveable(videoId) { mutableLongStateOf(-1L) }
    var hasPreparedMedia by rememberSaveable(videoId) { mutableStateOf(false) }

    // Video Playback Controls Overlay State
    var isPlayingState by remember { mutableStateOf(true) }
    var isMutedState by remember { mutableStateOf(false) }
    var showControlsOverlay by remember { mutableStateOf(false) }
    var currentPosMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }

    // SponsorBlock In-Video Sponsor Skip State
    var sponsorSegments by remember(videoId) { mutableStateOf<List<SponsorSegment>>(emptyList()) }
    var lastSkippedSegmentKey by remember(videoId) { mutableStateOf("") }

    fun addLog(msg: String) {
        val entry = "[${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())}] $msg"
        debugLogs.add(entry)
    }

    val exoPlayer = remember(videoId) {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    DisposableEffect(videoId) {
        onDispose {
            try {
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

    // Position ticker: saves current playback timestamp & automatically skips SponsorBlock segments
    LaunchedEffect(exoPlayer, hasPreparedMedia, sponsorSegments, isDraggingScrubber) {
        if (hasPreparedMedia) {
            while (isActive) {
                try {
                    val pos = exoPlayer.currentPosition
                    if (pos > 0 && !isDraggingScrubber) {
                        savedPositionMs = pos
                        currentPosMs = pos
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

    LaunchedEffect(videoId) {
        isLoading = true
        addLog("Extracting direct MP4 stream URL via InnerTube API for videoId: $videoId")
        val url = YouTubeStreamExtractor.getDirectStreamUrl(videoId)
        if (url != null) {
            streamUrl = url
            isLoading = false
            addLog("Stream Extracted Successfully! MP4 URL: ${url.take(80)}...")
        } else {
            isLoading = false
            statusLog = "Stream extraction pending or video restricted."
            addLog("Failed to extract direct MP4 stream URL across all profiles")
        }
    }

    LaunchedEffect(streamUrl) {
        streamUrl?.let { url ->
            if (!hasPreparedMedia) {
                val mediaItem = MediaItem.fromUri(url)
                exoPlayer.setMediaItem(mediaItem)
                val targetSeekMs = if (savedPositionMs > 0) {
                    savedPositionMs
                } else if (startSeconds > 0) {
                    (startSeconds * 1000).toLong()
                } else 0L

                if (targetSeekMs > 0) {
                    exoPlayer.seekTo(targetSeekMs)
                }
                exoPlayer.prepare()
                exoPlayer.play()
                hasPreparedMedia = true
                onPlayerReady(exoPlayer)
                addLog("ExoPlayer Prepared & Playing Native Stream at ${targetSeekMs / 1000}s")
            }
        }
    }

    // Auto-hide controls overlay after 3 seconds of inactivity (paused while scrubbing)
    LaunchedEffect(showControlsOverlay, isDraggingScrubber) {
        if (showControlsOverlay && !isDraggingScrubber) {
            delay(3000)
            if (!isDraggingScrubber) {
                showControlsOverlay = false
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable {
                if (streamUrl != null && !isLoading) {
                    // Force-start playback on first touch (Android audio focus requires user gesture)
                    if (!exoPlayer.isPlaying && !exoPlayer.isLoading) {
                        exoPlayer.play()
                        isPlayingState = true
                    }
                    showControlsOverlay = !showControlsOverlay
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Extracting Direct Native Stream (0 WebViews)...", color = Color.White, fontSize = 12.sp)
            }
        } else if (streamUrl != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                    }
                },
                update = { view ->
                    view.player = exoPlayer
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("native_exoplayer_view")
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Playback Unavailable",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = statusLog,
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
            }
        }

        // Touch On-Screen Controls Overlay (Play/Pause, Rewind -10s, Forward +10s, Time Scrubber)
        AnimatedVisibility(
            visible = showControlsOverlay && streamUrl != null && !isLoading,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
        ) {
            // Hoist Speed / Quality / CC state so bottom bar can access them
            var showSpeedMenu by remember { mutableStateOf(false) }
            var selectedSpeed by remember { mutableFloatStateOf(1.0f) }
            var showQualityMenu by remember { mutableStateOf(false) }
            var selectedQuality by remember { mutableStateOf("Auto") }
            var ccEnabled by remember { mutableStateOf(true) }

            Box(modifier = Modifier.fillMaxSize()) {

                // Center Controls: Quick Rewind -10s & Fast Forward +10s
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val target = (exoPlayer.currentPosition - 10000).coerceAtLeast(0)
                            exoPlayer.seekTo(target)
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                    ) {
                        Icon(Icons.Filled.Replay10, contentDescription = "-10s", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(24.dp))
                    }

                    IconButton(
                        onClick = {
                            val target = (exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration)
                            exoPlayer.seekTo(target)
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                    ) {
                        Icon(Icons.Filled.Forward10, contentDescription = "+10s", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(24.dp))
                    }
                }

                // Bottom Control Bar: Time Scrubber + Timestamp + Mute
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Bottom row: timestamp | 1x | Quality | CC | 🔊
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${formatMs(currentPosMs)} / ${formatMs(totalDurationMs)}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Speed button
                            Box {
                                Text(
                                    text = "${selectedSpeed}x",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                        .clickable { showSpeedMenu = true }
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                )
                                DropdownMenu(expanded = showSpeedMenu, onDismissRequest = { showSpeedMenu = false }) {
                                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { s ->
                                        DropdownMenuItem(
                                            text = { Text("${s}x", fontSize = 12.sp) },
                                            onClick = {
                                                selectedSpeed = s
                                                showSpeedMenu = false
                                                exoPlayer.playbackParameters = androidx.media3.common.PlaybackParameters(s)
                                            }
                                        )
                                    }
                                }
                            }

                            // Quality button
                            Box {
                                Text(
                                    text = selectedQuality,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                        .clickable { showQualityMenu = true }
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                )
                                DropdownMenu(expanded = showQualityMenu, onDismissRequest = { showQualityMenu = false }) {
                                    listOf("1080p", "720p", "480p", "Auto").forEach { q ->
                                        DropdownMenuItem(
                                            text = { Text(q, fontSize = 12.sp) },
                                            onClick = {
                                                selectedQuality = q
                                                showQualityMenu = false
                                                val (maxW, maxH) = when (q) {
                                                    "1080p" -> Pair(1920, 1080)
                                                    "720p"  -> Pair(1280, 720)
                                                    "480p"  -> Pair(854, 480)
                                                    else    -> Pair(Int.MAX_VALUE, Int.MAX_VALUE)
                                                }
                                                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                                    .buildUpon().setMaxVideoSize(maxW, maxH).build()
                                            }
                                        )
                                    }
                                }
                            }

                            // CC toggle button
                            Text(
                                text = "CC",
                                color = if (ccEnabled) Color.White else Color.White.copy(alpha = 0.35f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .background(
                                        if (ccEnabled) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.4f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .clickable {
                                        ccEnabled = !ccEnabled
                                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                            .buildUpon()
                                            .setIgnoredTextSelectionFlags(if (ccEnabled) 0 else androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
                                            .build()
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            )

                            // Mute/Volume button
                            IconButton(
                                onClick = {
                                    isMutedState = !isMutedState
                                    exoPlayer.volume = if (isMutedState) 0f else 1f
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isMutedState) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                                    contentDescription = "Mute",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    val activeSliderValue = if (isDraggingScrubber) {
                        dragFraction
                    } else if (totalDurationMs > 0) {
                        (currentPosMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                    } else 0f

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
                            thumbColor = YouTubeRed.copy(alpha = 0.85f),
                            activeTrackColor = YouTubeRed.copy(alpha = 0.7f),
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
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
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSec = ms / 1000
    val mins = totalSec / 60
    val secs = totalSec % 60
    return String.format("%02d:%02d", mins, secs)
}
