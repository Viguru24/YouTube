package com.example.data.repository

import com.example.data.model.VideoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationEngineTest {

    @Test
    fun scoreAndRankVideos_strictlyExcludesWatchedVideos() {
        val unwatchedVideo1 = VideoEntity(
            youtubeId = "vid_fresh_1",
            title = "Unwatched Tech News",
            channelName = "TechChannel",
            thumbnailUrl = "thumb1",
            publishedTimeText = "10 minutes ago",
            lastWatchedTimestamp = 0L,
            lastPositionSeconds = 0
        )
        val unwatchedVideo2 = VideoEntity(
            youtubeId = "vid_fresh_2",
            title = "Fresh Science Podcast",
            channelName = "ScienceChannel",
            thumbnailUrl = "thumb2",
            publishedTimeText = "1 hour ago",
            lastWatchedTimestamp = 0L,
            lastPositionSeconds = 0
        )
        val watchedVideo1 = VideoEntity(
            youtubeId = "vid_watched_completed",
            title = "Watched Video Completed",
            channelName = "HistoryChannel",
            thumbnailUrl = "thumb3",
            publishedTimeText = "2 hours ago",
            lastWatchedTimestamp = System.currentTimeMillis() - 10000L,
            lastPositionSeconds = 0
        )
        val watchedVideo2 = VideoEntity(
            youtubeId = "vid_watched_short_duration",
            title = "Watched For A Few Seconds",
            channelName = "NewsChannel",
            thumbnailUrl = "thumb4",
            publishedTimeText = "3 hours ago",
            lastWatchedTimestamp = System.currentTimeMillis() - 5000L,
            lastPositionSeconds = 3
        )

        val allCandidates = listOf(unwatchedVideo1, watchedVideo1, unwatchedVideo2, watchedVideo2)
        val watchHistory = listOf(watchedVideo1, watchedVideo2)

        val ranked = RecommendationEngine.scoreAndRankVideos(
            videos = allCandidates,
            favorites = emptyList(),
            watchHistory = watchHistory,
            settings = AlgorithmSettings()
        )

        // Watched videos must be 100% removed from the ranked feed
        val rankedIds = ranked.map { it.youtubeId }.toSet()
        assertFalse(rankedIds.contains("vid_watched_completed"))
        assertFalse(rankedIds.contains("vid_watched_short_duration"))

        // Unwatched videos must remain
        assertTrue(rankedIds.contains("vid_fresh_1"))
        assertTrue(rankedIds.contains("vid_fresh_2"))
        assertEquals(2, ranked.size)
    }
}
