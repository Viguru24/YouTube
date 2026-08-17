package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.ui.components.AddCategoryDialog
import com.example.ui.components.AddVideoDialog
import com.example.ui.components.GoogleSignInDialog
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.theme.YouTubePlayerTheme
import com.example.ui.theme.YouTubeRed
import com.example.ui.viewmodel.YouTubeViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: YouTubeViewModel by viewModels()
    private var isInPipMode by mutableStateOf(false)

    companion object {
        const val ACTION_PIP_CONTROL = "com.example.PIP_CONTROL"
        const val EXTRA_PIP_ACTION = "pip_action"
        const val PIP_ACTION_PLAY_PAUSE = "play_pause"
        const val PIP_ACTION_REWIND = "rewind"
        const val PIP_ACTION_FORWARD = "forward"
        const val PIP_ACTION_CLOSE = "close"
    }

    private val pipReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            val action = intent?.getStringExtra(EXTRA_PIP_ACTION)
            android.util.Log.d("MainActivity", "Received PiP control action: $action")
            when (action) {
                PIP_ACTION_PLAY_PAUSE -> {
                    viewModel.togglePlayPause()
                    val newPlaying = !viewModel.isPlayerPlaying.value
                    viewModel.setPlayerPlaying(newPlaying)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        try {
                            setPictureInPictureParams(buildPipParams(newPlaying))
                        } catch (e: Exception) { }
                    }
                }
                PIP_ACTION_REWIND -> viewModel.seekBy(-10)
                PIP_ACTION_FORWARD -> viewModel.seekBy(10)
                PIP_ACTION_CLOSE -> {
                    viewModel.clearActiveVideo()
                    moveTaskToBack(true)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Register PiP control broadcast receiver with RECEIVER_NOT_EXPORTED for security
        val filter = android.content.IntentFilter(ACTION_PIP_CONTROL)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pipReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(pipReceiver, filter)
        }

        // Keep device screen on continuously (prevents sleep, dimming & screensaver)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Request Battery Optimization Exemption for uninterrupted background playback
        try {
            val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        } catch (e: Exception) {
            // Ignore if OS blocks intent
        }

        // Initialize NewPipe Extractor for native YouTube stream extraction
        try {
            org.schabi.newpipe.extractor.NewPipe.init(
                com.example.data.remote.NPDownloader.getInstance()
            )
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "NewPipe Extractor init failed: ${e.message}")
        }

        // Automatically update PiP buttons when playing state changes
        lifecycleScope.launch {
            viewModel.isPlayerPlaying.collect { isPlaying ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    try {
                        setPictureInPictureParams(buildPipParams(isPlaying))
                    } catch (e: Exception) { }
                }
            }
        }

        handleIncomingIntent(intent)

        setContent {
            YouTubePlayerTheme {
                MainAppContent(
                    viewModel = viewModel,
                    isInPipMode = isInPipMode,
                    onEnterPip = { enterPipMode() }
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: android.content.Intent?) {
        val data = intent?.dataString ?: intent?.getStringExtra(android.content.Intent.EXTRA_TEXT) ?: return
        val videoId = com.example.util.YouTubeUtils.extractVideoId(data)
        if (videoId != null && videoId.length == 11) {
            val video = com.example.data.model.VideoEntity(
                youtubeId = videoId,
                title = "YouTube Video",
                channelName = "YouTube",
                thumbnailUrl = com.example.util.YouTubeUtils.getThumbnailUrl(videoId),
                durationText = "",
                category = "YouTube"
            )
            viewModel.playVideo(video)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(pipReceiver)
        } catch (e: Exception) { }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (viewModel.activeVideo.value != null) {
            enterPipMode()
        }
    }

    private fun enterPipMode() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val params = buildPipParams(viewModel.isPlayerPlaying.value)
            enterPictureInPictureMode(params)
        }
    }

    private fun buildPipParams(isPlaying: Boolean): android.app.PictureInPictureParams {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            throw UnsupportedOperationException("PiP requires Android 8.0+")
        }

        val isShort = viewModel.isPlayingAsShort.value == true
        val aspectRatio = if (isShort) android.util.Rational(9, 16) else android.util.Rational(16, 9)

        val actions = mutableListOf<android.app.RemoteAction>()

        // 1. Rewind 10s action
        val rewindIntent = android.app.PendingIntent.getBroadcast(
            this,
            101,
            android.content.Intent(ACTION_PIP_CONTROL).setPackage(packageName).putExtra(EXTRA_PIP_ACTION, PIP_ACTION_REWIND),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        actions.add(
            android.app.RemoteAction(
                android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_media_rew),
                "-10s",
                "Rewind 10 Seconds",
                rewindIntent
            )
        )

        // 2. Play / Pause action
        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseText = if (isPlaying) "Pause" else "Play"
        val playPauseIntent = android.app.PendingIntent.getBroadcast(
            this,
            102,
            android.content.Intent(ACTION_PIP_CONTROL).setPackage(packageName).putExtra(EXTRA_PIP_ACTION, PIP_ACTION_PLAY_PAUSE),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        actions.add(
            android.app.RemoteAction(
                android.graphics.drawable.Icon.createWithResource(this, playPauseIcon),
                playPauseText,
                playPauseText,
                playPauseIntent
            )
        )

        // 3. Fast Forward 10s action
        val forwardIntent = android.app.PendingIntent.getBroadcast(
            this,
            103,
            android.content.Intent(ACTION_PIP_CONTROL).setPackage(packageName).putExtra(EXTRA_PIP_ACTION, PIP_ACTION_FORWARD),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        actions.add(
            android.app.RemoteAction(
                android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_media_ff),
                "+10s",
                "Forward 10 Seconds",
                forwardIntent
            )
        )

        // 4. Close Pop-Up action
        val closeIntent = android.app.PendingIntent.getBroadcast(
            this,
            104,
            android.content.Intent(ACTION_PIP_CONTROL).setPackage(packageName).putExtra(EXTRA_PIP_ACTION, PIP_ACTION_CLOSE),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        actions.add(
            android.app.RemoteAction(
                android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                "Close",
                "Close Pop-up",
                closeIntent
            )
        )

        val builder = android.app.PictureInPictureParams.Builder()
            .setAspectRatio(aspectRatio)
            .setActions(actions)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(true)
        }

        return builder.build()
    }
}

