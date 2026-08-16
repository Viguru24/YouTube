package com.example.data.repository

import com.example.data.model.VideoEntity
import com.example.util.YouTubeUtils

data class AlgorithmSettings(
    val creatorWeight: Float = 0.7f,        // 0.0 to 1.0 (Importance of favorite creators)
    val discoveryRatio: Float = 0.2f,       // 0.0 to 1.0 (Fresh discoveries vs familiar topics)
    val shortsMode: String = "Carousel",     // "Carousel", "Hidden", "Separate"
    val minDurationMinutes: Int = 0,        // 0, 3, 5, 10 minutes
    val freshnessDecay: String = "Medium",   // "Slow", "Medium", "Fast"
    val autoDeleteDownloads: String = "Never", // "Never", "24h", "48h", "7d", "30d", "Watched"
    val blockedKeywords: List<String> = emptyList(), // Custom keywords/channels permanently excluded
    val boostedTopics: List<String> = emptyList()     // Custom topics/creators prioritized at the top
)

object RecommendationEngine {

    // Boredom Detection state (tracked 100% locally on device)
    private var consecutiveShortSkips = 0

    fun recordVideoWatchedDuration(durationSec: Int) {
        if (durationSec < 20) {
            consecutiveShortSkips++
        } else {
            consecutiveShortSkips = 0
        }
    }

    val isBoredomModeActive: Boolean
        get() = consecutiveShortSkips >= 3

    /**
     * Determines user-facing transparency reason for why a video is recommended.
     */
    fun getRecommendationReason(
        video: VideoEntity,
        favorites: List<VideoEntity>,
        watchHistory: List<VideoEntity>
    ): String {
        if (video.lastPositionSeconds > 0) return "🕒 Continue Watching"
        if (video.isFavorite) return "⭐ Saved Favorite"
        if (video.isWatchLater) return "🔖 Saved Watch Later"

        val isSubscribedProfileChannel = com.example.data.model.WillRyanProfileData.subscribedChannels.any {
            it.contains(video.channelName, ignoreCase = true) || video.channelName.contains(it, ignoreCase = true)
        }
        if (isSubscribedProfileChannel) return "💡 Subscribed Channel"

        val hasWatchedChannel = watchHistory.any { it.channelName.equals(video.channelName, ignoreCase = true) }
        if (hasWatchedChannel) return "🔥 Channel You Enjoy"

        if (isBoredomModeActive) return "✨ Fresh Topic Exploration"

        return "📈 Popular Recommendation"
    }

