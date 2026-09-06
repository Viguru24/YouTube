package com.example.data.repository

import com.example.data.dao.PlaylistCategoryDao
import com.example.data.dao.VideoDao
import com.example.data.dao.VideoNoteDao
import com.example.data.model.PlaylistCategoryEntity
import com.example.data.model.VideoEntity
import com.example.data.model.VideoNoteEntity
import kotlinx.coroutines.flow.Flow

class YouTubeRepository(
    private val videoDao: VideoDao,
    private val videoNoteDao: VideoNoteDao,
    private val categoryDao: PlaylistCategoryDao,
    private val mutedChannelDao: com.example.data.db.MutedChannelDao
) {
    val allVideos: Flow<List<VideoEntity>> = videoDao.getAllVideos()
    val favoriteVideos: Flow<List<VideoEntity>> = videoDao.getFavoriteVideos()
    val watchLaterVideos: Flow<List<VideoEntity>> = videoDao.getWatchLaterVideos()
    val watchHistory: Flow<List<VideoEntity>> = videoDao.getWatchHistory()
    val downloadedVideos: Flow<List<VideoEntity>> = videoDao.getDownloadedVideos()
    val categories: Flow<List<PlaylistCategoryEntity>> = categoryDao.getAllCategories()
    val mutedChannels: Flow<List<com.example.data.model.MutedChannelEntity>> = mutedChannelDao.getAllMutedChannels()

    suspend fun muteChannel(channelName: String) {
        mutedChannelDao.muteChannel(com.example.data.model.MutedChannelEntity(channelName))
    }

    suspend fun unmuteChannel(channelName: String) {
        mutedChannelDao.deleteByName(channelName)
    }

    suspend fun cleanupStaleRecommendations() {
        val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
        videoDao.deleteStaleUnsavedVideos(sevenDaysAgo)
    }

    suspend fun clearUnsavedRecommendations() {
        videoDao.clearUnsavedVideos()
    }

    fun getVideosByCategory(category: String): Flow<List<VideoEntity>> {
        return videoDao.getVideosByCategory(category)
    }

    suspend fun getAllVideosDirect(): List<VideoEntity> {
        return videoDao.getAllVideosDirect()
    }

    suspend fun getVideosByCategoryDirect(category: String): List<VideoEntity> {
        return videoDao.getVideosByCategoryDirect(category)
    }

    fun getVideosByChannel(channelName: String): Flow<List<VideoEntity>> {
        return videoDao.getVideosByChannel(channelName)
    }

    suspend fun getVideosByChannelDirect(channelName: String): List<VideoEntity> {
        return videoDao.getVideosByChannelDirect(channelName)
    }

    fun getVideoById(youtubeId: String): Flow<VideoEntity?> {
        return videoDao.getVideoByIdFlow(youtubeId)
    }

    suspend fun getVideoDirect(youtubeId: String): VideoEntity? {
        return videoDao.getVideoById(youtubeId)
    }

    fun searchVideos(query: String): Flow<List<VideoEntity>> {
        return videoDao.searchVideos(query)
    }

    suspend fun searchVideosDirect(query: String): List<VideoEntity> {
        return videoDao.searchVideosDirect(query)
    }

    suspend fun saveVideo(video: VideoEntity) {
        val existing = videoDao.getVideoById(video.youtubeId)
        if (existing != null) {
            val updated = video.copy(
                lastPositionSeconds = if (video.lastPositionSeconds > 0) video.lastPositionSeconds else existing.lastPositionSeconds,
                lastWatchedTimestamp = if (video.lastWatchedTimestamp > 0) video.lastWatchedTimestamp else existing.lastWatchedTimestamp,
                isFavorite = existing.isFavorite,
                isWatchLater = existing.isWatchLater,
                isDownloaded = existing.isDownloaded,
                localFilePath = existing.localFilePath,
                downloadSizeMb = existing.downloadSizeMb
            )
            videoDao.insertVideo(updated)
        } else {
            videoDao.insertVideo(video)
        }
    }

    suspend fun saveVideos(videos: List<VideoEntity>) {
        videos.forEach { saveVideo(it) }
    }

    suspend fun updatePlaybackPosition(youtubeId: String, positionSeconds: Int) {
        if (positionSeconds >= 0) {
            val existing = videoDao.getVideoById(youtubeId)
            if (existing != null) {
                videoDao.updateWatchHistory(youtubeId, System.currentTimeMillis(), positionSeconds)
            } else {
                videoDao.insertVideo(
                    VideoEntity(
                        youtubeId = youtubeId,
                        title = "YouTube Video ($youtubeId)",
                        channelName = "YouTube",
                        thumbnailUrl = com.example.util.YouTubeUtils.getThumbnailUrl(youtubeId),
                        lastPositionSeconds = positionSeconds,
                        lastWatchedTimestamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    suspend fun toggleFavorite(youtubeId: String, currentFavorite: Boolean) {
        val target = !currentFavorite
        val existing = videoDao.getVideoById(youtubeId)
        if (existing != null) {
            videoDao.insertVideo(existing.copy(isFavorite = target))
        } else {
            videoDao.updateFavorite(youtubeId, target)
        }
    }

    suspend fun toggleWatchLater(youtubeId: String, currentWatchLater: Boolean) {
        val target = !currentWatchLater
        val existing = videoDao.getVideoById(youtubeId)
        if (existing != null) {
            videoDao.insertVideo(existing.copy(isWatchLater = target))
        } else {
            videoDao.updateWatchLater(youtubeId, target)
        }
    }

    suspend fun updateWatchHistory(youtubeId: String, lastPosSeconds: Int) {
        val existing = videoDao.getVideoById(youtubeId)
        val posToSave = if (lastPosSeconds > 0) lastPosSeconds else (existing?.lastPositionSeconds ?: 0)
        if (existing != null) {
            videoDao.updateWatchHistory(youtubeId, System.currentTimeMillis(), posToSave)
        } else {
            videoDao.insertVideo(
                VideoEntity(
                    youtubeId = youtubeId,
                    title = "YouTube Video ($youtubeId)",
                    channelName = "YouTube",
                    thumbnailUrl = com.example.util.YouTubeUtils.getThumbnailUrl(youtubeId),
                    lastPositionSeconds = posToSave,
                    lastWatchedTimestamp = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun updateDownloadStatus(youtubeId: String, isDownloaded: Boolean, localFilePath: String, downloadSizeMb: Float) {
        videoDao.updateDownloadStatus(youtubeId, isDownloaded, localFilePath, downloadSizeMb)
    }

    suspend fun deleteVideo(video: VideoEntity) {
        videoDao.deleteVideo(video)
    }

    suspend fun deleteVideoById(youtubeId: String) {
        videoDao.deleteVideoById(youtubeId)
    }

    suspend fun clearHistory() {
        videoDao.clearWatchHistory()
    }

    // Notes
    fun getNotesForVideo(youtubeId: String): Flow<List<VideoNoteEntity>> {
        return videoNoteDao.getNotesForVideo(youtubeId)
    }

    suspend fun addNote(note: VideoNoteEntity) {
        videoNoteDao.insertNote(note)
        // update video note count
        val count = videoNoteDao.getNotesCount(note.youtubeId)
        val video = videoDao.getVideoById(note.youtubeId)
        video?.let {
            videoDao.updateVideo(it.copy(notesCount = count))
        }
    }

    suspend fun deleteNote(noteId: Long, youtubeId: String) {
        videoNoteDao.deleteNote(noteId)
        val count = videoNoteDao.getNotesCount(youtubeId)
        val video = videoDao.getVideoById(youtubeId)
        video?.let {
            videoDao.updateVideo(it.copy(notesCount = count))
        }
    }

    // Categories
    suspend fun updateVideoCategory(youtubeId: String, newCategory: String) {
        val existing = videoDao.getVideoById(youtubeId)
        if (existing != null) {
            videoDao.insertVideo(existing.copy(category = newCategory))
        }
    }

    suspend fun addCategory(category: PlaylistCategoryEntity) {
        categoryDao.insertCategory(category)
    }

    suspend fun updateCategory(category: PlaylistCategoryEntity) {
        categoryDao.updateCategory(category)
    }

    suspend fun deleteCategory(categoryId: Long) {
        categoryDao.deleteCategory(categoryId)
    }

    suspend fun deleteCategoryByName(name: String) {
        categoryDao.deleteCategoryByName(name)
    }
}
