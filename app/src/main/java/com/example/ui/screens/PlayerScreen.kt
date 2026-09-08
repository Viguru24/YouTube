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
import com.example.util.findActivity
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
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
    isDisliked: Boolean = false,
    onDislikeToggle: (VideoEntity) -> Unit = {},
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
    subscribedCreators: List<String> = emptyList(),
    onToggleSubscribe: (String) -> Unit = {},
    onPermanentlyDeleteChannel: (String) -> Unit = {},
    onSelectChannel: (String) -> Unit = {},
    onPositionUpdate: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var webViewInstance by remember { mutableStateOf<Any?>(null) }
    var showDebugConsole by remember { mutableStateOf(false) }
    var showSaveToSubjectDialog by remember { mutableStateOf(false) }
    var showAiSummaryModal by remember { mutableStateOf(false) }
    var showDeleteChannelDialog by remember { mutableStateOf(false) }
    var localIsFavorite by remember(video.youtubeId, video.isFavorite) { mutableStateOf(video.isFavorite) }
    var localIsDisliked by remember(video.youtubeId, isDisliked) { mutableStateOf(isDisliked) }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val isTablet = remember {
        val dm = context.resources.displayMetrics
        val wDp = dm.widthPixels / dm.density
        val hDp = dm.heightPixels / dm.density
        minOf(wDp, hDp) >= 600f
    }
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    var isMaximized by remember { mutableStateOf(false) }
    // On tablets, landscape is the default normal orientation -> only enter fullscreen when explicitly maximized
    val isFullscreen = if (isTablet) (isMaximized || isInPipMode) else (isLandscape || isMaximized || isInPipMode)

    val otherVideos = remember(video.youtubeId, playlistVideos) {
        playlistVideos
            .filter { it.youtubeId != video.youtubeId && it.lastPositionSeconds == 0 && it.lastWatchedTimestamp == 0L }
            .sortedWith(compareBy<VideoEntity> {
                com.example.util.YouTubeUtils.parsePublishedTimeToSeconds(it.publishedTimeText)
            })
    }

    // Automatically manage immersive system bars when phone is physically rotated 90 degrees or maximized
    LaunchedEffect(isLandscape, isMaximized, isInPipMode) {
        val act = context.findActivity()
        if (act != null) {
            val window = act.window
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            if (isFullscreen) {
                insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else if (!isInPipMode) {
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    var manualOrientationLock by remember { mutableStateOf<Int?>(null) }

    // Hardware accelerometer orientation tracking with zero Binder IPC overhead
    DisposableEffect(isTablet) {
        val act = context.findActivity()
        if (!isTablet && act != null) {
            var currentAppliedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            val orientationListener = object : android.view.OrientationEventListener(act, android.hardware.SensorManager.SENSOR_DELAY_NORMAL) {
                override fun onOrientationChanged(orientation: Int) {
                    if (orientation == ORIENTATION_UNKNOWN) return

                    // Wide deadbands (hysteresis) to prevent flickering near transitions
                    val isPhysicalLandscape = (orientation in 70..110) || (orientation in 250..290)
                    val isPhysicalPortrait = (orientation in 345..360) || (orientation in 0..15)

                    if (isPhysicalLandscape) {
                        if (manualOrientationLock != android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                            if (currentAppliedOrientation != android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
                                currentAppliedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                act.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            }
                        }
                    } else if (isPhysicalPortrait) {
                        manualOrientationLock = null
                        if (currentAppliedOrientation != android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER) {
                            currentAppliedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER
                            act.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER
                        }
                    }
                }
            }
            if (orientationListener.canDetectOrientation()) {
                orientationListener.enable()
            }
            onDispose {
                orientationListener.disable()
                act.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        } else {
            onDispose { }
        }
    }

    // When in fullscreen landscape, intercept Back gesture/button to return directly to portrait
    androidx.activity.compose.BackHandler(enabled = isFullscreen && !isInPipMode) {
        val act = context.findActivity()
        isMaximized = false
        manualOrientationLock = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        act?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    val toggleFullscreen: () -> Unit = {
        val act = context.findActivity()
        if (act != null) {
            if (isFullscreen) {
                // Return directly to portrait mode instantly (0ms delay)
                isMaximized = false
                manualOrientationLock = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                act.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                android.widget.Toast.makeText(context, "📱 Portrait Mode", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                // Enter fullscreen landscape instantly
                isMaximized = true
                manualOrientationLock = null
                act.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                android.widget.Toast.makeText(context, "📺 Fullscreen Landscape", android.widget.Toast.LENGTH_SHORT).show()
            }
        } else {
            isMaximized = !isMaximized
        }
    }

    val rotate180: () -> Unit = {
        val act = context.findActivity()
        if (act != null) {
            val currentOrientation = act.requestedOrientation
            val target = if (currentOrientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE) {
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } else {
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            }
            isMaximized = true
            act.requestedOrientation = target
            android.widget.Toast.makeText(context, "🔄 Flipped 180°", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val handleNextVideo: () -> Unit = {
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
    }

    val handleDislikeAndNext: () -> Unit = {
        localIsDisliked = true
        localIsFavorite = false
        onDislikeToggle(video)
        android.widget.Toast.makeText(context, "👎 I don't like • Next video", android.widget.Toast.LENGTH_SHORT).show()
        handleNextVideo()
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
                        IconButton(onClick = onEnterPip) {
                            Icon(
                                imageVector = Icons.Filled.PictureInPictureAlt,
                                contentDescription = "Pop-Out Floating Player (PiP)",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
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
                .padding(if (isFullscreen || isInPipMode) PaddingValues(0.dp) else innerPadding)
                .background(Color.Black)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Video Player Area - Single persistent ExoPlayer instance across rotation & fullscreen
                Box(
                    modifier = (if (isFullscreen || isInPipMode) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    })
                    .clipToBounds()
                    .background(Color.Black)
                ) {
                    YouTubePlayerView(
                        videoId = video.youtubeId,
                        startSeconds = video.lastPositionSeconds,
                        areAdvertsEnabled = areAdvertsEnabled,
                        showDebugConsole = showDebugConsole && !isInPipMode,
                        onToggleDebugConsole = { showDebugConsole = !showDebugConsole },
                        onEnterPip = onEnterPip,
                        isInPipMode = isInPipMode,
                        onRotate180 = rotate180,
                        onPositionUpdate = onPositionUpdate,
                        playerCommandFlow = playerCommandFlow,
                        onPlayingStateChanged = onPlayingStateChanged,
                        onNextVideo = handleNextVideo,
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
                        isFavorite = localIsFavorite,
                        isWatchLater = video.isWatchLater,
                        isDisliked = localIsDisliked,
                        onDislikeToggle = {
                            handleDislikeAndNext()
                        },
                        onFavoriteToggle = {
                            localIsFavorite = !localIsFavorite
                            if (localIsFavorite) localIsDisliked = false
                            onFavoriteToggle(video)
                            val msg = if (localIsFavorite) "Liked 👍" else "Unliked"
                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                        },
                        onWatchLaterToggle = { onWatchLaterToggle(video) },
                        onSaveToSubject = { showSaveToSubjectDialog = true },
                        isDownloaded = isDownloaded,
                        downloadProgress = downloadProgress,
                        localFilePath = video.localFilePath,
                        onDownloadClick = onDownloadClick,
                        onDeleteDownloadClick = onDeleteDownloadClick,
                        onAiSummaryClick = { showAiSummaryModal = true },
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.surface),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        // Title + Channel + Action Pills
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = video.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Channel Info & Subscribe
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onSelectChannel(video.channelName) }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = video.channelName.take(1).uppercase(),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = video.channelName,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (video.viewCountText.isNotBlank()) {
                                                Text(
                                                    text = video.viewCountText,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    val isSubbed = subscribedCreators.any { it.equals(video.channelName.trim(), ignoreCase = true) }
                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable {
                                                onToggleSubscribe(video.channelName)
                                                val msg = if (!isSubbed) "Subscribed to ${video.channelName}! 🎉" else "Unsubscribed from ${video.channelName}"
                                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                            },
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (isSubbed) MaterialTheme.colorScheme.surfaceVariant else YouTubeRed
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (isSubbed) {
                                                Icon(
                                                    imageVector = Icons.Filled.Check,
                                                    contentDescription = "Subscribed",
                                                    tint = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                            Text(
                                                text = if (isSubbed) "Subscribed" else "Subscribe",
                                                color = if (isSubbed) MaterialTheme.colorScheme.onSurface else Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    IconButton(
                                        onClick = { showDeleteChannelDialog = true },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Block,
                                            contentDescription = "Delete Channel Permanently",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                if (showDeleteChannelDialog) {
                                    AlertDialog(
                                        onDismissRequest = { showDeleteChannelDialog = false },
                                        icon = {
                                            Icon(
                                                imageVector = Icons.Filled.DeleteForever,
                                                contentDescription = null,
                                                tint = YouTubeRed,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        },
                                        title = {
                                            Text(
                                                text = "Permanently Delete Channel?",
                                                fontWeight = FontWeight.Bold
                                            )
                                        },
                                        text = {
                                            Text(
                                                text = "All videos from '${video.channelName}' will be removed and you will never see recommendations or videos from this channel again.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        },
                                        confirmButton = {
                                            Button(
                                                onClick = {
                                                    showDeleteChannelDialog = false
                                                    onPermanentlyDeleteChannel(video.channelName)
                                                    android.widget.Toast.makeText(context, "'${video.channelName}' permanently deleted and blocked 🚫", android.widget.Toast.LENGTH_SHORT).show()
                                                    onBackClick()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed)
                                            ) {
                                                Text("Delete Permanently", color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { showDeleteChannelDialog = false }) {
                                                Text("Cancel")
                                            }
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Action Pills Row: Like | I Don't Like 👎 | ✨ AI Chat | Watch Later | Organize | Share
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 1. Like Button
                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(18.dp))
                                            .clickable {
                                                localIsFavorite = !localIsFavorite
                                                if (localIsFavorite) localIsDisliked = false
                                                onFavoriteToggle(video)
                                                val msg = if (localIsFavorite) "Liked 👍" else "Unliked"
                                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                            },
                                        shape = RoundedCornerShape(18.dp),
                                        color = if (localIsFavorite) YouTubeRed.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                        border = if (localIsFavorite) androidx.compose.foundation.BorderStroke(1.dp, YouTubeRed.copy(alpha = 0.6f)) else null
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (localIsFavorite) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                                                contentDescription = "Like",
                                                tint = if (localIsFavorite) YouTubeRed else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Text(
                                                text = if (localIsFavorite) "Liked" else "Like",
                                                color = if (localIsFavorite) YouTubeRed else MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }

                                    // 2. "I don't like" Button (Immediately downvotes and moves to next video!)
                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(18.dp))
                                            .clickable { handleDislikeAndNext() },
                                        shape = RoundedCornerShape(18.dp),
                                        color = if (localIsDisliked) YouTubeRed.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                        border = if (localIsDisliked) androidx.compose.foundation.BorderStroke(1.dp, YouTubeRed.copy(alpha = 0.6f)) else null
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (localIsDisliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                                                contentDescription = "I don't like",
                                                tint = if (localIsDisliked) YouTubeRed else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Text(
                                                text = "I don't like",
                                                color = if (localIsDisliked) YouTubeRed else MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }

                                    // 3. ✨ AI Chat Button ("tiny little AI button" that opens the tight chat window!)
                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(18.dp))
                                            .clickable { showAiSummaryModal = true },
                                        shape = RoundedCornerShape(18.dp),
                                        color = YouTubeRed.copy(alpha = 0.18f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, YouTubeRed.copy(alpha = 0.5f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.AutoAwesome,
                                                contentDescription = "AI Chat",
                                                tint = YouTubeRed,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Text(
                                                text = "AI Chat",
                                                color = YouTubeRed,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }

                                    // 4. Watch Later Pill
                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(18.dp))
                                            .clickable { onWatchLaterToggle(video) },
                                        shape = RoundedCornerShape(18.dp),
                                        color = if (video.isWatchLater) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (video.isWatchLater) Icons.Filled.Bookmark else Icons.Outlined.WatchLater,
                                                contentDescription = "Watch Later",
                                                tint = if (video.isWatchLater) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Text(
                                                text = if (video.isWatchLater) "Saved" else "Save",
                                                color = if (video.isWatchLater) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }

                                    // 5. Organize Pill
                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(18.dp))
                                            .clickable { showSaveToSubjectDialog = true },
                                        shape = RoundedCornerShape(18.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.PlaylistAdd,
                                                contentDescription = "Organize",
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Text(
                                                text = "Organize",
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }

                                    // 6. Share Pill
                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(18.dp))
                                            .clickable {
                                                val sendIntent = android.content.Intent().apply {
                                                    action = android.content.Intent.ACTION_SEND
                                                    putExtra(android.content.Intent.EXTRA_TEXT, "https://youtu.be/${video.youtubeId}")
                                                    type = "text/plain"
                                                }
                                                val shareIntent = android.content.Intent.createChooser(sendIntent, null)
                                                context.startActivity(shareIntent)
                                            },
                                        shape = RoundedCornerShape(18.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Share,
                                                contentDescription = "Share",
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Text(
                                                text = "Share",
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Up Next Queue
                        if (otherVideos.isNotEmpty()) {
                            item {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                                )
                                Text(
                                    text = "Up Next",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
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
                // Seek player to timestamp (supports both native ExoPlayer and WebView fallback)
                try {
                    (webViewInstance as? androidx.media3.exoplayer.ExoPlayer)?.seekTo((seekSec * 1000L))
                    (webViewInstance as? android.webkit.WebView)?.evaluateJavascript("if (window.player && player.seekTo) player.seekTo($seekSec, true);", null)
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
    onSelectChannel: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val offsetX = remember(video.youtubeId) { Animatable(0f) }
    var isDismissed by remember(video.youtubeId) { mutableStateOf(false) }
    if (isDismissed) return

    val thresholdPx = 90f * density
    val edgeThresholdPx = 65f * density
    val context = androidx.compose.ui.platform.LocalContext.current

    Box(
        modifier = modifier
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
                                            isDismissed = true
                                            onNotInterested(video)
                                            android.widget.Toast.makeText(context, "Marked Not Interested 🚫", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    } else if (offsetX.value <= -thresholdPx) {
                                        coroutineScope.launch {
                                            offsetX.animateTo(-600f * density, tween(200))
                                            isDismissed = true
                                            onDeleteClick(video)
                                            android.widget.Toast.makeText(context, "Video Deleted 🗑️", android.widget.Toast.LENGTH_SHORT).show()
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
                                val newOffset = dragX.coerceIn(-200f * density, 200f * density)
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
