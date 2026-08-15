package com.example.data.dao

import androidx.room.*
import com.example.data.model.VideoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos ORDER BY addedTimestamp DESC")
    fun getAllVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE isFavorite = 1 ORDER BY addedTimestamp DESC")
    fun getFavoriteVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE isWatchLater = 1 ORDER BY addedTimestamp DESC")
    fun getWatchLaterVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE lastWatchedTimestamp > 0 ORDER BY lastWatchedTimestamp DESC")
    fun getWatchHistory(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE category = :category ORDER BY addedTimestamp DESC")
    fun getVideosByCategory(category: String): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE youtubeId = :youtubeId LIMIT 1")
    fun getVideoByIdFlow(youtubeId: String): Flow<VideoEntity?>

    @Query("SELECT * FROM videos WHERE youtubeId = :youtubeId LIMIT 1")
    suspend fun getVideoById(youtubeId: String): VideoEntity?

    @Query("SELECT * FROM videos WHERE title LIKE '%' || :query || '%' OR channelName LIKE '%' || :query || '%'")
    fun searchVideos(query: String): Flow<List<VideoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: VideoEntity)

    @Update
    suspend fun updateVideo(video: VideoEntity)

    @Query("UPDATE videos SET isFavorite = :isFavorite WHERE youtubeId = :youtubeId")
    suspend fun updateFavorite(youtubeId: String, isFavorite: Boolean)

    @Query("UPDATE videos SET isWatchLater = :isWatchLater WHERE youtubeId = :youtubeId")
    suspend fun updateWatchLater(youtubeId: String, isWatchLater: Boolean)

    @Query("UPDATE videos SET lastWatchedTimestamp = :timestamp, lastPositionSeconds = :lastPos WHERE youtubeId = :youtubeId")
    suspend fun updateWatchHistory(youtubeId: String, timestamp: Long, lastPos: Int)

    @Query("SELECT * FROM videos WHERE isDownloaded = 1 ORDER BY addedTimestamp DESC")
    fun getDownloadedVideos(): Flow<List<VideoEntity>>

    @Query("UPDATE videos SET isDownloaded = :isDownloaded, localFilePath = :localFilePath, downloadSizeMb = :downloadSizeMb WHERE youtubeId = :youtubeId")
    suspend fun updateDownloadStatus(youtubeId: String, isDownloaded: Boolean, localFilePath: String, downloadSizeMb: Float)

    @Delete
    suspend fun deleteVideo(video: VideoEntity)

    @Query("DELETE FROM videos WHERE addedTimestamp < :cutoffTimestamp AND isFavorite = 0 AND isWatchLater = 0 AND isDownloaded = 0 AND lastPositionSeconds = 0")
    suspend fun deleteStaleUnsavedVideos(cutoffTimestamp: Long)

    @Query("DELETE FROM videos WHERE isFavorite = 0 AND isWatchLater = 0 AND isDownloaded = 0 AND lastPositionSeconds = 0")
    suspend fun clearUnsavedVideos()

    @Query("DELETE FROM videos WHERE lastWatchedTimestamp > 0")
    suspend fun clearWatchHistory()

    @Query("UPDATE videos SET lastWatchedTimestamp = 0 WHERE lastPositionSeconds = 0")
    suspend fun sanitizeWatchTimestamps()
}
