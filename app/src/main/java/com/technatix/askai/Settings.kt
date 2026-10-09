package com.technatix.askai

import android.content.Context

const val DEFAULT_PROMPTS = "Explain this."

/** Plain preferences: target AI app, messages, and how the message travels with a screenshot. */
class Settings(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences("askai", Context.MODE_PRIVATE)

    /** The AI app that selected text and screenshots are sent to. */
    var app: AiApp
        get() = runCatching { AiApp.valueOf(sp.getString("app", AiApp.CLAUDE.name) ?: "") }
            .getOrDefault(AiApp.CLAUDE)
        set(value) = sp.edit().putString("app", value.name).apply()

    /** One message per line. */
    var promptsText: String
        get() = sp.getString("prompts", DEFAULT_PROMPTS) ?: DEFAULT_PROMPTS
        set(value) = sp.edit().putString("prompts", value.trim()).apply()

    /** Draw the message into the screenshot as a caption, so no paste is needed in the AI app. */
    var burnIn: Boolean
        get() = sp.getBoolean("burn_in", true)
        set(value) = sp.edit().putBoolean("burn_in", value).apply()

    fun prompts(): List<String> = promptsText.lines().map { it.trim() }.filter { it.isNotEmpty() }
}
