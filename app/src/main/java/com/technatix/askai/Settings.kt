package com.technatix.askai

import android.content.Context

const val DEFAULT_PROMPTS = "Explain what is in this screenshot."

/** Plain preferences: the messages that can accompany a screenshot sent to Claude. */
class Settings(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences("askai", Context.MODE_PRIVATE)

    /** One message per line. */
    var promptsText: String
        get() = sp.getString("prompts", DEFAULT_PROMPTS) ?: DEFAULT_PROMPTS
        set(value) = sp.edit().putString("prompts", value.trim()).apply()

    /** Draw the message into the screenshot as a caption, so no paste is needed in Claude. */
    var burnIn: Boolean
        get() = sp.getBoolean("burn_in", true)
        set(value) = sp.edit().putBoolean("burn_in", value).apply()

    fun prompts(): List<String> = promptsText.lines().map { it.trim() }.filter { it.isNotEmpty() }
}
