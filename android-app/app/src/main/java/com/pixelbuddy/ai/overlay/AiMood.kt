package com.pixelbuddy.ai.overlay

enum class AiMood {
    HAPPY,
    ANGRY,
    EXCITED,
    SAD,
    THINKING;

    companion object {
        fun fromApi(value: String?): AiMood =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: THINKING
    }
}