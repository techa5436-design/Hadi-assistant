package com.privateagent.private_agent.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privateagent.private_agent.data.SettingsRepository
import com.privateagent.private_agent.data.ThemePreference
import com.privateagent.private_agent.service.TelegramService

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settingsRepo = remember { SettingsRepository.getInstance(context) }
    val telegramService = remember { TelegramService.getInstance(context) }

    val currentProvider by settingsRepo.provider.collectAsState()
    val savedBaseUrl by settingsRepo.baseUrl.collectAsState()
    val savedApiKey by settingsRepo.apiKey.collectAsState()
    val savedModel by settingsRepo.model.collectAsState()
    val savedMaxTokens by settingsRepo.maxTokens.collectAsState()
    val savedTemp by settingsRepo.temperature.collectAsState()
    val savedSystemPrompt by settingsRepo.useSystemPrompt.collectAsState()
    val savedMaxSteps by settingsRepo.maxSteps.collectAsState()
    val savedStepDelay by settingsRepo.stepDelayMs.collectAsState()
    val savedSkillsEnabled by settingsRepo.skillsEnabled.collectAsState()
    val savedVoiceFeedback by settingsRepo.voiceFeedback.collectAsState()
    val savedTelegramEnabled by settingsRepo.telegramEnabled.collectAsState()
    val savedTelegramBotToken by settingsRepo.telegramBotToken.collectAsState()
    val savedTelegramOwnerChatId by settingsRepo.telegramOwnerChatId.collectAsState()
    val savedThemePref by settingsRepo.themePreference.collectAsState()

    var baseUrl by remember(savedBaseUrl) { mutableStateOf(savedBaseUrl) }
    var apiKey by remember(savedApiKey) { mutableStateOf(savedApiKey) }
    var model by remember(savedModel) { mutableStateOf(savedModel) }
    var showApiKey by remember { mutableStateOf(false) }

    var maxTokens by remember(savedMaxTokens) { mutableIntStateOf(savedMaxTokens) }
    var temperature by remember(savedTemp) { mutableFloatStateOf(savedTemp) }
    var useSystemPrompt by remember(savedSystemPrompt) { mutableStateOf(savedSystemPrompt) }

    var maxSteps by remember(savedMaxSteps) { mutableIntStateOf(savedMaxSteps) }
    var stepDelayMs by remember(savedStepDelay) { mutableLongStateOf(savedStepDelay) }
    var skillsEnabled by remember(savedSkillsEnabled) { mutableStateOf(savedSkillsEnabled) }
    var voiceFeedback by remember(savedVoiceFeedback) { mutableStateOf(savedVoiceFeedback) }

    var telegramEnabled by remember(savedTelegramEnabled) { mutableStateOf(savedTelegramEnabled) }
    var telegramBotToken by remember(savedTelegramBotToken) { mutableStateOf(savedTelegramBotToken) }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            settingsRepo.setBaseUrl(baseUrl)
                            settingsRepo.setApiKey(apiKey)
                            settingsRepo.setModel(model)
                            settingsRepo.setMaxTokens(maxTokens)
                            settingsRepo.setTemperature(temperature)
                            settingsRepo.setUseSystemPrompt(useSystemPrompt)
                            settingsRepo.setMaxSteps(maxSteps)
                            settingsRepo.setStepDelayMs(stepDelayMs)
                            settingsRepo.setSkillsEnabled(skillsEnabled)
                            settingsRepo.setVoiceFeedback(voiceFeedback)
                            settingsRepo.setTelegramEnabled(telegramEnabled)
                            settingsRepo.setTelegramBotToken(telegramBotToken)
                            telegramService.startIfEnabled()
                            Toast.makeText(context, "Settings saved successfully", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. AI Provider Section
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "AI Brain Provider",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Select your preferred OpenAI-compatible provider or self-hosted Ollama.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SettingsRepository.PROVIDERS.forEach { preset ->
                            FilterChip(
                                selected = currentProvider == preset.id,
                                onClick = {
                                    settingsRepo.setProvider(preset.id)
                                    baseUrl = preset.baseUrl
                                    model = preset.defaultModel
                                },
                                label = { Text(preset.label) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("Base URL") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(
                                    imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle Key Visibility"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("Model Name") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text("Use System Prompt", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Injects JSON schema & action instructions", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = useSystemPrompt, onCheckedChange = { useSystemPrompt = it })
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Temperature: ${String.format("%.2f", temperature)}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = temperature,
                        onValueChange = { temperature = it },
                        valueRange = 0.0f..1.0f
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Max Tokens: $maxTokens", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = maxTokens.toFloat(),
                        onValueChange = { maxTokens = it.toInt() },
                        valueRange = 100f..2000f,
                        steps = 19
                    )
                }
            }

            // 2. Automation & Loop Settings
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Agent Automation Loop",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Max Steps Per Task: $maxSteps", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = maxSteps.toFloat(),
                        onValueChange = { maxSteps = it.toInt() },
                        valueRange = 3f..40f,
                        steps = 37
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Step Delay: ${stepDelayMs}ms", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = stepDelayMs.toFloat(),
                        onValueChange = { stepDelayMs = it.toLong() },
                        valueRange = 200f..5000f,
                        steps = 24
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Skill Memory (Macro Replay)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Remember successful action sequences to save API tokens", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = skillsEnabled, onCheckedChange = { skillsEnabled = it })
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Voice Feedback", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Speak task completions aloud via TTS", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = voiceFeedback, onCheckedChange = { voiceFeedback = it })
                    }
                }
            }

            // 3. Telegram Remote Control
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Send, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Telegram Remote Control", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Switch(checked = telegramEnabled, onCheckedChange = { telegramEnabled = it })
                    }

                    if (telegramEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Control your phone remotely by texting your Telegram bot with commands like /status, /screenshot, /cancel, or any goal.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = telegramBotToken,
                            onValueChange = { telegramBotToken = it },
                            label = { Text("Bot API Token") },
                            placeholder = { Text("123456789:ABCdefGhIJKlmNoP...") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        if (savedTelegramOwnerChatId.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                    Text("Paired Chat ID: $savedTelegramOwnerChatId", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    Text("Only this chat is authorized.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                OutlinedButton(
                                    onClick = {
                                        settingsRepo.setTelegramOwnerChatId("")
                                        Toast.makeText(context, "Unpaired bot from chat", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Text("Unpair")
                                }
                            }
                        } else {
                            Text(
                                text = "Status: Not yet paired. Send any message to your bot on Telegram to pair automatically.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // 4. Appearance
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Theme Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ThemePreference.entries.forEach { pref ->
                            FilterChip(
                                selected = savedThemePref == pref,
                                onClick = { settingsRepo.setThemePreference(pref) },
                                label = { Text(pref.name.lowercase().replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                }
            }

            // Save Button
            Button(
                onClick = {
                    settingsRepo.setBaseUrl(baseUrl)
                    settingsRepo.setApiKey(apiKey)
                    settingsRepo.setModel(model)
                    settingsRepo.setMaxTokens(maxTokens)
                    settingsRepo.setTemperature(temperature)
                    settingsRepo.setUseSystemPrompt(useSystemPrompt)
                    settingsRepo.setMaxSteps(maxSteps)
                    settingsRepo.setStepDelayMs(stepDelayMs)
                    settingsRepo.setSkillsEnabled(skillsEnabled)
                    settingsRepo.setVoiceFeedback(voiceFeedback)
                    settingsRepo.setTelegramEnabled(telegramEnabled)
                    settingsRepo.setTelegramBotToken(telegramBotToken)
                    telegramService.startIfEnabled()
                    Toast.makeText(context, "All settings saved", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Changes", fontWeight = FontWeight.Bold)
            }
        }
    }
}
