package com.technatix.askai

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

enum class Provider(val label: String) {
    CLAUDE("Claude"),
    OPENAI("ChatGPT")
}

data class Preset(val label: String, val prompt: String)

const val DEFAULT_SYSTEM_PROMPT =
    "You are a concise assistant inside a phone text-selection menu. The user selected some text " +
        "and asks about it. Answer directly with no preamble. When asked to rewrite, translate or fix " +
        "text, return only the resulting text."

const val DEFAULT_PRESETS = """Summarize | Summarize this concisely.
Explain simply | Explain this in simple terms.
Fix grammar | Fix grammar and spelling. Return only the corrected text.
Translate to English | Translate to English. Return only the translation.
Translate to Urdu | Translate to Urdu. Return only the translation.
Reply | Write a short, polite reply to this message."""

class Settings(context: Context) {

    private val sp: SharedPreferences = open(context.applicationContext)

    private fun str(key: String, def: String) = sp.getString(key, def) ?: def
    private fun put(key: String, value: String) = sp.edit().putString(key, value).apply()

    var defaultProvider: Provider
        get() = runCatching { Provider.valueOf(str("provider", Provider.CLAUDE.name)) }
            .getOrDefault(Provider.CLAUDE)
        set(v) = put("provider", v.name)

    var claudeKey: String
        get() = str("claude_key", "")
        set(v) = put("claude_key", v.trim())

    var claudeModel: String
        get() = str("claude_model", "claude-sonnet-5-5")
        set(v) = put("claude_model", v.trim())

    var openaiKey: String
        get() = str("openai_key", "")
        set(v) = put("openai_key", v.trim())

    var openaiModel: String
        get() = str("openai_model", "gpt-4o-mini")
        set(v) = put("openai_model", v.trim())

    /** Any OpenAI-compatible endpoint works here (e.g. Gemini, OpenRouter, a local server). */
    var openaiBaseUrl: String
        get() = str("openai_base", "https://api.openai.com/v1")
        set(v) = put("openai_base", v.trim())

    var systemPrompt: String
        get() = str("system", DEFAULT_SYSTEM_PROMPT)
        set(v) = put("system", v.trim())

    var presetsText: String
        get() = str("presets", DEFAULT_PRESETS)
        set(v) = put("presets", v.trim())

    fun presets(): List<Preset> = presetsText.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { line ->
            val i = line.indexOf('|')
            if (i < 0) Preset(line, line)
            else Preset(line.substring(0, i).trim(), line.substring(i + 1).trim())
        }
        .filter { it.label.isNotEmpty() && it.prompt.isNotEmpty() }

    fun keyFor(p: Provider) = if (p == Provider.CLAUDE) claudeKey else openaiKey
    fun modelFor(p: Provider) = if (p == Provider.CLAUDE) claudeModel else openaiModel

    companion object {
        private const val FILE = "askai_secure"

        private fun create(ctx: Context): SharedPreferences {
            val key = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            return EncryptedSharedPreferences.create(
                ctx, FILE, key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }

        private fun open(ctx: Context): SharedPreferences = try {
            create(ctx)
        } catch (e: Exception) {
            // Keystore got reset (e.g. after a backup restore): start clean.
            ctx.deleteSharedPreferences(FILE)
            create(ctx)
        }
    }
}
