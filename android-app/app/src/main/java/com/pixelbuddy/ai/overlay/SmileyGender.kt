package com.pixelbuddy.ai.overlay

import android.content.Context
import android.content.SharedPreferences

enum class SmileyGender {
    MALE,
    FEMALE;

    fun toggled(): SmileyGender = if (this == MALE) FEMALE else MALE

    companion object {
        fun fromName(value: String?): SmileyGender =
            entries.firstOrNull { it.name == value } ?: MALE
    }
}

enum class FaceExpression {
    NORMAL,
    BLINK,
    SHY,
    DISAPPOINTED
}

/** Persists the chosen smiley so the app screen and the floating overlay always agree. */
class SmileyPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var gender: SmileyGender
        get() = SmileyGender.fromName(prefs.getString(KEY_GENDER, null))
        set(value) {
            prefs.edit().putString(KEY_GENDER, value.name).apply()
        }

    fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        const val KEY_GENDER = "gender"
        private const val FILE_NAME = "smiley_settings"
    }
}
