package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "video_notes")
data class VideoNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val youtubeId: String,
    val timestampSeconds: Int,
    val timestampFormatted: String,
    val noteText: String,
    val createdTimestamp: Long = System.currentTimeMillis()
)
