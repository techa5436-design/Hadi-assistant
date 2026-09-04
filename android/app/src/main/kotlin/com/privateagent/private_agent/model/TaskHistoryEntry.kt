package com.privateagent.private_agent.model

import org.json.JSONArray
import org.json.JSONObject

enum class TaskStatus {
    SUCCESS,
    FAILED,
    CANCELLED;

    companion object {
        fun fromString(str: String): TaskStatus {
            return entries.firstOrNull { it.name.equals(str, ignoreCase = true) } ?: FAILED
        }
    }
}

data class TaskHistoryEntry(
    val id: String,
    val goal: String,
    val status: TaskStatus,
    val steps: Int,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val logs: List<String> = emptyList()
) {
    val durationMs: Long?
        get() = if (finishedAt != null) finishedAt - startedAt else null

    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("goal", goal)
        obj.put("status", status.name)
        obj.put("steps", steps)
        obj.put("startedAt", startedAt)
        if (finishedAt != null) {
            obj.put("finishedAt", finishedAt)
        }
        val logsArray = JSONArray()
        logs.forEach { logsArray.put(it) }
        obj.put("logs", logsArray)
        return obj
    }

    companion object {
        fun fromJson(json: JSONObject): TaskHistoryEntry? {
            return try {
                val id = json.getString("id")
                val goal = json.getString("goal")
                val status = TaskStatus.fromString(json.getString("status"))
                val steps = json.optInt("steps", 0)
                val startedAt = json.getLong("startedAt")
                val finishedAt = if (json.has("finishedAt")) json.getLong("finishedAt") else null
                val logsArray = json.optJSONArray("logs") ?: JSONArray()
                val logsList = mutableListOf<String>()
                for (i in 0 until logsArray.length()) {
                    logsList.add(logsArray.getString(i))
                }

                TaskHistoryEntry(
                    id = id,
                    goal = goal,
                    status = status,
                    steps = steps,
                    startedAt = startedAt,
                    finishedAt = finishedAt,
                    logs = logsList
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
