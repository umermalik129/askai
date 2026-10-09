package com.technatix.askai

import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class Msg(val role: String, val content: String)

class LlmClient(private val settings: Settings) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var current: Call? = null

    fun cancel() {
        current?.cancel()
    }

    /** Blocking. Call from a background thread. [onDelta] receives each new piece of text. */
    fun stream(provider: Provider, history: List<Msg>, onDelta: (String) -> Unit) {
        val request = if (provider == Provider.CLAUDE) claudeRequest(history) else openAiRequest(history)
        val call = http.newCall(request)
        current = call
        call.execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException(errorMessage(resp.code, resp.body?.string()))
            }
            val source = resp.body?.source() ?: throw IOException("Empty response")
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data.isEmpty() || data == "[DONE]") continue
                val json = JSONObject(data)
                if (provider == Provider.CLAUDE) readClaude(json, onDelta) else readOpenAi(json, onDelta)
            }
        }
    }

    private fun readClaude(json: JSONObject, onDelta: (String) -> Unit) {
        when (json.optString("type")) {
            "content_block_delta" -> {
                val delta = json.optJSONObject("delta")
                if (delta != null && delta.optString("type") == "text_delta") {
                    onDelta(delta.optString("text"))
                }
            }
            "error" -> throw IOException(
                json.optJSONObject("error")?.optString("message") ?: "Stream error"
            )
        }
    }

    private fun readOpenAi(json: JSONObject, onDelta: (String) -> Unit) {
        val choices = json.optJSONArray("choices") ?: return
        if (choices.length() == 0) return
        val delta = choices.getJSONObject(0).optJSONObject("delta") ?: return
        if (!delta.isNull("content")) onDelta(delta.optString("content"))
    }

    private fun claudeRequest(history: List<Msg>): Request {
        val body = JSONObject()
            .put("model", settings.claudeModel)
            .put("max_tokens", 2048)
            .put("stream", true)
            .put("system", settings.systemPrompt)
            .put("messages", messages(history))
        return Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", settings.claudeKey)
            .header("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody(JSON))
            .build()
    }

    private fun openAiRequest(history: List<Msg>): Request {
        val all = listOf(Msg("system", settings.systemPrompt)) + history
        val body = JSONObject()
            .put("model", settings.openaiModel)
            .put("stream", true)
            .put("messages", messages(all))
        val url = settings.openaiBaseUrl.trimEnd('/') + "/chat/completions"
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${settings.openaiKey}")
            .post(body.toString().toRequestBody(JSON))
            .build()
    }

    private fun messages(list: List<Msg>): JSONArray {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("role", it.role).put("content", it.content)) }
        return arr
    }

    private fun errorMessage(code: Int, body: String?): String {
        val detail = runCatching {
            JSONObject(body ?: "").optJSONObject("error")?.optString("message")
        }.getOrNull()
        return when {
            code == 401 -> "Invalid API key (401). Check it in the AskAI app."
            !detail.isNullOrBlank() -> "Error $code: $detail"
            else -> "Error $code"
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
