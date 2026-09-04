package com.privateagent.private_agent.service

import com.privateagent.private_agent.model.ScreenNode
import com.privateagent.private_agent.model.TaskStep

class RecoveryEngine(
    private val maxRepeatingSteps: Int = 3,
    private val maxUnchangedScreens: Int = 3
) {
    private val actionHistory = mutableListOf<String>()
    private val screenSignatures = mutableListOf<String>()
    private var recoveryAttempt = 0

    fun reset() {
        actionHistory.clear()
        screenSignatures.clear()
        recoveryAttempt = 0
    }

    fun recordAction(step: TaskStep) {
        actionHistory.add(step.signature)
        if (actionHistory.size > 10) {
            actionHistory.removeAt(0)
        }
    }

    fun recordScreen(nodes: List<ScreenNode>) {
        val sig = nodes.joinToString("|") { "${it.index}:${it.bestLabel}:${it.centerX},${it.centerY}" }
        screenSignatures.add(sig)
        if (screenSignatures.size > 10) {
            screenSignatures.removeAt(0)
        }
    }

    fun isStuck(): Boolean {
        // Check repeating actions
        if (actionHistory.size >= maxRepeatingSteps) {
            val tail = actionHistory.takeLast(maxRepeatingSteps)
            if (tail.distinct().size == 1) {
                return true
            }
        }

        // Check unchanged screens
        if (screenSignatures.size >= maxUnchangedScreens) {
            val tail = screenSignatures.takeLast(maxUnchangedScreens)
            if (tail.distinct().size == 1 && tail.first().isNotBlank()) {
                return true
            }
        }

        return false
    }

    fun getSuggestedRecoveryStep(): TaskStep {
        recoveryAttempt++
        return when (recoveryAttempt % 3) {
            1 -> TaskStep(
                action = TaskStep.ACTION_SCROLL,
                params = mapOf("direction" to "down"),
                reasoning = "Recovery fallback: Screen appears stuck or repeating actions. Scrolling down to reveal more options."
            )
            2 -> TaskStep(
                action = TaskStep.ACTION_WAIT,
                params = mapOf("seconds" to 2),
                reasoning = "Recovery fallback: Waiting for UI animations or async loading."
            )
            else -> TaskStep(
                action = TaskStep.ACTION_PRESS_BACK,
                params = emptyMap(),
                reasoning = "Recovery fallback: Dismissing potential dialog or keyboard blocking the target."
            )
        }
    }
}
