package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.util.TranscriptSegment
import com.example.util.VideoAiTranscript
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AiSummarizerClient {
    private const val TAG = "AiSummarizerClient"
    private const val PREFS_NAME = "ai_prefs"
    private const val KEY_GEMINI_KEY = "gemini_api_key"
    private const val KEY_GROQ_KEY = "groq_api_key"
    private const val KEY_AI_PROVIDER = "ai_provider" // "gemini" or "groq"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    fun getGeminiApiKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_GEMINI_KEY, "")?.trim().orEmpty()
    }

    fun setGeminiApiKey(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_GEMINI_KEY, key.trim()).apply()
    }

    fun getGroqApiKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_GROQ_KEY, "")?.trim().orEmpty()
    }

    fun setGroqApiKey(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_GROQ_KEY, key.trim()).apply()
    }

    fun getAiProvider(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_AI_PROVIDER, "gemini") ?: "gemini"
    }

    fun setAiProvider(context: Context, provider: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_AI_PROVIDER, provider).apply()
    }

    fun hasApiKeyConfigured(context: Context): Boolean {
        val gemini = getGeminiApiKey(context)
        val groq = getGroqApiKey(context)
        return gemini.isNotBlank() || groq.isNotBlank()
    }

    /**
     * Sends the transcript to Gemini 1.5 Flash or Groq Llama to generate a genuine executive AI summary.
     */
    suspend fun generateLlmSummary(
        context: Context,
        videoId: String,
        title: String,
        channelName: String,
        rawTranscriptText: String,
        spokenSegments: List<TranscriptSegment>
    ): VideoAiTranscript? = withContext(Dispatchers.IO) {
        if (rawTranscriptText.isBlank()) return@withContext null

        val geminiKey = getGeminiApiKey(context)
        val groqKey = getGroqApiKey(context)
        val provider = getAiProvider(context)

        // Trim transcript if excessively long (keep up to ~25,000 characters for speed and token limits)
        val cleanTranscript = if (rawTranscriptText.length > 25000) {
            rawTranscriptText.take(25000) + "..."
        } else rawTranscriptText

        // Try selected provider first, then fallback to other provider if configured
        if (provider == "groq" && groqKey.isNotBlank()) {
            val result = callGroq(groqKey, videoId, title, channelName, cleanTranscript, spokenSegments)
            if (result != null) return@withContext result
        }

        if (geminiKey.isNotBlank()) {
            val result = callGemini(geminiKey, videoId, title, channelName, cleanTranscript, spokenSegments)
            if (result != null) return@withContext result
        }

        if (groqKey.isNotBlank()) {
            val result = callGroq(groqKey, videoId, title, channelName, cleanTranscript, spokenSegments)
            if (result != null) return@withContext result
        }

        null
    }

    private fun callGemini(
        apiKey: String,
        videoId: String,
        title: String,
        channelName: String,
        transcript: String,
        spokenSegments: List<TranscriptSegment>
    ): VideoAiTranscript? {
        try {
            val prompt = """
                You are an intelligent executive video summarizer.
                Summarize the following YouTube video accurately and intelligently.
                
                Video Title: "$title"
                Channel / Creator: "$channelName"
                
                Transcript of spoken words:
                $transcript
                
                Return a valid JSON object matching EXACTLY this structure:
                {
                  "topicPremise": "A clear, well-written 2-3 sentence overview of what the video is about and its main thesis.",
                  "executiveSummary": "A high-level executive summary of the entire video content.",
                  "keyTakeaways": [
                    "Actionable key takeaway 1",
                    "Actionable key takeaway 2",
                    "Actionable key takeaway 3"
                  ],
                  "discussionPoints": [
                    "Detailed discussion point 1 covering specific key topic explained in the video",
                    "Detailed discussion point 2 covering another major topic",
                    "Detailed discussion point 3...",
                    "Detailed discussion point 4..."
                  ],
                  "conclusion": "The video's final substantive conclusion or verdict (do NOT include host outro, like/subscribe plugs, or filler)."
                }
            """.trimIndent()

            val jsonBody = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        put("role", "user")
                        val partsArray = JSONArray().apply {
                            put(JSONObject().put("text", prompt))
                        }
                        put("parts", partsArray)
                    }
                    put(contentObj)
                }
                put("contents", contentsArray)
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.2)
                })
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonBody.toString().toRequestBody(mediaType)
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .header("Content-Type", "application/json")
                .build()

            val response = httpClient.newCall(request).execute()
            val responseStr = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                Log.e(TAG, "Gemini API failed (${response.code}): $responseStr")
                return null
            }

            val respJson = JSONObject(responseStr)
            val candidates = respJson.optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null

            val candidate = candidates.getJSONObject(0)
            val content = candidate.optJSONObject("content") ?: return null
            val parts = content.optJSONArray("parts") ?: return null
            if (parts.length() == 0) return null

            val jsonText = parts.getJSONObject(0).optString("text")
            return parseLlmJsonResponse(videoId, channelName, jsonText, spokenSegments)
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Gemini API", e)
            return null
        }
    }

    private fun callGroq(
        apiKey: String,
        videoId: String,
        title: String,
        channelName: String,
        transcript: String,
        spokenSegments: List<TranscriptSegment>
    ): VideoAiTranscript? {
        try {
            val systemPrompt = "You are an intelligent executive video summarizer. Output ONLY valid JSON."
            val userPrompt = """
                Summarize the following YouTube video accurately and intelligently.
                
                Video Title: "$title"
                Channel / Creator: "$channelName"
                
                Transcript:
                $transcript
                
                Return a valid JSON object matching EXACTLY this structure:
                {
                  "topicPremise": "A clear, well-written 2-3 sentence overview of what the video is about and its main thesis.",
                  "executiveSummary": "A high-level executive summary of the entire video content.",
                  "keyTakeaways": [
                    "Actionable key takeaway 1",
                    "Actionable key takeaway 2",
                    "Actionable key takeaway 3"
                  ],
                  "discussionPoints": [
                    "Detailed discussion point 1 covering specific key topic explained in the video",
                    "Detailed discussion point 2 covering another major topic",
                    "Detailed discussion point 3...",
                    "Detailed discussion point 4..."
                  ],
                  "conclusion": "The video's final substantive conclusion or verdict (do NOT include host outro, like/subscribe plugs, or filler)."
                }
            """.trimIndent()

            val jsonBody = JSONObject().apply {
                put("model", "llama-3.3-70b-versatile")
                val messagesArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userPrompt)
                    })
                }
                put("messages", messagesArray)
                put("response_format", JSONObject().put("type", "json_object"))
                put("temperature", 0.2)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonBody.toString().toRequestBody(mediaType)
            val url = "https://api.groq.com/openai/v1/chat/completions"

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .build()

            val response = httpClient.newCall(request).execute()
            val responseStr = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                Log.e(TAG, "Groq API failed (${response.code}): $responseStr")
                return null
            }

            val respJson = JSONObject(responseStr)
            val choices = respJson.optJSONArray("choices") ?: return null
            if (choices.length() == 0) return null

            val choice = choices.getJSONObject(0)
            val message = choice.optJSONObject("message") ?: return null
            val jsonText = message.optString("content")

            return parseLlmJsonResponse(videoId, channelName, jsonText, spokenSegments)
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Groq API", e)
            return null
        }
    }

    private fun parseLlmJsonResponse(
        videoId: String,
        channelName: String,
        jsonText: String,
        spokenSegments: List<TranscriptSegment>
    ): VideoAiTranscript? {
        try {
            // Find JSON content inside response text (in case of markdown code fences)
            val cleanedJson = jsonText
                .substringAfter("```json", jsonText)
                .substringAfter("```", jsonText)
                .substringBeforeLast("```")
                .trim()

            val obj = JSONObject(cleanedJson)
            val topicPremise = obj.optString("topicPremise")
            val executiveSummary = obj.optString("executiveSummary")
            val conclusion = obj.optString("conclusion")

            val takeaways = mutableListOf<String>()
            val takeawaysArr = obj.optJSONArray("keyTakeaways")
            if (takeawaysArr != null) {
                for (i in 0 until takeawaysArr.length()) {
                    val s = takeawaysArr.optString(i).trim()
                    if (s.isNotBlank()) takeaways.add(s)
                }
            }

            val discussionPoints = mutableListOf<String>()
            val discArr = obj.optJSONArray("discussionPoints")
            if (discArr != null) {
                for (i in 0 until discArr.length()) {
                    val s = discArr.optString(i).trim()
                    if (s.isNotBlank()) discussionPoints.add(s)
                }
            }

            return VideoAiTranscript(
                videoId = videoId,
                hostName = channelName,
                topicPremise = topicPremise,
                discussionPoints = if (discussionPoints.isNotEmpty()) discussionPoints else takeaways,
                conclusion = conclusion,
                executiveSummary = executiveSummary.ifBlank { topicPremise },
                keyTakeaways = takeaways,
                segments = spokenSegments
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing LLM JSON: $jsonText", e)
            return null
        }
    }
}
