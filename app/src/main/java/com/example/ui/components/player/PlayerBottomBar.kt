package com.example.ui.components.player

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.remote.StreamExtractionResult
import com.example.ui.theme.GoldStar
import com.example.ui.theme.YouTubeRed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PlayerBottomBar(
    shouldShowControls: Boolean,
    exoPlayer: ExoPlayer,
    context: Context,
    videoId: String,
    videoTitle: String,
    totalDurationMs: Long,
    currentPosMs: Long,
    isDraggingScrubber: Boolean,
    dragFraction: Float,
    onScrubberDragChange: (Float) -> Unit,
    onScrubberDragFinished: (Long) -> Unit,
    isPlayingState: Boolean,
    onPlayPauseClick: () -> Unit,
    isFavorite: Boolean,
    onFavoriteToggle: () -> Unit,
    onSaveToSubject: () -> Unit,
    isWatchLater: Boolean,
    onWatchLaterToggle: () -> Unit,
    selectedSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    onSpeedFeedback: (String?) -> Unit,
    onTakeScreenshot: () -> Unit,
    onOpenScreenshotFolder: () -> Unit,
    isAutoplayEnabled: Boolean,
    onToggleAutoplay: () -> Unit,
    isSleepTimerActive: Boolean,
    wasPausedBySleepTimer: Boolean,
    onSleepTimerClick: () -> Unit,
    captionsEnabled: Boolean,
    onToggleCaptions: () -> Unit,
    streamResult: StreamExtractionResult?,
    availableQualities: List<String>,
    selectedQuality: String,
    onQualitySelected: (String) -> Unit,
    onEnterPip: () -> Unit,
    onRotate180: () -> Unit,
    onToggleDebugConsole: () -> Unit,
    onSwitchStreamUrl: (targetUrl: String, quality: String) -> Unit,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    coroutineScope: CoroutineScope,
    modifier: Modifier = Modifier
) {
    var showSettingsMenu by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = shouldShowControls,
        enter = fadeIn(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(300)),
        modifier = modifier
    ) {
        val isLiveStream = exoPlayer.isCurrentMediaItemLive

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
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
                    onValueChange = onScrubberDragChange,
                    onValueChangeFinished = {
                        val targetMs = (dragFraction * totalDurationMs).toLong()
                        onScrubberDragFinished(targetMs)
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

            // 2. Utility Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side: Play/Pause + Favorites + Subject + Watch Later + Timestamp
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Quick Play / Pause Button
                    IconButton(
                        onClick = onPlayPauseClick,
                        modifier = Modifier.size(32.dp).testTag("bottom_bar_play_pause_btn")
                    ) {
                        Icon(
                            imageVector = if (isPlayingState) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlayingState) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Direct Star (Favorite) Button
                    IconButton(
                        onClick = onFavoriteToggle,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarOutline,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) GoldStar else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Direct Save to Subject Button
                    IconButton(
                        onClick = onSaveToSubject,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Folder,
                            contentDescription = "Save to Subject",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Direct Watch Later Button
                    IconButton(
                        onClick = onWatchLaterToggle,
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

                // Right side: Speed Pill + Screenshot + Folder + Autoplay + Sleep Timer + CC + Share + Settings + PiP + Fullscreen
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Discreet Speed Controls [ - ] 1.0x [ + ]
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (selectedSpeed != 1.0f) YouTubeRed.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.25f)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp)
                        ) {
                            // Slower [ - ]
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.25f, 2.5f, 3.0f)
                                        val prev = speeds.lastOrNull { it < (selectedSpeed - 0.01f) } ?: selectedSpeed
                                        if (prev != selectedSpeed) {
                                            onSpeedChange(prev)
                                            exoPlayer.playbackParameters = PlaybackParameters(prev)
                                            onSpeedFeedback("🐢 ${prev}x Speed")
                                            coroutineScope.launch {
                                                delay(750)
                                                onSpeedFeedback(null)
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("–", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }

                            // Speed Label (Tap to reset to 1.0x)
                            Text(
                                text = "${selectedSpeed}x",
                                color = if (selectedSpeed == 1.0f) Color.White else YouTubeRed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable {
                                        if (selectedSpeed != 1.0f) {
                                            onSpeedChange(1.0f)
                                            exoPlayer.playbackParameters = PlaybackParameters(1.0f)
                                            onSpeedFeedback("⚡ 1.0x Speed (Normal)")
                                            coroutineScope.launch {
                                                delay(750)
                                                onSpeedFeedback(null)
                                            }
                                        }
                                    }
                            )

                            // Faster [ + ]
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.25f, 2.5f, 3.0f)
                                        val next = speeds.firstOrNull { it > (selectedSpeed + 0.01f) } ?: selectedSpeed
                                        if (next != selectedSpeed) {
                                            onSpeedChange(next)
                                            exoPlayer.playbackParameters = PlaybackParameters(next)
                                            onSpeedFeedback("⚡ ${next}x Speed")
                                            coroutineScope.launch {
                                                delay(750)
                                                onSpeedFeedback(null)
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("+", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }

                    // Screenshot Button [📸]
                    IconButton(
                        onClick = onTakeScreenshot,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = "Screenshot",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Screenshot Folder Switcher [📁]
                    IconButton(
                        onClick = onOpenScreenshotFolder,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FolderOpen,
                            contentDescription = "Screenshot Folder",
                            tint = GoldStar,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Autoplay Toggle [▶️ / ⏸️]
                    IconButton(
                        onClick = onToggleAutoplay,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(19.dp)
                                    .clip(CircleShape)
                                    .background(if (isAutoplayEnabled) GoldStar.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isAutoplayEnabled) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                    contentDescription = if (isAutoplayEnabled) "Autoplay is ON" else "Autoplay is OFF",
                                    tint = if (isAutoplayEnabled) GoldStar else Color.LightGray,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            if (isAutoplayEnabled) {
                                Box(
                                    modifier = Modifier
                                        .width(10.dp)
                                        .height(2.dp)
                                        .background(GoldStar)
                                )
                            }
                        }
                    }

                    // Sleep Timer [🌙]
                    IconButton(
                        onClick = onSleepTimerClick,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.Bedtime,
                                contentDescription = "Sleep Timer",
                                tint = if (isSleepTimerActive || wasPausedBySleepTimer) GoldStar else Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                            if (isSleepTimerActive || wasPausedBySleepTimer) {
                                Box(
                                    modifier = Modifier
                                        .width(12.dp)
                                        .height(2.dp)
                                        .background(GoldStar)
                                )
                            }
                        }
                    }

                    // Subtitles [CC]
                    IconButton(
                        onClick = onToggleCaptions,
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

                    // Share Button [↗️]
                    IconButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, videoTitle)
                                putExtra(Intent.EXTRA_TEXT, "$videoTitle\nhttps://youtu.be/$videoId")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Share Video",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Settings Gear [⚙️]
                    Box {
                        IconButton(
                            onClick = { showSettingsMenu = true },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Settings",
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        PlayerSettingsDropdown(
                            expanded = showSettingsMenu,
                            onDismissRequest = { showSettingsMenu = false },
                            context = context,
                            videoId = videoId,
                            videoTitle = videoTitle,
                            exoPlayer = exoPlayer,
                            streamResult = streamResult,
                            availableQualities = availableQualities,
                            selectedQuality = selectedQuality,
                            onQualitySelected = onQualitySelected,
                            selectedSpeed = selectedSpeed,
                            onSpeedSelected = onSpeedChange,
                            onEnterPip = onEnterPip,
                            onRotate180 = onRotate180,
                            onToggleDebugConsole = onToggleDebugConsole,
                            onSwitchStreamUrl = onSwitchStreamUrl,
                            coroutineScope = coroutineScope
                        )
                    }

                    // Pop-Out / PiP Floating Window Button
                    IconButton(
                        onClick = onEnterPip,
                        modifier = Modifier.size(30.dp).testTag("pip_popout_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PictureInPictureAlt,
                            contentDescription = "Pop-Out Floating Player (PiP)",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Fullscreen / Maximize & Minimize Button [⤢ / ⤡]
                    IconButton(
                        onClick = onToggleFullscreen,
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
}

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSec = ms / 1000
    val mins = totalSec / 60
    val secs = totalSec % 60
    return String.format("%02d:%02d", mins, secs)
}
