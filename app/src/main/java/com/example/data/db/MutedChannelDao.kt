package com.example.data.db

import androidx.room.*
import com.example.data.model.MutedChannelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MutedChannelDao {
    @Query("SELECT * FROM muted_channels ORDER BY mutedTimestamp DESC")
    fun getAllMutedChannels(): Flow<List<MutedChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun muteChannel(channel: MutedChannelEntity)

    @Delete
    suspend fun unmuteChannel(channel: MutedChannelEntity)

    @Query("DELETE FROM muted_channels WHERE channelName = :channelName")
    suspend fun deleteByName(channelName: String)
}
