package com.privateagent.private_agent.model

import org.json.JSONArray
import org.json.JSONObject

data class Skill(
    val id: String,
    val goal: String,
    val normalizedGoal: String = normalize(goal),
    val steps: List<TaskStep>,
    val successCount: Int = 1,
    val lastUsedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
) {
    fun similarityTo(query: String): Double {
        val queryNorm = normalize(query)
        if (normalizedGoal == queryNorm) return 1.0

        val goalTokens = normalizedGoal.split(" ").filter { it.isNotBlank() }.toSet()
        val queryTokens = queryNorm.split(" ").filter { it.isNotBlank() }.toSet()
        if (goalTokens.isEmpty() || queryTokens.isEmpty()) return 0.0

        val intersection = goalTokens.intersect(queryTokens).size
        val union = goalTokens.union(queryTokens).size
        return intersection.toDouble() / union.toDouble()
    }

    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("goal", goal)
        obj.put("normalizedGoal", normalizedGoal)
        val stepsArray = JSONArray()
        steps.forEach { stepsArray.put(it.toJson()) }
        obj.put("steps", stepsArray)
        obj.put("successCount", successCount)
        obj.put("lastUsedAt", lastUsedAt)
        obj.put("createdAt", createdAt)
        return obj
    }

    companion object {
        fun normalize(text: String): String {
            return text.lowercase()
                .replace(Regex("[^a-z0-9\\s]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        fun fromJson(json: JSONObject): Skill? {
            return try {
                val id = json.getString("id")
                val goal = json.getString("goal")
                val normalizedGoal = json.optString("normalizedGoal", normalize(goal))
                val stepsArray = json.optJSONArray("steps") ?: JSONArray()
                val stepsList = mutableListOf<TaskStep>()
                for (i in 0 until stepsArray.length()) {
                    val stepObj = stepsArray.getJSONObject(i)
                    val action = stepObj.getString("action")
                    val paramsJson = stepObj.optJSONObject("params")
                    val paramsMap = mutableMapOf<String, Any>()
                    if (paramsJson != null) {
                        val keys = paramsJson.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            paramsMap[k] = paramsJson.get(k)
                        }
                    }
                    val reasoning = stepObj.optString("reasoning", "")
                    stepsList.add(TaskStep(action, paramsMap, reasoning))
                }
                val successCount = json.optInt("successCount", 1)
                val lastUsedAt = json.optLong("lastUsedAt", System.currentTimeMillis())
                val createdAt = json.optLong("createdAt", System.currentTimeMillis())

                Skill(
                    id = id,
                    goal = goal,
                    normalizedGoal = normalizedGoal,
                    steps = stepsList,
                    successCount = successCount,
                    lastUsedAt = lastUsedAt,
                    createdAt = createdAt
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
