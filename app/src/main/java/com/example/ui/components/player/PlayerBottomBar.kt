package com.example.ui.components.player

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GoldStar
import com.example.ui.theme.YouTubeRed

@Composable
fun PlayerBottomBar(
    visible: Boolean,
    isTablet: Boolean,
    isLiveStream: Boolean,
    currentPosMs: Long,
    totalDurationMs: Long,
    isDraggingScrubber: Boolean,
    dragFraction: Float,
    onScrubberDrag: (Float) -> Unit,
    onScrubberRelease: (Float) -> Unit,
    isFavorite: Boolean,
    onFavoriteToggle: () -> Unit,
    onSaveToSubject: () -> Unit,
    isWatchLater: Boolean,
    onWatchLaterToggle: () -> Unit,
    selectedSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    onSpeedFeedback: (String) -> Unit,
    onTakeScreenshot: () -> Unit,
    onOpenScreenshotFolder: () -> Unit,
    isAutoplayEnabled: Boolean,
    onToggleAutoplay: () -> Unit,
    isSleepTimerActive: Boolean,
    wasPausedBySleepTimer: Boolean,
    onSleepTimerClick: () -> Unit,
    captionsEnabled: Boolean,
    onToggleCaptions: () -> Unit,
    videoId: String,
    videoTitle: String,
    selectedQuality: String,
    onOpenQualityMenu: () -> Unit,
    onToggleDebugConsole: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bottomBtnSize = if (isTablet) 38.dp else 32.dp
    val bottomIconSize = if (isTablet) 20.dp else 17.dp
    val timeFontSize = if (isTablet) 12.sp else 10.sp

    var showSettingsMenu by remember { mutableStateOf(false) }

    fun formatMs(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val seconds = totalSec % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(300)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
                .padding(
                    horizontal = if (isTablet) 18.dp else 10.dp,
                    vertical = if (isTablet) 8.dp else 3.dp
                )
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
                        onScrubberDrag(fraction)
                    },
                    onValueChangeFinished = {
                        onScrubberRelease(dragFraction)
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
                // Left side: Favorites + Subject + Watch Later + Timestamp
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onFavoriteToggle,
                        modifier = Modifier.size(bottomBtnSize)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarOutline,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) GoldStar else Color.White,
                            modifier = Modifier.size(bottomIconSize)
                        )
                    }

                    IconButton(
                        onClick = onSaveToSubject,
                        modifier = Modifier.size(bottomBtnSize)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Folder,
                            contentDescription = "Save to Subject",
                            tint = Color.White,
                            modifier = Modifier.size(bottomIconSize)
                        )
                    }

                    IconButton(
                        onClick = onWatchLaterToggle,
                        modifier = Modifier.size(bottomBtnSize)
                    ) {
                        Icon(
                            imageVector = if (isWatchLater) Icons.Filled.WatchLater else Icons.Filled.AccessTime,
                            contentDescription = "Watch Later",
                            tint = if (isWatchLater) YouTubeRed else Color.White,
                            modifier = Modifier.size(bottomIconSize)
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
                            fontSize = timeFontSize,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Right side: Speed Pill + Screenshot + Folder + Sleep Timer + CC + Settings Gear
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Discreet Speed Controls [ - ] 1.0x [ + ]
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, if (selectedSpeed != 1.0f) YouTubeRed.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.25f))
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
                                            onSpeedFeedback("🐢 ${prev}x Speed")
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
                                            onSpeedFeedback("⚡ 1.0x Speed (Normal)")
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
                                            onSpeedFeedback("⚡ ${next}x Speed")
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("+", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }

                    // 1. Screenshot Button [📸]
                    IconButton(
                        onClick = onTakeScreenshot,
                        modifier = Modifier.size(bottomBtnSize)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = "Screenshot",
                            tint = Color.White,
                            modifier = Modifier.size(bottomIconSize)
                        )
                    }

                    // 2. Screenshot Folder Switcher [📁]
                    IconButton(
                        onClick = onOpenScreenshotFolder,
                        modifier = Modifier.size(bottomBtnSize)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FolderOpen,
                            contentDescription = "Screenshot Folder",
                            tint = GoldStar,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // 3. Autoplay Toggle [▶️ / ⏸️]
                    IconButton(
                        onClick = onToggleAutoplay,
                        modifier = Modifier.size(bottomBtnSize)
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

                    // 4. Sleep Timer [🌙]
                    IconButton(
                        onClick = onSleepTimerClick,
                        modifier = Modifier.size(bottomBtnSize)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.Bedtime,
                                contentDescription = "Sleep Timer",
                                tint = if (isSleepTimerActive || wasPausedBySleepTimer) GoldStar else Color.White,
                                modifier = Modifier.size(bottomIconSize)
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

                    // 5. Subtitles [CC]
                    IconButton(
                        onClick = onToggleCaptions,
                        modifier = Modifier.size(bottomBtnSize)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.ClosedCaption,
                                contentDescription = "Subtitles",
                                tint = if (captionsEnabled) YouTubeRed else Color.White,
                                modifier = Modifier.size(bottomIconSize)
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

                    // 6. Share Button [↗️]
                    IconButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, videoTitle)
                                putExtra(Intent.EXTRA_TEXT, "$videoTitle\nhttps://youtu.be/$videoId")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
                        },
                        modifier = Modifier.size(bottomBtnSize)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Share Video",
                            tint = Color.White,
                            modifier = Modifier.size(bottomIconSize)
                        )
                    }

                    // 7. Settings Gear [⚙️]
                    Box {
                        IconButton(
                            onClick = { showSettingsMenu = true },
                            modifier = Modifier.size(bottomBtnSize)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Settings",
                                tint = Color.White,
                                modifier = Modifier.size(bottomIconSize)
                            )
                        }

                        DropdownMenu(
                            expanded = showSettingsMenu,
                            onDismissRequest = { showSettingsMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Quality: $selectedQuality", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                                leadingIcon = { Icon(Icons.Filled.HighQuality, contentDescription = null, tint = YouTubeRed) },
                                onClick = {
                                    showSettingsMenu = false
                                    onOpenQualityMenu()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Stats & Debug Console", fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Filled.BugReport, contentDescription = null) },
                                onClick = {
                                    showSettingsMenu = false
                                    onToggleDebugConsole()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
