package com.technatix.askai

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

/** Where text and screenshots go: into another app via share, or straight to an LLM API. */
enum class SendMode { OPEN_APP, API }

enum class ApiProvider(val label: String, val defaultModel: String) {
    CLAUDE("Claude API", "claude-opus-5-5"),
    OPENAI("OpenAI-compatible API", "gpt-4o-mini")
}

/** A saved instruction. [label] is the short name shown in the picker. */
data class Prompt(val label: String, val text: String)

val DEFAULT_PROMPTS = listOf(
    Prompt("Explain", "Explain this clearly and briefly."),
    Prompt("Summarize", "Summarize this in a few short bullet points."),
    Prompt("Translate", "Translate this to English. Return only the translation.")
)

const val DEFAULT_SYSTEM_PROMPT =
    "You are a concise assistant inside a phone helper app. The user sends selected text or a " +
        "screenshot together with an instruction. Answer directly with no preamble. When asked to " +
        "rewrite, translate or fix text, return only the resulting text."

/** All preferences, stored encrypted because they include API keys. */
class Settings(context: Context) {

    private val sp: SharedPreferences = open(context.applicationContext)

    private fun str(key: String, def: String) = sp.getString(key, def) ?: def
    private fun put(key: String, value: String) = sp.edit().putString(key, value).apply()

    var mode: SendMode
        get() = runCatching { SendMode.valueOf(str("mode", SendMode.OPEN_APP.name)) }
            .getOrDefault(SendMode.OPEN_APP)
        set(v) = put("mode", v.name)

    /** The AI app used in [SendMode.OPEN_APP]. */
    var app: AiApp
        get() = runCatching { AiApp.valueOf(str("app", AiApp.CLAUDE.name)) }.getOrDefault(AiApp.CLAUDE)
        set(v) = put("app", v.name)

    /** Draw the prompt into the screenshot as a caption (open-app mode only). */
    var burnIn: Boolean
        get() = sp.getBoolean("burn_in", true)
        set(v) = sp.edit().putBoolean("burn_in", v).apply()

    var apiProvider: ApiProvider
        get() = runCatching { ApiProvider.valueOf(str("api_provider", ApiProvider.CLAUDE.name)) }
            .getOrDefault(ApiProvider.CLAUDE)
        set(v) = put("api_provider", v.name)

    var claudeKey: String
        get() = str("claude_key", "")
        set(v) = put("claude_key", v.trim())

    var claudeModel: String
        get() = str("claude_model", ApiProvider.CLAUDE.defaultModel)
        set(v) = put("claude_model", v.trim())

    var openaiKey: String
        get() = str("openai_key", "")
        set(v) = put("openai_key", v.trim())

    var openaiModel: String
        get() = str("openai_model", ApiProvider.OPENAI.defaultModel)
        set(v) = put("openai_model", v.trim())

    /** Any OpenAI-compatible endpoint (OpenAI, Gemini, OpenRouter, a local server). */
    var openaiBaseUrl: String
        get() = str("openai_base", "https://api.openai.com/v1")
        set(v) = put("openai_base", v.trim())

    val apiKey: String get() = if (apiProvider == ApiProvider.CLAUDE) claudeKey else openaiKey
    val apiModel: String
        get() = (if (apiProvider == ApiProvider.CLAUDE) claudeModel else openaiModel)
            .ifBlank { apiProvider.defaultModel }

    fun apiConfigured() = apiKey.isNotBlank()

    var prompts: List<Prompt>
        get() {
            val raw = sp.getString("prompts_json", null) ?: return DEFAULT_PROMPTS
            return runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    Prompt(o.optString("label"), o.optString("text"))
                }.filter { it.text.isNotBlank() }
            }.getOrDefault(DEFAULT_PROMPTS)
        }
        set(v) {
            val arr = JSONArray()
            v.forEach { arr.put(JSONObject().put("label", it.label).put("text", it.text)) }
            put("prompts_json", arr.toString())
        }

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
            // Keystore got reset (e.g. after a restore): start clean rather than crash.
            ctx.deleteSharedPreferences(FILE)
            create(ctx)
        }
    }
}
