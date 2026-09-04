package com.privateagent.private_agent.model

import org.json.JSONObject
import java.util.regex.Pattern

data class TaskStep(
    val action: String,
    val params: Map<String, Any> = emptyMap(),
    val reasoning: String = ""
) {
    val isComplete: Boolean
        get() = action.equals(ACTION_DONE, ignoreCase = true)

    val signature: String
        get() = "$action:${params.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value}" }}"

    fun getStringParam(key: String, default: String = ""): String {
        return params[key]?.toString() ?: default
    }

    fun getIntParam(key: String, default: Int = 0): Int {
        val value = params[key]
        return when (value) {
            is Number -> value.toInt()
            is String -> value.toDoubleOrNull()?.toInt() ?: default
            else -> default
        }
    }

    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("action", action)
        val paramsJson = JSONObject()
        params.forEach { (k, v) -> paramsJson.put(k, v) }
        json.put("params", paramsJson)
        if (reasoning.isNotBlank()) {
            json.put("reasoning", reasoning)
        }
        return json
    }

    companion object {
        const val ACTION_CLICK_TEXT = "click_text"
        const val ACTION_CLICK_AT = "click_at"
        const val ACTION_TYPE_TEXT = "type_text"
        const val ACTION_PRESS_ENTER = "press_enter"
        const val ACTION_SCROLL = "scroll"
        const val ACTION_SWIPE = "swipe"
        const val ACTION_PRESS_BACK = "press_back"
        const val ACTION_PRESS_HOME = "press_home"
        const val ACTION_OPEN_APP = "open_app"
        const val ACTION_WAIT = "wait"
        const val ACTION_DONE = "done"

        private val JSON_BLOCK_PATTERN = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)\\s*```", Pattern.CASE_INSENSITIVE)
        private val JSON_OBJECT_PATTERN = Pattern.compile("\\{[\\s\\S]*\\}")

        fun tryParse(raw: String): TaskStep? {
            val text = raw.trim()
            if (text.isEmpty()) return null

            // 1. Try markdown code fence
            val matcher = JSON_BLOCK_PATTERN.matcher(text)
            if (matcher.find()) {
                val block = matcher.group(1)?.trim().orEmpty()
                parseJsonString(block)?.let { return it }
            }

            // 2. Try raw text directly
            parseJsonString(text)?.let { return it }

            // 3. Try finding any top-level { ... } block
            val objMatcher = JSON_OBJECT_PATTERN.matcher(text)
            if (objMatcher.find()) {
                val candidate = objMatcher.group(0)?.trim().orEmpty()
                parseJsonString(candidate)?.let { return it }
            }

            return null
        }

        private fun parseJsonString(content: String): TaskStep? {
            return try {
                val json = JSONObject(content)
                val action = json.optString("action", "").trim()
                if (action.isEmpty()) return null

                val paramsMap = mutableMapOf<String, Any>()
                val paramsJson = json.optJSONObject("params")
                if (paramsJson != null) {
                    val keys = paramsJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        paramsMap[key] = paramsJson.get(key)
                    }
                }

                val reasoning = json.optString("reasoning", json.optString("thought", "")).trim()
                TaskStep(action = action, params = paramsMap, reasoning = reasoning)
            } catch (_: Exception) {
                null
            }
        }
    }
}
