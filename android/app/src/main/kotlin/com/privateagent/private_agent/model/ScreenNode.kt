package com.privateagent.private_agent.model

data class ScreenNode(
    val index: Int,
    val text: String = "",
    val contentDescription: String = "",
    val className: String = "",
    val viewId: String = "",
    val isClickable: Boolean = false,
    val isEditable: Boolean = false,
    val isScrollable: Boolean = false,
    val isCheckable: Boolean = false,
    val isEnabled: Boolean = true,
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
    val centerX: Int = (left + right) / 2,
    val centerY: Int = (top + bottom) / 2
) {
    val bestLabel: String
        get() {
            if (text.isNotBlank()) return text
            if (contentDescription.isNotBlank()) return contentDescription
            if (viewId.isNotBlank()) {
                val parts = viewId.split("/")
                return parts.lastOrNull() ?: viewId
            }
            return ""
        }

    val isInteractive: Boolean
        get() = isClickable || isEditable || isScrollable || isCheckable

    val shortClassName: String
        get() {
            val parts = className.split(".")
            return parts.lastOrNull() ?: className
        }

    fun matches(query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return false
        val t = text.lowercase()
        val c = contentDescription.lowercase()
        val v = viewId.lowercase()
        return t == q || c == q || t.contains(q) || c.contains(q) || v.contains(q)
    }

    companion object {
        fun fromMap(map: Map<String, Any?>): ScreenNode {
            val idx = (map["index"] as? Number)?.toInt() ?: 0
            val text = map["text"]?.toString().orEmpty()
            val desc = map["contentDescription"]?.toString().orEmpty()
            val className = map["className"]?.toString().orEmpty()
            val viewId = map["viewId"]?.toString().orEmpty()
            val isClickable = map["isClickable"] as? Boolean ?: false
            val isEditable = map["isEditable"] as? Boolean ?: false
            val isScrollable = map["isScrollable"] as? Boolean ?: false
            val isCheckable = map["isCheckable"] as? Boolean ?: false
            val isEnabled = map["isEnabled"] as? Boolean ?: true
            val left = (map["left"] as? Number)?.toInt() ?: 0
            val top = (map["top"] as? Number)?.toInt() ?: 0
            val right = (map["right"] as? Number)?.toInt() ?: 0
            val bottom = (map["bottom"] as? Number)?.toInt() ?: 0
            val centerX = (map["centerX"] as? Number)?.toInt() ?: ((left + right) / 2)
            val centerY = (map["centerY"] as? Number)?.toInt() ?: ((top + bottom) / 2)

            return ScreenNode(
                index = idx,
                text = text,
                contentDescription = desc,
                className = className,
                viewId = viewId,
                isClickable = isClickable,
                isEditable = isEditable,
                isScrollable = isScrollable,
                isCheckable = isCheckable,
                isEnabled = isEnabled,
                left = left,
                top = top,
                right = right,
                bottom = bottom,
                centerX = centerX,
                centerY = centerY
            )
        }
    }
}
