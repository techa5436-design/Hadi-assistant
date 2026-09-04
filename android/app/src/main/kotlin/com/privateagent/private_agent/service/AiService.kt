package com.privateagent.private_agent.service

import com.privateagent.private_agent.model.ChatMessage
import com.privateagent.private_agent.model.ChatRole
import com.privateagent.private_agent.model.TaskStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun complete(
        goal: String,
        screenDescription: String,
        history: List<ChatMessage>,
        baseUrl: String,
        apiKey: String,
        model: String,
        temperature: Float = 0.2f,
        maxTokens: Int = 600,
        useSystemPrompt: Boolean = true
    ): TaskStep = withContext(Dispatchers.IO) {
        val endpoint = normalizeEndpoint(baseUrl)
        val messagesArray = JSONArray()

        if (useSystemPrompt) {
            val systemObj = JSONObject()
            systemObj.put("role", "system")
            systemObj.put("content", GENIE_SYSTEM_PROMPT)
            messagesArray.put(systemObj)
        }

        // Add relevant prior steps
        val recentSteps = history.takeLast(10)
        for (msg in recentSteps) {
            val role = when (msg.role) {
                ChatRole.USER -> "user"
                ChatRole.AGENT -> "assistant"
                else -> null
            }
            if (role != null) {
                val obj = JSONObject()
                obj.put("role", role)
                obj.put("content", msg.text)
                messagesArray.put(obj)
            }
        }

        // Current observation prompt
        val currentPrompt = """
User Goal: $goal

Current Screen Hierarchy:
$screenDescription

Decide the single next action to make progress toward the goal. Respond with a valid JSON object only.
        """.trimIndent()

        val userObj = JSONObject()
        userObj.put("role", "user")
        userObj.put("content", currentPrompt)
        messagesArray.put(userObj)

        val requestBodyJson = JSONObject()
        requestBodyJson.put("model", model)
        requestBodyJson.put("messages", messagesArray)
        requestBodyJson.put("temperature", temperature)
        requestBodyJson.put("max_tokens", maxTokens)

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(requestBodyJson.toString().toRequestBody(jsonMediaType))

        if (apiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $apiKey")
        }

        val response = client.newCall(requestBuilder.build()).execute()
        val responseBody = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            val errMessage = try {
                val errJson = JSONObject(responseBody)
                errJson.optJSONObject("error")?.optString("message") ?: responseBody
            } catch (_: Exception) {
                responseBody
            }
            throw RuntimeException("API Error (${response.code}): $errMessage")
        }

        val json = JSONObject(responseBody)
        val choices = json.getJSONArray("choices")
        if (choices.length() == 0) {
            throw RuntimeException("Empty choices in LLM response")
        }
        val content = choices.getJSONObject(0).getJSONObject("message").getString("content")

        TaskStep.tryParse(content)
            ?: throw RuntimeException("Failed to parse JSON action step from LLM: $content")
    }

    suspend fun chat(
        message: String,
        history: List<ChatMessage>,
        baseUrl: String,
        apiKey: String,
        model: String,
        temperature: Float = 0.7f,
        maxTokens: Int = 1000
    ): String = withContext(Dispatchers.IO) {
        val endpoint = normalizeEndpoint(baseUrl)
        val messagesArray = JSONArray()

        val systemObj = JSONObject()
        systemObj.put("role", "system")
        systemObj.put("content", "You are PrivateAgent, an intelligent, helpful on-device AI assistant. Answer the user's questions clearly, concisely and warmly.")
        messagesArray.put(systemObj)

        for (msg in history.takeLast(12)) {
            val role = when (msg.role) {
                ChatRole.USER -> "user"
                ChatRole.AGENT -> "assistant"
                else -> null
            }
            if (role != null) {
                val obj = JSONObject()
                obj.put("role", role)
                obj.put("content", msg.text)
                messagesArray.put(obj)
            }
        }

        val userObj = JSONObject()
        userObj.put("role", "user")
        userObj.put("content", message)
        messagesArray.put(userObj)

        val requestBodyJson = JSONObject()
        requestBodyJson.put("model", model)
        requestBodyJson.put("messages", messagesArray)
        requestBodyJson.put("temperature", temperature)
        requestBodyJson.put("max_tokens", maxTokens)

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(requestBodyJson.toString().toRequestBody(jsonMediaType))

        if (apiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $apiKey")
        }

        val response = client.newCall(requestBuilder.build()).execute()
        val responseBody = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            throw RuntimeException("Chat Error (${response.code}): $responseBody")
        }

        val json = JSONObject(responseBody)
        val choices = json.getJSONArray("choices")
        if (choices.length() == 0) return@withContext "No response."
        choices.getJSONObject(0).getJSONObject("message").getString("content")
    }

    private fun normalizeEndpoint(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return if (trimmed.endsWith("/chat/completions")) {
            trimmed
        } else {
            "$trimmed/chat/completions"
        }
    }

    companion object {
        val GENIE_SYSTEM_PROMPT = """
You are PrivateAgent, an autonomous Android UI agent. You interact with phone apps to complete user tasks.
On each step, you receive:
1. User Goal
2. The current visible screen elements: #index "label" <Class> [flags] @(centerX,centerY) [left,top,right,bottom]

You MUST return STRICT JSON with exactly this format:
{
  "action": "click_text" | "click_at" | "type_text" | "press_enter" | "scroll" | "swipe" | "press_back" | "press_home" | "open_app" | "wait" | "done",
  "params": { ... },
  "reasoning": "Short explanation of your choice"
}

Available actions and params:
- "click_text": {"text": "exact label or substring"} -> preferred for clicking buttons/text
- "click_at": {"x": 540, "y": 1200} -> tap exact coordinates when text is missing
- "type_text": {"text": "the text to input"} -> types into current or focused editable field
- "press_enter": {} -> presses software keyboard enter/search
- "scroll": {"direction": "down" | "up" | "left" | "right"} -> scrolls to reveal more content
- "swipe": {"startX": 500, "startY": 1500, "endX": 500, "endY": 300, "durationMs": 300}
- "press_back": {} -> Android back button
- "press_home": {} -> Android home button
- "open_app": {"app_name": "Settings" | "Spotify" | "com.android.settings"} -> opens an app
- "wait": {"seconds": 2} -> pauses for UI loading
- "done": {"summary": "Completed the task successfully"} -> finishes execution

Guidelines:
1. Always output ONLY valid JSON without extra chat text.
2. If the goal is fulfilled, return action "done".
3. If an input field needs text, first click it if not focused, then type_text.
4. Prefer click_text with visible text over coordinate taps when possible.
        """.trimIndent()
    }
}
