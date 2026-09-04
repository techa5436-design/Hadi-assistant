package com.privateagent.private_agent.service

import android.content.Context
import android.content.SharedPreferences
import com.privateagent.private_agent.data.AppMode
import com.privateagent.private_agent.data.SettingsRepository
import com.privateagent.private_agent.model.ChatMessage
import com.privateagent.private_agent.model.ChatRole
import com.privateagent.private_agent.model.ScreenNode
import com.privateagent.private_agent.model.Skill
import com.privateagent.private_agent.model.TaskHistoryEntry
import com.privateagent.private_agent.model.TaskStatus
import com.privateagent.private_agent.model.TaskStep
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.UUID

enum class ExecutorStatus {
    IDLE,
    RUNNING,
    PAUSED
}

class TaskExecutor private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val settingsRepo = SettingsRepository.getInstance(context)
    private val skillMemory = SkillMemoryService.getInstance(context)
    private val appLauncher = AppLauncherService(context)
    private val systemControl = SystemControlService(context)
    private val voiceService = VoiceService(context)
    private val aiService = AiService()
    private val recoveryEngine = RecoveryEngine()

    private val historyPrefs: SharedPreferences =
        context.getSharedPreferences("privateagent_history_prefs", Context.MODE_PRIVATE)

    private var activeJob: Job? = null
    private var isPauseRequested = false

    private val _status = MutableStateFlow(ExecutorStatus.IDLE)
    val status: StateFlow<ExecutorStatus> = _status.asStateFlow()

    private val _currentGoal = MutableStateFlow<String?>(null)
    val currentGoal: StateFlow<String?> = _currentGoal.asStateFlow()

    private val _currentStep = MutableStateFlow(0)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    private val _transcript = MutableStateFlow<List<ChatMessage>>(emptyList())
    val transcript: StateFlow<List<ChatMessage>> = _transcript.asStateFlow()

    private val _historyEntries = MutableStateFlow<List<TaskHistoryEntry>>(emptyList())
    val historyEntries: StateFlow<List<TaskHistoryEntry>> = _historyEntries.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        val raw = historyPrefs.getString("task_history", null) ?: return
        try {
            val arr = JSONArray(raw)
            val list = mutableListOf<TaskHistoryEntry>()
            for (i in 0 until arr.length()) {
                TaskHistoryEntry.fromJson(arr.getJSONObject(i))?.let { list.add(it) }
            }
            _historyEntries.value = list.sortedByDescending { it.startedAt }
        } catch (_: Exception) {}
    }

    private fun saveHistory() {
        val arr = JSONArray()
        _historyEntries.value.forEach { arr.put(it.toJson()) }
        historyPrefs.edit().putString("task_history", arr.toString()).apply()
    }

    fun startTask(goal: String) {
        if (_status.value != ExecutorStatus.IDLE) return
        val trimmedGoal = goal.trim()
        if (trimmedGoal.isEmpty()) return

        _currentGoal.value = trimmedGoal
        _currentStep.value = 0
        _status.value = ExecutorStatus.RUNNING
        isPauseRequested = false
        recoveryEngine.reset()

        appendMessage(ChatMessage.user(trimmedGoal))

        activeJob = scope.launch {
            runTaskLoop(trimmedGoal)
        }
    }

    private suspend fun runTaskLoop(goal: String) {
        val startTime = System.currentTimeMillis()
        val runLogs = mutableListOf<String>()
        val executedSteps = mutableListOf<TaskStep>()
        var taskFinalStatus = TaskStatus.FAILED
        var matchedSkill: Skill? = null

        fun log(msg: String) {
            runLogs.add(msg)
        }

        try {
            log("[SYSTEM] Task started: $goal")

            // 1. Check Accessibility Service
            if (!ScreenAutomationService.isServiceEnabled(context)) {
                val err = "Accessibility service is OFF. Please enable PrivateAgent Screen Control in Settings."
                appendMessage(ChatMessage.error(err))
                log("[ERROR] $err")
                taskFinalStatus = TaskStatus.FAILED
                return
            }

            // 2. Check Skill Memory for macro replay
            if (settingsRepo.skillsEnabled.value) {
                matchedSkill = skillMemory.findMatch(goal)
                if (matchedSkill != null && matchedSkill.steps.isNotEmpty()) {
                    appendMessage(ChatMessage.system("⚡ Found learned skill with ${matchedSkill.steps.size} steps. Replaying macro..."))
                    log("[SKILL] Replaying matched skill: ${matchedSkill.goal}")

                    var replayOk = true
                    for ((idx, step) in matchedSkill.steps.withIndex()) {
                        while (isPauseRequested) {
                            _status.value = ExecutorStatus.PAUSED
                            delay(300)
                        }
                        _status.value = ExecutorStatus.RUNNING
                        _currentStep.value = idx + 1

                        appendMessage(ChatMessage.agent("Replaying: ${step.action} (${step.reasoning})", step = idx + 1))
                        log("[AGENT #${idx + 1}] Replay ${step.action} - ${step.reasoning}")

                        val success = executeSingleStep(step)
                        executedSteps.add(step)
                        if (!success) {
                            appendMessage(ChatMessage.system("Replay drifted at step ${idx + 1}. Switching back to live AI reasoning."))
                            log("[SKILL] Drifted at step ${idx + 1}")
                            replayOk = false
                            skillMemory.recordReplayFailure(matchedSkill.id)
                            break
                        }
                        delay(settingsRepo.stepDelayMs.value)
                    }

                    if (replayOk) {
                        taskFinalStatus = TaskStatus.SUCCESS
                        skillMemory.recordReplaySuccess(matchedSkill.id)
                        appendMessage(ChatMessage.system("🎉 Task completed successfully via skill replay!"))
                        log("[SYSTEM] Replay success")
                        return
                    }
                }
            }

            // 3. Main Observe -> Think -> Act loop
            val maxSteps = settingsRepo.maxSteps.value
            var stepNumber = executedSteps.size

            while (stepNumber < maxSteps) {
                while (isPauseRequested) {
                    _status.value = ExecutorStatus.PAUSED
                    delay(300)
                }
                _status.value = ExecutorStatus.RUNNING

                stepNumber++
                _currentStep.value = stepNumber

                // OBSERVE
                val nodes = ScreenAutomationService.dumpScreen()
                recoveryEngine.recordScreen(nodes)
                val screenDesc = ScreenAutomationService.getCompressedScreenDescription(nodes, goal)

                // THINK
                val step: TaskStep = if (recoveryEngine.isStuck()) {
                    val fallback = recoveryEngine.getSuggestedRecoveryStep()
                    appendMessage(ChatMessage.system("⚠️ Screen unchanged or repeating. Applying recovery action: ${fallback.action}"))
                    log("[RECOVERY] ${fallback.action}: ${fallback.reasoning}")
                    fallback
                } else {
                    try {
                        withContext(Dispatchers.IO) {
                            aiService.complete(
                                goal = goal,
                                screenDescription = screenDesc,
                                history = _transcript.value,
                                baseUrl = settingsRepo.baseUrl.value,
                                apiKey = settingsRepo.apiKey.value,
                                model = settingsRepo.model.value,
                                temperature = settingsRepo.temperature.value,
                                maxTokens = settingsRepo.maxTokens.value,
                                useSystemPrompt = settingsRepo.useSystemPrompt.value
                            )
                        }
                    } catch (e: Exception) {
                        val errMsg = "AI Error: ${e.localizedMessage ?: e.message}"
                        appendMessage(ChatMessage.error(errMsg, step = stepNumber))
                        log("[ERROR] $errMsg")
                        taskFinalStatus = TaskStatus.FAILED
                        return
                    }
                }

                recoveryEngine.recordAction(step)
                executedSteps.add(step)

                // Log agent thought and step action
                val reasoningText = if (step.reasoning.isNotBlank()) "**${step.action}**\n${step.reasoning}" else "**${step.action}**"
                appendMessage(ChatMessage.agent(reasoningText, step = stepNumber))
                log("[AGENT #$stepNumber] ${step.action}: ${step.reasoning}")

                if (step.isComplete) {
                    taskFinalStatus = TaskStatus.SUCCESS
                    appendMessage(ChatMessage.system("🎉 Task marked as complete!"))
                    log("[DONE] Task complete")
                    break
                }

                // ACT
                val actSuccess = executeSingleStep(step, nodes)
                if (!actSuccess) {
                    log("[WARN] Step action returned false or could not find element")
                }

                delay(settingsRepo.stepDelayMs.value)
            }

            if (stepNumber >= maxSteps && taskFinalStatus != TaskStatus.SUCCESS) {
                appendMessage(ChatMessage.error("Reached max step limit ($maxSteps). Stopping.", step = stepNumber))
                log("[SYSTEM] Reached maximum steps limit")
                taskFinalStatus = TaskStatus.FAILED
            }

        } catch (_: CancellationException) {
            taskFinalStatus = TaskStatus.CANCELLED
            appendMessage(ChatMessage.system("🛑 Task cancelled by user."))
            log("[SYSTEM] Cancelled by user")
        } catch (e: Exception) {
            taskFinalStatus = TaskStatus.FAILED
            val err = "Execution failed: ${e.localizedMessage ?: e.message}"
            appendMessage(ChatMessage.error(err))
            log("[ERROR] $err")
        } finally {
            val finishTime = System.currentTimeMillis()
            val entry = TaskHistoryEntry(
                id = UUID.randomUUID().toString(),
                goal = goal,
                status = taskFinalStatus,
                steps = executedSteps.size,
                startedAt = startTime,
                finishedAt = finishTime,
                logs = runLogs
            )
            _historyEntries.value = listOf(entry) + _historyEntries.value
            saveHistory()

            if (taskFinalStatus == TaskStatus.SUCCESS && settingsRepo.skillsEnabled.value && executedSteps.isNotEmpty()) {
                skillMemory.saveSkill(goal, executedSteps)
            }

            if (settingsRepo.voiceFeedback.value) {
                val voiceMsg = if (taskFinalStatus == TaskStatus.SUCCESS) "Task completed" else "Task stopped"
                voiceService.speak(voiceMsg)
            }

            systemControl.showNotification(
                title = "PrivateAgent: ${if (taskFinalStatus == TaskStatus.SUCCESS) "Task Complete" else "Task Finished"}",
                body = goal
            )

            _status.value = ExecutorStatus.IDLE
            _currentGoal.value = null
            isPauseRequested = false
        }
    }

    private suspend fun executeSingleStep(step: TaskStep, cachedNodes: List<ScreenNode>? = null): Boolean {
        return when (step.action.lowercase()) {
            TaskStep.ACTION_CLICK_TEXT -> {
                val query = step.getStringParam("text")
                ScreenAutomationService.clickText(query, cachedNodes)
            }
            TaskStep.ACTION_CLICK_AT -> {
                val x = step.getIntParam("x")
                val y = step.getIntParam("y")
                ScreenAutomationService.clickAt(x, y)
            }
            TaskStep.ACTION_TYPE_TEXT -> {
                val text = step.getStringParam("text")
                ScreenAutomationService.typeText(text)
            }
            TaskStep.ACTION_PRESS_ENTER -> {
                ScreenAutomationService.pressKey("enter")
            }
            TaskStep.ACTION_SCROLL -> {
                val dir = step.getStringParam("direction", "down")
                ScreenAutomationService.scroll(dir)
            }
            TaskStep.ACTION_SWIPE -> {
                val sx = step.getIntParam("startX", 500)
                val sy = step.getIntParam("startY", 1500)
                val ex = step.getIntParam("endX", 500)
                val ey = step.getIntParam("endY", 300)
                val dur = step.getIntParam("durationMs", 300).toLong()
                ScreenAutomationService.swipe(sx, sy, ex, ey, dur)
            }
            TaskStep.ACTION_PRESS_BACK -> {
                ScreenAutomationService.pressKey("back")
            }
            TaskStep.ACTION_PRESS_HOME -> {
                ScreenAutomationService.pressKey("home")
            }
            TaskStep.ACTION_OPEN_APP -> {
                val appName = step.getStringParam("app_name")
                appLauncher.openApp(appName)
            }
            TaskStep.ACTION_WAIT -> {
                val secs = step.getIntParam("seconds", 2)
                delay((secs * 1000).toLong())
                true
            }
            TaskStep.ACTION_DONE -> true
            else -> false
        }
    }

    fun sendChatMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        appendMessage(ChatMessage.user(trimmed))
        _status.value = ExecutorStatus.RUNNING

        scope.launch {
            try {
                val reply = withContext(Dispatchers.IO) {
                    aiService.chat(
                        message = trimmed,
                        history = _transcript.value,
                        baseUrl = settingsRepo.baseUrl.value,
                        apiKey = settingsRepo.apiKey.value,
                        model = settingsRepo.model.value,
                        temperature = settingsRepo.temperature.value,
                        maxTokens = settingsRepo.maxTokens.value
                    )
                }
                appendMessage(ChatMessage.agent(reply))
            } catch (e: Exception) {
                appendMessage(ChatMessage.error("Chat error: ${e.localizedMessage ?: e.message}"))
            } finally {
                _status.value = ExecutorStatus.IDLE
            }
        }
    }

    fun pause() {
        if (_status.value == ExecutorStatus.RUNNING) {
            isPauseRequested = true
            _status.value = ExecutorStatus.PAUSED
        }
    }

    fun resume() {
        if (_status.value == ExecutorStatus.PAUSED) {
            isPauseRequested = false
            _status.value = ExecutorStatus.RUNNING
        }
    }

    fun cancel() {
        activeJob?.cancel()
        _status.value = ExecutorStatus.IDLE
        _currentGoal.value = null
        isPauseRequested = false
    }

    fun clearTranscript() {
        _transcript.value = emptyList()
    }

    fun clearHistory() {
        _historyEntries.value = emptyList()
        historyPrefs.edit().remove("task_history").apply()
    }

    fun deleteHistoryEntry(id: String) {
        _historyEntries.value = _historyEntries.value.filter { it.id != id }
        saveHistory()
    }

    private fun appendMessage(msg: ChatMessage) {
        _transcript.value = _transcript.value + msg
    }

    companion object {
        @Volatile
        private var instance: TaskExecutor? = null

        fun getInstance(context: Context): TaskExecutor {
            return instance ?: synchronized(this) {
                instance ?: TaskExecutor(context.applicationContext).also { instance = it }
            }
        }
    }
}