    /**
     * Calculates personal relevance score for a list of videos based on user settings and activity.
     */
    fun scoreAndRankVideos(
        videos: List<VideoEntity>,
        favorites: List<VideoEntity>,
        watchHistory: List<VideoEntity>,
        mutedChannels: List<com.example.data.model.MutedChannelEntity> = emptyList(),
        dislikedVideoIds: Set<String> = emptySet(),
        settings: AlgorithmSettings
    ): List<VideoEntity> {
        if (videos.isEmpty()) return emptyList()

        val mutedNames = mutedChannels.map { it.channelName.lowercase() }.toSet()
        val blockedLower = settings.blockedKeywords.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val boostedLower = settings.boostedTopics.map { it.trim().lowercase() }.filter { it.isNotEmpty() }

        val unmutedVideos = videos.filter { video ->
            if (video.youtubeId in dislikedVideoIds) return@filter false
            val titleLower = video.title.lowercase()
            val chanLower = video.channelName.lowercase()
            chanLower !in mutedNames &&
            !YouTubeUtils.isForeignLanguageContent(video.title, video.channelName) &&
            blockedLower.none { blk -> titleLower.contains(blk) || chanLower.contains(blk) }
        }

        // 1. Identify top favorite channels
        val topChannels = (favorites.map { it.channelName } + watchHistory.map { it.channelName })
            .groupingBy { it }
            .eachCount()

        // 2. Identify top watched keywords in titles
        val topKeywords = watchHistory
            .flatMap { it.title.lowercase().split("\\s+".toRegex()) }
            .filter { it.length > 3 && it !in setOf("video", "with", "this", "that", "from", "2026", "youtube") }
            .groupingBy { it }
            .eachCount()

        val scoredList = unmutedVideos.map { video ->
            var score = 50.0f

            // A. Favorite & Watch Later Boost
            if (video.isFavorite) score += 40.0f
            if (video.isWatchLater) score += 25.0f

            // B. User Boosted Topics & Creators (Top Priority)
            val titleLower = video.title.lowercase()
            val chanLower = video.channelName.lowercase()
            val catLower = video.category.lowercase()
            if (boostedLower.any { bst -> titleLower.contains(bst) || chanLower.contains(bst) || catLower.contains(bst) }) {
                score += 90.0f
            }

            // C. Creator & Subscribed Profile Channel Boost
            val isSubscribedProfileChannel = com.example.data.model.WillRyanProfileData.subscribedChannels.any {
                it.contains(video.channelName, ignoreCase = true) || video.channelName.contains(it, ignoreCase = true)
            }
            if (isSubscribedProfileChannel) {
                score += 80.0f * settings.creatorWeight
            }

            val channelHits = topChannels[video.channelName] ?: 0
            if (channelHits > 0) {
                score += (channelHits * 15.0f * settings.creatorWeight).coerceAtMost(50.0f)
            }

            // C. Keyword & Subject Affinity
            val titleWords = video.title.lowercase().split("\\s+".toRegex())
            var keywordHits = 0
            for (word in titleWords) {
                if (topKeywords.containsKey(word)) {
                    keywordHits += topKeywords[word] ?: 0
                }
            }
            if (keywordHits > 0) {
                score += (keywordHits * 4.0f).coerceAtMost(30.0f)
            }

            // D. Discovery & Serendipity Boost (Wild Discovery Mode)
            if (channelHits == 0 && !isSubscribedProfileChannel) {
                score += settings.discoveryRatio * 75.0f
            }

            // E. Boredom Detection Engine: Inject Fresh Unvisited Topics
            if (isBoredomModeActive && channelHits == 0 && !isSubscribedProfileChannel) {
                score += 65.0f // Boost fresh unknown topics to break recommendation fatigue
            }

            // F. Freshness & Recency Engine (Massive boost for today's new uploads)
            val timeText = video.publishedTimeText.lowercase()
            val recencyBonus = when {
                timeText.contains("min") || timeText.contains("hour") || timeText.contains("just now") || timeText.contains("moment") -> 200.0f
                timeText.contains("today") -> 150.0f
                timeText.contains("1 day") || timeText.contains("yesterday") -> 80.0f
                timeText.contains("day") -> 50.0f
                timeText.contains("week") -> 25.0f
                else -> 5.0f
            }
            score += recencyBonus

            // G. Duration Preference Filter
            if (settings.minDurationMinutes > 0 && !YouTubeUtils.isShortVideo(video)) {
                val durSec = parseDurationSeconds(video.durationText)
                if (durSec > 0 && durSec < settings.minDurationMinutes * 60) {
                    score -= 40.0f // Penalize videos under minimum preferred duration
                }
            }

            // H. Watched Progress / Deprioritize Already Watched Videos on Feed
            if (video.lastPositionSeconds > 0) {
                score -= 60.0f // Deprioritize already watched videos to keep discovery feed fresh
            }

            Pair(video, score)
        }

        // 3. Sort descending by score, breaking ties with actual upload time
        val sortedList = scoredList
            .sortedWith(
                compareByDescending<Pair<VideoEntity, Float>> { it.second }
                    .thenBy { YouTubeUtils.parsePublishedTimeToSeconds(it.first.publishedTimeText) }
            )
            .map { it.first }

        // 4. Channel Diversity Cap: Enforce max 3 videos per channel in top 15, prioritizing newest
        val finalDiverseList = mutableListOf<VideoEntity>()
        val channelCounts = mutableMapOf<String, Int>()

        for (v in sortedList) {
            val count = channelCounts.getOrDefault(v.channelName, 0)
            val isFresh = v.publishedTimeText.contains("hour", ignoreCase = true) || v.publishedTimeText.contains("min", ignoreCase = true)
            // Allow up to 4 fresh videos for active creators, 2 for older videos
            val maxAllowed = if (isFresh) 4 else 2
            if (finalDiverseList.size < 15 && count >= maxAllowed) {
                continue
            }
            channelCounts[v.channelName] = count + 1
            finalDiverseList.add(v)
        }

        return finalDiverseList
    }

    private fun parseDurationSeconds(dur: String): Int {
        val parts = dur.split(":")
        return try {
            when (parts.size) {
                2 -> parts[0].toInt() * 60 + parts[1].toInt()
                3 -> parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toInt()
                else -> 0
            }
        } catch (e: Exception) { 0 }
    }
}