@Composable
fun MainAppContent(
    viewModel: YouTubeViewModel,
    isInPipMode: Boolean = false,
    onEnterPip: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedNavIndex by remember { mutableIntStateOf(0) } // 0: Home, 1: Library, 2: History

    var showAddVideoDialog by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showGoogleAuthDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val favoriteVideos by viewModel.favoriteVideos.collectAsStateWithLifecycle()
    val watchLaterVideos by viewModel.watchLaterVideos.collectAsStateWithLifecycle()
    val watchHistory by viewModel.watchHistory.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val subscribedCreators by viewModel.subscribedCreators.collectAsStateWithLifecycle()
    val dislikedVideoIds by viewModel.dislikedVideoIds.collectAsStateWithLifecycle()
    val googleAccount by viewModel.googleAccount.collectAsStateWithLifecycle()
    val areAdvertsEnabled by viewModel.areAdvertsEnabled.collectAsStateWithLifecycle()

    var showManageTopicsAndCreatorsDialog by remember { mutableStateOf(false) }
    var manageInitialTab by remember { mutableIntStateOf(0) }

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val selectedTimeFilter by viewModel.selectedTimeFilter.collectAsStateWithLifecycle()
    val selectedSubscribedChannel by viewModel.selectedSubscribedChannel.collectAsStateWithLifecycle()
    val liveSearchResults by viewModel.liveSearchResults.collectAsStateWithLifecycle()
    val categoryVideos by viewModel.categoryVideos.collectAsStateWithLifecycle()

    val downloadedVideos by viewModel.downloadedVideos.collectAsStateWithLifecycle()
    val downloadProgressMap by viewModel.downloadProgressMap.collectAsStateWithLifecycle()

    val activeVideoId by viewModel.activeVideoId.collectAsStateWithLifecycle()
    val activeVideo by viewModel.activeVideo.collectAsStateWithLifecycle()
    val activeNotes by viewModel.activeNotes.collectAsStateWithLifecycle()

    // Start Foreground Media Playback Service with WakeLock when a video is played
    LaunchedEffect(activeVideo) {
        activeVideo?.let { v ->
            try {
                val intent = android.content.Intent(context, com.example.service.MediaPlaybackService::class.java).apply {
                    putExtra(com.example.service.MediaPlaybackService.EXTRA_TITLE, v.title)
                    putExtra(com.example.service.MediaPlaybackService.EXTRA_CHANNEL, v.channelName)
                }
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) { }
        }
    }

    // Intercept Android system edge swipe-back gesture: navigate within YouTube app instead of exiting to launcher
    androidx.activity.compose.BackHandler(enabled = !isInPipMode && (activeVideo != null || selectedNavIndex != 0)) {
        if (activeVideo != null) {
            viewModel.clearActiveVideo()
        } else if (selectedNavIndex != 0) {
            selectedNavIndex = 0
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val isPlayingAsShort by viewModel.isPlayingAsShort.collectAsState()

        if (activeVideo != null) {
            val isShort = isPlayingAsShort == true
            if (isShort) {
                // Full Screen Portrait Shorts Player View with swipe gestures
                com.example.ui.components.ShortsPlayerView(
                    videoId = activeVideo!!.youtubeId,
                    videoTitle = activeVideo!!.title,
                    channelName = activeVideo!!.channelName,
                    isFavorite = activeVideo!!.isFavorite,
                    isWatchLater = activeVideo!!.isWatchLater,
                    isDisliked = activeVideo!!.youtubeId in dislikedVideoIds,
                    isInPipMode = isInPipMode,
                    onEnterPip = onEnterPip,
                    playerCommandFlow = viewModel.playerCommand,
                    onPlayingStateChanged = { isPlaying -> viewModel.setPlayerPlaying(isPlaying) },
                    onBackClick = { viewModel.clearActiveVideo() },
                    onNextShort = {
                        viewModel.playNextShort(activeVideo!!.youtubeId)
                    },
                    onPreviousShort = {
                        viewModel.playPreviousShort(activeVideo!!.youtubeId)
                    },
                    onThumbsUp = { viewModel.thumbsUpShort(activeVideo!!) },
                    onThumbsDown = {
                        val v = activeVideo!!
                        viewModel.thumbsDownShort(v)
                        android.widget.Toast.makeText(context, "Disliked & removed from feed 👎", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    onFavoriteToggle = { viewModel.toggleFavorite(activeVideo!!.youtubeId, activeVideo!!.isFavorite) },
                    onWatchLaterToggle = { viewModel.toggleWatchLater(activeVideo!!.youtubeId, activeVideo!!.isWatchLater) },
                    onPositionUpdate = { _ -> }
                )
            } else {
                val isDownloadedVideo = activeVideo!!.isDownloaded || downloadedVideos.any { it.youtubeId == activeVideo!!.youtubeId }
                val currentProgress = downloadProgressMap[activeVideo!!.youtubeId] ?: 0

                // Standard Landscape/Portrait Video Player Screen
                PlayerScreen(
                    video = activeVideo!!,
                    notes = activeNotes,
                    playlistVideos = videos,
                    googleAccount = googleAccount,
                    isInPipMode = isInPipMode,
                    onEnterPip = onEnterPip,
                    playerCommandFlow = viewModel.playerCommand,
                    onPlayingStateChanged = { isPlaying -> viewModel.setPlayerPlaying(isPlaying) },
                    onBackClick = { viewModel.clearActiveVideo() },
                    onFavoriteToggle = { v -> viewModel.toggleFavorite(v.youtubeId, v.isFavorite) },
                    onWatchLaterToggle = { v -> viewModel.toggleWatchLater(v.youtubeId, v.isWatchLater) },
                    onAddNote = { timeSec, timeStr, noteText ->
                        viewModel.addNoteToActiveVideo(timeSec, timeStr, noteText)
                    },
                    onDeleteNote = { noteId -> viewModel.deleteNote(noteId) },
                    onSelectOtherVideo = { v -> viewModel.playVideo(v) },
                    onOpenGoogleAuth = { showGoogleAuthDialog = true },
                    areAdvertsEnabled = areAdvertsEnabled,
                    onNotInterested = { v -> viewModel.deleteVideo(v) },
                    onSaveToSubject = { video, subject -> viewModel.updateVideoCategory(video.youtubeId, subject) },
                    isDownloaded = isDownloadedVideo,
                    downloadProgress = currentProgress,
                    onDownloadClick = {
                        viewModel.downloadVideo(activeVideo!!, onComplete = {
                            android.widget.Toast.makeText(context, "Video Downloaded for Offline Watching! ✈️", android.widget.Toast.LENGTH_LONG).show()
                        }, onError = { err ->
                            android.widget.Toast.makeText(context, err, android.widget.Toast.LENGTH_LONG).show()
                        })
                    },
                    onDeleteDownloadClick = {
                        viewModel.deleteDownloadedVideo(activeVideo!!)
                        android.widget.Toast.makeText(context, "Removed from offline downloads", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    subscribedCreators = subscribedCreators,
                    onToggleSubscribe = { channelName -> viewModel.toggleSubscribedCreator(channelName) }
                )
            }
        } else {
            // Main Bottom Bar Layout Screen
            Scaffold(
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .height(52.dp)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .testTag("bottom_nav_bar")
                    ) {
                        NavigationBarItem(
                            selected = selectedNavIndex == 0,
                            onClick = { selectedNavIndex = 0 },
                            icon = {
                                Icon(
                                    imageVector = if (selectedNavIndex == 0) Icons.Filled.Home else Icons.Outlined.Home,
                                    contentDescription = "Home",
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            label = { Text("Home", fontSize = 10.sp, fontWeight = FontWeight.Medium) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = YouTubeRed,
                                indicatorColor = YouTubeRed.copy(alpha = 0.8f)
                            ),
                            modifier = Modifier.testTag("nav_item_home")
                        )

                        NavigationBarItem(
                            selected = selectedNavIndex == 1,
                            onClick = { selectedNavIndex = 1 },
                            icon = {
                                Icon(
                                    imageVector = if (selectedNavIndex == 1) Icons.Filled.VideoLibrary else Icons.Outlined.VideoLibrary,
                                    contentDescription = "Library",
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            label = { Text("Library", fontSize = 10.sp, fontWeight = FontWeight.Medium) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = YouTubeRed,
                                indicatorColor = YouTubeRed.copy(alpha = 0.8f)
                            ),
                            modifier = Modifier.testTag("nav_item_library")
                        )

                        NavigationBarItem(
                            selected = selectedNavIndex == 2,
                            onClick = { selectedNavIndex = 2 },
                            icon = {
                                Icon(
                                    imageVector = if (selectedNavIndex == 2) Icons.Filled.History else Icons.Outlined.History,
                                    contentDescription = "History",
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            label = { Text("History", fontSize = 10.sp, fontWeight = FontWeight.Medium) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = YouTubeRed,
                                indicatorColor = YouTubeRed.copy(alpha = 0.8f)
                            ),
                            modifier = Modifier.testTag("nav_item_history")
                        )
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (selectedNavIndex) {
                        0 -> HomeScreen(
                            videos = videos,
                            historyVideos = watchHistory,
                            categories = categories,
                            selectedCategory = selectedCategory,
                            searchQuery = searchQuery,
                            googleAccount = googleAccount,
                            onCategorySelected = { viewModel.selectedCategory.value = it },
                            onSearchQueryChanged = { viewModel.searchQuery.value = it },
                            onVideoClick = { v -> viewModel.playVideo(v, isShort = false) },
                            onShortClick = { v -> viewModel.playShort(v) },
                            onFavoriteToggle = { v -> viewModel.toggleFavorite(v.youtubeId, v.isFavorite) },
                            onWatchLaterToggle = { v -> viewModel.toggleWatchLater(v.youtubeId, v.isWatchLater) },
                            onDeleteVideo = { v -> viewModel.deleteVideo(v) },
                            onNotInterested = { v -> viewModel.markNotInterested(v) },
                            onOpenAddVideoDialog = { showAddVideoDialog = true },
                            onOpenGoogleAuth = { showGoogleAuthDialog = true },
                            onOpenSettings = { showSettingsDialog = true },
                            onRefreshFeed = { viewModel.refreshFeed() },
                            liveSearchResults = liveSearchResults,
                            categoryVideos = categoryVideos,
                            dislikedVideoIds = dislikedVideoIds,
                            onLoadMore = { viewModel.loadMoreCategoryVideos() },
                            selectedTimeFilter = selectedTimeFilter,
                            onTimeFilterSelected = { viewModel.selectedTimeFilter.value = it },
                            selectedSubscribedChannel = selectedSubscribedChannel,
                            onSubscribedChannelSelected = { channel -> viewModel.selectSubscribedChannel(channel) },
                            onRefreshSubscribedChannel = { channel -> viewModel.refreshSubscribedChannel(channel) },
                            onOpenHistory = { selectedNavIndex = 2 },
                            onOpenManageTopicsAndCreators = { tab ->
                                manageInitialTab = tab
                                showManageTopicsAndCreatorsDialog = true
                            },
                            onSaveToSubject = { video, subject -> viewModel.updateVideoCategory(video.youtubeId, subject) }
                        )
                        1 -> LibraryScreen(
                            categories = categories,
                            favoriteVideos = favoriteVideos,
                            watchLaterVideos = watchLaterVideos,
                            allVideos = videos,
                            googleAccount = googleAccount,
                            onVideoClick = { v -> viewModel.playVideo(v) },
                            onFavoriteToggle = { v -> viewModel.toggleFavorite(v.youtubeId, v.isFavorite) },
                            onWatchLaterToggle = { v -> viewModel.toggleWatchLater(v.youtubeId, v.isWatchLater) },
                            onDeleteVideo = { v -> viewModel.deleteVideo(v) },
                            onOpenAddCategoryDialog = { showAddCategoryDialog = true },
                            onOpenAddVideoDialog = { showAddVideoDialog = true },
                            onOpenGoogleAuth = { showGoogleAuthDialog = true },
                            historyVideos = watchHistory,
                            downloadedVideos = downloadedVideos,
                            onDeleteDownload = { v -> viewModel.deleteDownloadedVideo(v) },
                            onOpenHistory = { selectedNavIndex = 2 }
                        )
                        2 -> HistoryScreen(
                            historyVideos = watchHistory,
                            onVideoClick = { v -> viewModel.playVideo(v) },
                            onFavoriteToggle = { v -> viewModel.toggleFavorite(v.youtubeId, v.isFavorite) },
                            onWatchLaterToggle = { v -> viewModel.toggleWatchLater(v.youtubeId, v.isWatchLater) },
                            onDeleteVideo = { v -> viewModel.deleteVideo(v) },
                            onClearHistory = { viewModel.clearHistory() }
                        )
                    }
                }
            }
        }

        // Add Video Dialog
        if (showAddVideoDialog) {
            AddVideoDialog(
                categories = categories,
                onDismiss = { showAddVideoDialog = false },
                onAddVideo = { url, title, channel, cat, dur, initNote, onError ->
                    viewModel.addVideoFromUrl(
                        urlOrId = url,
                        title = title,
                        channelName = channel,
                        category = cat,
                        durationText = dur,
                        initialNote = initNote,
                        onSuccess = { video ->
                            showAddVideoDialog = false
                            viewModel.playVideo(video, isShort = com.example.util.YouTubeUtils.isShortVideo(video))
                        },
                        onError = onError
                    )
                }
            )
        }

        // Add Category Dialog
        if (showAddCategoryDialog) {
            AddCategoryDialog(
                onDismiss = { showAddCategoryDialog = false },
                onAddCategory = { name, icon, colorHex ->
                    viewModel.addCategory(name, icon, colorHex)
                }
            )
        }

        // Google Sign-In Dialog
        if (showGoogleAuthDialog) {
            GoogleSignInDialog(
                account = googleAccount,
                onDismiss = { showGoogleAuthDialog = false },
                onSignIn = { name, email ->
                    viewModel.signInGoogle(name, email)
                    showGoogleAuthDialog = false
                },
                onSignOut = {
                    viewModel.signOutGoogle()
                    showGoogleAuthDialog = false
                }
            )
        }

        // App Settings Dialog
        if (showSettingsDialog) {
            val algorithmSettings by viewModel.algorithmSettings.collectAsStateWithLifecycle()
            val mutedChannels by viewModel.mutedChannels.collectAsStateWithLifecycle()
            com.example.ui.components.SettingsDialog(
                areAdvertsEnabled = areAdvertsEnabled,
                onAdvertsToggle = { viewModel.setAdvertsEnabled(it) },
                algorithmSettings = algorithmSettings,
                onAlgorithmSettingsChanged = { viewModel.updateAlgorithmSettings(it) },
                mutedChannels = mutedChannels,
                onUnmuteChannel = { viewModel.unmuteChannel(it) },
                onOpenManageTopicsAndCreators = {
                    manageInitialTab = 0
                    showManageTopicsAndCreatorsDialog = true
                },
                onDismiss = { showSettingsDialog = false }
            )
        }

        // Manage Topics & Creators Dialog
        if (showManageTopicsAndCreatorsDialog) {
            com.example.ui.components.ManageTopicsAndCreatorsDialog(
                subscribedCreators = subscribedCreators,
                categories = categories,
                onAddCreator = { name -> viewModel.addSubscribedCreator(name) },
                onRemoveCreator = { name -> viewModel.removeSubscribedCreator(name) },
                onRenameCreator = { oldName, newName -> viewModel.renameSubscribedCreator(oldName, newName) },
                onAddCategory = { name, icon, colorHex -> viewModel.addCategory(name, icon, colorHex) },
                onRemoveCategory = { cat -> viewModel.deleteCategory(cat) },
                onRenameCategory = { cat, newName -> viewModel.renameCategory(cat, newName) },
                initialTab = manageInitialTab,
                onDismiss = { showManageTopicsAndCreatorsDialog = false }
            )
        }
    }
}
