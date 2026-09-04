package com.privateagent.private_agent.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ProviderPreset(
    val id: String,
    val label: String,
    val baseUrl: String,
    val defaultModel: String,
    val requiresApiKey: Boolean = true
)

enum class AppMode {
    GENIE,
    CHAT
}

enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK
}

class SettingsRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "privateagent_settings_prefs"

        val PROVIDERS = listOf(
            ProviderPreset("deepseek", "DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
            ProviderPreset("openai", "OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
            ProviderPreset("gemini", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai/", "gemini-2.5-flash"),
            ProviderPreset("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", "deepseek/deepseek-chat"),
            ProviderPreset("nvidia", "NVIDIA NIM", "https://integrate.api.nvidia.com/v1", "meta/llama-3.1-70b-instruct"),
            ProviderPreset("ollama", "Ollama (Local)", "http://localhost:11434/v1", "llama3.2:latest", requiresApiKey = false),
            ProviderPreset("custom", "Custom OpenAI-Compatible", "http://localhost:8000/v1", "custom-model")
        )

        @Volatile
        private var instance: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository {
            return instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    private val _provider = MutableStateFlow(prefs.getString("provider", "deepseek") ?: "deepseek")
    val provider: StateFlow<String> = _provider.asStateFlow()

    private val _baseUrl = MutableStateFlow(prefs.getString("baseUrl", "https://api.deepseek.com/v1") ?: "https://api.deepseek.com/v1")
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    private val _apiKey = MutableStateFlow(prefs.getString("apiKey", "") ?: "")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _model = MutableStateFlow(prefs.getString("model", "deepseek-chat") ?: "deepseek-chat")
    val model: StateFlow<String> = _model.asStateFlow()

    private val _maxTokens = MutableStateFlow(prefs.getInt("maxTokens", 600))
    val maxTokens: StateFlow<Int> = _maxTokens.asStateFlow()

    private val _temperature = MutableStateFlow(prefs.getFloat("temperature", 0.2f))
    val temperature: StateFlow<Float> = _temperature.asStateFlow()

    private val _useSystemPrompt = MutableStateFlow(prefs.getBoolean("useSystemPrompt", true))
    val useSystemPrompt: StateFlow<Boolean> = _useSystemPrompt.asStateFlow()

    private val _maxSteps = MutableStateFlow(prefs.getInt("maxSteps", 15))
    val maxSteps: StateFlow<Int> = _maxSteps.asStateFlow()

    private val _stepDelayMs = MutableStateFlow(prefs.getLong("stepDelayMs", 1000L))
    val stepDelayMs: StateFlow<Long> = _stepDelayMs.asStateFlow()

    private val _skillsEnabled = MutableStateFlow(prefs.getBoolean("skillsEnabled", true))
    val skillsEnabled: StateFlow<Boolean> = _skillsEnabled.asStateFlow()

    private val _voiceFeedback = MutableStateFlow(prefs.getBoolean("voiceFeedback", false))
    val voiceFeedback: StateFlow<Boolean> = _voiceFeedback.asStateFlow()

    private val _telegramEnabled = MutableStateFlow(prefs.getBoolean("telegramEnabled", false))
    val telegramEnabled: StateFlow<Boolean> = _telegramEnabled.asStateFlow()

    private val _telegramBotToken = MutableStateFlow(prefs.getString("telegramBotToken", "") ?: "")
    val telegramBotToken: StateFlow<String> = _telegramBotToken.asStateFlow()

    private val _telegramOwnerChatId = MutableStateFlow(prefs.getString("telegramOwnerChatId", "") ?: "")
    val telegramOwnerChatId: StateFlow<String> = _telegramOwnerChatId.asStateFlow()

    private val _appMode = MutableStateFlow(AppMode.valueOf(prefs.getString("appMode", AppMode.GENIE.name) ?: AppMode.GENIE.name))
    val appMode: StateFlow<AppMode> = _appMode.asStateFlow()

    private val _themePreference = MutableStateFlow(ThemePreference.valueOf(prefs.getString("themePreference", ThemePreference.SYSTEM.name) ?: ThemePreference.SYSTEM.name))
    val themePreference: StateFlow<ThemePreference> = _themePreference.asStateFlow()

    private val _onboardingComplete = MutableStateFlow(prefs.getBoolean("onboardingComplete", false))
    val onboardingComplete: StateFlow<Boolean> = _onboardingComplete.asStateFlow()

    fun setProvider(newProviderId: String) {
        val preset = PROVIDERS.firstOrNull { it.id == newProviderId } ?: PROVIDERS.first()
        _provider.value = preset.id
        _baseUrl.value = preset.baseUrl
        _model.value = preset.defaultModel
        prefs.edit()
            .putString("provider", preset.id)
            .putString("baseUrl", preset.baseUrl)
            .putString("model", preset.defaultModel)
            .apply()
    }

    fun setBaseUrl(url: String) {
        _baseUrl.value = url
        prefs.edit().putString("baseUrl", url).apply()
    }

    fun setApiKey(key: String) {
        _apiKey.value = key
        prefs.edit().putString("apiKey", key).apply()
    }

    fun setModel(m: String) {
        _model.value = m
        prefs.edit().putString("model", m).apply()
    }

    fun setMaxTokens(tokens: Int) {
        _maxTokens.value = tokens
        prefs.edit().putInt("maxTokens", tokens).apply()
    }

    fun setTemperature(temp: Float) {
        _temperature.value = temp
        prefs.edit().putFloat("temperature", temp).apply()
    }

    fun setUseSystemPrompt(use: Boolean) {
        _useSystemPrompt.value = use
        prefs.edit().putBoolean("useSystemPrompt", use).apply()
    }

    fun setMaxSteps(steps: Int) {
        _maxSteps.value = steps
        prefs.edit().putInt("maxSteps", steps).apply()
    }

    fun setStepDelayMs(ms: Long) {
        _stepDelayMs.value = ms
        prefs.edit().putLong("stepDelayMs", ms).apply()
    }

    fun setSkillsEnabled(enabled: Boolean) {
        _skillsEnabled.value = enabled
        prefs.edit().putBoolean("skillsEnabled", enabled).apply()
    }

    fun setVoiceFeedback(enabled: Boolean) {
        _voiceFeedback.value = enabled
        prefs.edit().putBoolean("voiceFeedback", enabled).apply()
    }

    fun setTelegramEnabled(enabled: Boolean) {
        _telegramEnabled.value = enabled
        prefs.edit().putBoolean("telegramEnabled", enabled).apply()
    }

    fun setTelegramBotToken(token: String) {
        _telegramBotToken.value = token
        prefs.edit().putString("telegramBotToken", token).apply()
    }

    fun setTelegramOwnerChatId(chatId: String) {
        _telegramOwnerChatId.value = chatId
        prefs.edit().putString("telegramOwnerChatId", chatId).apply()
    }

    fun setAppMode(mode: AppMode) {
        _appMode.value = mode
        prefs.edit().putString("appMode", mode.name).apply()
    }

    fun setThemePreference(pref: ThemePreference) {
        _themePreference.value = pref
        prefs.edit().putString("themePreference", pref.name).apply()
    }

    fun setOnboardingComplete(complete: Boolean) {
        _onboardingComplete.value = complete
        prefs.edit().putBoolean("onboardingComplete", complete).apply()
    }
}
