package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey val youtubeId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String,
    val durationText: String = "10:00",
    val category: String = "General",
    val addedTimestamp: Long = System.currentTimeMillis(),
    val lastWatchedTimestamp: Long = 0L,
    val lastPositionSeconds: Int = 0,
    val isFavorite: Boolean = false,
    val isWatchLater: Boolean = false,
    val notesCount: Int = 0,
    val viewCountText: String = "",
    val publishedTimeText: String = "",
    val isDownloaded: Boolean = false,
    val localFilePath: String = "",
    val downloadSizeMb: Float = 0.0f
)
