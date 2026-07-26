package com.example

import com.example.data.remote.YouTubeStreamExtractor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeStreamExtractorTest {

    @Test
    fun testDirectStreamExtraction_UserReportedVideo() = runBlocking {
        val videoId = "9gike0k58LQ"
        println("Starting stream extraction for user-reported videoId: $videoId")
        val streamUrl = YouTubeStreamExtractor.getDirectStreamUrl(videoId)
        println("Extracted Stream URL for $videoId: $streamUrl")
        assertNotNull("Stream URL should not be null for videoId $videoId", streamUrl)
        assertTrue("Stream URL should contain googlevideo.com domain", streamUrl?.contains("googlevideo.com") == true)
    }

    @Test
    fun testDirectStreamExtraction_SeeYouAgain() = runBlocking {
        val videoId = "dcqnIrMy2a8"
        println("Starting test simulation stream extraction for videoId: $videoId")
        val streamUrl = YouTubeStreamExtractor.getDirectStreamUrl(videoId)
        println("Extracted Stream URL: $streamUrl")
        assertNotNull("Stream URL should not be null for videoId $videoId", streamUrl)
        assertTrue("Stream URL should contain googlevideo.com domain", streamUrl?.contains("googlevideo.com") == true)
    }
}
