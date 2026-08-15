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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
            val isShort = viewModel.isPlayingAsShort.value == true
            val aspectRatio = if (isShort) android.util.Rational(9, 16) else android.util.Rational(16, 9)
            val params = android.app.PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .build()
            enterPictureInPictureMode(params)
        }
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
    val googleAccount by viewModel.googleAccount.collectAsStateWithLifecycle()
    val areAdvertsEnabled by viewModel.areAdvertsEnabled.collectAsStateWithLifecycle()

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
                    isInPipMode = isInPipMode,
                    onEnterPip = onEnterPip,
                    onBackClick = { viewModel.clearActiveVideo() },
                    onNextShort = {
                        viewModel.playNextShort(activeVideo!!.youtubeId)
                    },
                    onPreviousShort = {
                        viewModel.playPreviousShort(activeVideo!!.youtubeId)
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
                    }
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
                            onOpenAddVideoDialog = { showAddVideoDialog = true },
                            onOpenGoogleAuth = { showGoogleAuthDialog = true },
                            onOpenSettings = { showSettingsDialog = true },
                            onRefreshFeed = { viewModel.refreshFeed() },
                            liveSearchResults = liveSearchResults,
                            categoryVideos = categoryVideos,
                            onLoadMore = { viewModel.loadMoreCategoryVideos() },
                            selectedTimeFilter = selectedTimeFilter,
                            onTimeFilterSelected = { viewModel.selectedTimeFilter.value = it },
                            selectedSubscribedChannel = selectedSubscribedChannel,
                            onSubscribedChannelSelected = { channel -> viewModel.selectSubscribedChannel(channel) },
                            onOpenHistory = { selectedNavIndex = 2 },
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
                        onSuccess = { newId ->
                            showAddVideoDialog = false
                            viewModel.setActiveVideo(newId)
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
            com.example.ui.components.SettingsDialog(
                areAdvertsEnabled = areAdvertsEnabled,
                onAdvertsToggle = { viewModel.setAdvertsEnabled(it) },
                onDismiss = { showSettingsDialog = false }
            )
        }
    }
}
