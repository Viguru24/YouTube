package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
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
import com.example.ui.theme.YouTubeRed


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
    isDisliked: Boolean = false,
    onSaveToSubject: (video: VideoEntity, subject: String) -> Unit = { _, _ -> },
    isDownloaded: Boolean = false,
    downloadProgress: Int = 0,
    onDownloadClick: () -> Unit = {},
    onDeleteDownloadClick: () -> Unit = {},
    subscribedCreators: List<String> = emptyList(),
    onToggleSubscribe: (String) -> Unit = {},
    onSelectChannel: (String) -> Unit = {},
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

    val otherVideos = remember(video.youtubeId, playlistVideos) {
        playlistVideos
            .filter { it.youtubeId != video.youtubeId && it.lastPositionSeconds == 0 && it.lastWatchedTimestamp == 0L }
            .sortedWith(compareBy<VideoEntity> {
                com.example.util.YouTubeUtils.parsePublishedTimeToSeconds(it.publishedTimeText)
            })
    }

    // Automatically manage immersive system bars when phone is physically rotated 90 degrees or maximized
    LaunchedEffect(isLandscape, isMaximized, isInPipMode) {
        val act = context as? android.app.Activity
        if (act != null) {
            val window = act.window
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            if (isLandscape || isMaximized) {
                insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else if (!isInPipMode) {
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val act = context as? android.app.Activity
            act?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER
        }
    }

    val toggleFullscreen: () -> Unit = {
        val act = context as? android.app.Activity
        if (act != null) {
            val isLand = act.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            if (isLand || isMaximized) {
                isMaximized = false
                act.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER
            } else {
                isMaximized = true
                act.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        } else {
            isMaximized = !isMaximized
        }
    }

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
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showDebugConsole = !showDebugConsole }) {
                            Icon(
                                imageVector = Icons.Filled.BugReport,
                                contentDescription = "Debug Logs",
                                tint = if (showDebugConsole) YouTubeRed else MaterialTheme.colorScheme.onSurface
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isFullscreen) PaddingValues(0.dp) else innerPadding)
                .background(Color.Black)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Video Player Area - Single persistent ExoPlayer instance across rotation & fullscreen
                Box(
                    modifier = (if (isFullscreen) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    }).background(Color.Black)
                ) {
                    YouTubePlayerView(
                        videoId = video.youtubeId,
                        startSeconds = video.lastPositionSeconds,
                        areAdvertsEnabled = areAdvertsEnabled,
                        showDebugConsole = showDebugConsole && !isInPipMode,
                        onToggleDebugConsole = { showDebugConsole = !showDebugConsole },
                        playerCommandFlow = playerCommandFlow,
                        onPlayingStateChanged = onPlayingStateChanged,
                        onNextVideo = {
                            val currentIndex = playlistVideos.indexOfFirst { it.youtubeId == video.youtubeId }
                            val next = if (currentIndex != -1 && currentIndex < playlistVideos.size - 1) {
                                playlistVideos[currentIndex + 1]
                            } else {
                                otherVideos.firstOrNull() ?: playlistVideos.firstOrNull { it.youtubeId != video.youtubeId }
                            }
                            if (next != null) {
                                onSelectOtherVideo(next)
                            } else {
                                android.widget.Toast.makeText(context, "No next video in feed", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        onPreviousVideo = {
                            val currentIndex = playlistVideos.indexOfFirst { it.youtubeId == video.youtubeId }
                            val prev = if (currentIndex > 0) {
                                playlistVideos[currentIndex - 1]
                            } else {
                                playlistVideos.takeWhile { it.youtubeId != video.youtubeId }.lastOrNull()
                            }
                            if (prev != null) {
                                onSelectOtherVideo(prev)
                            } else {
                                android.widget.Toast.makeText(context, "No previous video", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        isFavorite = video.isFavorite,
                        isWatchLater = video.isWatchLater,
                        onFavoriteToggle = { onFavoriteToggle(video) },
                        onWatchLaterToggle = { onWatchLaterToggle(video) },
                        onSaveToSubject = { showSaveToSubjectDialog = true },
                        isDisliked = isDisliked,
                        onDislikeToggle = {
                            onNotInterested(video)
                            val nextVideo = otherVideos.firstOrNull() ?: playlistVideos.firstOrNull { it.youtubeId != video.youtubeId }
                            if (nextVideo != null) {
                                onSelectOtherVideo(nextVideo)
                            } else {
                                onBackClick()
                            }
                        },
                        onAiSummaryClick = { showAiSummaryModal = true },
                        isDownloaded = isDownloaded,
                        downloadProgress = downloadProgress,
                        onDownloadClick = onDownloadClick,
                        onDeleteDownloadClick = onDeleteDownloadClick,
                        videoTitle = video.title,
                        isFullscreen = isFullscreen,
                        onToggleFullscreen = toggleFullscreen,
                        onPlayerReady = { wv -> webViewInstance = wv },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Below-video content (portrait non-PiP only)
                if (!isFullscreen && !isInPipMode) {
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
                                Spacer(modifier = Modifier.height(8.dp))

                                // Creator Row + Subscribe Button
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onSelectChannel(video.channelName) }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = video.channelName.take(1).uppercase(),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = video.channelName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val subDetails = listOfNotNull(
                                                video.publishedTimeText.takeIf { it.isNotBlank() },
                                                video.viewCountText.takeIf { it.isNotBlank() }
                                            ).joinToString(" • ")
                                            if (subDetails.isNotBlank()) {
                                                Text(
                                                    text = subDetails,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    val isSubbed = subscribedCreators.any { it.equals(video.channelName.trim(), ignoreCase = true) }
                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .clickable {
                                                onToggleSubscribe(video.channelName)
                                                val msg = if (!isSubbed) "Subscribed to ${video.channelName}! 🎉" else "Unsubscribed from ${video.channelName}"
                                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                            },
                                        shape = RoundedCornerShape(20.dp),
                                        color = if (isSubbed) MaterialTheme.colorScheme.surfaceVariant else YouTubeRed
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (isSubbed) {
                                                Icon(
                                                    imageVector = Icons.Filled.Check,
                                                    contentDescription = "Subscribed",
                                                    tint = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                            Text(
                                                text = if (isSubbed) "Subscribed" else "Subscribe",
                                                color = if (isSubbed) MaterialTheme.colorScheme.onSurface else Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            )
                        }

                    // Saved Notes & AI Summaries Section
                    if (notes.isNotEmpty()) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.AutoAwesome,
                                                contentDescription = null,
                                                tint = Color(0xFFAB47BC),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "Saved Notes & AI Summaries (${notes.size})",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    notes.forEach { note ->
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    if (note.timestampFormatted.isNotBlank() && note.timestampFormatted != "00:00") {
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = YouTubeRed.copy(alpha = 0.15f)
                                                        ) {
                                                            Text(
                                                                text = note.timestampFormatted,
                                                                color = YouTubeRed,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    } else {
                                                        Text(
                                                            text = "📝 AI Summary",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = Color(0xFFAB47BC),
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                    Row {
                                                        IconButton(
                                                            onClick = {
                                                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Video Note", note.noteText))
                                                                android.widget.Toast.makeText(context, "Copied note to clipboard 📋", android.widget.Toast.LENGTH_SHORT).show()
                                                            },
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Filled.ContentCopy,
                                                                contentDescription = "Copy",
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        IconButton(
                                                            onClick = {
                                                                onDeleteNote(note.id)
                                                                android.widget.Toast.makeText(context, "Deleted note", android.widget.Toast.LENGTH_SHORT).show()
                                                            },
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Filled.DeleteOutline,
                                                                contentDescription = "Delete",
                                                                tint = Color.Gray,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = note.noteText,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    lineHeight = 20.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
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
                                onClick = { onSelectOtherVideo(other) },
                                onDeleteClick = { onNotInterested(other) },
                                onNotInterested = { onNotInterested(other) },
                                onSelectChannel = { onSelectChannel(other.channelName) }
                            )
                        }
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
    onClick: () -> Unit,
    onDeleteClick: (VideoEntity) -> Unit = {},
    onNotInterested: (VideoEntity) -> Unit = {},
    onSelectChannel: (String) -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val offsetX = remember { Animatable(0f) }
    val thresholdPx = 90f * density
    val edgeThresholdPx = 40f * density
    val context = androidx.compose.ui.platform.LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
    ) {
        // 1. Background revealed during rightward swipe ("Not Interested" action)
        if (offsetX.value > 10f) {
            val swipeProgress = (offsetX.value / thresholdPx).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (swipeProgress >= 1f) YouTubeRed else YouTubeRed.copy(alpha = 0.85f))
                    .padding(start = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.VisibilityOff,
                        contentDescription = "Not Interested",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Not Interested",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // 1b. Background revealed during leftward swipe ("Delete Video" action)
        if (offsetX.value < -10f) {
            val swipeProgress = (-offsetX.value / thresholdPx).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (swipeProgress >= 1f) Color(0xFFD32F2F) else Color(0xFFD32F2F).copy(alpha = 0.85f))
                    .padding(end = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Delete",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete Video",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 2. Foreground Card with edge-swipe handling
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(video.youtubeId) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startX = down.position.x
                        val cardWidth = size.width.toFloat()
                        val isLeftEdge = startX <= edgeThresholdPx
                        val isRightEdge = startX >= (cardWidth - edgeThresholdPx)
                        if (!isLeftEdge && !isRightEdge) return@awaitEachGesture

                        var totalDx = 0f
                        var isHorizontalLocked = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                if (isHorizontalLocked) {
                                    if (offsetX.value >= thresholdPx) {
                                        coroutineScope.launch {
                                            offsetX.animateTo(600f * density, tween(200))
                                            onNotInterested(video)
                                        }
                                    } else if (offsetX.value <= -thresholdPx) {
                                        coroutineScope.launch {
                                            offsetX.animateTo(-600f * density, tween(200))
                                            onDeleteClick(video)
                                        }
                                    } else {
                                        coroutineScope.launch {
                                            offsetX.animateTo(0f, androidx.compose.animation.core.spring())
                                        }
                                    }
                                }
                                break
                            }

                            val dragX = change.position.x - down.position.x
                            val dragY = change.position.y - down.position.y
                            totalDx = dragX

                            if (!isHorizontalLocked) {
                                if (kotlin.math.abs(dragX) > 12f * density && kotlin.math.abs(dragX) > kotlin.math.abs(dragY) * 1.3f) {
                                    isHorizontalLocked = true
                                } else if (kotlin.math.abs(dragY) > 12f * density) {
                                    break
                                }
                            }

                            if (isHorizontalLocked) {
                                change.consume()
                                val newOffset = if (isLeftEdge) {
                                    dragX.coerceIn(0f, 160f * density)
                                } else {
                                    dragX.coerceIn(-160f * density, 0f)
                                }
                                coroutineScope.launch { offsetX.snapTo(newOffset) }
                            }
                        }
                    }
                },
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

                    if (video.channelName.isNotBlank()) {
                        Text(
                            text = video.channelName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { onSelectChannel(video.channelName) }
                        )
                    }

                    val queueSubText = listOfNotNull(
                        video.publishedTimeText.takeIf { it.isNotBlank() },
                        video.viewCountText.takeIf { it.isNotBlank() }
                    ).joinToString(" • ")

                    if (queueSubText.isNotBlank()) {
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
    }
}
