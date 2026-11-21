package com.example

import com.example.data.remote.YouTubeLiveSearchService
import com.example.util.YouTubeUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class SortAndFilterTest {

    @Test
    fun normalizeSortOption_mapsAllAliasesAndDefaultsToLatest() {
        assertEquals("Latest", YouTubeLiveSearchService.normalizeSortOption("Latest"))
        assertEquals("Latest", YouTubeLiveSearchService.normalizeSortOption("Newest"))
        assertEquals("Latest", YouTubeLiveSearchService.normalizeSortOption(""))

        assertEquals("Most Viewed", YouTubeLiveSearchService.normalizeSortOption("Most Viewed"))
        assertEquals("Most Viewed", YouTubeLiveSearchService.normalizeSortOption("Most Popular"))
        assertEquals("Most Viewed", YouTubeLiveSearchService.normalizeSortOption("View Count"))

        assertEquals("Longest", YouTubeLiveSearchService.normalizeSortOption("Longest"))
        assertEquals("Shortest", YouTubeLiveSearchService.normalizeSortOption("Shortest"))
        assertEquals("Oldest", YouTubeLiveSearchService.normalizeSortOption("Oldest"))
        assertEquals("Top Rated", YouTubeLiveSearchService.normalizeSortOption("Top Rated"))

        assertEquals("Relevance", YouTubeLiveSearchService.normalizeSortOption("Relevance"))
        assertEquals("Relevance", YouTubeLiveSearchService.normalizeSortOption("Default"))
        assertEquals("Relevance", YouTubeLiveSearchService.normalizeSortOption("Recommended"))
        assertEquals("Relevance", YouTubeLiveSearchService.normalizeSortOption("", sortByUploadDate = false))
    }

    @Test
    fun lengthDurationBucketsAndViewCountParsingWorkAccurately() {
        // Length filter thresholds: < 4 min (1..239), 4–20 min (240..1200), > 20 min (> 1200)
        assertEquals(185, YouTubeUtils.parseFormattedTimeToSeconds("3:05"))
        assertEquals(240, YouTubeUtils.parseFormattedTimeToSeconds("4:00"))
        assertEquals(1200, YouTubeUtils.parseFormattedTimeToSeconds("20:00"))
        assertEquals(3661, YouTubeUtils.parseFormattedTimeToSeconds("1:01:01"))

        // View counts used for Most Viewed sort
        assertEquals(2_500_000L, YouTubeUtils.parseViewCount("2.5M views"))
        assertEquals(45_000L, YouTubeUtils.parseViewCount("45K views"))
        assertEquals(1_200L, YouTubeUtils.parseViewCount("1,200 views"))
    }
}
