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
    isInPipMode: Boolean = false,
    onEnterPip: () -> Unit = {},
    playerCommandFlow: kotlinx.coroutines.flow.SharedFlow<String>? = null,
    onPlayingStateChanged: (Boolean) -> Unit = {},
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
    isDownloaded: Boolean = false,
    downloadProgress: Int = 0,
    onDownloadClick: () -> Unit = {},
    onDeleteDownloadClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var webViewInstance by remember { mutableStateOf<Any?>(null) }
    var showDebugConsole by remember { mutableStateOf(false) }
    var showSaveToSubjectDialog by remember { mutableStateOf(false) }
    var showAiSummaryModal by remember { mutableStateOf(false) }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    var isMaximized by remember { mutableStateOf(false) }

    val isFullscreen = isLandscape || isMaximized || isInPipMode

    Scaffold(
        topBar = {
            if (!isFullscreen && !isInPipMode) {
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
                    actions = {
                        IconButton(
                            onClick = onEnterPip,
                            modifier = Modifier.testTag("player_pip_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PictureInPictureAlt,
                                contentDescription = "Floating Pop-up Window"
                            )
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
                .padding(if (isFullscreen || isInPipMode) PaddingValues(0.dp) else innerPadding)
        ) {
            // Native ExoPlayer view — touch to show controls (scrub bar, speed, quality, mute, ±10s)
            YouTubePlayerView(
                videoId = video.youtubeId,
                startSeconds = video.lastPositionSeconds,
                areAdvertsEnabled = areAdvertsEnabled,
                showDebugConsole = showDebugConsole && !isInPipMode,
                onToggleDebugConsole = { showDebugConsole = !showDebugConsole },
                playerCommandFlow = playerCommandFlow,
                onPlayingStateChanged = onPlayingStateChanged,
                onPlayerReady = { wv -> webViewInstance = wv },
                modifier = if (isFullscreen || isInPipMode) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                }
            )

            // Below-video content (portrait non-PiP only)
            if (!isFullscreen && !isInPipMode) {
                // Strictly sort Up Next videos by published timestamp descending (Newest First!)
                val otherVideos = playlistVideos
                    .filter { it.youtubeId != video.youtubeId && it.lastPositionSeconds == 0 && it.lastWatchedTimestamp == 0L }
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

                            // Sleek Compact Below-Video Action Bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Like 👍
                                IconButton(
                                    onClick = {
                                        onFavoriteToggle(video)
                                        android.widget.Toast.makeText(context, if (!video.isFavorite) "Liked video 👍" else "Unliked", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ThumbUp,
                                        contentDescription = "Like",
                                        tint = if (video.isFavorite) com.example.ui.theme.YouTubeRed else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                // 2. Save As 📁 (Subject / Playlist)
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

                                // 3. Watch Later 🕒
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

                                // 4. ✨ 1-Tap AI Summary Button
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color(0xFF8E24AA).copy(alpha = 0.15f),
                                    modifier = Modifier.clickable { showAiSummaryModal = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.AutoAwesome,
                                            contentDescription = "AI Summary",
                                            tint = Color(0xFFAB47BC),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Summary",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFAB47BC)
                                        )
                                    }
                                }

                                // 5. ✈️ 1-Tap Offline Download Button
                                if (isDownloaded) {
                                    IconButton(
                                        onClick = onDeleteDownloadClick
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = "Downloaded",
                                            tint = Color(0xFF4CAF50),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                } else if (downloadProgress in 1..99) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp)) {
                                        CircularProgressIndicator(
                                            progress = { downloadProgress / 100f },
                                            modifier = Modifier.size(26.dp),
                                            color = com.example.ui.theme.YouTubeRed,
                                            strokeWidth = 2.5.dp
                                        )
                                        Text(
                                            text = "$downloadProgress",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = com.example.ui.theme.YouTubeRed
                                        )
                                    }
                                } else {
                                    IconButton(
                                        onClick = onDownloadClick
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Download,
                                            contentDescription = "Download Video",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
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

    if (showAiSummaryModal) {
        com.example.ui.components.AiSummaryModal(
            video = video,
            onDismiss = { showAiSummaryModal = false },
            onSeekTo = { seekSec ->
                // Seek player to timestamp
                try {
                    (webViewInstance as? androidx.media3.exoplayer.ExoPlayer)?.seekTo((seekSec * 1000).toLong())
                } catch (e: Exception) {}
            },
            onSaveToNotes = { summaryText ->
                onAddNote(0, "00:00", summaryText)
            }
        )
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
