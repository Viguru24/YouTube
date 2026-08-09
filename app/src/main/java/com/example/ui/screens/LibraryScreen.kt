package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GoogleAccount
import com.example.data.model.PlaylistCategoryEntity
import com.example.data.model.VideoEntity
import com.example.ui.components.VideoCard
import com.example.ui.theme.GoldStar
import com.example.ui.theme.YouTubeRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    categories: List<PlaylistCategoryEntity>,
    favoriteVideos: List<VideoEntity>,
    watchLaterVideos: List<VideoEntity>,
    allVideos: List<VideoEntity>,
    googleAccount: GoogleAccount,
    onVideoClick: (VideoEntity) -> Unit,
    onFavoriteToggle: (VideoEntity) -> Unit,
    onWatchLaterToggle: (VideoEntity) -> Unit,
    onDeleteVideo: (VideoEntity) -> Unit,
    onOpenAddCategoryDialog: () -> Unit,
    onOpenAddVideoDialog: () -> Unit,
    onOpenGoogleAuth: () -> Unit,
    historyVideos: List<VideoEntity> = emptyList(),
    onOpenHistory: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Subjects/Categories, 1: Favorites, 2: Watch Later, 3: History

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Library & Subjects",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(
                        onClick = onOpenHistory,
                        modifier = Modifier.testTag("library_history_top_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = "Watch History",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = onOpenAddCategoryDialog,
                        modifier = Modifier.testTag("add_category_top_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CreateNewFolder,
                            contentDescription = "New Subject",
                            tint = YouTubeRed
                        )
                    }

                    // Google Account Avatar Button
                    IconButton(
                        onClick = onOpenGoogleAuth,
                        modifier = Modifier.testTag("library_google_auth_btn")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(if (googleAccount.isSignedIn) Color(0xFF4285F4) else Color.Gray),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = googleAccount.avatarInitials,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tabs Bar
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = YouTubeRed,
                edgePadding = 12.dp
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Subjects (${categories.size})") },
                    icon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                    modifier = Modifier.testTag("tab_categories")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Favorites (${favoriteVideos.size})") },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 1) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = null,
                            tint = if (selectedTab == 1) GoldStar else LocalContentColor.current
                        )
                    },
                    modifier = Modifier.testTag("tab_favorites")
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Watch Later (${watchLaterVideos.size})") },
                    icon = { Icon(Icons.Outlined.WatchLater, contentDescription = null) },
                    modifier = Modifier.testTag("tab_watch_later")
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("History (${historyVideos.size})") },
                    icon = { Icon(Icons.Filled.History, contentDescription = null) },
                    modifier = Modifier.testTag("tab_history")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (selectedTab) {
                0 -> CategoriesTabContent(
                    categories = categories,
                    allVideos = allVideos,
                    onVideoClick = onVideoClick,
                    onFavoriteToggle = onFavoriteToggle,
                    onWatchLaterToggle = onWatchLaterToggle,
                    onDeleteVideo = onDeleteVideo,
                    onOpenAddCategoryDialog = onOpenAddCategoryDialog
                )
                1 -> VideoListTabContent(
                    title = "Favorite Videos",
                    emptyText = "No favorite videos saved yet. Tap the star icon on any video to bookmark it here!",
                    videos = favoriteVideos,
                    onVideoClick = onVideoClick,
                    onFavoriteToggle = onFavoriteToggle,
                    onWatchLaterToggle = onWatchLaterToggle,
                    onDeleteVideo = onDeleteVideo
                )
                2 -> VideoListTabContent(
                    title = "Watch Later List",
                    emptyText = "Your Watch Later queue is empty. Add videos from the home feed to save them for later!",
                    videos = watchLaterVideos,
                    onVideoClick = onVideoClick,
                    onFavoriteToggle = onFavoriteToggle,
                    onWatchLaterToggle = onWatchLaterToggle,
                    onDeleteVideo = onDeleteVideo
                )
                3 -> VideoListTabContent(
                    title = "Watch History",
                    emptyText = "No watch history recorded yet. Videos you watch will automatically appear here!",
                    videos = historyVideos,
                    onVideoClick = onVideoClick,
                    onFavoriteToggle = onFavoriteToggle,
                    onWatchLaterToggle = onWatchLaterToggle,
                    onDeleteVideo = onDeleteVideo
                )
            }
        }
    }
}

@Composable
private fun CategoriesTabContent(
    categories: List<PlaylistCategoryEntity>,
    allVideos: List<VideoEntity>,
    onVideoClick: (VideoEntity) -> Unit,
    onFavoriteToggle: (VideoEntity) -> Unit,
    onWatchLaterToggle: (VideoEntity) -> Unit,
    onDeleteVideo: (VideoEntity) -> Unit,
    onOpenAddCategoryDialog: () -> Unit
) {
    var activeCategoryFilter by remember { mutableStateOf<String?>(null) }

    val filteredCategoryVideos = remember(activeCategoryFilter, allVideos) {
        if (activeCategoryFilter == null) emptyList()
        else allVideos.filter { it.category.equals(activeCategoryFilter, ignoreCase = true) }
    }

    if (activeCategoryFilter != null) {
        // Active Category View
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { activeCategoryFilter = null }) {
                    Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Category: $activeCategoryFilter",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (filteredCategoryVideos.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No videos in '$activeCategoryFilter' category.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(filteredCategoryVideos, key = { it.youtubeId }) { video ->
                        VideoCard(
                            video = video,
                            onVideoClick = onVideoClick,
                            onFavoriteToggle = onFavoriteToggle,
                            onWatchLaterToggle = onWatchLaterToggle,
                            onDeleteClick = onDeleteVideo
                        )
                    }
                }
            }
        }
    } else {
        // Categories Grid/List
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Custom Playlists",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onOpenAddCategoryDialog) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Category")
                    }
                }
            }

            items(categories, key = { it.id }) { category ->
                val videoCount = allVideos.count { it.category.equals(category.name, ignoreCase = true) }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { activeCategoryFilter = category.name },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(YouTubeRed.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FolderSpecial,
                                contentDescription = null,
                                tint = YouTubeRed,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$videoCount video${if (videoCount != 1) "s" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoListTabContent(
    title: String,
    emptyText: String,
    videos: List<VideoEntity>,
    onVideoClick: (VideoEntity) -> Unit,
    onFavoriteToggle: (VideoEntity) -> Unit,
    onWatchLaterToggle: (VideoEntity) -> Unit,
    onDeleteVideo: (VideoEntity) -> Unit
) {
    if (videos.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.VideoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(videos, key = { it.youtubeId }) { video ->
                VideoCard(
                    video = video,
                    onVideoClick = onVideoClick,
                    onFavoriteToggle = onFavoriteToggle,
                    onWatchLaterToggle = onWatchLaterToggle,
                    onDeleteClick = onDeleteVideo
                )
            }
        }
    }
}
