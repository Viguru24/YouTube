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
    private val repository = YouTubeRepository(db.videoDao(), db.videoNoteDao(), db.playlistCategoryDao())

    // Google Auth Account state
    private val _googleAccount = MutableStateFlow(GoogleAccount(isSignedIn = false))
    val googleAccount: StateFlow<GoogleAccount> = _googleAccount.asStateFlow()

    fun signInGoogle(name: String, email: String) {
        val initials = name.split(" ")
            .mapNotNull { it.firstOrNull()?.toString() }
            .take(2)
            .joinToString("")
            .ifEmpty { "G" }
            .uppercase()

        _googleAccount.value = GoogleAccount(
            name = name,
            email = email,
            avatarInitials = initials,
            isSignedIn = true
        )
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

    init {
        viewModelScope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            searchQuery
                .collectLatest { query ->
                    val trimmed = query.trim()
                    if (trimmed.isNotBlank()) {
                        _liveSearchResults.value = emptyList() // Instantly clear stale results!
                        val realVideos = com.example.data.remote.YouTubeLiveSearchService.searchRealYouTubeVideos(trimmed)
                        _liveSearchResults.value = realVideos
                    } else {
                        _liveSearchResults.value = emptyList()
                    }
                }
        }

        viewModelScope.launch {
            selectedCategory.collectLatest { category ->
                if (category != "All") {
                    val searchTopic = when (category) {
                        "Tech & Code" -> "Android coding tutorial programming tech"
                        "Music" -> "Trending music videos official 2026"
                        "Tutorials" -> "Full tutorial how to guide"
                        "Gaming" -> "Gaming walkthrough 4K 60fps"
                        else -> "$category trending videos"
                    }
                    val fetched = com.example.data.remote.YouTubeLiveSearchService.searchRealYouTubeVideos(searchTopic)
                    _categoryVideos.value = fetched
                } else {
                    _categoryVideos.value = emptyList()
                }
            }
        }
    }

    fun loadMoreCategoryVideos() {
        val currentCategory = selectedCategory.value
        val currentQuery = searchQuery.value
        viewModelScope.launch {
            val searchTerm = if (currentQuery.isNotBlank()) currentQuery else if (currentCategory != "All") currentCategory else "Trending YouTube videos"
            val additionalQueries = listOf("best $searchTerm 2026", "$searchTerm full playlist", "new $searchTerm review", "$searchTerm 4K", "popular $searchTerm")
            val nextQuery = additionalQueries.random()
            val newBatch = com.example.data.remote.YouTubeLiveSearchService.searchRealYouTubeVideos(nextQuery)
            if (newBatch.isNotEmpty()) {
                val updated = (_categoryVideos.value + newBatch).distinctBy { it.youtubeId }
                _categoryVideos.value = updated
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
        var filtered = all
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

    // Active playing video state
    private val _activeVideoId = MutableStateFlow<String?>(null)
    val activeVideoId: StateFlow<String?> = _activeVideoId.asStateFlow()

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

    fun playVideo(video: VideoEntity) {
        viewModelScope.launch {
            repository.saveVideo(video)
            repository.updateWatchHistory(video.youtubeId, video.lastPositionSeconds)
            _activeVideoId.value = video.youtubeId
        }
    }

    fun setActiveVideo(youtubeId: String, startSeconds: Int = 0) {
        _activeVideoId.value = youtubeId
        viewModelScope.launch {
            repository.updateWatchHistory(youtubeId, startSeconds)
        }
    }

    fun clearActiveVideo() {
        _activeVideoId.value = null
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
        val currentQuery = searchQuery.value
        if (currentQuery.isNotEmpty()) {
            searchQuery.value = ""
            searchQuery.value = currentQuery
        }
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
