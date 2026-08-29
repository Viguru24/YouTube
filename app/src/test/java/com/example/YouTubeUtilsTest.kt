package com.example

import com.example.util.YouTubeUtils
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeUtilsTest {

    @Test
    fun testRejectsForeignAndSouthAsianSerials() {
        assertTrue(YouTubeUtils.isForeignLanguageContent("Puli - Back to Back Comedy Scenes", "Adithya TV"))
        assertTrue(YouTubeUtils.isForeignLanguageContent("BIG DISPLAY + 144Hz Brave Tech", "Loud Oli Tech"))
        assertTrue(YouTubeUtils.isForeignLanguageContent("Dr. Aarambhi Today Episode 145", "Telly Tashan"))
        assertTrue(YouTubeUtils.isForeignLanguageContent("Yeh Fitoor Tera Today Episode 88", "Telly Tashan"))
        assertTrue(YouTubeUtils.isForeignLanguageContent("34 IKEA Items Worth Buying", "SANU Loves IKEA"))
        assertTrue(YouTubeUtils.isForeignLanguageContent("Desi Comedy Drama Episode 1", "ARY Digital"))
        assertTrue(YouTubeUtils.isForeignLanguageContent("Taaza Khabar Aaj Ki", "Aaj Tak"))
        assertTrue(YouTubeUtils.isForeignLanguageContent("New Hindi Song Lyrical", "T-Series"))
        assertTrue(YouTubeUtils.isForeignLanguageContent("Har Pal Geo Drama 4K", "Har Pal Geo"))
    }

    @Test
    fun testRejectsNonLatinScripts() {
        assertTrue(YouTubeUtils.isForeignLanguageContent("இந்த விலைக்கு இவ்வளவு வசதியா!", "Tamil Tech"))
        assertTrue(YouTubeUtils.isForeignLanguageContent("कल देखिए नया एपिसोड", "Hindi Serials"))
        assertTrue(YouTubeUtils.isForeignLanguageContent("بث مباشر للأخبار", "Arabic News"))
        assertTrue(YouTubeUtils.isForeignLanguageContent("Новости сегодня", "Russian TV"))
    }

    @Test
    fun testAllowsValidEnglishCreatorVideos() {
        assertFalse(YouTubeUtils.isForeignLanguageContent("HUGE Gemini 4.0 Leaks, DeepSeek", "WorldofAI"))
        assertFalse(YouTubeUtils.isForeignLanguageContent("Benny Johnson Breaks Down White House Press", "Benny Johnson"))
        assertFalse(YouTubeUtils.isForeignLanguageContent("The Rubin Report: Direct Interview with Dave", "The Rubin Report"))
        assertFalse(YouTubeUtils.isForeignLanguageContent("Tucker Carlson on the Future of Media", "Tucker Carlson"))
        assertFalse(YouTubeUtils.isForeignLanguageContent("Lex Fridman Podcast with Andrej Karpathy", "Lex Fridman"))
        assertFalse(YouTubeUtils.isForeignLanguageContent("Veritasium: The Problem with Quantum Physics", "Veritasium"))
    }
}
