package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.GoogleAccount
import com.example.data.model.PlaylistCategoryEntity
import com.example.data.model.VideoEntity
import com.example.data.model.VideoNoteEntity
import com.example.data.repository.YouTubeRepository
import com.example.util.YouTubeUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class YouTubeViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val repository = YouTubeRepository(db.videoDao(), db.videoNoteDao(), db.playlistCategoryDao(), db.mutedChannelDao())

    private val prefs = application.getSharedPreferences("google_accounts_prefs", android.content.Context.MODE_PRIVATE)

    val mutedChannels: StateFlow<List<com.example.data.model.MutedChannelEntity>> = repository.mutedChannels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadedVideos: StateFlow<List<VideoEntity>> = repository.downloadedVideos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadProgressMap: StateFlow<Map<String, Int>> = com.example.data.remote.VideoDownloadManager.downloadProgressMap

    fun downloadVideo(video: VideoEntity, onComplete: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            repository.saveVideo(video)
            com.example.data.remote.VideoDownloadManager.downloadVideo(
                context = getApplication(),
                video = video,
                onSuccess = { localPath, sizeMb ->
                    viewModelScope.launch {
                        repository.updateDownloadStatus(video.youtubeId, true, localPath, sizeMb)
                        onComplete()
                    }
                },
                onError = onError
            )
        }
    }

    fun deleteDownloadedVideo(video: VideoEntity) {
        viewModelScope.launch {
            com.example.data.remote.VideoDownloadManager.deleteDownloadedVideo(getApplication(), video.youtubeId)
            repository.updateDownloadStatus(video.youtubeId, false, "", 0.0f)
        }
    }

    fun checkAndCleanExpiredDownloads() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val list = repository.downloadedVideos.first()
            com.example.data.remote.VideoDownloadManager.cleanExpiredDownloads(
                context = getApplication(),
                autoDeleteSetting = _algorithmSettings.value.autoDeleteDownloads,
                downloadedVideos = list,
                onVideoDeleted = { deletedId ->
                    repository.updateDownloadStatus(deletedId, false, "", 0.0f)
                }
            )
        }
    }

    fun muteChannel(channelName: String) {
        viewModelScope.launch {
            repository.muteChannel(channelName)
        }
    }

    fun unmuteChannel(channelName: String) {
        viewModelScope.launch {
            repository.unmuteChannel(channelName)
        }
    }

    // Google Auth Account state & Saved Multi-Account List
    private val _googleAccount = MutableStateFlow(GoogleAccount(isSignedIn = false))
    val googleAccount: StateFlow<GoogleAccount> = _googleAccount.asStateFlow()

    private val _savedAccounts = MutableStateFlow<List<GoogleAccount>>(emptyList())
    val savedAccounts: StateFlow<List<GoogleAccount>> = _savedAccounts.asStateFlow()

    init {
        loadSavedAccounts()
        viewModelScope.launch {
            repository.cleanupStaleRecommendations()
        }
    }

    private fun loadSavedAccounts() {
        val savedJson = prefs.getString("accounts_list", "") ?: ""
        val accountsList = mutableListOf<GoogleAccount>()

        if (savedJson.isNotBlank()) {
            try {
                val lines = savedJson.split(";")
                for (line in lines) {
                    val parts = line.split("|")
                    if (parts.size >= 2) {
                        val name = parts[0]
                        val email = parts[1]
                        val initials = name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").ifEmpty { "G" }.uppercase()
                        accountsList.add(GoogleAccount(name = name, email = email, avatarInitials = initials, isSignedIn = true))
                    }
                }
            } catch (e: Exception) {}
        }

        if (accountsList.isEmpty()) {
            accountsList.add(GoogleAccount(name = "Louis de Souza", email = "louisdesouza@gmail.com", avatarInitials = "LS", isSignedIn = true))
        }

        _savedAccounts.value = accountsList

        // Restore last signed-in account automatically
        val lastEmail = prefs.getString("last_email", accountsList.first().email) ?: accountsList.first().email
        val lastAccount = accountsList.find { it.email == lastEmail } ?: accountsList.first()
        _googleAccount.value = lastAccount
    }

    fun signInGoogle(name: String, email: String) {
        val initials = name.split(" ")
            .mapNotNull { it.firstOrNull()?.toString() }
            .take(2)
            .joinToString("")
            .ifEmpty { "G" }
            .uppercase()

        val newAcc = GoogleAccount(
            name = name,
            email = email,
            avatarInitials = initials,
            isSignedIn = true
        )

        _googleAccount.value = newAcc

        // Update persistent accounts list
        val currentList = _savedAccounts.value.toMutableList()
        currentList.removeAll { it.email == email }
        currentList.add(0, newAcc)
        _savedAccounts.value = currentList

        // Persist to SharedPreferences
        val encoded = currentList.joinToString(";") { "${it.name}|${it.email}" }
        prefs.edit()
            .putString("accounts_list", encoded)
            .putString("last_email", email)
            .apply()

        // Automatically trigger Google Account Real Playlists & Channel Subscriptions Sync!
        syncGoogleAccountData()
    }

    fun switchAccount(acc: GoogleAccount) {
        signInGoogle(acc.name, acc.email)
    }

    fun syncGoogleAccountData() {
        val current = _googleAccount.value
        if (!current.isSignedIn || current.email.isBlank()) return

        viewModelScope.launch {
            com.example.data.remote.GoogleAccountSyncService.syncUserAccountData(
                email = current.email,
                videoDao = db.videoDao(),
                categoryDao = db.playlistCategoryDao()
            )
            refreshFeed()
        }
    }

    fun signOutGoogle() {
        _googleAccount.value = GoogleAccount(
            name = "Guest User",
            email = "",
            avatarInitials = "?",
            isSignedIn = false
        )
    }

    // Search query & Category filter state
    val searchQuery = MutableStateFlow("")
    val selectedCategory = MutableStateFlow("All")
    val selectedTimeFilter = MutableStateFlow("Any Time")

    private var currentSearchBatchIndex = 0

    // Adverts (Ads ON / OFF) Settings state
    private val _areAdvertsEnabled = MutableStateFlow(false) // Default AdBlock Active (Adverts OFF)
    val areAdvertsEnabled: StateFlow<Boolean> = _areAdvertsEnabled.asStateFlow()

    fun setAdvertsEnabled(enabled: Boolean) {
        _areAdvertsEnabled.value = enabled
    }

    // Real Live YouTube Search & Category Results state
    private val _liveSearchResults = MutableStateFlow<List<VideoEntity>>(emptyList())
    val liveSearchResults: StateFlow<List<VideoEntity>> = _liveSearchResults.asStateFlow()

    private val _categoryVideos = MutableStateFlow<List<VideoEntity>>(emptyList())
    val categoryVideos: StateFlow<List<VideoEntity>> = _categoryVideos.asStateFlow()

    private val _algorithmSettings = MutableStateFlow(com.example.data.repository.AlgorithmSettings())
    val algorithmSettings: StateFlow<com.example.data.repository.AlgorithmSettings> = _algorithmSettings.asStateFlow()

    fun updateAlgorithmSettings(newSettings: com.example.data.repository.AlgorithmSettings) {
        _algorithmSettings.value = newSettings
        checkAndCleanExpiredDownloads()
    }

    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            repository.sanitizeWatchTimestamps()
            repository.clearUnsavedRecommendations()
            checkAndCleanExpiredDownloads()
        }

        viewModelScope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            searchQuery
                .debounce(400L)
                .collectLatest { query ->
                    val trimmed = query.trim()
                    if (trimmed.isNotBlank()) {
                        currentSearchBatchIndex = 0
                        _liveSearchResults.value = emptyList() // Instantly clear stale results!
                        val realVideos = com.example.data.remote.YouTubeLiveSearchService.searchRealYouTubeVideosBatch(trimmed, 0)
                        _liveSearchResults.value = realVideos
                        realVideos.forEach { v -> repository.saveVideo(v) }
                    } else {
                        currentSearchBatchIndex = 0
                        _liveSearchResults.value = emptyList()
                    }
                }
        }

        viewModelScope.launch {
            selectedCategory.collectLatest { category ->
                _categoryVideos.value = emptyList() // Clear immediately on category switch
                try {
                    val fetched = if (category == "All") {
                        val homeFeed = try {
                            com.example.data.remote.YouTubeLiveSearchService.fetchHomeRecommendationFeed()
                        } catch (e: Exception) { emptyList() }

                        val profileFeed = try {
                            com.example.data.remote.YouTubeLiveSearchService.fetchSubscribedProfileFeed()
                        } catch (e: Exception) { emptyList() }

                        val shortsFeed = try {
                            com.example.data.remote.YouTubeLiveSearchService.fetchShortsFeed()
                        } catch (e: Exception) { emptyList() }

                        (homeFeed + profileFeed + shortsFeed).distinctBy { it.youtubeId }
                    } else {
                        com.example.data.remote.YouTubeLiveSearchService.fetchCategoryFeed(category)
                    }
                    val englishOnly = fetched.filter { !YouTubeUtils.isForeignLanguageContent(it.title, it.channelName) }
                    _categoryVideos.value = englishOnly
                    englishOnly.forEach { v -> repository.saveVideo(v) }
                } catch (e: Exception) {
                    android.util.Log.e("YouTubeViewModel", "Category fetch error: ${e.message}")
                }
            }
        }
    }

    fun refreshTrendingFeed() {
        viewModelScope.launch {
            try {
                repository.clearUnsavedRecommendations()
                _categoryVideos.value = emptyList() // Instantly clear stale video list
                selectedSubscribedChannel.value = "" // Reset subscribed channel filter
                searchQuery.value = ""

                val profileFeed = com.example.data.remote.YouTubeLiveSearchService.fetchSubscribedProfileFeed()
                val homeFeed = com.example.data.remote.YouTubeLiveSearchService.fetchHomeRecommendationFeed()
                val shortsFeed = com.example.data.remote.YouTubeLiveSearchService.fetchShortsFeed()

                val combined = (profileFeed + homeFeed + shortsFeed)
                    .filter { !YouTubeUtils.isForeignLanguageContent(it.title, it.channelName) }
                    .distinctBy { it.youtubeId }
                    .sortedWith(
                        compareBy<VideoEntity> { com.example.util.YouTubeUtils.parsePublishedTimeToSeconds(it.publishedTimeText) }
                    )

                if (combined.isNotEmpty()) {
                    _categoryVideos.value = combined
                    combined.forEach { v -> repository.saveVideo(v) }
                }

                if (selectedCategory.value == "All") {
                    selectedCategory.value = ""
                    selectedCategory.value = "All"
                } else {
                    selectedCategory.value = "All"
                }
            } catch (e: Exception) {
                android.util.Log.e("YouTubeViewModel", "Feed refresh error: ${e.message}")
            }
        }
    }

    private var channelBatchIndex = 0
    private var isLoadingMore = false

    fun loadMoreCategoryVideos() {
        val channel = selectedSubscribedChannel.value.trim()
        if (channel.isNotBlank()) {
            loadMoreSubscribedChannelVideos()
            return
        }
        val currentCategory = selectedCategory.value
        val currentQuery = searchQuery.value
        if (currentQuery.isNotBlank()) {
            loadMoreSearchResults()
            return
        }
        if (isLoadingMore) return
        isLoadingMore = true
        viewModelScope.launch {
            try {
                val searchTerm = if (currentCategory != "All") currentCategory else "Trending YouTube videos"
                val additionalQueries = listOf(
                    "best $searchTerm 2026",
                    "$searchTerm full playlist",
                    "new $searchTerm review",
                    "$searchTerm 4K",
                    "popular $searchTerm",
                    "$searchTerm #shorts"
                )
                val nextQuery = additionalQueries.random()
                val newBatch = com.example.data.remote.YouTubeLiveSearchService.searchRealYouTubeVideos(nextQuery)
                if (newBatch.isNotEmpty()) {
                    val updated = (_categoryVideos.value + newBatch).distinctBy { it.youtubeId }
                    _categoryVideos.value = updated
                    newBatch.forEach { v -> repository.saveVideo(v) }
                }
            } finally {
                isLoadingMore = false
            }
        }
    }

    fun loadMoreSubscribedChannelVideos() {
        val channel = selectedSubscribedChannel.value.trim()
        if (channel.isBlank() || isLoadingMore) return
        isLoadingMore = true
        viewModelScope.launch {
            try {
                channelBatchIndex++
                val newBatch = com.example.data.remote.YouTubeLiveSearchService.fetchChannelVideosBatch(channel, channelBatchIndex)
                if (newBatch.isNotEmpty()) {
                    val updated = (_categoryVideos.value + newBatch)
                        .distinctBy { it.youtubeId }
                        .sortedWith(
                            compareBy<VideoEntity> { com.example.util.YouTubeUtils.parsePublishedTimeToSeconds(it.publishedTimeText) }
                        )
                    _categoryVideos.value = updated
                    newBatch.forEach { v -> repository.saveVideo(v) }
                }
            } finally {
                isLoadingMore = false
            }
        }
    }

    val selectedSubscribedChannel = MutableStateFlow("")

    fun selectSubscribedChannel(channelName: String) {
        selectedSubscribedChannel.value = channelName
        channelBatchIndex = 0
        if (channelName.isNotBlank()) {
            viewModelScope.launch {
                _categoryVideos.value = emptyList()
                val latestVideos = com.example.data.remote.YouTubeLiveSearchService.fetchChannelLatestVideos(channelName)
                _categoryVideos.value = latestVideos
                latestVideos.forEach { v -> repository.saveVideo(v) }
            }
        }
    }

    fun updateVideoCategory(videoId: String, newCategory: String) {
        viewModelScope.launch {
            repository.updateVideoCategory(videoId, newCategory)
        }
    }

    fun loadMoreSearchResults() {
        val currentQuery = searchQuery.value.trim()
        if (currentQuery.isBlank() || isLoadingMore) return
        isLoadingMore = true
        viewModelScope.launch {
            try {
                currentSearchBatchIndex++
                val newBatch = com.example.data.remote.YouTubeLiveSearchService.searchRealYouTubeVideosBatch(currentQuery, currentSearchBatchIndex)
                if (newBatch.isNotEmpty()) {
                    val updated = (_liveSearchResults.value + newBatch).distinctBy { it.youtubeId }
                    _liveSearchResults.value = updated
                    newBatch.forEach { v -> repository.saveVideo(v) }
                }
            } finally {
                isLoadingMore = false
            }
        }
    }

    // Video streams
    val categories: StateFlow<List<PlaylistCategoryEntity>> = repository.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val videos: StateFlow<List<VideoEntity>> = combine(
        repository.allVideos,
        searchQuery,
        selectedCategory
    ) { all, query, category ->
        var filtered = all.filter { !YouTubeUtils.isForeignLanguageContent(it.title, it.channelName) }
        if (category != "All") {
            filtered = filtered.filter { it.category.equals(category, ignoreCase = true) }
        }
        if (query.isNotBlank()) {
            val trimmedQuery = query.trim()
            val extractedId = YouTubeUtils.extractVideoId(trimmedQuery)
            filtered = filtered.filter {
                it.title.contains(trimmedQuery, ignoreCase = true) ||
                it.channelName.contains(trimmedQuery, ignoreCase = true) ||
                it.category.contains(trimmedQuery, ignoreCase = true) ||
                it.youtubeId.equals(trimmedQuery, ignoreCase = true) ||
                (extractedId != null && it.youtubeId.equals(extractedId, ignoreCase = true))
            }
        }
        filtered
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteVideos: StateFlow<List<VideoEntity>> = repository.favoriteVideos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val watchLaterVideos: StateFlow<List<VideoEntity>> = repository.watchLaterVideos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val watchHistory: StateFlow<List<VideoEntity>> = repository.watchHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active playing video state & Shorts mode tracking
    private val _activeVideoId = MutableStateFlow<String?>(null)
    val activeVideoId: StateFlow<String?> = _activeVideoId.asStateFlow()

    private val _isPlayingAsShort = MutableStateFlow<Boolean?>(null)
    val isPlayingAsShort: StateFlow<Boolean?> = _isPlayingAsShort.asStateFlow()

    private val _isPlayerPlaying = MutableStateFlow<Boolean>(true)
    val isPlayerPlaying: StateFlow<Boolean> = _isPlayerPlaying.asStateFlow()

    private val _playerCommand = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 5)
    val playerCommand: kotlinx.coroutines.flow.SharedFlow<String> = _playerCommand.asSharedFlow()

    fun togglePlayPause() {
        _playerCommand.tryEmit("TOGGLE_PLAY_PAUSE")
    }

    fun seekBy(seconds: Int) {
        if (seconds > 0) {
            _playerCommand.tryEmit("SEEK_FORWARD_$seconds")
        } else {
            _playerCommand.tryEmit("SEEK_BACKWARD_${-seconds}")
        }
    }

    fun setPlayerPlaying(playing: Boolean) {
        _isPlayerPlaying.value = playing
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeVideo: StateFlow<VideoEntity?> = _activeVideoId.flatMapLatest { id ->
        if (id == null) flowOf(null)
        else repository.getVideoById(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeNotes: StateFlow<List<VideoNoteEntity>> = _activeVideoId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList())
        else repository.getNotesForVideo(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _shortsQueue = MutableStateFlow<List<VideoEntity>>(emptyList())
    val shortsQueue: StateFlow<List<VideoEntity>> = _shortsQueue.asStateFlow()

    fun playVideo(video: VideoEntity, isShort: Boolean = false) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isPlayingAsShort.value = isShort
            val now = System.currentTimeMillis()
            val updated = video.copy(lastWatchedTimestamp = now)
            repository.saveVideo(updated)
            repository.updateWatchHistory(video.youtubeId, video.lastPositionSeconds)
            _activeVideoId.value = video.youtubeId
        }
    }

    fun playShort(video: VideoEntity) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val shortVid = video.copy(category = "Shorts")
            val currentList = _shortsQueue.value
            if (!currentList.any { it.youtubeId == shortVid.youtubeId }) {
                _shortsQueue.value = (currentList + shortVid).distinctBy { it.youtubeId }
            }
            // Pre-fetch more shorts in background if queue has less than 8 items
            if (_shortsQueue.value.size < 8) {
                try {
                    val freshShorts = com.example.data.remote.YouTubeLiveSearchService.fetchShortsFeed()
                    _shortsQueue.value = (_shortsQueue.value + freshShorts).distinctBy { it.youtubeId }
                    freshShorts.forEach { repository.saveVideo(it) }
                } catch (e: Exception) { }
            }
            playVideo(shortVid, isShort = true)
        }
    }

    fun playNextShort(currentVideoId: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var currentList = _shortsQueue.value
            if (currentList.isEmpty()) {
                currentList = _categoryVideos.value.filter { com.example.util.YouTubeUtils.isShortVideo(it) }
            }
            var currentIndex = currentList.indexOfFirst { it.youtubeId == currentVideoId }

            // If nearing end of queue, fetch fresh batch immediately
            if (currentIndex >= currentList.size - 3 || currentIndex == -1 || currentList.size < 4) {
                try {
                    val freshShorts = com.example.data.remote.YouTubeLiveSearchService.fetchShortsFeed()
                    currentList = (currentList + freshShorts).distinctBy { it.youtubeId }
                    _shortsQueue.value = currentList
                    freshShorts.forEach { repository.saveVideo(it) }
                } catch (e: Exception) { }
            }

            currentIndex = currentList.indexOfFirst { it.youtubeId == currentVideoId }
            if (currentIndex >= 0 && currentIndex < currentList.size - 1) {
                playShort(currentList[currentIndex + 1])
            } else if (currentList.isNotEmpty()) {
                val next = currentList.getOrNull(currentIndex + 1) ?: currentList.first()
                playShort(next)
            }
        }
    }

    fun playPreviousShort(currentVideoId: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val currentList = _shortsQueue.value.ifEmpty {
                _categoryVideos.value.filter { com.example.util.YouTubeUtils.isShortVideo(it) }
            }
            val currentIndex = currentList.indexOfFirst { it.youtubeId == currentVideoId }
            if (currentIndex > 0) {
                playShort(currentList[currentIndex - 1])
            }
        }
    }

    fun updatePlaybackPosition(youtubeId: String, positionSeconds: Int) {
        if (positionSeconds > 0) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                repository.updatePlaybackPosition(youtubeId, positionSeconds)
            }
        }
    }

    fun setActiveVideo(youtubeId: String, startSeconds: Int = 0) {
        _isPlayingAsShort.value = false
        _activeVideoId.value = youtubeId
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            repository.updateWatchHistory(youtubeId, startSeconds)
        }
    }

    fun clearActiveVideo() {
        _activeVideoId.value = null
        _isPlayingAsShort.value = null
    }

    fun onOAuthTokenReceived(accessToken: String) {
        viewModelScope.launch {
            try {
                // Save token to SharedPreferences for reuse
                val prefs = getApplication<android.app.Application>().getSharedPreferences("yt_prefs", android.content.Context.MODE_PRIVATE)
                prefs.edit().putString("oauth_access_token", accessToken).apply()

                // Fetch Google profile user email/name from token
                val client = okhttp3.OkHttpClient()
                val req = okhttp3.Request.Builder()
                    .url("https://www.googleapis.com/oauth2/v3/userinfo")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val response = client.newCall(req).execute()
                        if (response.isSuccessful) {
                            val body = response.body?.string() ?: ""
                            val json = org.json.JSONObject(body)
                            val email = json.optString("email")
                            val name = json.optString("name").ifEmpty { email.substringBefore("@") }
                            if (email.isNotBlank()) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    signInGoogle(name, email)
                                }
                            }
                        }
                    } catch (e: Exception) {}
                }

                // Trigger real YouTube Cloud history + playlists sync asynchronously in background
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        com.example.data.remote.GoogleAccountSyncService.syncOAuthCloudHistoryAndPlaylists(
                            accessToken = accessToken,
                            videoDao = db.videoDao(),
                            categoryDao = db.playlistCategoryDao()
                        )
                    } catch (e: Exception) {}
                }
            } catch (e: Exception) {
                // Log but don't crash
            }
        }
    }

    fun toggleFavorite(youtubeId: String, currentFavorite: Boolean) {
        viewModelScope.launch {
            repository.toggleFavorite(youtubeId, currentFavorite)
        }
    }

    fun toggleWatchLater(youtubeId: String, currentWatchLater: Boolean) {
        viewModelScope.launch {
            repository.toggleWatchLater(youtubeId, currentWatchLater)
        }
    }

    fun addVideoFromUrl(
        urlOrId: String,
        title: String,
        channelName: String,
        category: String,
        durationText: String,
        initialNote: String? = null,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val extractedId = YouTubeUtils.extractVideoId(urlOrId)
        if (extractedId == null) {
            onError("Invalid YouTube URL or Video ID. Please check the link and try again.")
            return
        }

        viewModelScope.launch {
            val videoTitle = if (title.isBlank()) "YouTube Video ($extractedId)" else title.trim()
            val channel = if (channelName.isBlank()) "Personal YouTube" else channelName.trim()
            val thumbnailUrl = YouTubeUtils.getThumbnailUrl(extractedId)

            val newVideo = VideoEntity(
                youtubeId = extractedId,
                title = videoTitle,
                channelName = channel,
                thumbnailUrl = thumbnailUrl,
                durationText = if (durationText.isBlank()) "10:00" else durationText.trim(),
                category = category,
                addedTimestamp = System.currentTimeMillis()
            )

            repository.saveVideo(newVideo)

            if (!initialNote.isNullOrBlank()) {
                val note = VideoNoteEntity(
                    youtubeId = extractedId,
                    timestampSeconds = 0,
                    timestampFormatted = "00:00",
                    noteText = initialNote.trim()
                )
                repository.addNote(note)
            }

            onSuccess(extractedId)
        }
    }

    fun refreshFeed() {
        refreshTrendingFeed()
    }

    fun addNoteToActiveVideo(timestampSeconds: Int, timestampFormatted: String, noteText: String) {
        val videoId = _activeVideoId.value ?: return
        if (noteText.isBlank()) return

        viewModelScope.launch {
            val note = VideoNoteEntity(
                youtubeId = videoId,
                timestampSeconds = timestampSeconds,
                timestampFormatted = timestampFormatted,
                noteText = noteText.trim()
            )
            repository.addNote(note)
        }
    }

    fun deleteNote(noteId: Long) {
        val videoId = _activeVideoId.value ?: return
        viewModelScope.launch {
            repository.deleteNote(noteId, videoId)
        }
    }

    fun deleteVideo(video: VideoEntity) {
        viewModelScope.launch {
            repository.deleteVideo(video)
            if (_activeVideoId.value == video.youtubeId) {
                _activeVideoId.value = null
            }
        }
    }

    fun addCategory(name: String, iconName: String, colorHex: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.addCategory(
                PlaylistCategoryEntity(
                    name = name.trim(),
                    iconName = iconName,
                    colorHex = colorHex
                )
            )
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }
}
