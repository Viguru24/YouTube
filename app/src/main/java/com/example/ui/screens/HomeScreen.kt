package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import com.example.data.model.GoogleAccount
import com.example.data.model.PlaylistCategoryEntity
import com.example.data.model.VideoEntity
import com.example.ui.components.VideoCard
import com.example.ui.theme.YouTubeRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    videos: List<VideoEntity>,
    historyVideos: List<VideoEntity>,
    categories: List<PlaylistCategoryEntity>,
    selectedCategory: String,
    searchQuery: String,
    googleAccount: GoogleAccount,
    onCategorySelected: (String) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onVideoClick: (VideoEntity) -> Unit,
    onShortClick: (VideoEntity) -> Unit = onVideoClick,
    onFavoriteToggle: (VideoEntity) -> Unit,
    onWatchLaterToggle: (VideoEntity) -> Unit,
    onDeleteVideo: (VideoEntity) -> Unit,
    onNotInterested: (VideoEntity) -> Unit = onDeleteVideo,
    onOpenAddVideoDialog: () -> Unit,
    onOpenGoogleAuth: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onRefreshFeed: () -> Unit = {},
    liveSearchResults: List<VideoEntity> = emptyList(),
    categoryVideos: List<VideoEntity> = emptyList(),
    algorithmSettings: com.example.data.repository.AlgorithmSettings = com.example.data.repository.AlgorithmSettings(),
    mutedChannels: List<com.example.data.model.MutedChannelEntity> = emptyList(),
    dislikedVideoIds: Set<String> = emptySet(),
    onMuteChannel: (String) -> Unit = {},
    onLoadMore: () -> Unit = {},
    selectedTimeFilter: String = "Any Time",
    onTimeFilterSelected: (String) -> Unit = {},
    selectedSubscribedChannel: String = "",
    onSubscribedChannelSelected: (String) -> Unit = {},
    onRefreshSubscribedChannel: (String) -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenManageTopicsAndCreators: (initialTab: Int) -> Unit = {},
    onSaveToSubject: (video: VideoEntity, subject: String) -> Unit = { _, _ -> },
    subscribedCreators: List<String> = emptyList(),
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    var isSearchExpanded by remember { mutableStateOf(false) }
    var showSubscribedChannelsMenu by remember { mutableStateOf(false) }
    var showAddChannelDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showProfileMenu by remember { mutableStateOf(false) }
    var showSortSubMenu by remember { mutableStateOf(false) }
    var videoToSaveToSubject by remember { mutableStateOf<VideoEntity?>(null) }
    val focusRequester = remember { FocusRequester() }
    // Sort state hoisted here so top bar can access it
    var selectedSort by remember { mutableStateOf("Default") }
    val sortCycle = listOf("Default", "Newest", "Oldest")
    val timeFilterOptions = listOf("Any Time", "Last Hour", "Today", "This Week", "This Month", "This Year")
    val strings = com.example.util.LocalAppStrings.current

    val subscribedChannelsList = if (subscribedCreators.isNotEmpty()) subscribedCreators else com.example.data.model.WillRyanProfileData.subscribedChannels

    // Reset scroll to top on category or channel change
    LaunchedEffect(selectedCategory, selectedSubscribedChannel) {
        gridState.scrollToItem(0)
    }

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) {
            focusRequester.requestFocus()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.statusBars,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                title = {
                    if (isSearchExpanded) {
                        var localSearchQuery by remember(searchQuery) { mutableStateOf(searchQuery) }
                        val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

                        OutlinedTextField(
                            value = localSearchQuery,
                            onValueChange = { newText ->
                                localSearchQuery = newText
                                onSearchQueryChanged(newText)
                            },
                            placeholder = { Text("Search videos or channels...", fontSize = 14.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Search
                            ),
                            keyboardActions = KeyboardActions(onSearch = {
                                onSearchQueryChanged(localSearchQuery)
                                keyboardController?.hide()
                                val pastedId = com.example.util.YouTubeUtils.extractVideoId(localSearchQuery)
                                if (pastedId != null) {
                                    val video = VideoEntity(
                                        youtubeId = pastedId,
                                        title = "YouTube Video",
                                        channelName = "YouTube",
                                        thumbnailUrl = com.example.util.YouTubeUtils.getThumbnailUrl(pastedId),
                                        durationText = "",
                                        category = "YouTube"
                                    )
                                    onVideoClick(video)
                                }
                            }),
                            leadingIcon = {
                                IconButton(onClick = {
                                    localSearchQuery = ""
                                    onSearchQueryChanged("")
                                    isSearchExpanded = false
                                    keyboardController?.hide()
                                }) {
                                    Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                                }
                            },
                            trailingIcon = {
                                if (localSearchQuery.isNotEmpty()) {
                                    IconButton(onClick = {
                                        localSearchQuery = ""
                                        onSearchQueryChanged("")
                                    }) {
                                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            },
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedBorderColor = YouTubeRed,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            textStyle = LocalTextStyle.current.copy(fontSize = 15.sp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .focusRequester(focusRequester)
                                .testTag("search_text_field")
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Big round orange/red play button on the left without any text
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(YouTubeRed)
                                    .clickable {
                                        coroutineScope.launch {
                                            gridState.scrollToItem(0)
                                        }
                                        onCategorySelected("All")
                                        onSearchQueryChanged("")
                                        onSubscribedChannelSelected("")
                                        onRefreshFeed()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Home",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Subscribed Channels Pull-Down Menu Button (Right next to the Play Button)
                            Box {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (selectedSubscribedChannel.isNotBlank()) YouTubeRed.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (selectedSubscribedChannel.isNotBlank()) YouTubeRed else Color(0xFF333333)
                                    ),
                                    modifier = Modifier
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable { showSubscribedChannelsMenu = true }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Subscriptions,
                                            contentDescription = "Subscriptions",
                                            tint = if (selectedSubscribedChannel.isNotBlank()) YouTubeRed else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (selectedSubscribedChannel.isNotBlank()) selectedSubscribedChannel else strings.subscribed,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (selectedSubscribedChannel.isNotBlank()) YouTubeRed else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Filled.ArrowDropDown,
                                            contentDescription = "Open Subscriptions",
                                            tint = if (selectedSubscribedChannel.isNotBlank()) YouTubeRed else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = showSubscribedChannelsMenu,
                                    onDismissRequest = { showSubscribedChannelsMenu = false },
                                    modifier = Modifier.heightIn(max = 400.dp).widthIn(min = 220.dp)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("⭐ ${strings.allFeedVideos}", fontWeight = FontWeight.Bold, color = YouTubeRed) },
                                        onClick = {
                                            showSubscribedChannelsMenu = false
                                            onSubscribedChannelSelected("")
                                            onCategorySelected("All")
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("⚙️ ${strings.manageCreators} (${subscribedChannelsList.size})", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                                        onClick = {
                                            showSubscribedChannelsMenu = false
                                            onOpenManageTopicsAndCreators(0)
                                        }
                                    )
                                    HorizontalDivider()
                                    subscribedChannelsList.forEach { channel ->
                                        val isCurrent = channel.equals(selectedSubscribedChannel, ignoreCase = true)
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = channel,
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isCurrent) YouTubeRed else MaterialTheme.colorScheme.onSurface,
                                                    fontSize = 13.sp
                                                )
                                            },
                                            onClick = {
                                                showSubscribedChannelsMenu = false
                                                onSubscribedChannelSelected(channel)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                actions = {
                    if (!isSearchExpanded) {
                        val currentLang by com.example.util.LanguageManager.currentLanguage.collectAsState()

                        // 1. Search 🔍
                        IconButton(onClick = { isSearchExpanded = true }) {
                            Icon(imageVector = Icons.Outlined.Search, contentDescription = "Search")
                        }

                        // 2. Direct 1-Click Language Switcher 🌐
                        IconButton(
                            onClick = { showLanguageDialog = true },
                            modifier = Modifier.testTag("top_language_btn")
                        ) {
                            Text("🌐", fontSize = 18.sp)
                        }

                        // 3. Profile Button (Opens Settings & Full Menu Options)
                        Box {
                            IconButton(
                                onClick = { showProfileMenu = true },
                                modifier = Modifier.testTag("top_ls_gear_btn")
                            ) {
                                Box(contentAlignment = Alignment.BottomEnd) {
                                    Surface(
                                        shape = CircleShape,
                                        color = YouTubeRed,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = if (googleAccount.isSignedIn) googleAccount.avatarInitials else "GU",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(13.dp)
                                            .background(Color.Black, shape = CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Settings,
                                            contentDescription = "Menu",
                                            tint = Color.White,
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                }
                            }

                            DropdownMenu(
                                expanded = showProfileMenu,
                                onDismissRequest = { showProfileMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("🌐 ${strings.appLanguageTitle} (${currentLang.flagEmoji} ${currentLang.nativeName})", fontWeight = FontWeight.SemiBold) },
                                    onClick = {
                                        showProfileMenu = false
                                        showLanguageDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("⚙️ ${strings.settingsTitle}", fontWeight = FontWeight.SemiBold) },
                                    onClick = {
                                        showProfileMenu = false
                                        onOpenSettings()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (googleAccount.isSignedIn) "👤 Profile (${googleAccount.avatarInitials})" else "👤 ${strings.profileAccount}") },
                                    onClick = {
                                        showProfileMenu = false
                                        onOpenGoogleAuth()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("➕ ${strings.addVideoUrl}") },
                                    onClick = {
                                        showProfileMenu = false
                                        onOpenAddVideoDialog()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("🔀 ${strings.sortFeed} (${selectedSort})") },
                                    onClick = {
                                        showProfileMenu = false
                                        showSortSubMenu = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("🏷️ ${strings.manageTopicsCreators}") },
                                    onClick = {
                                        showProfileMenu = false
                                        onOpenManageTopicsAndCreators(0)
                                    }
                                )
                            }

                            DropdownMenu(
                                expanded = showSortSubMenu,
                                onDismissRequest = { showSortSubMenu = false }
                            ) {
                                sortCycle.forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = when (option) {
                                                    "Newest" -> "Newest First 🕒"
                                                    "Oldest" -> "Oldest First ⌛"
                                                    else -> "Default (Recommended) ⭐"
                                                },
                                                fontWeight = if (selectedSort == option) FontWeight.Bold else FontWeight.Normal,
                                                color = if (selectedSort == option) YouTubeRed else MaterialTheme.colorScheme.onSurface,
                                                fontSize = 13.sp
                                            )
                                        },
                                        onClick = {
                                            selectedSort = option
                                            showSortSubMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Direct Link Extraction Bar (Quick Paste Player)
            val extractedVideoId = remember(searchQuery) { com.example.util.YouTubeUtils.extractVideoId(searchQuery) }
            AnimatedVisibility(visible = extractedVideoId != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clickable {
                            extractedVideoId?.let { id ->
                                onVideoClick(
                                    VideoEntity(
                                        youtubeId = id,
                                        title = "YouTube Video ($id)",
                                        channelName = "YouTube",
                                        thumbnailUrl = com.example.util.YouTubeUtils.getThumbnailUrl(id),
                                        durationText = "",
                                        category = selectedCategory.ifEmpty { "General" }
                                    )
                                )
                            }
                        },
                    colors = CardDefaults.cardColors(containerColor = YouTubeRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.PlayCircle,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Play YouTube Link: $extractedVideoId",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Tap to launch video in player now",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Filled.ArrowForward,
                            contentDescription = "Go",
                            tint = Color.White
                        )
                    }
                }
            }

            // Category Filter Chips & Search Time Selector Row
            val defaultCategories = listOf("All", "⏰ Last 24h", "Tech & Code", "Music", "Tutorials", "Gaming", "Focus & Ambient")
            val allCategoryNames = (defaultCategories + categories.map { it.name }).distinct()

            if (searchQuery.isNotEmpty()) {
                // Upload Date / Time Selector Bar for Search Results
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(
                        text = "Filter Upload Date:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(timeFilterOptions) { filter ->
                            val isSelected = filter.equals(selectedTimeFilter, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = { onTimeFilterSelected(filter) },
                                label = { Text(filter, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF2A2A2A),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFF141414),
                                    labelColor = Color(0xFFCCCCCC)
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = Color(0xFF333333),
                                    selectedBorderColor = Color.White,
                                    borderWidth = 1.dp,
                                    selectedBorderWidth = 1.5.dp
                                ),
                                modifier = Modifier.testTag("time_filter_chip_$filter")
                            )
                        }
                    }
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Category Chips (Monochrome Black & White Styling)
                    items(allCategoryNames) { category ->
                        val isSelected = category.equals(selectedCategory, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                onCategorySelected(category)
                                if (selectedSubscribedChannel.isNotBlank()) {
                                    onSubscribedChannelSelected("")
                                }
                            },
                            label = { 
                                Text(
                                    text = category, 
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else Color(0xFFCCCCCC)
                                ) 
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF2A2A2A),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF141414),
                                labelColor = Color(0xFFCCCCCC)
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = Color(0xFF333333),
                                selectedBorderColor = Color.White,
                                borderWidth = 1.dp,
                                selectedBorderWidth = 1.5.dp
                            ),
                            modifier = Modifier.testTag("category_chip_$category")
                        )
                    }

                    item {
                        FilterChip(
                            selected = false,
                            onClick = { onOpenManageTopicsAndCreators(1) },
                            label = { Text("✏️ Edit Topics", fontWeight = FontWeight.SemiBold, color = Color.White) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color(0xFF141414),
                                labelColor = Color.White
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = false,
                                borderColor = Color(0xFF333333),
                                selectedBorderColor = Color.White,
                                borderWidth = 1.dp,
                                selectedBorderWidth = 1.dp
                            )
                        )
                    }
                }
            }

            // Main Feed Video 2-Column Grid & Real Live Search Results & Subscribed Channel Filtering
            // Automatically remove any video that has been watched, disliked, or muted from the feed
            val watchedIds = remember(historyVideos) {
                historyVideos.filter { it.lastWatchedTimestamp > 0L || it.lastPositionSeconds > 0 }.map { it.youtubeId }.toSet()
            }

            val mutedChannelNames = remember(mutedChannels) {
                mutedChannels.map { it.channelName.lowercase().trim() }.toSet()
            }

            fun isVideoHidden(video: VideoEntity, allowWatched: Boolean = false): Boolean {
                val isWatched = !allowWatched && (video.youtubeId in watchedIds)
                val isDisliked = video.youtubeId in dislikedVideoIds
                val isMuted = video.channelName.lowercase().trim() in mutedChannelNames
                return isWatched || isDisliked || isMuted
            }

            val candidateList = categoryVideos.ifEmpty { videos }

            val displayList = remember(
                candidateList,
                liveSearchResults,
                selectedSubscribedChannel,
                subscribedChannelsList,
                searchQuery,
                extractedVideoId,
                selectedCategory,
                selectedTimeFilter,
                selectedSort,
                watchedIds,
                dislikedVideoIds,
                mutedChannelNames,
                mutedChannels,
                historyVideos,
                algorithmSettings
            ) {
                val rawDisplayList = if (selectedSubscribedChannel.isNotBlank()) {
                    val targetCh = selectedSubscribedChannel.lowercase().trim()
                    candidateList.filter { video ->
                        val vCh = video.channelName.lowercase().trim()
                        (vCh.contains(targetCh) || targetCh.contains(vCh) || vCh.replace(" ", "") == targetCh.replace(" ", "") ||
                        (vCh.contains("youtube") && video.title.lowercase().contains(targetCh))) && !isVideoHidden(video, allowWatched = true)
                    }.distinctBy { it.youtubeId }
                } else if (searchQuery.isNotBlank() && extractedVideoId == null) {
                    val searchList = if (liveSearchResults.isNotEmpty()) {
                        liveSearchResults
                    } else {
                        candidateList.filter { 
                            it.title.contains(searchQuery, ignoreCase = true) || 
                            it.channelName.contains(searchQuery, ignoreCase = true) 
                        }
                    }
                    searchList.filter { !isVideoHidden(it, allowWatched = true) }.distinctBy { it.youtubeId }
                } else if (selectedCategory == "🔔 Subscriptions") {
                    val subSet = subscribedChannelsList.map { it.lowercase().trim() }.filter { it.isNotBlank() }
                    candidateList.filter { video ->
                        val vCh = video.channelName.lowercase().trim()
                        val vTitle = video.title.lowercase().trim()
                        (subSet.any { sub -> vCh.contains(sub) || sub.contains(vCh) || vCh.replace(" ", "") == sub.replace(" ", "") } ||
                         (vCh.contains("youtube") && subSet.any { sub -> vTitle.contains(sub) })) &&
                        !isVideoHidden(video, allowWatched = true)
                    }.distinctBy { it.youtubeId }
                } else if (selectedCategory.contains("24h", ignoreCase = true) || selectedCategory.contains("24 hours", ignoreCase = true) || selectedCategory.contains("Last 24", ignoreCase = true) || selectedCategory.equals("Today", ignoreCase = true)) {
                    val allCandidate = (categoryVideos + videos).distinctBy { it.youtubeId }
                    val within24h = allCandidate
                        .filter {
                            val timeLower = it.publishedTimeText.lowercase()
                            val sec = com.example.util.YouTubeUtils.parsePublishedTimeToSeconds(it.publishedTimeText)
                            (sec <= 86400L || timeLower.contains("min") || timeLower.contains("hour") || timeLower.contains("today") || timeLower.contains("1 day") || timeLower.contains("just now")) &&
                            !isVideoHidden(it, allowWatched = true)
                        }
                        .sortedWith(compareBy { com.example.util.YouTubeUtils.parsePublishedTimeToSeconds(it.publishedTimeText) })
                        .distinctBy { it.youtubeId }

                    if (within24h.isNotEmpty()) {
                        within24h
                    } else {
                        allCandidate
                            .filter { !isVideoHidden(it, allowWatched = true) }
                            .sortedWith(compareBy { com.example.util.YouTubeUtils.parsePublishedTimeToSeconds(it.publishedTimeText) })
                            .take(25)
                    }
                } else if (selectedCategory != "All") {
                    candidateList
                        .filter { (it.category.equals(selectedCategory, ignoreCase = true) || selectedCategory == "All") && !isVideoHidden(it) }
                        .distinctBy { it.youtubeId }
                } else {
                    // Main Home Feed: filter out any video that has been watched, disliked, or muted!
                    val filtered = candidateList
                        .filter { !isVideoHidden(it) }
                        .distinctBy { it.youtubeId }
                    // If aggressive watch filtering leaves fewer than 4 items, backfill with not-disliked candidate items
                    if (filtered.size >= 4) {
                        filtered
                    } else {
                        val notDisliked = candidateList.filter { it.youtubeId !in dislikedVideoIds && it.channelName.lowercase().trim() !in mutedChannelNames }
                        (filtered + notDisliked).distinctBy { it.youtubeId }
                    }
                }

                // Apply Upload Date Time Selector Filter to Search Results
                val timeFilteredList = if (selectedTimeFilter != "Any Time" && searchQuery.isNotBlank()) {
                    val maxSeconds = when (selectedTimeFilter.lowercase().trim()) {
                        "last hour", "1 hour" -> 3600L
                        "today", "last 24h", "last 24 hours", "24 hours", "24h" -> 86400L
                        "this week", "week" -> 604800L
                        "this month", "month" -> 2592000L
                        "this year", "year" -> 31536000L
                        else -> Long.MAX_VALUE
                    }
                    rawDisplayList.filter { video ->
                        val sec = com.example.util.YouTubeUtils.parsePublishedTimeToSeconds(video.publishedTimeText)
                        val tLower = video.publishedTimeText.lowercase()
                        sec <= maxSeconds || (maxSeconds == 86400L && (tLower.contains("min") || tLower.contains("hour") || tLower.contains("today") || tLower.contains("1 day") || tLower.contains("just now")))
                    }
                } else {
                    rawDisplayList
                }

                val rankedDisplayList = com.example.data.repository.RecommendationEngine.scoreAndRankVideos(
                    videos = timeFilteredList,
                    favorites = videos.filter { it.isFavorite },
                    watchHistory = historyVideos,
                    mutedChannels = mutedChannels,
                    dislikedVideoIds = dislikedVideoIds,
                    settings = algorithmSettings
                )

                if (selectedSubscribedChannel.isNotBlank() || searchQuery.isNotBlank()) {
                    timeFilteredList.sortedWith(
                        compareBy<VideoEntity> { com.example.util.YouTubeUtils.parsePublishedTimeToSeconds(it.publishedTimeText) }
                            .thenByDescending { it.addedTimestamp }
                    )
                } else when (selectedSort) {
                    "Oldest" -> rankedDisplayList.sortedWith(
                        compareByDescending<VideoEntity> { com.example.util.YouTubeUtils.parsePublishedTimeToSeconds(it.publishedTimeText) }
                            .thenBy { it.addedTimestamp }
                    )
                    else -> {
                        // Default & Newest both strictly prioritize newest uploads first (minutes, hours, days ago)
                        rankedDisplayList.sortedWith(
                            compareBy<VideoEntity> { com.example.util.YouTubeUtils.parsePublishedTimeToSeconds(it.publishedTimeText) }
                                .thenByDescending { it.addedTimestamp }
                        )
                    }
                }
            }

            if (displayList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VideoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (selectedSubscribedChannel.isNotBlank()) "Fetching latest videos for '$selectedSubscribedChannel'..." else if (searchQuery.isNotEmpty()) "Searching YouTube for '$searchQuery'..." else "Loading $selectedCategory videos...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Discover endless videos by scrolling or searching any topic!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = onOpenAddVideoDialog,
                            colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed)
                        ) {
                            Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (searchQuery.isNotEmpty()) "Add '$searchQuery' as Custom Video" else "Add Video")
                        }
                    }
                }
            } else {
                val shortsList = remember(displayList, algorithmSettings.shortsMode) {
                    if (algorithmSettings.shortsMode == "Hidden") {
                        emptyList()
                    } else {
                        displayList.filter { com.example.util.YouTubeUtils.isShortVideo(it) }
                    }
                }
                val mainVideosList = remember(displayList) {
                    displayList.filter { !com.example.util.YouTubeUtils.isShortVideo(it) }
                }
                val recommendationReasons = remember(mainVideosList, videos, historyVideos) {
                    val favs = videos.filter { it.isFavorite }
                    mainVideosList.associate { it.youtubeId to com.example.data.repository.RecommendationEngine.getRecommendationReason(it, favs, historyVideos) }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        state = gridState,
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 88.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                    if (selectedSubscribedChannel.isNotBlank()) {
                        item(span = { GridItemSpan(2) }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Latest Uploads: $selectedSubscribedChannel",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = YouTubeRed
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { onRefreshSubscribedChannel(selectedSubscribedChannel) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Refresh,
                                            contentDescription = "Refresh Channel",
                                            tint = YouTubeRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            onSubscribedChannelSelected("")
                                            onCategorySelected("All")
                                        }
                                    ) {
                                        Text("Clear ✖", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    } else if (searchQuery.isNotEmpty()) {
                        item(span = { GridItemSpan(2) }) {
                            Text(
                                text = "Search Results for '$searchQuery'",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = YouTubeRed
                            )
                        }
                    } else if (selectedCategory != "All") {
                        item(span = { GridItemSpan(2) }) {
                            Text(
                                text = "$selectedCategory Videos",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = YouTubeRed
                            )
                        }
                    }

                    // 1. Horizontal Shorts Reel Section (If Shorts exist)
                    if (shortsList.isNotEmpty()) {
                        item(span = { GridItemSpan(2) }) {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.FlashOn,
                                        contentDescription = null,
                                        tint = YouTubeRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Shorts",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(shortsList, key = { it.youtubeId }) { shortVideo ->
                                        ShortsReelCard(
                                            video = shortVideo,
                                            onClick = {
                                                android.util.Log.d("ShortsReel", "ShortsReelCard CLICKED: ${shortVideo.youtubeId} - ${shortVideo.title}")
                                                onShortClick(shortVideo)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. Main Videos Section (2-Column Grid)
                    itemsIndexed(
                        items = mainVideosList,
                        key = { _, video -> video.youtubeId },
                        contentType = { _, _ -> "video_card" }
                    ) { index, video ->
                        // Infinite scroll trigger: prefetch when within last 4 items of current batch
                        if (index >= (mainVideosList.size - 4).coerceAtLeast(0)) {
                            LaunchedEffect(index, mainVideosList.size) {
                                onLoadMore()
                            }
                        }

                        val reason = recommendationReasons[video.youtubeId].orEmpty()

                        VideoCard(
                            video = video,
                            onVideoClick = onVideoClick,
                            onFavoriteToggle = onFavoriteToggle,
                            onWatchLaterToggle = onWatchLaterToggle,
                            onDeleteClick = onDeleteVideo,
                            recommendationReason = reason,
                            onMuteChannel = onMuteChannel,
                            onSaveToSubject = { v -> videoToSaveToSubject = v },
                            onNotInterested = onDeleteVideo,
                            onChannelClick = onSubscribedChannelSelected,
                            modifier = Modifier.animateItem()
                        )
                    }

                    // Bottom loading indicator & infinite scroll anchor
                    if (mainVideosList.isNotEmpty()) {
                        item(span = { GridItemSpan(2) }) {
                            LaunchedEffect(Unit) {
                                onLoadMore()
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = YouTubeRed,
                                    strokeWidth = 2.5.dp
                                )
                            }
                        }
                    }
                }

                // Floating "Back to Top" Action Button
                val showScrollToTop by remember {
                    derivedStateOf { gridState.firstVisibleItemIndex > 3 }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = showScrollToTop,
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 96.dp, end = 16.dp)
                ) {
                    FloatingActionButton(
                        onClick = {
                            coroutineScope.launch {
                                gridState.animateScrollToItem(0)
                            }
                        },
                        containerColor = YouTubeRed,
                        contentColor = Color.White,
                        modifier = Modifier.size(46.dp),
                        shape = androidx.compose.foundation.shape.CircleShape
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowUpward,
                            contentDescription = "Scroll to top",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        if (videoToSaveToSubject != null) {
            com.example.ui.components.SaveToSubjectDialog(
                video = videoToSaveToSubject!!,
                categories = categories,
                onDismiss = { videoToSaveToSubject = null },
                onSaveToSubject = { subject ->
                    onSaveToSubject(videoToSaveToSubject!!, subject)
                    videoToSaveToSubject = null
                }
            )
        }

        if (showAddChannelDialog) {
            var newChannelInput by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showAddChannelDialog = false },
                title = { Text("Subscribe to New Channel", fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = newChannelInput,
                        onValueChange = { newChannelInput = it },
                        label = { Text("Channel Name") },
                        placeholder = { Text("e.g. Marques Brownlee, Lex Fridman") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val trimmed = newChannelInput.trim()
                            if (trimmed.isNotBlank()) {
                                com.example.data.model.WillRyanProfileData.addSubscribedChannel(trimmed)
                                onSubscribedChannelSelected(trimmed)
                                showAddChannelDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed)
                    ) {
                        Text("Subscribe")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddChannelDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showLanguageDialog) {
            com.example.ui.components.LanguageSelectionDialog(
                onDismiss = { showLanguageDialog = false }
            )
        }
    }
}
}

@Composable
private fun ContinueWatchingCard(
    video: VideoEntity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(180.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = video.durationText,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                text = video.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(6.dp)
            )
        }
    }
}

@Composable
private fun ShortsReelCard(
    video: VideoEntity,
    onClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    Column(
        modifier = Modifier.width(155.dp)
    ) {
        // Vertical Short Poster Card (Clean & Tap Anywhere to Play, 0 Play Buttons)
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(235.dp)
                .clip(RoundedCornerShape(14.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Gentle Gradient at Bottom
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                            )
                        )
                )
                // Red Shorts Lightning Badge Top-Left
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(4.dp)
                        .align(Alignment.TopStart)
                ) {
                    Icon(
                        imageVector = Icons.Filled.FlashOn,
                        contentDescription = null,
                        tint = YouTubeRed,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Title below poster
        Text(
            text = video.title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 15.sp,
            modifier = Modifier.padding(horizontal = 2.dp)
        )

        // Views or Channel sub-text
        if (video.viewCountText.isNotEmpty() || video.channelName.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (video.viewCountText.isNotEmpty()) video.viewCountText else video.channelName,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
    }
}
