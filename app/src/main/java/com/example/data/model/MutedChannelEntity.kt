package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "muted_channels")
data class MutedChannelEntity(
    @PrimaryKey val channelName: String,
    val mutedTimestamp: Long = System.currentTimeMillis()
)
