package com.example.data.dao

import androidx.room.*
import com.example.data.model.VideoNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoNoteDao {
    @Query("SELECT * FROM video_notes WHERE youtubeId = :youtubeId ORDER BY timestampSeconds ASC")
    fun getNotesForVideo(youtubeId: String): Flow<List<VideoNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: VideoNoteEntity)

    @Query("DELETE FROM video_notes WHERE id = :noteId")
    suspend fun deleteNote(noteId: Long)

    @Query("SELECT COUNT(*) FROM video_notes WHERE youtubeId = :youtubeId")
    suspend fun getNotesCount(youtubeId: String): Int
}
