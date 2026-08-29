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
    downloadedVideos: List<VideoEntity> = emptyList(),
    onDeleteDownload: (VideoEntity) -> Unit = {},
    onOpenHistory: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Subjects, 1: Downloads, 2: Favorites, 3: Watch Later, 4: History
    val strings = com.example.util.LocalAppStrings.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = strings.libraryTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    var showLanguageDialog by remember { mutableStateOf(false) }

                    // 1-Click Direct Language Selector 🌐
                    IconButton(
                        onClick = { showLanguageDialog = true },
                        modifier = Modifier.testTag("library_language_btn")
                    ) {
                        Text("🌐", fontSize = 18.sp)
                    }

                    if (showLanguageDialog) {
                        com.example.ui.components.LanguageSelectionDialog(
                            onDismiss = { showLanguageDialog = false }
                        )
                    }

                    IconButton(
                        onClick = onOpenHistory,
                        modifier = Modifier.testTag("library_history_top_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = strings.tabHistory,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = onOpenAddCategoryDialog,
                        modifier = Modifier.testTag("add_category_top_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CreateNewFolder,
                            contentDescription = strings.addCategory,
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
                    text = { Text("${strings.tabSubjects} (${categories.size})") },
                    icon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                    modifier = Modifier.testTag("tab_categories")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("📥 ${strings.tabDownloads} (${downloadedVideos.size})") },
                    icon = { Icon(Icons.Filled.DownloadDone, contentDescription = null, tint = if (selectedTab == 1) Color(0xFF4CAF50) else LocalContentColor.current) },
                    modifier = Modifier.testTag("tab_downloads")
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("${strings.tabFavorites} (${favoriteVideos.size})") },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 2) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = null,
                            tint = if (selectedTab == 2) GoldStar else LocalContentColor.current
                        )
                    },
                    modifier = Modifier.testTag("tab_favorites")
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("${strings.tabWatchLater} (${watchLaterVideos.size})") },
                    icon = { Icon(Icons.Outlined.WatchLater, contentDescription = null) },
                    modifier = Modifier.testTag("tab_watch_later")
                )
                Tab(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    text = { Text("${strings.tabHistory} (${historyVideos.size})") },
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
                1 -> DownloadedVideosTabContent(
                    videos = downloadedVideos,
                    onVideoClick = onVideoClick,
                    onDeleteDownload = onDeleteDownload
                )
                2 -> VideoListTabContent(
                    title = strings.tabFavorites,
                    emptyText = strings.noFavoritesText,
                    videos = favoriteVideos,
                    onVideoClick = onVideoClick,
                    onFavoriteToggle = onFavoriteToggle,
                    onWatchLaterToggle = onWatchLaterToggle,
                    onDeleteVideo = onDeleteVideo
                )
                3 -> VideoListTabContent(
                    title = strings.tabWatchLater,
                    emptyText = strings.noWatchLaterText,
                    videos = watchLaterVideos,
                    onVideoClick = onVideoClick,
                    onFavoriteToggle = onFavoriteToggle,
                    onWatchLaterToggle = onWatchLaterToggle,
                    onDeleteVideo = onDeleteVideo
                )
                4 -> VideoListTabContent(
                    title = strings.tabHistory,
                    emptyText = strings.noHistoryText,
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
private fun DownloadedVideosTabContent(
    videos: List<VideoEntity>,
    onVideoClick: (VideoEntity) -> Unit,
    onDeleteDownload: (VideoEntity) -> Unit
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
                    imageVector = Icons.Filled.AirplanemodeActive,
                    contentDescription = null,
                    tint = YouTubeRed,
                    modifier = Modifier.size(54.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No Offline Downloads Yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Tap the ⬇️ Download button on any video to save it for offline watching (e.g. on airplanes).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    } else {
        val totalMb = videos.sumOf { it.downloadSizeMb.toDouble() }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "✈️ Ready for Offline & Airplane Mode",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                    Text(
                        text = "${String.format(java.util.Locale.US, "%.1f", totalMb)} MB used",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(videos, key = { "dl_${it.youtubeId}" }) { video ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onVideoClick(video) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(100.dp)
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(6.dp))
                        ) {
                            coil.compose.AsyncImage(
                                model = video.thumbnailUrl,
                                contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = video.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = Color(0xFF4CAF50).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "✓ Offline (${video.downloadSizeMb}MB)",
                                        color = Color(0xFF4CAF50),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        IconButton(onClick = { onDeleteDownload(video) }) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Delete Download",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
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
                TryOurOtherProductsCard()
            }

            item {
                val strings = com.example.util.LocalAppStrings.current
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.customPlaylists,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onOpenAddCategoryDialog) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.addCategory)
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

@Composable
private fun TryOurOtherProductsCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val strings = com.example.util.LocalAppStrings.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "✨",
                    fontSize = 18.sp
                )
                Text(
                    text = strings.tryOurOtherProducts,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = strings.otherProductsSub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 1. Cosmo Whisper
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://cosmowhisper.com"))
                            context.startActivity(intent)
                        } catch (e: Exception) { }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(Color(0xFF8E24AA).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Mic,
                                contentDescription = "Cosmo Whisper",
                                tint = Color(0xFFAB47BC),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Cosmo Whisper",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "AI Speech-to-Text & Transcription",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            try {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://cosmowhisper.com"))
                                context.startActivity(intent)
                            } catch (e: Exception) { }
                        }
                    ) {
                        Text(strings.visitWebsite, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFAB47BC))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Cosmo Symphony (Microsoft Store)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://apps.microsoft.com/detail/9P4DFBGWGFF6?hl=en-us&gl=GB&ocid=pdpshare"))
                            context.startActivity(intent)
                        } catch (e: Exception) { }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(Color(0xFF0078D4).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MusicNote,
                                contentDescription = "Cosmo Symphony",
                                tint = Color(0xFF0078D4),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Cosmo Symphony",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Music & Media on Microsoft Store",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            try {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://apps.microsoft.com/detail/9P4DFBGWGFF6?hl=en-us&gl=GB&ocid=pdpshare"))
                                context.startActivity(intent)
                            } catch (e: Exception) { }
                        }
                    ) {
                        Text(strings.getOnStore, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0078D4))
                    }
                }
            }
        }
    }
}
