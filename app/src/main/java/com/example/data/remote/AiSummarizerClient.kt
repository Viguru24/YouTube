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
        val geminiKey = getGeminiApiKey(context)
        val groqKey = getGroqApiKey(context)
        val provider = getAiProvider(context)

        val effectiveTranscript = if (rawTranscriptText.isNotBlank()) {
            if (rawTranscriptText.length > 25000) rawTranscriptText.take(25000) + "..." else rawTranscriptText
        } else {
            "YouTube speech-to-text is still processing. Analyze the subject from the video title: '$title' and creator: '$channelName'."
        }

        // Try selected provider first, then fallback to other provider if configured
        if (provider == "groq" && groqKey.isNotBlank()) {
            val result = callGroq(groqKey, videoId, title, channelName, effectiveTranscript, spokenSegments)
            if (result != null) return@withContext result
        }

        if (geminiKey.isNotBlank()) {
            val result = callGemini(geminiKey, videoId, title, channelName, effectiveTranscript, spokenSegments)
            if (result != null) return@withContext result
        }

        if (groqKey.isNotBlank()) {
            val result = callGroq(groqKey, videoId, title, channelName, effectiveTranscript, spokenSegments)
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
        val models = listOf("gemini-2.5-flash", "gemini-flash-latest", "gemini-2.5-flash-lite", "gemini-2.0-flash", "gemini-1.5-flash", "gemini-pro-latest")
        val prompt = """
            You are an elite, highly intelligent executive video analyst and summarizer.
            Analyze the following YouTube video and provide a comprehensive, deep, and beautifully written executive summary.
            
            Video Title: "$title"
            Channel / Creator: "$channelName"
            
            Spoken Word Transcript:
            $transcript
            
            Provide a complete, high-quality, professional executive summary in valid JSON matching this schema:
            {
              "topicPremise": "A clear, compelling, well-written 2-3 sentence overview of what the video is about and its central thesis.",
              "executiveSummary": "A comprehensive executive briefing summarizing the main storyline, arguments, and facts.",
              "keyTakeaways": [
                "Key takeaway 1 with specific facts or metrics",
                "Key takeaway 2 with specific facts or metrics",
                "Key takeaway 3 with specific facts or metrics"
              ],
              "discussionPoints": [
                "Detailed discussion point 1 covering the first major theme or discovery",
                "Detailed discussion point 2 covering the technical or narrative details",
                "Detailed discussion point 3 covering implications and broader context",
                "Detailed discussion point 4 covering critical analysis"
              ],
              "conclusion": "A strong, substantive concluding summary of the creator's final takeaways and verdict (strictly excluding outro filler, like/subscribe plugs, or sponsor mentions)."
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

        for (model in models) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .header("Content-Type", "application/json")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val responseStr = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val respJson = JSONObject(responseStr)
                        val candidates = respJson.optJSONArray("candidates")
                        if (candidates != null && candidates.length() > 0) {
                            val candidate = candidates.getJSONObject(0)
                            val content = candidate.optJSONObject("content")
                            val parts = content?.optJSONArray("parts")
                            if (parts != null && parts.length() > 0) {
                                val jsonText = parts.getJSONObject(0).optString("text")
                                val parsed = parseLlmJsonResponse(videoId, channelName, jsonText, spokenSegments)
                                if (parsed != null) {
                                    Log.d(TAG, "Successfully generated AI summary with Gemini ($model)")
                                    return parsed
                                }
                            }
                        }
                    } else {
                        Log.e(TAG, "Gemini ($model) failed with code ${response.code}: ${responseStr.take(120)}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error calling Gemini ($model)", e)
            }
        }
        return null
    }

    private fun callGroq(
        apiKey: String,
        videoId: String,
        title: String,
        channelName: String,
        transcript: String,
        spokenSegments: List<TranscriptSegment>
    ): VideoAiTranscript? {
        val models = listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "mixtral-8x7b-32768")
        val systemPrompt = "You are an elite, highly intelligent executive video analyst and summarizer. Output ONLY valid JSON."
        val userPrompt = """
            Analyze the following YouTube video and provide a comprehensive, deep, and beautifully written executive summary.
            
            Video Title: "$title"
            Channel / Creator: "$channelName"
            
            Spoken Word Transcript:
            $transcript
            
            Provide a complete, high-quality, professional executive summary in valid JSON matching this schema:
            {
              "topicPremise": "A clear, compelling, well-written 2-3 sentence overview of what the video is about and its central thesis.",
              "executiveSummary": "A comprehensive executive briefing summarizing the main storyline, arguments, and facts.",
              "keyTakeaways": [
                "Key takeaway 1 with specific facts or metrics",
                "Key takeaway 2 with specific facts or metrics",
                "Key takeaway 3 with specific facts or metrics"
              ],
              "discussionPoints": [
                "Detailed discussion point 1 covering the first major theme or discovery",
                "Detailed discussion point 2 covering the technical or narrative details",
                "Detailed discussion point 3 covering implications and broader context",
                "Detailed discussion point 4 covering critical analysis"
              ],
              "conclusion": "A strong, substantive concluding summary of the creator's final takeaways and verdict (strictly excluding outro filler, like/subscribe plugs, or sponsor mentions)."
            }
        """.trimIndent()

        val mediaType = "application/json; charset=utf-8".toMediaType()

        for (model in models) {
            try {
                val jsonBody = JSONObject().apply {
                    put("model", model)
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

                val requestBody = jsonBody.toString().toRequestBody(mediaType)
                val url = "https://api.groq.com/openai/v1/chat/completions"

                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .header("Authorization", "Bearer $apiKey")
                    .header("Content-Type", "application/json")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val responseStr = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val respJson = JSONObject(responseStr)
                        val choices = respJson.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val choice = choices.getJSONObject(0)
                            val message = choice.optJSONObject("message")
                            val jsonText = message?.optString("content").orEmpty()
                            val parsed = parseLlmJsonResponse(videoId, channelName, jsonText, spokenSegments)
                            if (parsed != null) {
                                Log.d(TAG, "Successfully generated AI summary with Groq ($model)")
                                return parsed
                            }
                        }
                    } else {
                        Log.e(TAG, "Groq ($model) failed with code ${response.code}: ${responseStr.take(120)}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error calling Groq ($model)", e)
            }
        }
        return null
    }

    private fun extractJsonBlock(raw: String): String {
        val text = raw.trim()
        val startIdx = text.indexOf('{')
        val endIdx = text.lastIndexOf('}')
        if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
            return text.substring(startIdx, endIdx + 1).trim()
        }
        return text
    }

    private fun parseLlmJsonResponse(
        videoId: String,
        channelName: String,
        jsonText: String,
        spokenSegments: List<TranscriptSegment>
    ): VideoAiTranscript? {
        try {
            val cleanedJson = extractJsonBlock(jsonText)
            val obj = JSONObject(cleanedJson)
            val topicPremise = obj.optString("topicPremise").trim()
            val executiveSummary = obj.optString("executiveSummary").trim()
            val conclusion = obj.optString("conclusion").trim()

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

            if (topicPremise.isBlank() && discussionPoints.isEmpty()) {
                return null
            }

            return VideoAiTranscript(
                videoId = videoId,
                hostName = channelName,
                topicPremise = topicPremise,
                discussionPoints = if (discussionPoints.isNotEmpty()) discussionPoints else takeaways,
                conclusion = conclusion,
                executiveSummary = executiveSummary.ifBlank { topicPremise },
                keyTakeaways = if (takeaways.isNotEmpty()) takeaways else discussionPoints,
                segments = spokenSegments
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing LLM JSON: ${jsonText.take(150)}", e)
            return null
        }
    }

    suspend fun askChatbot(
        context: Context,
        videoId: String,
        title: String,
        channelName: String,
        rawTranscriptText: String,
        history: List<ChatMessage>,
        userQuestion: String
    ): String = withContext(Dispatchers.IO) {
        val geminiKey = getGeminiApiKey(context)
        val groqKey = getGroqApiKey(context)
        val provider = getAiProvider(context)

        val effectiveTranscript = if (rawTranscriptText.isNotBlank()) {
            if (rawTranscriptText.length > 30000) rawTranscriptText.take(30000) + "..." else rawTranscriptText
        } else {
            "Video: '$title' by $channelName."
        }

        if (provider == "groq" && groqKey.isNotBlank()) {
            val ans = chatWithGroq(groqKey, title, channelName, effectiveTranscript, history, userQuestion)
            if (!ans.isNullOrBlank()) return@withContext ans
        }
        if (geminiKey.isNotBlank()) {
            val ans = chatWithGemini(geminiKey, title, channelName, effectiveTranscript, history, userQuestion)
            if (!ans.isNullOrBlank()) return@withContext ans
        }
        if (groqKey.isNotBlank()) {
            val ans = chatWithGroq(groqKey, title, channelName, effectiveTranscript, history, userQuestion)
            if (!ans.isNullOrBlank()) return@withContext ans
        }

        return@withContext answerQuestionOffline(title, channelName, effectiveTranscript, userQuestion)
    }

    private fun chatWithGemini(
        apiKey: String,
        title: String,
        channelName: String,
        transcript: String,
        history: List<ChatMessage>,
        userQuestion: String
    ): String? {
        val models = listOf("gemini-2.5-flash", "gemini-flash-latest", "gemini-2.5-flash-lite", "gemini-2.0-flash", "gemini-1.5-flash", "gemini-pro-latest")
        val systemPrompt = "You are an intelligent, friendly AI chatbot for this YouTube video titled '$title' by '$channelName'. Video content/transcript: $transcript. Answer the user's questions clearly and conversationally. When mentioning moments, include timestamps like [MM:SS] so the user can seek to them."

        val contentsArray = JSONArray()
        contentsArray.put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
        })
        contentsArray.put(JSONObject().apply {
            put("role", "model")
            put("parts", JSONArray().put(JSONObject().put("text", "Understood! I am ready to answer any questions about the video '$title'.")))
        })

        for (msg in history.takeLast(8)) {
            contentsArray.put(JSONObject().apply {
                put("role", if (msg.isUser) "user" else "model")
                put("parts", JSONArray().put(JSONObject().put("text", msg.text)))
            })
        }
        contentsArray.put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(JSONObject().put("text", userQuestion)))
        })

        val jsonBody = JSONObject().apply {
            put("contents", contentsArray)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.4)
                put("maxOutputTokens", 1024)
            })
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonBody.toString().toRequestBody(mediaType)

        for (model in models) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .header("Content-Type", "application/json")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val respStr = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val respJson = JSONObject(respStr)
                        val candidates = respJson.optJSONArray("candidates")
                        if (candidates != null && candidates.length() > 0) {
                            val candidate = candidates.getJSONObject(0)
                            val content = candidate.optJSONObject("content")
                            val parts = content?.optJSONArray("parts")
                            if (parts != null && parts.length() > 0) {
                                val text = parts.getJSONObject(0).optString("text").trim()
                                if (text.isNotBlank()) return text
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Gemini chat error with $model", e)
            }
        }
        return null
    }

    private fun chatWithGroq(
        apiKey: String,
        title: String,
        channelName: String,
        transcript: String,
        history: List<ChatMessage>,
        userQuestion: String
    ): String? {
        val models = listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "mixtral-8x7b-32768")
        val systemPrompt = "You are an intelligent, friendly AI chatbot for this YouTube video titled '$title' by '$channelName'. Video content: $transcript. Answer the user's questions clearly and conversationally. Format timestamps like [MM:SS]."

        val messagesArray = JSONArray()
        messagesArray.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })

        for (msg in history.takeLast(8)) {
            messagesArray.put(JSONObject().apply {
                put("role", if (msg.isUser) "user" else "assistant")
                put("content", msg.text)
            })
        }
        messagesArray.put(JSONObject().apply {
            put("role", "user")
            put("content", userQuestion)
        })

        val jsonBody = JSONObject().apply {
            put("messages", messagesArray)
            put("temperature", 0.4)
            put("max_tokens", 1024)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()

        for (model in models) {
            try {
                jsonBody.put("model", model)
                val requestBody = jsonBody.toString().toRequestBody(mediaType)
                val request = Request.Builder()
                    .url("https://api.groq.com/openai/v1/chat/completions")
                    .post(requestBody)
                    .header("Authorization", "Bearer $apiKey")
                    .header("Content-Type", "application/json")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val respStr = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val respJson = JSONObject(respStr)
                        val choices = respJson.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val choice = choices.getJSONObject(0)
                            val message = choice.optJSONObject("message")
                            val text = message?.optString("content").orEmpty().trim()
                            if (text.isNotBlank()) return text
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Groq chat error with $model", e)
            }
        }
        return null
    }

    private fun answerQuestionOffline(
        title: String,
        channelName: String,
        transcript: String,
        question: String
    ): String {
        val qLower = question.lowercase()

        // Clean and filter meaningful sentences from the transcript
        val sentences = transcript
            .split(Regex("[.!?\n]+"))
            .map { it.trim() }
            .filter { s ->
                s.length > 25 &&
                !s.lowercase().startsWith("hi and welcome") &&
                !s.lowercase().startsWith("welcome back") &&
                !s.lowercase().contains("like and subscribe") &&
                !s.lowercase().contains("sponsor") &&
                !s.lowercase().contains("leave a comment")
            }

        if (qLower.contains("summar") || qLower.contains("overview") || qLower.contains("what is this") || qLower.contains("about") || qLower.contains("takeaway") || qLower.contains("key point")) {
            val count = sentences.size
            val point1 = if (count > 0) sentences[count / 5.coerceAtLeast(1)] else "Covers fundamental developments."
            val point2 = if (count > 1) sentences[count / 2] else "Discusses industry and market shifts."
            val point3 = if (count > 2) sentences[(count * 4) / 5] else "Analyzes upcoming plans and production data."

            return buildString {
                append("✨ **3-Point Executive Summary: '$title'**\n")
                append("Creator: $channelName\n\n")
                append("1. **Core Development**: $point1.\n\n")
                append("2. **Market Impact**: $point2.\n\n")
                append("3. **Outlook & Evidence**: $point3.\n\n")
                append("🏁 **Bottom Line**: $channelName breaks down how these compounding factors are driving significant changes in this space.")
            }
        }

        val words = qLower.split(Regex("[^a-zA-Z0-9]+")).filter { it.length > 3 && it !in setOf("what", "when", "where", "which", "about", "this", "that", "with", "from", "video", "tell", "does") }
        val matches = mutableListOf<String>()
        if (words.isNotEmpty()) {
            for (sentence in sentences) {
                if (words.any { sentence.lowercase().contains(it) }) {
                    matches.add("• \"$sentence\"")
                    if (matches.size >= 3) break
                }
            }
        }
        if (matches.isNotEmpty()) {
            return "Here is what $channelName discussed regarding \"$question\":\n\n${matches.joinToString("\n\n")}"
        }

        val fallbackSnippet = sentences.take(2).joinToString(". ")
        return "Regarding '$question' in '$title':\n\n$fallbackSnippet."
    }
}

data class ChatMessage(
    val isUser: Boolean,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)
