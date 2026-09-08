package com.example.ui.components.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GoldStar
import com.example.ui.theme.YouTubeRed

/**
 * Centered mid-playback buffering and quality-switching HUD indicator.
 */
@Composable
fun PlayerBufferingOverlay(
    isBuffering: Boolean,
    isFirstFrameRendered: Boolean,
    isLoading: Boolean,
    useWebPlayerFallback: Boolean,
    isSwitchingQuality: String?,
    modifier: Modifier = Modifier
) {
    if ((isBuffering && isFirstFrameRendered && !isLoading && !useWebPlayerFallback) || isSwitchingQuality != null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.70f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(26.dp),
                        color = YouTubeRed,
                        strokeWidth = 2.5.dp
                    )
                    Text(
                        text = if (isSwitchingQuality != null) "Switching to $isSwitchingQuality..." else "Buffering...",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Vertical volume gesture HUD on the right side of the screen.
 */
@Composable
fun PlayerVolumeHUD(
    isAdjustingVolume: Boolean,
    volumeFraction: Float,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isAdjustingVolume,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = modifier
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
                    imageVector = if (volumeFraction > 0.5f) Icons.Filled.VolumeUp else if (volumeFraction > 0.05f) Icons.Filled.VolumeDown else Icons.Filled.VolumeMute,
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
                            .fillMaxHeight(volumeFraction.coerceIn(0f, 1f))
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "${(volumeFraction * 100).toInt()}%",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

/**
 * Vertical brightness gesture HUD on the left side of the screen.
 */
@Composable
fun PlayerBrightnessHUD(
    isAdjustingBrightness: Boolean,
    brightnessFraction: Float,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isAdjustingBrightness,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = modifier
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
                    imageVector = if (brightnessFraction > 0.6f) Icons.Filled.BrightnessHigh else if (brightnessFraction > 0.25f) Icons.Filled.BrightnessMedium else Icons.Filled.BrightnessLow,
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
                            .fillMaxHeight(brightnessFraction.coerceIn(0f, 1f))
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "${(brightnessFraction * 100).toInt()}%",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

/**
 * Center seek / scrub feedback badge (e.g. "⏪ -10s", "⏩ +20s").
 */
@Composable
fun PlayerSeekFeedbackHUD(
    feedback: String?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = feedback != null,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.80f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
        ) {
            Text(
                text = feedback ?: "",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp)
            )
        }
    }
}

/**
 * Animated center play/pause indicator bubble.
 */
@Composable
fun PlayerPlayPauseBubble(
    state: Boolean?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = state != null,
        enter = scaleIn(initialScale = 0.6f) + fadeIn(),
        exit = scaleOut(targetScale = 1.2f) + fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (state == true) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                contentDescription = if (state == true) "Playing" else "Paused",
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

/**
 * Top-right sleep timer countdown badge.
 */
@Composable
fun PlayerSleepTimerBadge(
    isActive: Boolean,
    isInPipMode: Boolean,
    endOfVideo: Boolean,
    remainingSec: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isActive && !isInPipMode) {
        val countdownText = if (endOfVideo) {
            "End"
        } else {
            val m = remainingSec / 60
            val s = remainingSec % 60
            String.format("%02d:%02d", m, s)
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.Black.copy(alpha = 0.35f),
            border = androidx.compose.foundation.BorderStroke(0.75.dp, Color.White.copy(alpha = 0.25f)),
            modifier = modifier
                .padding(top = 8.dp, end = 8.dp)
                .clickable { onClick() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Bedtime,
                    contentDescription = "Sleep Countdown",
                    tint = GoldStar.copy(alpha = 0.85f),
                    modifier = Modifier.size(10.dp)
                )
                Text(
                    text = countdownText,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

