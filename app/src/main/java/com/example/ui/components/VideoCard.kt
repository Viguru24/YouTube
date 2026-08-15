package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.VideoEntity
import com.example.ui.theme.GoldStar
import com.example.ui.theme.YouTubeRed

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput

@Composable
fun VideoCard(
    video: VideoEntity,
    onVideoClick: (VideoEntity) -> Unit,
    onFavoriteToggle: (VideoEntity) -> Unit,
    onWatchLaterToggle: (VideoEntity) -> Unit,
    onDeleteClick: (VideoEntity) -> Unit,
    recommendationReason: String = "",
    onMuteChannel: (String) -> Unit = {},
    onSaveToSubject: (VideoEntity) -> Unit = {},
    onNotInterested: (VideoEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    var showMenu by remember { mutableStateOf(false) }

    Card(
        onClick = { onVideoClick(video) },
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .testTag("video_card_${video.youtubeId}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        val isShort = com.example.util.YouTubeUtils.isShortVideo(video)
        val hasValidTime = video.publishedTimeText.isNotBlank() &&
                !video.publishedTimeText.equals("Recent", ignoreCase = true) &&
                !video.publishedTimeText.equals("Recently", ignoreCase = true)

        Column(modifier = Modifier.fillMaxWidth()) {
            // Thumbnail container with Duration Overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (isShort) 9f / 16f else 16f / 9f)
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Published Time Badge Top Left (compact: 3D, 2M, 1Y)
                if (hasValidTime) {
                    val compactTime = com.example.util.YouTubeUtils.formatCompactTime(video.publishedTimeText)
                    if (compactTime.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .align(Alignment.TopStart)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.Black.copy(alpha = 0.7f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = compactTime,
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.sp
                            )
                        }
                    }
                }

                // Duration Badge Bottom Right
                val isLive = video.durationText == "0:00" || video.durationText == "00:00" || video.durationText.isBlank() || video.publishedTimeText.contains("live", ignoreCase = true)
                if (isLive) {
                    Box(
                        modifier = Modifier
                            .padding(6.dp)
                            .align(Alignment.BottomEnd)
                            .clip(RoundedCornerShape(4.dp))
                            .background(YouTubeRed)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(Color.White, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "LIVE",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            )
                        }
                    }
                } else if (video.durationText.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .padding(6.dp)
                            .align(Alignment.BottomEnd)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.8f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = video.durationText,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }

                // Recommendation indicator dot — Top Right
                if (recommendationReason.isNotBlank()) {
                    val dotColor = when {
                        recommendationReason.contains("Subscribed") -> Color(0xFFFF6D00) // Orange
                        recommendationReason.contains("Fresh") || recommendationReason.contains("Exploration") -> Color(0xFF4CAF50) // Green
                        recommendationReason.contains("Continue") -> Color(0xFF2196F3) // Blue
                        recommendationReason.contains("Favorite") -> Color(0xFFFFD600) // Gold
                        recommendationReason.contains("Enjoy") -> Color(0xFFFF5252) // Red
                        else -> Color(0xFFBDBDBD) // Grey
                    }
                    Box(
                        modifier = Modifier
                            .padding(6.dp)
                            .align(Alignment.TopEnd)
                            .size(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(dotColor)
                    )
                }
            }

            // Video Details Header & Mute Options Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp, lineHeight = 16.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    val subText = listOfNotNull(
                        video.channelName.takeIf { it.isNotBlank() },
                        video.viewCountText.takeIf { it.isNotBlank() }
                    ).joinToString(" • ")

                    Text(
                        text = subText,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("📁 Save to Subject / Playlist") },
                            onClick = {
                                showMenu = false
                                onSaveToSubject(video)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (video.isWatchLater) "✔ In Watch Later" else "🕒 Save to Watch Later") },
                            onClick = {
                                showMenu = false
                                onWatchLaterToggle(video)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (video.isFavorite) "⭐ Favorited" else "⭐ Add to Favorites") },
                            onClick = {
                                showMenu = false
                                onFavoriteToggle(video)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("👎 Not Interested") },
                            onClick = {
                                showMenu = false
                                onNotInterested(video)
                                android.widget.Toast.makeText(context, "Marked as Not Interested 👎", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("🚫 Mute '${video.channelName}'") },
                            onClick = {
                                showMenu = false
                                onMuteChannel(video.channelName)
                                android.widget.Toast.makeText(context, "Muted ${video.channelName} 🚫", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }
}
