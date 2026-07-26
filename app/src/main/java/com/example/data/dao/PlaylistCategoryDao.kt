package com.example.data.dao

import androidx.room.*
import com.example.data.model.PlaylistCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistCategoryDao {
    @Query("SELECT * FROM playlist_categories ORDER BY id ASC")
    fun getAllCategories(): Flow<List<PlaylistCategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: PlaylistCategoryEntity)

    @Query("DELETE FROM playlist_categories WHERE id = :id")
    suspend fun deleteCategory(id: Long)
}
