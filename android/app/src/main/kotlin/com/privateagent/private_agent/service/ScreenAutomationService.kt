package com.privateagent.private_agent.service

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.privateagent.private_agent.model.ScreenNode
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object ScreenAutomationService {

    val isConnected: Boolean
        get() = AgentAccessibilityService.isConnected

    fun isServiceEnabled(context: Context): Boolean {
        if (AgentAccessibilityService.isConnected) return true
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        return enabledServices.any {
            it.resolveInfo?.serviceInfo?.packageName == context.packageName
        }
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openAppInfoSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun dumpScreen(): List<ScreenNode> {
        val service = AgentAccessibilityService.instance ?: return emptyList()
        val raw = service.dumpScreen()
        return raw.map { ScreenNode.fromMap(it) }
    }

    fun formatNodes(nodes: List<ScreenNode>): String {
        if (nodes.isEmpty()) return "(no interactive elements found on screen)"
        val sb = StringBuilder()
        for (node in nodes) {
            val flags = mutableListOf<String>()
            if (node.isClickable) flags.add("clickable")
            if (node.isEditable) flags.add("editable")
            if (node.isScrollable) flags.add("scrollable")
            if (node.isCheckable) flags.add("checkable")

            val flagsStr = if (flags.isNotEmpty()) " [${flags.joinToString(",")}]" else ""
            val label = if (node.bestLabel.isNotBlank()) "\"${node.bestLabel}\"" else "(no label)"
            val cls = if (node.shortClassName.isNotBlank()) " <${node.shortClassName}>" else ""
            val coords = " @(${node.centerX},${node.centerY})"
            val bounds = " [${node.left},${node.top},${node.right},${node.bottom}]"

            sb.append("#${node.index} $label$cls$flagsStr$coords$bounds\n")
        }
        return sb.toString().trimEnd()
    }

    fun getCompressedScreenDescription(nodes: List<ScreenNode>, goal: String): String {
        if (nodes.isEmpty()) return "(screen is empty or obscured)"

        val goalTokens = goal.lowercase().split(Regex("[^a-z0-9]")).filter { it.length > 2 }.toSet()

        // Score nodes: prioritize visible text, interactive flags, and goal keyword matches
        val scored = nodes.map { node ->
            var score = 0
            if (node.isInteractive) score += 10
            if (node.text.isNotBlank()) score += 5
            if (node.contentDescription.isNotBlank()) score += 3
            if (node.isEditable) score += 8

            val labelLower = node.bestLabel.lowercase()
            for (token in goalTokens) {
                if (labelLower.contains(token)) {
                    score += 20
                }
            }
            Pair(node, score)
        }

        // Retain top 40 most relevant elements if list is very large
        val selectedNodes = if (nodes.size > 45) {
            scored.sortedByDescending { it.second }.take(40).map { it.first }.sortedBy { it.index }
        } else {
            nodes
        }

        return formatNodes(selectedNodes)
    }

    suspend fun clickAt(x: Int, y: Int): Boolean = suspendCancellableCoroutine { cont ->
        val service = AgentAccessibilityService.instance
        if (service == null) {
            cont.resume(false)
            return@suspendCancellableCoroutine
        }
        service.clickAt(x.toFloat(), y.toFloat()) { ok ->
            if (cont.isActive) cont.resume(ok)
        }
    }

    suspend fun clickText(query: String, nodes: List<ScreenNode>? = null): Boolean {
        val currentNodes = nodes ?: dumpScreen()
        val match = currentNodes.firstOrNull { it.matches(query) } ?: return false
        return clickAt(match.centerX, match.centerY)
    }

    suspend fun swipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        durationMs: Long = 300L
    ): Boolean = suspendCancellableCoroutine { cont ->
        val service = AgentAccessibilityService.instance
        if (service == null) {
            cont.resume(false)
            return@suspendCancellableCoroutine
        }
        service.swipe(
            startX.toFloat(),
            startY.toFloat(),
            endX.toFloat(),
            endY.toFloat(),
            durationMs
        ) { ok ->
            if (cont.isActive) cont.resume(ok)
        }
    }

    suspend fun scroll(direction: String): Boolean = suspendCancellableCoroutine { cont ->
        val service = AgentAccessibilityService.instance
        if (service == null) {
            cont.resume(false)
            return@suspendCancellableCoroutine
        }
        service.scroll(direction) { ok ->
            if (cont.isActive) cont.resume(ok)
        }
    }

    fun typeText(text: String): Boolean {
        val service = AgentAccessibilityService.instance ?: return false
        return service.typeText(text)
    }

    fun pressKey(key: String): Boolean {
        val service = AgentAccessibilityService.instance ?: return false
        return service.pressKey(key)
    }

    suspend fun takeScreenshotBase64(): Pair<String?, String?> = suspendCancellableCoroutine { cont ->
        val service = AgentAccessibilityService.instance
        if (service == null) {
            cont.resume(Pair(null, "Accessibility service is not connected"))
            return@suspendCancellableCoroutine
        }
        service.takeScreenshotBase64 { base64, error ->
            if (cont.isActive) cont.resume(Pair(base64, error))
        }
    }
}
