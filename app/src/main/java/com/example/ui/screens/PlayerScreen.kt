package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.GoogleAccount
import com.example.data.model.VideoEntity
import com.example.data.model.VideoNoteEntity
import com.example.ui.components.YouTubePlayerView
import com.example.ui.theme.GoldStar


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    video: VideoEntity,
    notes: List<VideoNoteEntity>,
    playlistVideos: List<VideoEntity>,
    googleAccount: GoogleAccount,
    onBackClick: () -> Unit,
    onFavoriteToggle: (VideoEntity) -> Unit,
    onWatchLaterToggle: (VideoEntity) -> Unit,
    onAddNote: (timestampSeconds: Int, timestampFormatted: String, noteText: String) -> Unit,
    onDeleteNote: (noteId: Long) -> Unit,
    onSelectOtherVideo: (VideoEntity) -> Unit,
    onOpenGoogleAuth: () -> Unit,
    areAdvertsEnabled: Boolean = false,
    onNotInterested: (VideoEntity) -> Unit = {},
    onSaveToSubject: (video: VideoEntity, subject: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var webViewInstance by remember { mutableStateOf<Any?>(null) }
    var showDebugConsole by remember { mutableStateOf(false) }
    var showSaveToSubjectDialog by remember { mutableStateOf(false) }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    var isMaximized by remember { mutableStateOf(false) }

    val isFullscreen = isLandscape || isMaximized

    Scaffold(
        topBar = {
            if (!isFullscreen) {
                TopAppBar(
                    title = {
                        Text(
                            text = "Now Playing",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.testTag("player_back_btn")
                        ) {
                            Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isFullscreen) PaddingValues(0.dp) else innerPadding)
        ) {
            // Native ExoPlayer view — touch to show controls (scrub bar, speed, quality, mute, ±10s)
            YouTubePlayerView(
                videoId = video.youtubeId,
                startSeconds = video.lastPositionSeconds,
                areAdvertsEnabled = areAdvertsEnabled,
                showDebugConsole = showDebugConsole,
                onToggleDebugConsole = { showDebugConsole = !showDebugConsole },
                onPlayerReady = { wv -> webViewInstance = wv },
                modifier = if (isFullscreen) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                }
            )

            // Below-video content (portrait only)
            if (!isFullscreen) {
                // Strictly sort Up Next videos by published timestamp descending (Newest First!)
                val otherVideos = playlistVideos
                    .filter { it.youtubeId != video.youtubeId }
                    .sortedWith(compareBy<VideoEntity> {
                        com.example.util.YouTubeUtils.parsePublishedTimeToSeconds(it.publishedTimeText)
                    })

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    // Title + Channel + Below-Video Action Buttons
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = video.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val subDetails = listOfNotNull(
                                video.channelName.takeIf { it.isNotBlank() },
                                video.publishedTimeText.takeIf { it.isNotBlank() },
                                video.viewCountText.takeIf { it.isNotBlank() }
                            ).joinToString(" • ")

                            Text(
                                text = subDetails,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Sleek Compact Below-Video Action Bar (Icon Buttons Only - Zero Text Clutter)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Like 👍
                                var isLiked by remember { mutableStateOf(false) }
                                IconButton(
                                    onClick = {
                                        isLiked = !isLiked
                                        android.widget.Toast.makeText(context, if (isLiked) "Liked video 👍" else "Unliked", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ThumbUp,
                                        contentDescription = "Like",
                                        tint = if (isLiked) com.example.ui.theme.YouTubeRed else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                // 2. Not Interested 👎
                                IconButton(
                                    onClick = {
                                        onNotInterested(video)
                                        android.widget.Toast.makeText(context, "Marked as Not Interested 👎", android.widget.Toast.LENGTH_SHORT).show()
                                        onBackClick()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ThumbDown,
                                        contentDescription = "Not Interested",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                // 3. Star ⭐ (Favorite)
                                IconButton(
                                    onClick = { onFavoriteToggle(video) }
                                ) {
                                    Icon(
                                        imageVector = if (video.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                        contentDescription = "Favorite",
                                        tint = if (video.isFavorite) GoldStar else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                // 4. Save As 📁 (Subject / Playlist)
                                IconButton(
                                    onClick = { showSaveToSubjectDialog = true }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Folder,
                                        contentDescription = "Save As",
                                        tint = com.example.ui.theme.YouTubeRed,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                // 5. Watch Later 🕒
                                IconButton(
                                    onClick = { onWatchLaterToggle(video) }
                                ) {
                                    Icon(
                                        imageVector = if (video.isWatchLater) Icons.Filled.WatchLater else Icons.Outlined.WatchLater,
                                        contentDescription = "Watch Later",
                                        tint = if (video.isWatchLater) com.example.ui.theme.YouTubeRed else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        )
                    }

                    // Up Next
                    if (otherVideos.isNotEmpty()) {
                        item {
                            Text(
                                text = "Up Next",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                        items(otherVideos, key = { "q_${it.youtubeId}" }) { other ->
                            PlaylistQueueItem(
                                video = other,
                                onClick = { onSelectOtherVideo(other) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSaveToSubjectDialog) {
        com.example.ui.components.SaveToSubjectDialog(
            video = video,
            onDismiss = { showSaveToSubjectDialog = false },
            onSaveToSubject = { selectedSubject ->
                showSaveToSubjectDialog = false
                onSaveToSubject(video, selectedSubject)
            }
        )
    }
}

@Composable
private fun PlaylistQueueItem(
    video: VideoEntity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(110.dp)
                    .height(65.dp)
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Top Left Overlay Badge: Published Age
                if (video.publishedTimeText.isNotBlank()) {
                    val compactTime = com.example.util.YouTubeUtils.formatCompactTime(video.publishedTimeText)
                    if (compactTime.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.Black.copy(alpha = 0.8f))
                                .padding(horizontal = 3.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = compactTime,
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Bottom Right Overlay Badge: Video Duration
                if (video.durationText.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.Black.copy(alpha = 0.8f))
                            .padding(horizontal = 3.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = video.durationText,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                val queueSubText = listOfNotNull(
                    video.channelName.takeIf { it.isNotBlank() },
                    video.publishedTimeText.takeIf { it.isNotBlank() }
                ).joinToString(" • ")

                Text(
                    text = queueSubText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
