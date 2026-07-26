package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
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
    onFavoriteToggle: (VideoEntity) -> Unit,
    onWatchLaterToggle: (VideoEntity) -> Unit,
    onDeleteVideo: (VideoEntity) -> Unit,
    onOpenAddVideoDialog: () -> Unit,
    onOpenGoogleAuth: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onRefreshFeed: () -> Unit = {},
    liveSearchResults: List<VideoEntity> = emptyList(),
    categoryVideos: List<VideoEntity> = emptyList(),
    onLoadMore: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isSearchExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) {
            focusRequester.requestFocus()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchExpanded) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChanged,
                            placeholder = { Text("Search videos or channels...", fontSize = 14.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { }),
                            leadingIcon = {
                                IconButton(onClick = {
                                    onSearchQueryChanged("")
                                    isSearchExpanded = false
                                }) {
                                    Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                                }
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onSearchQueryChanged("") }) {
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
                            Icon(
                                imageVector = Icons.Filled.PlayCircle,
                                contentDescription = "YouTube",
                                tint = YouTubeRed,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "YouTube",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "AD-FREE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = YouTubeRed,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (!isSearchExpanded) {
                        IconButton(onClick = { isSearchExpanded = true }) {
                            Icon(imageVector = Icons.Outlined.Search, contentDescription = "Search")
                        }

                        // Add Video Quick Button
                        IconButton(onClick = onOpenAddVideoDialog) {
                            Icon(imageVector = Icons.Filled.AddCircle, contentDescription = "Add Video", tint = YouTubeRed)
                        }

                        // Account Avatar
                        IconButton(onClick = onOpenGoogleAuth) {
                            if (googleAccount.isSignedIn) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = YouTubeRed,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = googleAccount.avatarInitials,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            } else {
                                Icon(imageVector = Icons.Filled.AccountCircle, contentDescription = "Account")
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenAddVideoDialog,
                containerColor = YouTubeRed,
                contentColor = Color.White,
                modifier = Modifier.testTag("add_video_fab")
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Add Video")
            }
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
                                        durationText = "03:45",
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

            // Category Filter Chips Row
            val defaultCategories = listOf("All", "Tech & Code", "Music", "Tutorials", "Gaming", "Focus & Ambient")
            val allCategoryNames = (defaultCategories + categories.map { it.name }).distinct()

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(allCategoryNames) { category ->
                    val isSelected = category.equals(selectedCategory, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onCategorySelected(category) },
                        label = { Text(category, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = YouTubeRed,
                            selectedLabelColor = Color.White,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.testTag("category_chip_$category")
                    )
                }
            }

            // Continue Watching Horizontal Rail (if history exists)
            val continueWatchingList = historyVideos.take(5)
            AnimatedVisibility(visible = continueWatchingList.isNotEmpty() && searchQuery.isEmpty() && selectedCategory == "All") {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = null,
                            tint = YouTubeRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Continue Watching",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(continueWatchingList, key = { "cw_${it.youtubeId}" }) { video ->
                            ContinueWatchingCard(video = video, onClick = { onVideoClick(video) })
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                }
            }

            // Main Feed Video 2-Column Grid & Real Live Search Results & Category Infinite Scroll
            val displayList = if (searchQuery.isNotBlank() && extractedVideoId == null) {
                if (liveSearchResults.isNotEmpty()) {
                    liveSearchResults
                } else {
                    com.example.util.YouTubeUtils.searchYouTubeVideos(searchQuery)
                }
            } else if (selectedCategory != "All") {
                (categoryVideos + videos.filter { it.category.equals(selectedCategory, ignoreCase = true) }).distinctBy { it.youtubeId }
            } else {
                (videos + categoryVideos).distinctBy { it.youtubeId }
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
                            text = if (searchQuery.isNotEmpty()) "Searching YouTube for '$searchQuery'..." else "Loading $selectedCategory videos...",
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
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 88.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (searchQuery.isNotEmpty()) {
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

                    itemsIndexed(displayList, key = { _, video -> video.youtubeId }) { index, video ->
                        // Infinite scroll trigger: when reaching near end of grid, fetch next batch automatically!
                        if (index >= displayList.size - 4) {
                            LaunchedEffect(index) {
                                onLoadMore()
                            }
                        }

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
