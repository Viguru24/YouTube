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
    private val categoryDao: PlaylistCategoryDao
) {
    val allVideos: Flow<List<VideoEntity>> = videoDao.getAllVideos()
    val favoriteVideos: Flow<List<VideoEntity>> = videoDao.getFavoriteVideos()
    val watchLaterVideos: Flow<List<VideoEntity>> = videoDao.getWatchLaterVideos()
    val watchHistory: Flow<List<VideoEntity>> = videoDao.getWatchHistory()
    val categories: Flow<List<PlaylistCategoryEntity>> = categoryDao.getAllCategories()

    fun getVideosByCategory(category: String): Flow<List<VideoEntity>> {
        return videoDao.getVideosByCategory(category)
    }

    fun getVideoById(youtubeId: String): Flow<VideoEntity?> {
        return videoDao.getVideoByIdFlow(youtubeId)
    }

    fun searchVideos(query: String): Flow<List<VideoEntity>> {
        return videoDao.searchVideos(query)
    }

    suspend fun saveVideo(video: VideoEntity) {
        videoDao.insertVideo(video)
    }

    suspend fun toggleFavorite(youtubeId: String, currentFavorite: Boolean) {
        videoDao.updateFavorite(youtubeId, !currentFavorite)
    }

    suspend fun toggleWatchLater(youtubeId: String, currentWatchLater: Boolean) {
        videoDao.updateWatchLater(youtubeId, !currentWatchLater)
    }

    suspend fun updateWatchHistory(youtubeId: String, lastPosSeconds: Int) {
        videoDao.updateWatchHistory(youtubeId, System.currentTimeMillis(), lastPosSeconds)
    }

    suspend fun deleteVideo(video: VideoEntity) {
        videoDao.deleteVideo(video)
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
    suspend fun addCategory(category: PlaylistCategoryEntity) {
        categoryDao.insertCategory(category)
    }

    suspend fun deleteCategory(categoryId: Long) {
        categoryDao.deleteCategory(categoryId)
    }
}
