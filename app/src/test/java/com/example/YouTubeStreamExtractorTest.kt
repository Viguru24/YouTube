package com.example

import com.example.data.remote.YouTubeStreamExtractor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeStreamExtractorTest {

    @Test
    fun testDirectStreamExtraction_SeeYouAgain() = runBlocking {
        val videoId = "dcqnIrMy2a8"
        println("Starting test simulation stream extraction for videoId: $videoId")
        val streamUrl = YouTubeStreamExtractor.getDirectStreamUrl(videoId)
        println("Extracted Stream URL: $streamUrl")
        assertNotNull("Stream URL should not be null for videoId $videoId", streamUrl)
        assertTrue("Stream URL should contain googlevideo.com domain", streamUrl?.contains("googlevideo.com") == true)
    }

    @Test
    fun testDirectStreamExtraction_HarryPotter() = runBlocking {
        val videoId = "7qoHfKa-3pI"
        println("Starting test simulation stream extraction for videoId: $videoId")
        val streamUrl = YouTubeStreamExtractor.getDirectStreamUrl(videoId)
        println("Extracted Stream URL: $streamUrl")
        assertNotNull("Stream URL should not be null for videoId $videoId", streamUrl)
        assertTrue("Stream URL should contain googlevideo.com domain", streamUrl?.contains("googlevideo.com") == true)
    }
}
