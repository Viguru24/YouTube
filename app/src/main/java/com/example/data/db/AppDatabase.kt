package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.PlaylistCategoryDao
import com.example.data.dao.VideoDao
import com.example.data.dao.VideoNoteDao
import com.example.data.model.PlaylistCategoryEntity
import com.example.data.model.VideoEntity
import com.example.data.model.VideoNoteEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [VideoEntity::class, VideoNoteEntity::class, PlaylistCategoryEntity::class, com.example.data.model.MutedChannelEntity::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
    abstract fun videoNoteDao(): VideoNoteDao
    abstract fun playlistCategoryDao(): PlaylistCategoryDao
    abstract fun mutedChannelDao(): com.example.data.db.MutedChannelDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "youtube_player_db"
                )
                .fallbackToDestructiveMigration()
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Populate initial default categories directly via SQL
                        try {
                            db.execSQL("INSERT OR IGNORE INTO playlist_categories (name, iconName, colorHex) VALUES ('Tech & Code', 'Code', '#2196F3')")
                            db.execSQL("INSERT OR IGNORE INTO playlist_categories (name, iconName, colorHex) VALUES ('Music', 'MusicNote', '#E91E63')")
                            db.execSQL("INSERT OR IGNORE INTO playlist_categories (name, iconName, colorHex) VALUES ('Tutorials', 'School', '#4CAF50')")
                            db.execSQL("INSERT OR IGNORE INTO playlist_categories (name, iconName, colorHex) VALUES ('Gaming', 'Gamepad', '#FF9800')")
                            db.execSQL("INSERT OR IGNORE INTO playlist_categories (name, iconName, colorHex) VALUES ('Focus & Ambient', 'Headphones', '#9C27B0')")
                        } catch (e: Exception) {
                            android.util.Log.e("AppDatabase", "Error seeding default categories: ${e.message}")
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }

        private suspend fun populateInitialData(db: AppDatabase) {
            val categoryDao = db.playlistCategoryDao()

            // Default Categories
            val defaultCategories = listOf(
                PlaylistCategoryEntity(name = "Tutorials", iconName = "School", colorHex = "#4CAF50"),
                PlaylistCategoryEntity(name = "Music", iconName = "MusicNote", colorHex = "#E91E63"),
                PlaylistCategoryEntity(name = "Tech & Code", iconName = "Code", colorHex = "#2196F3"),
                PlaylistCategoryEntity(name = "Focus & Ambient", iconName = "Headphones", colorHex = "#9C27B0"),
                PlaylistCategoryEntity(name = "Favorites", iconName = "Star", colorHex = "#FFC107")
            )
            defaultCategories.forEach { categoryDao.insertCategory(it) }
        }
    }
}
