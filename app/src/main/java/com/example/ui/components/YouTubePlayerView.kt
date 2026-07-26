package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
    modifier: Modifier = Modifier,
    onPlayerReady: (Any) -> Unit = {}
) {
    val context = LocalContext.current
    var streamUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var statusLog by remember { mutableStateOf("Initializing 100% Native ExoPlayer Stream Engine...") }
    val logs = remember { mutableStateListOf<String>() }

    var showDebugConsole by remember { mutableStateOf(false) }

    // Touch overlay state
    var showControlsOverlay by remember { mutableStateOf(false) }
    var isPlayingState by remember { mutableStateOf(true) }
    var currentPosMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }
    var isMutedState by remember { mutableStateOf(false) }

    // Control Deck States
    var selectedQuality by remember { mutableStateOf("1080p") }
    var selectedSpeed by remember { mutableFloatStateOf(1.0f) }
    var isCaptionsOn by remember { mutableStateOf(true) }
    var isLoopMode by remember { mutableStateOf(false) }

    // SponsorBlock segments for creator sponsor auto-skipping
    var sponsorSegments by remember { mutableStateOf<List<SponsorSegment>>(emptyList()) }
    var lastSkippedCategory by remember { mutableStateOf<String?>(null) }

    fun addLog(msg: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        logs.add(0, "[$time] $msg")
        statusLog = msg
    }

    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            onPlayerReady(this)
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    // SponsorBlock segment fetch
    LaunchedEffect(videoId) {
        try {
            val segments = SponsorBlockService.getSponsorSegments(videoId)
            sponsorSegments = segments
            if (segments.isNotEmpty()) {
                addLog("SponsorBlock: Loaded ${segments.size} creator sponsor segment(s) to auto-skip!")
            }
        } catch (e: Exception) {
            // Ignore optional SponsorBlock errors
        }
    }

    // Real-time position ticker & SponsorBlock auto-skipping loop
    LaunchedEffect(exoPlayer) {
        while (isActive) {
            try {
                if (exoPlayer.isPlaying) {
                    currentPosMs = exoPlayer.currentPosition
                    totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)

                    // Automated SponsorBlock Segment Auto-Skip Check
                    if (!areAdvertsEnabled && sponsorSegments.isNotEmpty()) {
                        for (seg in sponsorSegments) {
                            if (currentPosMs >= seg.startMs && currentPosMs < seg.endMs - 500L) {
                                exoPlayer.seekTo(seg.endMs)
                                lastSkippedCategory = seg.category
                                addLog("SponsorBlock Auto-Skipped creator sponsor segment (${seg.category}) to ${seg.endMs / 1000}s!")
                                break
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
            delay(400)
        }
    }

    // Fetch Direct Native MP4 Stream URL via InnerTube API / Piped Fallback
    LaunchedEffect(videoId) {
        isLoading = true
        streamUrl = null
        addLog("Extracting direct MP4 stream URL via InnerTube API for videoId: $videoId")

        try {
            val url = YouTubeStreamExtractor.getDirectStreamUrl(videoId)
            if (!url.isNullOrEmpty()) {
                streamUrl = url
                addLog("Successfully extracted direct MP4 stream URL (0 WebViews)!")

                val mediaItem = MediaItem.fromUri(url)
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                if (startSeconds > 0) {
                    exoPlayer.seekTo(startSeconds * 1000L)
                }
                exoPlayer.play()
                isPlayingState = true
                isLoading = false
            } else {
                addLog("Failed to extract direct MP4 stream URL across all profiles.")
                isLoading = false
            }
        } catch (e: Exception) {
            addLog("Stream Extraction Error: ${e.message}")
            isLoading = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                showControlsOverlay = !showControlsOverlay
            },
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = YouTubeRed, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Extracting Direct Stream...", color = Color.White, fontSize = 12.sp)
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

        // Sleek, Minimalist, See-Through Controls Overlay
        AnimatedVisibility(
            visible = showControlsOverlay && streamUrl != null && !isLoading,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Sleek Center Controls: Compact See-Through Icons (-10s, Play/Pause, +10s)
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val target = (exoPlayer.currentPosition - 10000).coerceAtLeast(0)
                            exoPlayer.seekTo(target)
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Replay10,
                            contentDescription = "-10s",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (exoPlayer.isPlaying) {
                                exoPlayer.pause()
                                isPlayingState = false
                            } else {
                                exoPlayer.play()
                                isPlayingState = true
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlayingState) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            val target = (exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration)
                            exoPlayer.seekTo(target)
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Forward10,
                            contentDescription = "+10s",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Bottom Control Bar: Scrubber Line + Timestamp + BOTTOM RIGHT Controls (Quality, Speed, CC, Loop, Mute)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${formatMs(currentPosMs)} / ${formatMs(totalDurationMs)}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        // Bottom-Right Controls Row: Quality, Speed, CC, Loop, Mute
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Quality Selector Chip
                            Box {
                                var showQualityMenu by remember { mutableStateOf(false) }
                                Surface(
                                    onClick = { showQualityMenu = true },
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.Black.copy(alpha = 0.65f),
                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        text = selectedQuality,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showQualityMenu,
                                    onDismissRequest = { showQualityMenu = false }
                                ) {
                                    listOf("1080p", "720p", "480p", "Auto").forEach { q ->
                                        DropdownMenuItem(
                                            text = { Text(q, fontSize = 12.sp) },
                                            onClick = {
                                                selectedQuality = q
                                                showQualityMenu = false
                                                Toast.makeText(context, "Resolution Quality: $q", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                }
                            }

                            // Playback Speed Selector Chip
                            Box {
                                var showSpeedMenu by remember { mutableStateOf(false) }
                                Surface(
                                    onClick = { showSpeedMenu = true },
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.Black.copy(alpha = 0.65f),
                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        text = if (selectedSpeed == 1.0f) "1x" else "${selectedSpeed}x",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showSpeedMenu,
                                    onDismissRequest = { showSpeedMenu = false }
                                ) {
                                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { s ->
                                        DropdownMenuItem(
                                            text = { Text(if (s == 1.0f) "1.0x (Normal)" else "${s}x", fontSize = 12.sp) },
                                            onClick = {
                                                selectedSpeed = s
                                                showSpeedMenu = false
                                                exoPlayer.playbackParameters = androidx.media3.common.PlaybackParameters(s)
                                                Toast.makeText(context, "Speed: ${s}x", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                }
                            }

                            // CC Subtitles Chip
                            Surface(
                                onClick = {
                                    isCaptionsOn = !isCaptionsOn
                                    try {
                                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                            .buildUpon()
                                            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, !isCaptionsOn)
                                            .build()
                                    } catch (e: Exception) { }
                                    Toast.makeText(context, if (isCaptionsOn) "CC Subtitles On" else "Captions Off", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isCaptionsOn) YouTubeRed else Color.Black.copy(alpha = 0.65f),
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = if (isCaptionsOn) "CC On" else "CC Off",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }

                            // Loop Video Chip
                            Surface(
                                onClick = {
                                    isLoopMode = !isLoopMode
                                    exoPlayer.repeatMode = if (isLoopMode) androidx.media3.common.Player.REPEAT_MODE_ONE else androidx.media3.common.Player.REPEAT_MODE_OFF
                                    Toast.makeText(context, if (isLoopMode) "Repeat Video On" else "Repeat Off", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isLoopMode) YouTubeRed else Color.Black.copy(alpha = 0.65f),
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = if (isLoopMode) "Loop On" else "Loop",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    isMutedState = !isMutedState
                                    exoPlayer.volume = if (isMutedState) 0f else 1f
                                },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = if (isMutedState) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                                    contentDescription = "Mute",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Ultra-thin sleek scrubber line
                    Slider(
                        value = if (totalDurationMs > 0) (currentPosMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f) else 0f,
                        onValueChange = { fraction ->
                            val targetMs = (fraction * totalDurationMs).toLong()
                            exoPlayer.seekTo(targetMs)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = YouTubeRed,
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier.fillMaxWidth().height(16.dp)
                    )
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
