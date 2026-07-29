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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            YouTubePlayerTheme {
                MainAppContent(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainAppContent(viewModel: YouTubeViewModel) {
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
    val liveSearchResults by viewModel.liveSearchResults.collectAsStateWithLifecycle()
    val categoryVideos by viewModel.categoryVideos.collectAsStateWithLifecycle()

    val activeVideoId by viewModel.activeVideoId.collectAsStateWithLifecycle()
    val activeVideo by viewModel.activeVideo.collectAsStateWithLifecycle()
    val activeNotes by viewModel.activeNotes.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        if (activeVideo != null) {
            // Full Screen Player View
            PlayerScreen(
                video = activeVideo!!,
                notes = activeNotes,
                playlistVideos = videos,
                googleAccount = googleAccount,
                onBackClick = { viewModel.clearActiveVideo() },
                onFavoriteToggle = { v -> viewModel.toggleFavorite(v.youtubeId, v.isFavorite) },
                onWatchLaterToggle = { v -> viewModel.toggleWatchLater(v.youtubeId, v.isWatchLater) },
                onAddNote = { timeSec, timeStr, noteText ->
                    viewModel.addNoteToActiveVideo(timeSec, timeStr, noteText)
                },
                onDeleteNote = { noteId -> viewModel.deleteNote(noteId) },
                onSelectOtherVideo = { v -> viewModel.playVideo(v) },
                onOpenGoogleAuth = { showGoogleAuthDialog = true },
                areAdvertsEnabled = areAdvertsEnabled
            )
        } else {
            // Main Bottom Bar Layout Screen
            Scaffold(
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .testTag("bottom_nav_bar")
                    ) {
                        NavigationBarItem(
                            selected = selectedNavIndex == 0,
                            onClick = { selectedNavIndex = 0 },
                            icon = {
                                Icon(
                                    imageVector = if (selectedNavIndex == 0) Icons.Filled.Home else Icons.Outlined.Home,
                                    contentDescription = "Home"
                                )
                            },
                            label = { Text("Home", fontWeight = FontWeight.SemiBold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = YouTubeRed,
                                indicatorColor = YouTubeRed
                            ),
                            modifier = Modifier.testTag("nav_item_home")
                        )

                        NavigationBarItem(
                            selected = selectedNavIndex == 1,
                            onClick = { selectedNavIndex = 1 },
                            icon = {
                                Icon(
                                    imageVector = if (selectedNavIndex == 1) Icons.Filled.VideoLibrary else Icons.Outlined.VideoLibrary,
                                    contentDescription = "Library"
                                )
                            },
                            label = { Text("Library", fontWeight = FontWeight.SemiBold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = YouTubeRed,
                                indicatorColor = YouTubeRed
                            ),
                            modifier = Modifier.testTag("nav_item_library")
                        )

                        NavigationBarItem(
                            selected = selectedNavIndex == 2,
                            onClick = { selectedNavIndex = 2 },
                            icon = {
                                Icon(
                                    imageVector = if (selectedNavIndex == 2) Icons.Filled.History else Icons.Outlined.History,
                                    contentDescription = "History"
                                )
                            },
                            label = { Text("History", fontWeight = FontWeight.SemiBold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = YouTubeRed,
                                indicatorColor = YouTubeRed
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
                            onVideoClick = { v -> viewModel.playVideo(v) },
                            onFavoriteToggle = { v -> viewModel.toggleFavorite(v.youtubeId, v.isFavorite) },
                            onWatchLaterToggle = { v -> viewModel.toggleWatchLater(v.youtubeId, v.isWatchLater) },
                            onDeleteVideo = { v -> viewModel.deleteVideo(v) },
                            onOpenAddVideoDialog = { showAddVideoDialog = true },
                            onOpenGoogleAuth = { showGoogleAuthDialog = true },
                            onOpenSettings = { showSettingsDialog = true },
                            onRefreshFeed = { viewModel.refreshFeed() },
                            liveSearchResults = liveSearchResults,
                            categoryVideos = categoryVideos,
                            onLoadMore = { viewModel.loadMoreCategoryVideos() }
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
                            onOpenGoogleAuth = { showGoogleAuthDialog = true }
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
