package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
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
    downloadRetention: String = "Never",
    onDownloadRetentionChanged: (String) -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onMuteChannel: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Subjects, 1: Downloads, 2: Favorites, 3: Watch Later, 4: History

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Library & Downloads",
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
                                .background(if (googleAccount.isSignedIn) YouTubeRed else Color.Gray),
                            contentAlignment = Alignment.Center
                        ) {
                            if (googleAccount.isSignedIn) {
                                if (googleAccount.avatarUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = googleAccount.avatarUrl,
                                        contentDescription = "Profile",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(androidx.compose.foundation.shape.CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(
                                        text = googleAccount.avatarInitials.ifBlank { "U" },
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = "Guest",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
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
                    text = { Text("📥 Downloads (${downloadedVideos.size})") },
                    icon = { Icon(Icons.Filled.DownloadDone, contentDescription = null, tint = if (selectedTab == 1) Color(0xFF4CAF50) else LocalContentColor.current) },
                    modifier = Modifier.testTag("tab_downloads")
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Favorites (${favoriteVideos.size})") },
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
                    text = { Text("Watch Later (${watchLaterVideos.size})") },
                    icon = { Icon(Icons.Outlined.WatchLater, contentDescription = null) },
                    modifier = Modifier.testTag("tab_watch_later")
                )
                Tab(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
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
                    onMuteChannel = onMuteChannel,
                    onOpenAddCategoryDialog = onOpenAddCategoryDialog
                )
                1 -> DownloadedVideosTabContent(
                    videos = downloadedVideos,
                    onVideoClick = onVideoClick,
                    onDeleteDownload = onDeleteDownload,
                    downloadRetention = downloadRetention,
                    onDownloadRetentionChanged = onDownloadRetentionChanged
                )
                2 -> VideoListTabContent(
                    title = "Favorite Videos",
                    emptyText = "No favorite videos saved yet. Tap the star icon on any video to bookmark it here!",
                    videos = favoriteVideos,
                    onVideoClick = onVideoClick,
                    onFavoriteToggle = onFavoriteToggle,
                    onWatchLaterToggle = onWatchLaterToggle,
                    onDeleteVideo = onDeleteVideo,
                    onMuteChannel = onMuteChannel
                )
                3 -> VideoListTabContent(
                    title = "Watch Later List",
                    emptyText = "Your Watch Later queue is empty. Add videos from the home feed to save them for later!",
                    videos = watchLaterVideos,
                    onVideoClick = onVideoClick,
                    onFavoriteToggle = onFavoriteToggle,
                    onWatchLaterToggle = onWatchLaterToggle,
                    onDeleteVideo = onDeleteVideo,
                    onMuteChannel = onMuteChannel
                )
                4 -> VideoListTabContent(
                    title = "Watch History",
                    emptyText = "No watch history recorded yet. Videos you watch will automatically appear here!",
                    videos = historyVideos,
                    onVideoClick = onVideoClick,
                    onFavoriteToggle = onFavoriteToggle,
                    onWatchLaterToggle = onWatchLaterToggle,
                    onDeleteVideo = onDeleteVideo,
                    onMuteChannel = onMuteChannel
                )
            }
        }
    }
}

