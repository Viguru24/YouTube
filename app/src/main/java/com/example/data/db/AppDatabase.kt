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
    entities = [VideoEntity::class, VideoNoteEntity::class, PlaylistCategoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
    abstract fun videoNoteDao(): VideoNoteDao
    abstract fun playlistCategoryDao(): PlaylistCategoryDao

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
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Populate initial default data in background
                        INSTANCE?.let { database ->
                            CoroutineScope(Dispatchers.IO).launch {
                                populateInitialData(database)
                            }
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
            val videoDao = db.videoDao()
            val noteDao = db.videoNoteDao()

            // Default Categories
            val defaultCategories = listOf(
                PlaylistCategoryEntity(name = "Tutorials", iconName = "School", colorHex = "#4CAF50"),
                PlaylistCategoryEntity(name = "Music", iconName = "MusicNote", colorHex = "#E91E63"),
                PlaylistCategoryEntity(name = "Tech & Code", iconName = "Code", colorHex = "#2196F3"),
                PlaylistCategoryEntity(name = "Focus & Ambient", iconName = "Headphones", colorHex = "#9C27B0"),
                PlaylistCategoryEntity(name = "Favorites", iconName = "Star", colorHex = "#FFC107")
            )
            defaultCategories.forEach { categoryDao.insertCategory(it) }

            // Default Curated Videos
            val defaultVideos = listOf(
                VideoEntity(
                    youtubeId = "jfKfPfyJRdk",
                    title = "Lofi Hip Hop Radio - Beats to Relax / Study to",
                    channelName = "Lofi Girl",
                    thumbnailUrl = "https://img.youtube.com/vi/jfKfPfyJRdk/hqdefault.jpg",
                    durationText = "LIVE",
                    category = "Focus & Ambient",
                    isFavorite = true,
                    isWatchLater = true,
                    notesCount = 1
                ),
                VideoEntity(
                    youtubeId = "jfKfPfyJRdk",
                    title = "Lofi Hip Hop Radio - Beats to Relax / Study to",
                    channelName = "Lofi Girl",
                    thumbnailUrl = "https://img.youtube.com/vi/jfKfPfyJRdk/hqdefault.jpg",
                    durationText = "LIVE",
                    category = "Focus & Ambient",
                    isFavorite = true,
                    isWatchLater = true,
                    notesCount = 1
                ),
                VideoEntity(
                    youtubeId = "dQw4w9WgXcQ",
                    title = "Rick Astley - Never Gonna Give You Up (Official Music Video)",
                    channelName = "Rick Astley",
                    thumbnailUrl = "https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                    durationText = "3:33",
                    category = "Music",
                    isFavorite = false,
                    isWatchLater = true,
                    notesCount = 1
                ),
                VideoEntity(
                    youtubeId = "g18I7m1A7Bw",
                    title = "Kotlin Coroutines & Flow Deep Dive Tutorial",
                    channelName = "Philipp Lackner",
                    thumbnailUrl = "https://img.youtube.com/vi/g18I7m1A7Bw/hqdefault.jpg",
                    durationText = "28:15",
                    category = "Tutorials",
                    isFavorite = true,
                    isWatchLater = false,
                    notesCount = 1
                ),
                VideoEntity(
                    youtubeId = "lF3V84I3fGk",
                    title = "10 Hour Relaxing Rain & Thunderstorm Sounds for Focus & Sleep",
                    channelName = "Calm Soundscapes",
                    thumbnailUrl = "https://img.youtube.com/vi/lF3V84I3fGk/hqdefault.jpg",
                    durationText = "10:00:00",
                    category = "Focus & Ambient",
                    isFavorite = false,
                    isWatchLater = false,
                    notesCount = 0
                )
            )
            defaultVideos.forEach { videoDao.insertVideo(it) }

            // Default Sample Timestamped Notes
            val defaultNotes = listOf(
                VideoNoteEntity(
                    youtubeId = "M576WGiDBdQ",
                    timestampSeconds = 120,
                    timestampFormatted = "02:00",
                    noteText = "Introduction to Composable functions & state overview"
                ),
                VideoNoteEntity(
                    youtubeId = "M576WGiDBdQ",
                    timestampSeconds = 480,
                    timestampFormatted = "08:00",
                    noteText = "Scaffold and Material3 design tokens implementation"
                ),
                VideoNoteEntity(
                    youtubeId = "g18I7m1A7Bw",
                    timestampSeconds = 300,
                    timestampFormatted = "05:00",
                    noteText = "StateFlow vs SharedFlow comparison matrix"
                ),
                VideoNoteEntity(
                    youtubeId = "dQw4w9WgXcQ",
                    timestampSeconds = 18,
                    timestampFormatted = "00:18",
                    noteText = "Iconic chorus intro drop!"
                )
            )
            defaultNotes.forEach { noteDao.insertNote(it) }
        }
    }
}
