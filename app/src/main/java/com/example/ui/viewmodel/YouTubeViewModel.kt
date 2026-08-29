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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
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
                targetResolution = _algorithmSettings.value.downloadResolution,
                onSuccess = { localPath, sizeMb ->
                    viewModelScope.launch {
                        repository.updateDownloadStatus(video.youtubeId, true, localPath, sizeMb)
                        onComplete()
                    }
                },
                onError = { err ->
                    viewModelScope.launch { onError(err) }
                }
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
            accountsList.add(GoogleAccount(name = "Local User", email = "local@vixz.app", avatarInitials = "U", isSignedIn = true))
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

    private fun saveAlgorithmSettings(settings: com.example.data.repository.AlgorithmSettings) {
        val algoPrefs = getApplication<android.app.Application>().getSharedPreferences("algo_prefs", android.content.Context.MODE_PRIVATE)
        algoPrefs.edit()
            .putFloat("creator_weight", settings.creatorWeight)
            .putFloat("discovery_ratio", settings.discoveryRatio)
            .putString("shorts_mode", settings.shortsMode)
            .putInt("min_duration", settings.minDurationMinutes)
            .putString("freshness_decay", settings.freshnessDecay)
            .putString("auto_delete", settings.autoDeleteDownloads)
            .putString("download_resolution", settings.downloadResolution)
            .putStringSet("blocked_keywords", settings.blockedKeywords.toSet())
            .putStringSet("boosted_topics", settings.boostedTopics.toSet())
            .apply()
    }

    private fun loadAlgorithmSettings(): com.example.data.repository.AlgorithmSettings {
        val algoPrefs = getApplication<android.app.Application>().getSharedPreferences("algo_prefs", android.content.Context.MODE_PRIVATE)
        return com.example.data.repository.AlgorithmSettings(
            creatorWeight = algoPrefs.getFloat("creator_weight", 0.7f),
            discoveryRatio = algoPrefs.getFloat("discovery_ratio", 0.2f),
            shortsMode = algoPrefs.getString("shorts_mode", "Carousel") ?: "Carousel",
            minDurationMinutes = algoPrefs.getInt("min_duration", 0),
            freshnessDecay = algoPrefs.getString("freshness_decay", "Medium") ?: "Medium",
            autoDeleteDownloads = algoPrefs.getString("auto_delete", "Never") ?: "Never",
            downloadResolution = algoPrefs.getString("download_resolution", "720p") ?: "720p",
            blockedKeywords = algoPrefs.getStringSet("blocked_keywords", emptySet())?.toList() ?: emptyList(),
            boostedTopics = algoPrefs.getStringSet("boosted_topics", emptySet())?.toList() ?: emptyList()
        )
    }

    // Subscribed Creators Management (Add, Remove, Rename)
    private val DEFAULT_CREATORS = listOf(
        "Benny Johnson",
        "The Rubin Report",
        "Lex Fridman",
        "Tucker Carlson",
        "Piers Morgan Uncensored",
        "Veritasium",
        "Huberman Lab",
        "Cleo Abram"
    )

    private fun saveSubscribedCreators(creators: List<String>) {
        val prefs = getApplication<android.app.Application>().getSharedPreferences("creator_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putStringSet("subscribed_creators", creators.toSet()).apply()
    }

    private fun loadSubscribedCreators(): List<String> {
        val prefs = getApplication<android.app.Application>().getSharedPreferences("creator_prefs", android.content.Context.MODE_PRIVATE)
        val saved = prefs.getStringSet("subscribed_creators", null)
        val list = if (saved != null && saved.isNotEmpty()) {
            saved.toList().sorted()
        } else {
            DEFAULT_CREATORS
        }
        com.example.data.model.WillRyanProfileData.clearAllSubscribedChannels()
        list.forEach { com.example.data.model.WillRyanProfileData.addSubscribedChannel(it) }
        return list
    }

    private val _subscribedCreators = MutableStateFlow<List<String>>(loadSubscribedCreators())
    val subscribedCreators: StateFlow<List<String>> = _subscribedCreators.asStateFlow()

    private fun saveDislikedVideoIds(ids: Set<String>) {
        val prefs = getApplication<android.app.Application>().getSharedPreferences("algo_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putStringSet("disliked_video_ids", ids).apply()
    }

    private fun loadDislikedVideoIds(): Set<String> {
        val prefs = getApplication<android.app.Application>().getSharedPreferences("algo_prefs", android.content.Context.MODE_PRIVATE)
        return prefs.getStringSet("disliked_video_ids", emptySet()) ?: emptySet()
    }

    private val _dislikedVideoIds = MutableStateFlow<Set<String>>(loadDislikedVideoIds())
    val dislikedVideoIds: StateFlow<Set<String>> = _dislikedVideoIds.asStateFlow()

    private val _algorithmSettings = MutableStateFlow(loadAlgorithmSettings())
    val algorithmSettings: StateFlow<com.example.data.repository.AlgorithmSettings> = _algorithmSettings.asStateFlow()

    fun updateAlgorithmSettings(newSettings: com.example.data.repository.AlgorithmSettings) {
        _algorithmSettings.value = newSettings
        saveAlgorithmSettings(newSettings)
        checkAndCleanExpiredDownloads()
    }

    // Background continuous feed buffer for seamless, instant infinite scrolling
    private val _feedBuffer = MutableStateFlow<List<VideoEntity>>(emptyList())
    private val MIN_BUFFER_THRESHOLD = 16

    private var feedBatchIndex = 0

    private fun replenishFeedBufferAsync() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                feedBatchIndex++
                val profileBatch = com.example.data.remote.YouTubeLiveSearchService.fetchSubscribedProfileFeed(
                    batchIndex = feedBatchIndex,
                    batchSize = 8
                )
                val discoveryBatch = com.example.data.remote.YouTubeLiveSearchService.fetchIntelligentDiscoveryVideos(_subscribedCreators.value)
                val combined = (profileBatch + discoveryBatch)

                val disliked = _dislikedVideoIds.value
                val fresh = combined
                    .filter { !YouTubeUtils.isForeignLanguageContent(it.title, it.channelName) }
                    .filter { it.youtubeId !in disliked }
                    .distinctBy { it.youtubeId }

                if (fresh.isNotEmpty()) {
                    val currentIds = _categoryVideos.value.map { it.youtubeId }.toSet()
                    val newUnique = fresh.filter { it.youtubeId !in currentIds }
                    if (newUnique.isNotEmpty()) {
                        _categoryVideos.value = (_categoryVideos.value + newUnique).distinctBy { it.youtubeId }
                        newUnique.forEach { repository.saveVideo(it) }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("YouTubeViewModel", "Buffer replenishment error: ${e.message}")
            }
        }
    }

    init {
        // 1. Instant 0ms Cache Load on Startup & Thorough Database Purge of Unsubscribed/Foreign Content
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            checkAndCleanExpiredDownloads()
            val cached = repository.getAllVideosDirect()
            if (cached.isNotEmpty()) {
                val subscribedSet = _subscribedCreators.value.map { it.lowercase().trim() }.toSet()
                val (keepVideos, junkVideos) = cached.partition { video ->
                    val ch = video.channelName.lowercase().trim()
                    val isSub = subscribedSet.any { sub -> ch.contains(sub) || sub.contains(ch) }
                    val isUserSaved = video.isFavorite || video.isWatchLater || video.lastWatchedTimestamp > 0L || video.lastPositionSeconds > 0
                    !YouTubeUtils.isForeignLanguageContent(video.title, video.channelName) && (isSub || isUserSaved)
                }

                junkVideos.forEach { junk ->
                    try { repository.deleteVideo(junk) } catch (e: Exception) { }
                }

                val valid = keepVideos.filter { it.youtubeId !in _dislikedVideoIds.value }
                if (valid.isNotEmpty()) {
                    _categoryVideos.value = valid
                }
            }
            // Trigger fresh feed load from subscribed channels
            refreshTrendingFeed()
        }

        // 2. Continuous background updater: keeps the feed buffer refreshed with subscribed creator content
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            while (coroutineContext.isActive) {
                try {
                    if (selectedCategory.value == "All" && searchQuery.value.isBlank() && selectedSubscribedChannel.value.isBlank()) {
                        if (_categoryVideos.value.size < MIN_BUFFER_THRESHOLD) {
                            replenishFeedBufferAsync()
                        }
                    }
                } catch (e: Exception) { }
                kotlinx.coroutines.delay(30_000L) // check every 30s in background
            }
        }

        // 3. Search: SWR Flow (Instant Local DB Matches -> Smooth Debounced Background Network Search)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            searchQuery
                .debounce(650L)
                .collectLatest { query ->
                    val trimmed = query.trim()
                    if (trimmed.isNotBlank()) {
                        currentSearchBatchIndex = 0
                        // Step A: Instant cached search results
                        val cachedMatches = repository.searchVideosDirect(trimmed)
                        if (cachedMatches.isNotEmpty()) {
                            _liveSearchResults.value = cachedMatches
                        }

                        // Step B: Smooth Live Network Search without blocking typing
                        val realVideos = com.example.data.remote.YouTubeLiveSearchService.searchRealYouTubeVideos(trimmed, sortByUploadDate = false)
                        if (realVideos.isNotEmpty()) {
                            _liveSearchResults.value = realVideos.distinctBy { it.youtubeId }
                            realVideos.forEach { v -> repository.saveVideo(v) }
                        }
                    } else {
                        currentSearchBatchIndex = 0
                        _liveSearchResults.value = emptyList()
                    }
                }
        }

        // 4. Category / Home: SWR Flow (Instant Local DB -> Silent Live Network Sync)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            selectedCategory.collectLatest { category ->
                feedBatchIndex = 0
                try {
                    // Step A: Instant 0ms cached videos for category
                    val cached = if (category == "All") {
                        repository.getAllVideosDirect()
                    } else {
                        repository.getVideosByCategoryDirect(category)
                    }
                    if (cached.isNotEmpty()) {
                        val valid = cached.filter { it.youtubeId !in _dislikedVideoIds.value && !YouTubeUtils.isForeignLanguageContent(it.title, it.channelName) }
                        if (valid.isNotEmpty()) {
                            _categoryVideos.value = valid
                        }
                    }

                    // Step B: Parallel Live Network Sync (Strictly Subscribed Creators or Curated Categories)
                    val fetched = if (category == "All") {
                        val profileFeed = try {
                            com.example.data.remote.YouTubeLiveSearchService.fetchSubscribedProfileFeed(batchIndex = 0, batchSize = 12)
                        } catch (e: Exception) { emptyList() }

                        val freshTechNews = try {
                            com.example.data.remote.YouTubeLiveSearchService.searchRealYouTubeVideos("latest breakthrough news 2026", sortByUploadDate = true)
                        } catch (e: Exception) { emptyList() }

                        (profileFeed + freshTechNews).distinctBy { it.youtubeId }
                    } else {
                        com.example.data.remote.YouTubeLiveSearchService.fetchCategoryFeed(category)
                    }
                    val filtered = fetched
                        .filter { !YouTubeUtils.isForeignLanguageContent(it.title, it.channelName) }
                        .filter { it.youtubeId !in _dislikedVideoIds.value }
                    if (filtered.isNotEmpty()) {
                        _categoryVideos.value = (filtered + _categoryVideos.value).distinctBy { it.youtubeId }
                        filtered.forEach { v -> repository.saveVideo(v) }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("YouTubeViewModel", "Category fetch error: ${e.message}")
                }
            }
        }
    }

    fun refreshTrendingFeed() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                feedBatchIndex = 0
                channelBatchIndex = 0
                currentSearchBatchIndex = 0
                selectedSubscribedChannel.value = ""
                searchQuery.value = ""
                selectedCategory.value = "All"
                selectedTimeFilter.value = "Any Time"

                com.example.data.remote.YouTubeLiveSearchService.clearCache()

                // 1. Fetch latest uploads from all subscribed channels (Benny Johnson, Tucker Carlson, The Rubin Report, etc.)
                val profileFeed = try {
                    com.example.data.remote.YouTubeLiveSearchService.fetchSubscribedProfileFeed(batchIndex = 0, batchSize = 12, forceRefresh = true)
                } catch (e: Exception) { emptyList() }

                // 2. Fetch intelligent discovery videos matching user's creators and interests
                val discoveryFeed = try {
                    com.example.data.remote.YouTubeLiveSearchService.fetchIntelligentDiscoveryVideos(_subscribedCreators.value, forceRefresh = true)
                } catch (e: Exception) { emptyList() }

                val combined = (profileFeed + discoveryFeed)
                    .filter { !YouTubeUtils.isForeignLanguageContent(it.title, it.channelName) }
                    .filter { it.youtubeId !in _dislikedVideoIds.value }
                    .distinctBy { it.youtubeId }

                val allCached = try { repository.getAllVideosDirect() } catch (e: Exception) { emptyList() }
                val favList = allCached.filter { it.isFavorite }
                val histList = allCached.filter { it.lastWatchedTimestamp > 0L }

                val ranked = com.example.data.repository.RecommendationEngine.scoreAndRankVideos(
                    videos = combined,
                    favorites = favList,
                    watchHistory = histList,
                    mutedChannels = mutedChannels.value,
                    dislikedVideoIds = _dislikedVideoIds.value,
                    settings = _algorithmSettings.value
                )

                if (ranked.isNotEmpty()) {
                    _categoryVideos.value = ranked
                    ranked.forEach { v -> repository.saveVideo(v) }
                }

                // Continuously top up the background buffer
                replenishFeedBufferAsync()
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
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                if (currentCategory == "All") {
                    feedBatchIndex++
                    val profileBatch = try {
                        com.example.data.remote.YouTubeLiveSearchService.fetchSubscribedProfileFeed(
                            batchIndex = feedBatchIndex,
                            batchSize = 8
                        )
                    } catch (e: Exception) { emptyList() }

                    val discoveryQueries = listOf(
                        "Benny Johnson podcast 2026",
                        "Tucker Carlson in depth",
                        "Lex Fridman science tech",
                        "breakthrough tech AI 2026",
                        "fascinating podcast full"
                    )
                    val discoveryBatch = try {
                        com.example.data.remote.YouTubeLiveSearchService.searchRealYouTubeVideos(discoveryQueries.random())
                    } catch (e: Exception) { emptyList() }

                    val combined = (profileBatch + discoveryBatch)
                        .filter { !YouTubeUtils.isForeignLanguageContent(it.title, it.channelName) }
                        .filter { it.youtubeId !in _dislikedVideoIds.value }
                        .distinctBy { it.youtubeId }

                    if (combined.isNotEmpty()) {
                        _categoryVideos.value = (_categoryVideos.value + combined).distinctBy { it.youtubeId }
                        combined.forEach { v -> repository.saveVideo(v) }
                    }
                } else {
                    feedBatchIndex++
                    val searchTerm = currentCategory
                    val additionalQueries = listOf(
                        "best $searchTerm 2026",
                        "$searchTerm full playlist",
                        "new $searchTerm review",
                        "$searchTerm 4K",
                        "top $searchTerm highlights"
                    )
                    val newBatch = com.example.data.remote.YouTubeLiveSearchService.searchRealYouTubeVideos(additionalQueries.random())
                    val unDisliked = newBatch
                        .filter { !YouTubeUtils.isForeignLanguageContent(it.title, it.channelName) }
                        .filter { it.youtubeId !in _dislikedVideoIds.value }

                    if (unDisliked.isNotEmpty()) {
                        val updated = (_categoryVideos.value + unDisliked).distinctBy { it.youtubeId }
                        _categoryVideos.value = updated
                        unDisliked.forEach { v -> repository.saveVideo(v) }
                    }
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
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                // Step A: Instant 0ms cached channel videos from local DB
                val cached = repository.getVideosByChannelDirect(channelName)
                if (cached.isNotEmpty()) {
                    _categoryVideos.value = cached.sortedWith(
                        compareBy<VideoEntity> { com.example.util.YouTubeUtils.parsePublishedTimeToSeconds(it.publishedTimeText) }
                    )
                }

                // Step B: Parallel Live Channel Fetch from YouTube (Always live & forced fresh)
                val latestVideos = com.example.data.remote.YouTubeLiveSearchService.fetchChannelLatestVideos(channelName, forceRefresh = true)
                if (latestVideos.isNotEmpty()) {
                    val merged = (latestVideos + _categoryVideos.value)
                        .distinctBy { it.youtubeId }
                        .sortedWith(
                            compareBy<VideoEntity> { com.example.util.YouTubeUtils.parsePublishedTimeToSeconds(it.publishedTimeText) }
                        )
                    _categoryVideos.value = merged
                    latestVideos.forEach { v -> repository.saveVideo(v) }
                }
            }
        }
    }

    fun refreshSubscribedChannel(channelName: String) {
        selectSubscribedChannel(channelName)
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

    private val _activeVideo = MutableStateFlow<VideoEntity?>(null)
    val activeVideo: StateFlow<VideoEntity?> = _activeVideo.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeNotes: StateFlow<List<VideoNoteEntity>> = _activeVideoId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList())
        else repository.getNotesForVideo(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _shortsQueue = MutableStateFlow<List<VideoEntity>>(emptyList())
    val shortsQueue: StateFlow<List<VideoEntity>> = _shortsQueue.asStateFlow()

    // Non-repeating Shorts Session History:
    // Tracks every Short played in session order so scroll-down NEVER repeats a short,
    // while scroll-up steps backward through the exact history stack.
    private val _watchedShortsHistory = mutableListOf<VideoEntity>()
    private var _currentShortHistoryIndex = -1
    private val _seenShortIds = mutableSetOf<String>()

    fun playVideo(video: VideoEntity, isShort: Boolean = false) {
        android.util.Log.d("YouTubeViewModel", "playVideo called: id=${video.youtubeId}, isShort=$isShort, title=${video.title}")
        _isPlayingAsShort.value = isShort
        _activeVideoId.value = video.youtubeId
        _activeVideo.value = video

        // Immediately filter out from active feed lists and background buffer so returning to feed shows it removed
        _categoryVideos.value = _categoryVideos.value.filter { it.youtubeId != video.youtubeId }
        _feedBuffer.value = _feedBuffer.value.filter { it.youtubeId != video.youtubeId }
        _liveSearchResults.value = _liveSearchResults.value.filter { it.youtubeId != video.youtubeId }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val updated = video.copy(lastWatchedTimestamp = now)
            repository.saveVideo(updated)
            repository.updateWatchHistory(video.youtubeId, video.lastPositionSeconds)
        }
    }

    fun playShort(video: VideoEntity) {
        android.util.Log.d("YouTubeViewModel", "playShort called: id=${video.youtubeId}, title=${video.title}")
        val shortVid = video.copy(category = "Shorts")

        // Record in session history
        if (_currentShortHistoryIndex in _watchedShortsHistory.indices && _watchedShortsHistory[_currentShortHistoryIndex].youtubeId == shortVid.youtubeId) {
            // Already at current position in history
        } else {
            val existingIndex = _watchedShortsHistory.indexOfFirst { it.youtubeId == shortVid.youtubeId }
            if (existingIndex != -1) {
                _currentShortHistoryIndex = existingIndex
            } else {
                _watchedShortsHistory.add(shortVid)
                _seenShortIds.add(shortVid.youtubeId)
                _currentShortHistoryIndex = _watchedShortsHistory.size - 1
            }
        }

        val currentList = _shortsQueue.value
        if (!currentList.any { it.youtubeId == shortVid.youtubeId }) {
            _shortsQueue.value = (currentList + shortVid).distinctBy { it.youtubeId }
        }
        _categoryVideos.value = _categoryVideos.value.filter { it.youtubeId != video.youtubeId }
        _liveSearchResults.value = _liveSearchResults.value.filter { it.youtubeId != video.youtubeId }
        
        // 1. Immediately launch video with 0ms delay
        playVideo(shortVid, isShort = true)

        // 2. Pre-fetch more unique shorts in background asynchronously
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val unplayedInQueue = _shortsQueue.value.count { it.youtubeId !in _seenShortIds && it.youtubeId !in _dislikedVideoIds.value }
            if (unplayedInQueue < 10) {
                try {
                    val freshShorts = com.example.data.remote.YouTubeLiveSearchService.fetchShortsFeed()
                    val unDisliked = freshShorts.filter { it.youtubeId !in _dislikedVideoIds.value && it.youtubeId !in _seenShortIds }
                    _shortsQueue.value = (_shortsQueue.value + unDisliked).distinctBy { it.youtubeId }
                    unDisliked.forEach { repository.saveVideo(it) }
                } catch (e: Exception) { }
            }
        }
    }

    fun thumbsUpShort(video: VideoEntity) {
        toggleFavorite(video.youtubeId, video.isFavorite)
        // Boost creator in algorithm settings if not already present
        if (!video.isFavorite) {
            val currentBoosted = _algorithmSettings.value.boostedTopics.toMutableList()
            if (!currentBoosted.any { it.equals(video.channelName, ignoreCase = true) }) {
                currentBoosted.add(0, video.channelName)
                updateAlgorithmSettings(_algorithmSettings.value.copy(boostedTopics = currentBoosted))
            }
        }
    }

    fun thumbsDownShort(video: VideoEntity) {
        val newDisliked = _dislikedVideoIds.value + video.youtubeId
        _dislikedVideoIds.value = newDisliked
        saveDislikedVideoIds(newDisliked)

        // If favorited, unfavorite
        if (video.isFavorite) {
            toggleFavorite(video.youtubeId, true)
        }

        // Remove from shorts queue
        val currentQueue = _shortsQueue.value.filter { it.youtubeId != video.youtubeId }
        _shortsQueue.value = currentQueue

        // Remove from category videos / feed
        _categoryVideos.value = _categoryVideos.value.filter { it.youtubeId != video.youtubeId }

        // Immediately skip to next short
        playNextShort(video.youtubeId)
    }

    fun playNextShort(currentVideoId: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // 1. If user previously scrolled UP into history, scrolling DOWN returns forward through history
            if (_currentShortHistoryIndex >= 0 && _currentShortHistoryIndex < _watchedShortsHistory.size - 1) {
                _currentShortHistoryIndex++
                val nextInHistory = _watchedShortsHistory[_currentShortHistoryIndex]
                playVideo(nextInHistory, isShort = true)
                return@launch
            }

            // 2. User is at the frontier: select a 100% BRAND NEW UNSEEN SHORT (NEVER REPEATS)
            val disliked = _dislikedVideoIds.value
            var candidate = _shortsQueue.value.firstOrNull { it.youtubeId !in _seenShortIds && it.youtubeId !in disliked }

            // If no unplayed shorts remaining in queue, fetch fresh batch immediately
            if (candidate == null) {
                try {
                    val freshShorts = com.example.data.remote.YouTubeLiveSearchService.fetchShortsFeed()
                    val unDisliked = freshShorts.filter { it.youtubeId !in disliked && it.youtubeId !in _seenShortIds }
                    _shortsQueue.value = (_shortsQueue.value + unDisliked).distinctBy { it.youtubeId }
                    unDisliked.forEach { repository.saveVideo(it) }
                    candidate = unDisliked.firstOrNull()
                } catch (e: Exception) { }
            }

            // Fallback: Check category videos for any unplayed Short
            if (candidate == null) {
                candidate = _categoryVideos.value.firstOrNull { 
                    com.example.util.YouTubeUtils.isShortVideo(it) && it.youtubeId !in _seenShortIds && it.youtubeId !in disliked 
                }
            }

            if (candidate != null) {
                _seenShortIds.add(candidate.youtubeId)
                _watchedShortsHistory.add(candidate)
                _currentShortHistoryIndex = _watchedShortsHistory.size - 1
                playVideo(candidate, isShort = true)

                // Background refill when buffer gets low
                if (_shortsQueue.value.count { it.youtubeId !in _seenShortIds } < 8) {
                    try {
                        val fresh = com.example.data.remote.YouTubeLiveSearchService.fetchShortsFeed()
                        val valid = fresh.filter { it.youtubeId !in disliked && it.youtubeId !in _seenShortIds }
                        _shortsQueue.value = (_shortsQueue.value + valid).distinctBy { it.youtubeId }
                        valid.forEach { repository.saveVideo(it) }
                    } catch (e: Exception) { }
                }
            }
        }
    }

    fun playPreviousShort(currentVideoId: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // Scrolling UP: Step backwards in chronological watched history
            if (_currentShortHistoryIndex > 0) {
                _currentShortHistoryIndex--
                val prevInHistory = _watchedShortsHistory[_currentShortHistoryIndex]
                playVideo(prevInHistory, isShort = true)
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
            val video = repository.getVideoDirect(youtubeId) ?: VideoEntity(
                youtubeId = youtubeId,
                title = "YouTube Video ($youtubeId)",
                channelName = "Personal YouTube",
                thumbnailUrl = YouTubeUtils.getThumbnailUrl(youtubeId),
                durationText = "10:00",
                category = "General"
            )
            _activeVideo.value = video
            repository.updateWatchHistory(youtubeId, startSeconds)
        }
    }

    fun clearActiveVideo() {
        _activeVideoId.value = null
        _activeVideo.value = null
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
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val target = !currentFavorite
            val current = _activeVideo.value
            if (current != null && current.youtubeId == youtubeId) {
                val updated = current.copy(isFavorite = target)
                _activeVideo.value = updated
                repository.saveVideo(updated)
            }
            repository.toggleFavorite(youtubeId, currentFavorite)
        }
    }

    fun toggleWatchLater(youtubeId: String, currentWatchLater: Boolean) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val target = !currentWatchLater
            val current = _activeVideo.value
            if (current != null && current.youtubeId == youtubeId) {
                val updated = current.copy(isWatchLater = target)
                _activeVideo.value = updated
                repository.saveVideo(updated)
            }
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
        onSuccess: (VideoEntity) -> Unit,
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

            _activeVideo.value = newVideo
            _activeVideoId.value = extractedId
            _isPlayingAsShort.value = YouTubeUtils.isShortVideo(newVideo)

            onSuccess(newVideo)
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
        val newDisliked = _dislikedVideoIds.value + video.youtubeId
        _dislikedVideoIds.value = newDisliked
        saveDislikedVideoIds(newDisliked)

        _categoryVideos.value = _categoryVideos.value.filter { it.youtubeId != video.youtubeId }
        _feedBuffer.value = _feedBuffer.value.filter { it.youtubeId != video.youtubeId }
        _liveSearchResults.value = _liveSearchResults.value.filter { it.youtubeId != video.youtubeId }
        _shortsQueue.value = _shortsQueue.value.filter { it.youtubeId != video.youtubeId }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            repository.deleteVideo(video)
            if (_activeVideoId.value == video.youtubeId) {
                _activeVideoId.value = null
            }
        }
    }

    fun markNotInterested(video: VideoEntity) {
        deleteVideo(video)
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

    fun renameCategory(category: PlaylistCategoryEntity, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            repository.updateCategory(category.copy(name = trimmed))
        }
    }

    fun deleteCategory(category: PlaylistCategoryEntity) {
        viewModelScope.launch {
            repository.deleteCategory(category.id)
            if (selectedCategory.value.equals(category.name, ignoreCase = true)) {
                selectedCategory.value = "All"
            }
        }
    }

    // Subscribed Creators Management (Add, Remove, Rename)

    fun addSubscribedCreator(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val current = _subscribedCreators.value.toMutableList()
        if (!current.any { it.equals(trimmed, ignoreCase = true) }) {
            current.add(0, trimmed)
            _subscribedCreators.value = current
            saveSubscribedCreators(current)
            com.example.data.model.WillRyanProfileData.addSubscribedChannel(trimmed)
            refreshTrendingFeed()
        }
    }

    fun toggleSubscribedCreator(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return false
        val isSubbed = _subscribedCreators.value.any { it.equals(trimmed, ignoreCase = true) }
        if (isSubbed) {
            removeSubscribedCreator(trimmed)
            return false
        } else {
            addSubscribedCreator(trimmed)
            return true
        }
    }

    fun removeSubscribedCreator(name: String) {
        val current = _subscribedCreators.value.filter { !it.equals(name.trim(), ignoreCase = true) }
        _subscribedCreators.value = current
        saveSubscribedCreators(current)
        com.example.data.model.WillRyanProfileData.subscribedChannels.removeIf { it.equals(name.trim(), ignoreCase = true) }
        if (selectedSubscribedChannel.value.equals(name.trim(), ignoreCase = true)) {
            selectedSubscribedChannel.value = ""
        }
        refreshTrendingFeed()
    }

    fun renameSubscribedCreator(oldName: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        val current = _subscribedCreators.value.map {
            if (it.equals(oldName.trim(), ignoreCase = true)) trimmed else it
        }
        _subscribedCreators.value = current
        saveSubscribedCreators(current)
        val idx = com.example.data.model.WillRyanProfileData.subscribedChannels.indexOfFirst { it.equals(oldName.trim(), ignoreCase = true) }
        if (idx >= 0) {
            com.example.data.model.WillRyanProfileData.subscribedChannels[idx] = trimmed
        }
        if (selectedSubscribedChannel.value.equals(oldName.trim(), ignoreCase = true)) {
            selectedSubscribedChannel.value = trimmed
        }
        refreshTrendingFeed()
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }
}
