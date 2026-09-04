package com.privateagent.private_agent.service

import android.content.Context
import android.util.Base64
import com.privateagent.private_agent.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TelegramService private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val settingsRepo = SettingsRepository.getInstance(context)
    private val executor = TaskExecutor.getInstance(context)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private var pollJob: Job? = null
    private var lastUpdateId = 0L

    fun startIfEnabled() {
        if (settingsRepo.telegramEnabled.value && settingsRepo.telegramBotToken.value.isNotBlank()) {
            startPolling()
        } else {
            stopPolling()
        }
    }

    fun startPolling() {
        stopPolling()
        pollJob = scope.launch {
            while (isActive) {
                try {
                    val token = settingsRepo.telegramBotToken.value
                    if (token.isBlank() || !settingsRepo.telegramEnabled.value) {
                        delay(3000)
                        continue
                    }
                    pollUpdates(token)
                } catch (e: Exception) {
                    delay(5000)
                }
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private suspend fun pollUpdates(token: String) = withContext(Dispatchers.IO) {
        val url = "https://api.telegram.org/bot$token/getUpdates?offset=${lastUpdateId + 1}&timeout=25"
        val request = Request.Builder().url(url).build()

        val response = client.newCall(request).execute()
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) return@withContext

        val json = JSONObject(body)
        if (!json.optBoolean("ok", false)) return@withContext

        val results = json.optJSONArray("result") ?: return@withContext
        for (i in 0 until results.length()) {
            val item = results.getJSONObject(i)
            val updateId = item.getLong("update_id")
            if (updateId > lastUpdateId) {
                lastUpdateId = updateId
            }

            val msg = item.optJSONObject("message") ?: continue
            val chat = msg.getJSONObject("chat")
            val chatId = chat.getLong("id").toString()
            val text = msg.optString("text", "").trim()

            handleIncomingMessage(token, chatId, text)
        }
    }

    private suspend fun handleIncomingMessage(token: String, chatId: String, text: String) {
        val ownerId = settingsRepo.telegramOwnerChatId.value

        // Auto-pairing: if no owner is set, claim this chat
        if (ownerId.isBlank()) {
            settingsRepo.setTelegramOwnerChatId(chatId)
            sendMessage(token, chatId, "🤖 Paired PrivateAgent with this chat! Send a command or goal to start.")
            return
        }

        // Only process messages from owner
        if (ownerId != chatId) {
            sendMessage(token, chatId, "⚠️ Unauthorized. This bot is paired with another user.")
            return
        }

        when {
            text.startsWith("/status") -> {
                val status = executor.status.value
                val goal = executor.currentGoal.value ?: "None"
                val step = executor.currentStep.value
                val reply = "📊 *PrivateAgent Status*\nStatus: `${status.name}`\nGoal: `$goal`\nStep: `$step`"
                sendMessage(token, chatId, reply)
            }
            text.startsWith("/cancel") -> {
                executor.cancel()
                sendMessage(token, chatId, "🛑 Current task cancelled.")
            }
            text.startsWith("/screenshot") -> {
                sendMessage(token, chatId, "📸 Capturing screen...")
                val (b64, err) = ScreenAutomationService.takeScreenshotBase64()
                if (b64 != null) {
                    val bytes = Base64.decode(b64, Base64.NO_WRAP)
                    sendPhoto(token, chatId, bytes, "Current screen")
                } else {
                    sendMessage(token, chatId, "❌ Screenshot failed: ${err ?: "Unknown error"}")
                }
            }
            text.isNotBlank() -> {
                sendMessage(token, chatId, "🚀 Starting task: \"$text\"")
                withContext(Dispatchers.Main) {
                    executor.startTask(text)
                }
            }
        }
    }

    suspend fun sendMessage(token: String, chatId: String, text: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("chat_id", chatId)
                put("text", text)
                put("parse_mode", "Markdown")
            }
            val req = Request.Builder()
                .url("https://api.telegram.org/bot$token/sendMessage")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val resp = client.newCall(req).execute()
            resp.isSuccessful
        } catch (_: Exception) {
            false
        }
    }

    suspend fun sendPhoto(token: String, chatId: String, imageBytes: ByteArray, caption: String = ""): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId)
                .addFormDataPart("caption", caption)
                .addFormDataPart(
                    "photo",
                    "screenshot.png",
                    imageBytes.toRequestBody("image/png".toMediaType())
                )
                .build()
            val req = Request.Builder()
                .url("https://api.telegram.org/bot$token/sendPhoto")
                .post(body)
                .build()
            val resp = client.newCall(req).execute()
            resp.isSuccessful
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        @Volatile
        private var instance: TelegramService? = null

        fun getInstance(context: Context): TelegramService {
            return instance ?: synchronized(this) {
                instance ?: TelegramService(context.applicationContext).also { instance = it }
            }
        }
    }
}
