package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.AddCategoryDialog
import com.example.ui.components.AddVideoDialog
import com.example.ui.components.DiagnosticOverlay
import com.example.ui.components.GoogleSignInDialog
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.theme.YouTubePlayerTheme
import com.example.ui.theme.YouTubeRed
import com.example.ui.viewmodel.YouTubeViewModel
import com.example.util.DiagnosticLogger
import com.example.util.PkceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

class MainActivity : ComponentActivity() {

    private val viewModel: YouTubeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        DiagnosticLogger.init(applicationContext)
        // Handle OAuth2 deep link if app was cold-started via redirect
        handleOAuthIntent(intent)

        setContent {
            YouTubePlayerTheme {
                MainAppContent(viewModel = viewModel)
            }
        }
    }

    // Called when app is already running (singleTask) and receives the OAuth2 redirect deep link
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthIntent(intent)
    }

    private fun handleOAuthIntent(intent: Intent?) {
        val uri: Uri = intent?.data ?: return
        val scheme = uri.scheme ?: return
        if (!scheme.startsWith("com.googleusercontent.apps")) return

        val code = uri.getQueryParameter("code")
            ?: run {
                // Fallback: manually parse query string for single-slash URIs (opaque URIs)
                val uriStr = uri.toString()
                val qIdx = uriStr.indexOf('?')
                if (qIdx >= 0) {
                    uriStr.substring(qIdx + 1).split("&")
                        .map { it.split("=") }
                        .firstOrNull { it.firstOrNull() == "code" }
                        ?.getOrNull(1)
                } else null
            }
        val error = uri.getQueryParameter("error")

        if (error != null) {
            Toast.makeText(this, "OAuth2 cancelled: $error", Toast.LENGTH_SHORT).show()
            return
        }

        if (code.isNullOrBlank()) return

        // Exchange the authorization code for an access token using PKCE
        val codeVerifier = PkceStore.codeVerifier
        if (codeVerifier.isBlank()) {
            Toast.makeText(this, "OAuth2 error: PKCE verifier missing", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Google permission granted! Syncing YouTube history...", Toast.LENGTH_LONG).show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val body = FormBody.Builder()
                    .add("grant_type", "authorization_code")
                    .add("code", code)
                    .add("redirect_uri", "com.googleusercontent.apps.465362446681-0sfu3enhj0ab66j3k1j676obimach39j:/oauth2redirect")
                    .add("client_id", "465362446681-0sfu3enhj0ab66j3k1j676obimach39j.apps.googleusercontent.com")
                    .add("code_verifier", codeVerifier)
                    .build()
                val request = Request.Builder()
                    .url("https://oauth2.googleapis.com/token")
                    .post(body)
                    .build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    // Parse access_token from JSON response
                    val accessToken = org.json.JSONObject(responseBody).optString("access_token")
                    if (accessToken.isNotBlank()) {
                        PkceStore.codeVerifier = "" // Clear verifier after use
                        withContext(Dispatchers.Main) {
                            viewModel.onOAuthTokenReceived(accessToken)
                            Toast.makeText(this@MainActivity, "✅ YouTube account connected!", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Token exchange failed: $responseBody", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "OAuth error: ${e.message}", Toast.LENGTH_LONG).show()
                }
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
                otherVideos = videos,
                googleAccount = googleAccount,
                onBackClick = { viewModel.clearActiveVideo() },
                onFavoriteToggle = { v -> viewModel.toggleFavorite(v.youtubeId, v.isFavorite) },
                onWatchLaterToggle = { v -> viewModel.toggleWatchLater(v.youtubeId, v.isWatchLater) },
                onAddNote = { timeSec, timeStr, noteText ->
                    viewModel.addNoteToActiveVideo(timeSec, timeStr, noteText)
                },
                onDeleteNote = { noteId -> viewModel.deleteNote(noteId) },
                onSelectOtherVideo = { v -> viewModel.playVideo(v) },
                onNextVideo = {
                    val idx = videos.indexOfFirst { it.youtubeId == activeVideo!!.youtubeId }
                    val next = videos.getOrNull(idx + 1) ?: videos.firstOrNull()
                    if (next != null) viewModel.playVideo(next)
                },
                onPreviousVideo = {
                    val idx = videos.indexOfFirst { it.youtubeId == activeVideo!!.youtubeId }
                    val prev = videos.getOrNull(idx - 1) ?: videos.lastOrNull()
                    if (prev != null) viewModel.playVideo(prev)
                },
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
                            label = { Text("Home") },
                            modifier = Modifier.testTag("nav_home_btn")
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
                            label = { Text("Library") },
                            modifier = Modifier.testTag("nav_library_btn")
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
                            label = { Text("History") },
                            modifier = Modifier.testTag("nav_history_btn")
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
                            onCategorySelected = { cat -> viewModel.selectedCategory.value = cat },
                            onSearchQueryChanged = { q -> viewModel.searchQuery.value = q },
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
        val savedAccounts by viewModel.savedAccounts.collectAsState()

        if (showGoogleAuthDialog) {
            GoogleSignInDialog(
                account = googleAccount,
                savedAccounts = savedAccounts,
                onDismiss = { showGoogleAuthDialog = false },
                onSignIn = { name, email ->
                    viewModel.signInGoogle(name, email)
                    showGoogleAuthDialog = false
                },
                onSwitchAccount = { acc ->
                    viewModel.switchAccount(acc)
                },
                onSignOut = {
                    viewModel.signOutGoogle()
                    showGoogleAuthDialog = false
                },
                onSyncPlaylists = {
                    viewModel.syncGoogleAccountData()
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