@Composable
private fun DownloadedVideosTabContent(
    videos: List<VideoEntity>,
    onVideoClick: (VideoEntity) -> Unit,
    onDeleteDownload: (VideoEntity) -> Unit,
    downloadRetention: String = "Never",
    onDownloadRetentionChanged: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val totalMb = videos.sumOf { it.downloadSizeMb.toDouble() }

    var activeDownloadLocation by remember {
        mutableStateOf(com.example.data.remote.VideoDownloadManager.getActiveLocation(context))
    }

    val folderPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            try {
                val takeFlags: Int = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) { }

            val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
            var resolvedPath: String? = null
            if (docId != null) {
                val split = docId.split(":")
                if (split.size >= 2) {
                    val type = split[0]
                    val relativePath = split[1]
                    resolvedPath = if (type.equals("primary", ignoreCase = true)) {
                        android.os.Environment.getExternalStorageDirectory().absolutePath + "/" + relativePath
                    } else {
                        "/storage/$type/$relativePath"
                    }
                }
            }
            if (resolvedPath == null) {
                resolvedPath = uri.path ?: uri.toString()
            }

            com.example.data.remote.VideoDownloadManager.setDownloadLocation(context, "CUSTOM", resolvedPath, uri.toString())
            activeDownloadLocation = com.example.data.remote.VideoDownloadManager.getActiveLocation(context)
            android.widget.Toast.makeText(context, "📁 Download directory set to:\n$resolvedPath", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    val retentionLabel = when (downloadRetention.lowercase().trim()) {
        "24h", "1d" -> "24 Hours"
        "48h", "2d" -> "48 Hours"
        "7d", "1w"  -> "7 Days"
        "30d", "1m" -> "30 Days"
        "watched"   -> "Watched"
        else        -> "Permanently"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Retention & Storage Header Card
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF14131E).copy(alpha = 0.9f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        ) {
                            Text("✈️", fontSize = 16.sp)
                            Text(
                                text = "Offline Downloads",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF81C784),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (retentionLabel == "Permanently") Color(0xFF4CAF50).copy(alpha = 0.15f) else Color(0xFFFF9800).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "⏱️ $retentionLabel",
                                color = if (retentionLabel == "Permanently") Color(0xFF81C784) else Color(0xFFFFB74D),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Download Directory Controller (Pull-Down Menu)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Download Directory:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )

                        var showLibraryFolderDropdown by remember { mutableStateOf(false) }

                        Box(modifier = Modifier.fillMaxWidth()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF1E1C2E),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { showLibraryFolderDropdown = true }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f).padding(end = 6.dp)
                                    ) {
                                        Text(
                                            text = when (activeDownloadLocation.type) {
                                                "DEFAULT" -> "📱 App Storage (.offline_videos)"
                                                "MOVIES" -> "🎬 Movies Folder"
                                                "DOWNLOADS" -> "📥 Downloads Folder"
                                                "SD_CARD" -> "💾 MicroSD Card"
                                                "CUSTOM" -> "📂 ${activeDownloadLocation.displayName}"
                                                else -> "📁 ${activeDownloadLocation.displayName}"
                                            },
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Filled.ArrowDropDown,
                                        contentDescription = "Pull Down Menu",
                                        tint = YouTubeRed,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showLibraryFolderDropdown,
                                onDismissRequest = { showLibraryFolderDropdown = false },
                                modifier = Modifier.widthIn(min = 280.dp)
                            ) {
                                val locations = com.example.data.remote.VideoDownloadManager.getAvailableLocations(context)
                                locations.forEach { loc ->
                                    val isSelected = activeDownloadLocation.type == loc.type &&
                                        (loc.type != "CUSTOM" || activeDownloadLocation.path == loc.path)
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = when (loc.type) {
                                                        "DEFAULT" -> "📱 App Storage (.offline_videos)"
                                                        "MOVIES" -> "🎬 Movies Folder"
                                                        "DOWNLOADS" -> "📥 Downloads Folder"
                                                        "SD_CARD" -> "💾 MicroSD Card"
                                                        "CUSTOM" -> "📂 ${loc.displayName}"
                                                        else -> loc.displayName
                                                    },
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) YouTubeRed else MaterialTheme.colorScheme.onSurface,
                                                    fontSize = 12.sp
                                                )
                                                Text(
                                                    text = loc.path,
                                                    fontSize = 9.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        },
                                        leadingIcon = {
                                            if (isSelected) {
                                                Icon(Icons.Filled.Check, contentDescription = null, tint = YouTubeRed, modifier = Modifier.size(16.dp))
                                            } else {
                                                Spacer(modifier = Modifier.size(16.dp))
                                            }
                                        },
                                        onClick = {
                                            showLibraryFolderDropdown = false
                                            com.example.data.remote.VideoDownloadManager.setDownloadLocation(context, loc.type, loc.path)
                                            activeDownloadLocation = com.example.data.remote.VideoDownloadManager.getActiveLocation(context)
                                            android.widget.Toast.makeText(context, "📁 Saved to: ${loc.displayName}", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }

                                HorizontalDivider()

                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "➕ Browse / Choose Custom Folder...",
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF81C784),
                                            fontSize = 12.sp
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = Color(0xFF81C784), modifier = Modifier.size(16.dp))
                                    },
                                    onClick = {
                                        showLibraryFolderDropdown = false
                                        try {
                                            folderPickerLauncher.launch(null)
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(context, "Folder picker error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        }

                        Text(
                            text = "Active path: ${activeDownloadLocation.path}",
                            fontSize = 9.sp,
                            color = Color(0xFF81C784),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Time Downloads Stay in Folder:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "${videos.size} videos • ${String.format(java.util.Locale.US, "%.1f", totalMb)} MB",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }

                    Text(
                        text = "Default: permanently remain in folder. Select auto-delete duration if desired.",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        FilterChip(
                            selected = downloadRetention.equals("Never", ignoreCase = true) || downloadRetention.equals("Permanently", ignoreCase = true) || downloadRetention.isBlank(),
                            onClick = {
                                onDownloadRetentionChanged("Never")
                                android.widget.Toast.makeText(context, "♾️ Downloads will permanently remain in folder", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            label = { Text("♾️ Permanently (Default)", fontSize = 10.sp) }
                        )
                        FilterChip(
                            selected = downloadRetention.equals("24h", ignoreCase = true),
                            onClick = {
                                onDownloadRetentionChanged("24h")
                                android.widget.Toast.makeText(context, "⏱️ Downloads will stay for 24 Hours", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            label = { Text("⏳ 24h", fontSize = 10.sp) }
                        )
                        FilterChip(
                            selected = downloadRetention.equals("48h", ignoreCase = true),
                            onClick = {
                                onDownloadRetentionChanged("48h")
                                android.widget.Toast.makeText(context, "⏱️ Downloads will stay for 48 Hours", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            label = { Text("⏳ 48h", fontSize = 10.sp) }
                        )
                        FilterChip(
                            selected = downloadRetention.equals("7d", ignoreCase = true),
                            onClick = {
                                onDownloadRetentionChanged("7d")
                                android.widget.Toast.makeText(context, "📅 Downloads will stay for 7 Days", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            label = { Text("📅 7d", fontSize = 10.sp) }
                        )
                        FilterChip(
                            selected = downloadRetention.equals("30d", ignoreCase = true),
                            onClick = {
                                onDownloadRetentionChanged("30d")
                                android.widget.Toast.makeText(context, "🗓️ Downloads will stay for 30 Days", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            label = { Text("🗓️ 30d", fontSize = 10.sp) }
                        )
                        FilterChip(
                            selected = downloadRetention.equals("Watched", ignoreCase = true),
                            onClick = {
                                onDownloadRetentionChanged("Watched")
                                android.widget.Toast.makeText(context, "👁️ Downloads will delete after watched", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            label = { Text("👁️ Watched", fontSize = 10.sp) }
                        )
                    }
                }
            }
        }

        if (videos.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
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
            }
        } else {

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
    onMuteChannel: (String) -> Unit = {},
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
                            onDeleteClick = onDeleteVideo,
                            onMuteChannel = onMuteChannel
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
    onDeleteVideo: (VideoEntity) -> Unit,
    onMuteChannel: (String) -> Unit = {}
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
                    onDeleteClick = onDeleteVideo,
                    onMuteChannel = onMuteChannel
                )
            }
        }
    }
}
