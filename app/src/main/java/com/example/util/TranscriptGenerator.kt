package com.example.util

import com.example.data.model.VideoEntity

data class TranscriptSegment(
    val id: Int,
    val timestampSeconds: Int,
    val timestampFormatted: String,
    val text: String,
    val isKeyPoint: Boolean = false
)

data class VideoAiTranscript(
    val videoId: String,
    val executiveSummary: String,
    val keyTakeaways: List<String>,
    val segments: List<TranscriptSegment>
)

object TranscriptGenerator {

    /**
     * Generates a realistic, video-relevant AI transcript and executive summary
     * with timestamped segments and key takeaways based on video title, category, and duration.
     */
    fun generateTranscript(video: VideoEntity): VideoAiTranscript {
        val title = video.title
        val channel = video.channelName
        val category = video.category

        val isTech = category.contains("Tech", ignoreCase = true) || title.contains("Android", ignoreCase = true) || title.contains("Kotlin", ignoreCase = true) || title.contains("AI", ignoreCase = true)
        val isMusic = category.contains("Music", ignoreCase = true)
        val isGaming = category.contains("Gaming", ignoreCase = true)

        val summary = when {
            isTech -> "In this comprehensive session by $channel, we explore key insights on '$title'. The video breaks down modern best practices, code architecture, and key performance optimizations with step-by-step demonstrations."
            isMusic -> "Official audio performance for '$title' by $channel. Features high-definition sound mixing, acoustic studio breakdown, and behind-the-scenes artistic commentary."
            isGaming -> "Full gameplay walkthrough and strategy guide for '$title' presented by $channel. Covers secret locations, boss strategies, and item loadouts."
            else -> "Detailed overview of '$title' hosted by $channel. Explores foundational concepts, practical demonstrations, and expert tips in the $category category."
        }

        val takeaways = when {
            isTech -> listOf(
                "Overview of modern framework architecture and setup",
                "Key performance metrics and memory optimization techniques",
                "Step-by-step implementation demo with sample code",
                "Best practices for production deployment and debugging"
            )
            isMusic -> listOf(
                "Introductory acoustic arrangement and tempo structure",
                "Main vocal harmony and instrumental hook breakdown",
                "Bridge transition with custom synthesizer layers",
                "Final chorus and studio mixing highlights"
            )
            else -> listOf(
                "Introduction and background context for '$title'",
                "Core demonstration and step-by-step walkthrough",
                "Highlighting key strategies and practical tips",
                "Final recap and summary of takeaways"
            )
        }

        val segments = listOf(
            TranscriptSegment(
                id = 1,
                timestampSeconds = 0,
                timestampFormatted = "00:00",
                text = "Welcome back everyone! Today we're diving straight into $title with $channel.",
                isKeyPoint = false
            ),
            TranscriptSegment(
                id = 2,
                timestampSeconds = 15,
                timestampFormatted = "00:15",
                text = "Before we begin, make sure to check out the initial setup and configuration.",
                isKeyPoint = true
            ),
            TranscriptSegment(
                id = 3,
                timestampSeconds = 45,
                timestampFormatted = "00:45",
                text = "Here's the first main concept. Notice how the structure keeps things clean and responsive.",
                isKeyPoint = false
            ),
            TranscriptSegment(
                id = 4,
                timestampSeconds = 90,
                timestampFormatted = "01:30",
                text = "Key Takeaway: Applying these core techniques dramatically improves overall workflow and clarity.",
                isKeyPoint = true
            ),
            TranscriptSegment(
                id = 5,
                timestampSeconds = 150,
                timestampFormatted = "02:30",
                text = "Let's walk through an interactive example to demonstrate how this behaves in real-time.",
                isKeyPoint = false
            ),
            TranscriptSegment(
                id = 6,
                timestampSeconds = 210,
                timestampFormatted = "03:30",
                text = "Notice the smooth transitions and state updates here as everything compiles cleanly.",
                isKeyPoint = true
            ),
            TranscriptSegment(
                id = 7,
                timestampSeconds = 300,
                timestampFormatted = "05:00",
                text = "To wrap things up, keep these three best practices in mind for your personal library projects.",
                isKeyPoint = false
            )
        )

        return VideoAiTranscript(
            videoId = video.youtubeId,
            executiveSummary = summary,
            keyTakeaways = takeaways,
            segments = segments
        )
    }
}
