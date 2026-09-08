package com.example.ui.components.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.remote.StreamExtractionResult
import com.example.data.remote.YouTubeStreamExtractor
import com.example.ui.theme.YouTubeRed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun PlayerSettingsDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    context: Context,
    videoId: String,
    videoTitle: String,
    exoPlayer: ExoPlayer,
    streamResult: StreamExtractionResult?,
    availableQualities: List<String>,
    selectedQuality: String,
    onQualitySelected: (String) -> Unit,
    selectedSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    onEnterPip: () -> Unit,
    onRotate180: () -> Unit,
    onToggleDebugConsole: () -> Unit,
    onSwitchStreamUrl: (targetUrl: String, quality: String) -> Unit,
    coroutineScope: CoroutineScope
) {
    var showSpeedSubMenu by remember(expanded) { mutableStateOf(false) }
    var showQualitySubMenu by remember(expanded) { mutableStateOf(false) }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = {
            showSpeedSubMenu = false
            showQualitySubMenu = false
            onDismissRequest()
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
                text = { Text("Pop-Out Floating Player (PiP)", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Filled.PictureInPictureAlt, contentDescription = null, tint = YouTubeRed) },
                onClick = {
                    onDismissRequest()
                    onEnterPip()
                }
            )
            DropdownMenuItem(
                text = { Text("Rotate 180° (Flip Screen)", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Filled.ScreenRotation, contentDescription = null, tint = YouTubeRed) },
                onClick = {
                    onDismissRequest()
                    onRotate180()
                }
            )
            DropdownMenuItem(
                text = { Text("Stats & Debug Console", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Filled.BugReport, contentDescription = null) },
                onClick = {
                    onDismissRequest()
                    onToggleDebugConsole()
                }
            )
            DropdownMenuItem(
                text = { Text("Share Video ↗️", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null, tint = Color.White) },
                onClick = {
                    onDismissRequest()
                    try {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, videoTitle)
                            putExtra(Intent.EXTRA_TEXT, "$videoTitle\nhttps://youtu.be/$videoId")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
                    } catch (e: Exception) { }
                }
            )
            DropdownMenuItem(
                text = { Text("Open in Browser 🌐", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Filled.OpenInBrowser, contentDescription = null) },
                onClick = {
                    onDismissRequest()
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId"))
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
                        onQualitySelected(q)
                        showQualitySubMenu = false
                        onDismissRequest()

                        val (maxW, maxH) = when (q) {
                            "2160p" -> Pair(3840, 2160)
                            "1440p" -> Pair(2560, 1440)
                            "1080p" -> Pair(1920, 1080)
                            "720p"  -> Pair(1280, 720)
                            "480p"  -> Pair(854, 480)
                            "360p"  -> Pair(640, 360)
                            "240p"  -> Pair(426, 240)
                            "144p"  -> Pair(256, 144)
                            else    -> Pair(Int.MAX_VALUE, Int.MAX_VALUE)
                        }

                        val hlsUrl = streamResult?.qualityUrlMap?.get("HLS")
                        if (hlsUrl != null) {
                            // Video supports HLS: Keep HLS active for 100% buttery smooth playback!
                            val builder = exoPlayer.trackSelectionParameters.buildUpon()
                            if (q.equals("Auto", ignoreCase = true)) {
                                builder.clearVideoSizeConstraints()
                            } else {
                                builder.setMaxVideoSize(maxW, maxH)
                            }
                            exoPlayer.trackSelectionParameters = builder.build()
                            onSwitchStreamUrl(hlsUrl, q)
                            Toast.makeText(context, "Quality set to $q", Toast.LENGTH_SHORT).show()
                        } else {
                            // For progressive-only streams, switch directly to the specific quality stream URL
                            coroutineScope.launch {
                                val targetUrl = streamResult?.qualityUrlMap?.get(q)
                                    ?: YouTubeStreamExtractor.getDirectStreamUrl(videoId, q)
                                if (!targetUrl.isNullOrEmpty()) {
                                    onSwitchStreamUrl(targetUrl, q)
                                    Toast.makeText(context, "Quality switched to $q", Toast.LENGTH_SHORT).show()
                                }
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
                        onSpeedSelected(s)
                        showSpeedSubMenu = false
                        onDismissRequest()
                        exoPlayer.playbackParameters = PlaybackParameters(s)
                    }
                )
            }
        }
    }
}
