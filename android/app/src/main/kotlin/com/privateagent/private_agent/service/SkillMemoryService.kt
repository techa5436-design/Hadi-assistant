package com.privateagent.private_agent.service

import android.content.Context
import android.content.SharedPreferences
import com.privateagent.private_agent.model.Skill
import com.privateagent.private_agent.model.TaskStep
import org.json.JSONArray
import java.util.UUID

class SkillMemoryService private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val skills = mutableListOf<Skill>()

    init {
        loadSkills()
    }

    @Synchronized
    private fun loadSkills() {
        skills.clear()
        val raw = prefs.getString(KEY_SKILLS, null) ?: return
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                Skill.fromJson(obj)?.let { skills.add(it) }
            }
        } catch (_: Exception) {}
    }

    @Synchronized
    private fun saveSkillsToPrefs() {
        val array = JSONArray()
        skills.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_SKILLS, array.toString()).apply()
    }

    @Synchronized
    fun getAllSkills(): List<Skill> = skills.toList()

    @Synchronized
    fun findMatch(goal: String, threshold: Double = 0.75): Skill? {
        val normalized = Skill.normalize(goal)
        // Exact match
        val exact = skills.firstOrNull { it.normalizedGoal == normalized }
        if (exact != null) return exact

        // Fuzzy token similarity
        var bestSkill: Skill? = null
        var bestScore = 0.0
        for (skill in skills) {
            val score = skill.similarityTo(goal)
            if (score > bestScore && score >= threshold) {
                bestScore = score
                bestSkill = skill
            }
        }
        return bestSkill
    }

    @Synchronized
    fun saveSkill(goal: String, steps: List<TaskStep>): Skill {
        val normalized = Skill.normalize(goal)
        val existingIndex = skills.indexOfFirst { it.normalizedGoal == normalized }
        val nonDoneSteps = steps.filter { !it.isComplete }

        if (existingIndex >= 0) {
            val existing = skills[existingIndex]
            val updated = existing.copy(
                steps = nonDoneSteps,
                successCount = existing.successCount + 1,
                lastUsedAt = System.currentTimeMillis()
            )
            skills[existingIndex] = updated
            saveSkillsToPrefs()
            return updated
        } else {
            val skill = Skill(
                id = UUID.randomUUID().toString(),
                goal = goal,
                normalizedGoal = normalized,
                steps = nonDoneSteps,
                successCount = 1,
                lastUsedAt = System.currentTimeMillis(),
                createdAt = System.currentTimeMillis()
            )
            skills.add(skill)
            saveSkillsToPrefs()
            return skill
        }
    }

    @Synchronized
    fun recordReplaySuccess(id: String) {
        val index = skills.indexOfFirst { it.id == id }
        if (index >= 0) {
            val s = skills[index]
            skills[index] = s.copy(
                successCount = s.successCount + 1,
                lastUsedAt = System.currentTimeMillis()
            )
            saveSkillsToPrefs()
        }
    }

    @Synchronized
    fun recordReplayFailure(id: String) {
        val index = skills.indexOfFirst { it.id == id }
        if (index >= 0) {
            val s = skills[index]
            if (s.successCount <= 1) {
                skills.removeAt(index)
            } else {
                skills[index] = s.copy(successCount = s.successCount - 1)
            }
            saveSkillsToPrefs()
        }
    }

    @Synchronized
    fun deleteSkill(id: String) {
        skills.removeAll { it.id == id }
        saveSkillsToPrefs()
    }

    @Synchronized
    fun clear() {
        skills.clear()
        prefs.edit().remove(KEY_SKILLS).apply()
    }

    companion object {
        private const val PREFS_NAME = "privateagent_skills_prefs"
        private const val KEY_SKILLS = "saved_skills"

        @Volatile
        private var instance: SkillMemoryService? = null

        fun getInstance(context: Context): SkillMemoryService {
            return instance ?: synchronized(this) {
                instance ?: SkillMemoryService(context.applicationContext).also { instance = it }
            }
        }
    }
}
