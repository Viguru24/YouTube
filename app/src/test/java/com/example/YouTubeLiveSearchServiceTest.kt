package com.example

import com.example.data.remote.YouTubeLiveSearchService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeLiveSearchServiceTest {

    @Test
    fun testSearchForShow() = runBlocking {
        val query = "show"
        println("=== Testing Live Search for Query: '$query' ===")
        val results = YouTubeLiveSearchService.searchRealYouTubeVideos(query)
        println("Extracted ${results.size} video results:")
        results.forEachIndexed { i, video ->
            println("[$i] ${video.youtubeId} | ${video.title} | ${video.channelName}")
        }
        assertTrue("Search results for '$query' must not be empty!", results.isNotEmpty())
    }

    @Test
    fun testSearchForRickAndMorty() = runBlocking {
        val query = "rick and morty"
        println("=== Testing Live Search for Query: '$query' ===")
        val results = YouTubeLiveSearchService.searchRealYouTubeVideos(query)
        println("Extracted ${results.size} video results:")
        results.take(5).forEachIndexed { i, video ->
            println("[$i] ${video.youtubeId} | ${video.title} | ${video.channelName}")
        }
        assertTrue("Search results for '$query' must not be empty!", results.isNotEmpty())
    }
}
