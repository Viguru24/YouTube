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

    @Test
    fun scoreAndRankVideos_strictlyExcludesMutedAndBlockedChannels() {
        val vidGood = VideoEntity(
            youtubeId = "good_1",
            title = "Great Coding Tutorial",
            channelName = "CodingWithWill",
            thumbnailUrl = "thumb_good"
        )
        val vidBlockedChannel = VideoEntity(
            youtubeId = "bad_1",
            title = "Annoying Clickbait Video",
            channelName = "ClickbaitChannel",
            thumbnailUrl = "thumb_bad"
        )
        val vidKeywordBlocked = VideoEntity(
            youtubeId = "bad_2",
            title = "Celebrity Gossip Secrets",
            channelName = "HollywoodNews",
            thumbnailUrl = "thumb_gossip"
        )

        val candidates = listOf(vidGood, vidBlockedChannel, vidKeywordBlocked)
        val mutedList = listOf(com.example.data.model.MutedChannelEntity("ClickbaitChannel"))
        val settings = AlgorithmSettings(blockedKeywords = listOf("Gossip"))

        val ranked = RecommendationEngine.scoreAndRankVideos(
            videos = candidates,
            favorites = emptyList(),
            watchHistory = emptyList(),
            mutedChannels = mutedList,
            settings = settings
        )

        val rankedIds = ranked.map { it.youtubeId }.toSet()
        assertEquals(1, ranked.size)
        assertTrue(rankedIds.contains("good_1"))
        assertFalse(rankedIds.contains("bad_1"))
        assertFalse(rankedIds.contains("bad_2"))
    }
}
