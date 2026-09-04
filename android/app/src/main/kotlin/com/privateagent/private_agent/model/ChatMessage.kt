package com.privateagent.private_agent.model

enum class ChatRole {
    USER,
    AGENT,
    SYSTEM,
    ERROR;

    companion object {
        fun fromString(name: String): ChatRole {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: SYSTEM
        }
    }
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: ChatRole,
    val text: String,
    val time: Long = System.currentTimeMillis(),
    val step: Int? = null,
    val isMarkdown: Boolean = role == ChatRole.AGENT
) {
    companion object {
        fun user(text: String) = ChatMessage(role = ChatRole.USER, text = text)
        fun agent(text: String, step: Int? = null) = ChatMessage(role = ChatRole.AGENT, text = text, step = step)
        fun system(text: String) = ChatMessage(role = ChatRole.SYSTEM, text = text)
        fun error(text: String, step: Int? = null) = ChatMessage(role = ChatRole.ERROR, text = text, step = step)
    }
}
