package com.technatix.askai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.max

/** One question for the model: an instruction plus either selected text or a screenshot. */
class LlmRequest(val prompt: String, val text: String?, val image: File?)

/** Result of a streamed call. [stopReason] is the provider's own value, e.g. "refusal". */
class LlmResult(val stopReason: String?)

/** Streams answers from the Claude Messages API or any OpenAI-compatible chat endpoint over raw HTTP. */
class LlmClient(private val settings: Settings) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var current: Call? = null

    fun cancel() {
        current?.cancel()
    }

    /** Blocking. Call from a background thread. [onDelta] receives each new piece of text. */
    fun stream(req: LlmRequest, onDelta: (String) -> Unit): LlmResult {
        val provider = settings.apiProvider
        val imageB64 = req.image?.let { encodeImage(it) }
        val request = if (provider == ApiProvider.CLAUDE) claudeRequest(req, imageB64)
        else openAiRequest(req, imageB64)

        val call = http.newCall(request)
        current = call
        var stop: String? = null
        call.execute().use { resp ->
            if (!resp.isSuccessful) throw IOException(errorMessage(resp.code, resp.body?.string()))
            val source = resp.body?.source() ?: throw IOException("Empty response")
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data.isEmpty() || data == "[DONE]") continue
                val json = JSONObject(data)
                val s = if (provider == ApiProvider.CLAUDE) readClaude(json, onDelta) else readOpenAi(json, onDelta)
                if (s != null) stop = s
            }
        }
        return LlmResult(stop)
    }

    // ---- Claude Messages API ----

    private fun claudeRequest(req: LlmRequest, imageB64: String?): Request {
        val content = JSONArray()
        if (imageB64 != null) {
            content.put(
                JSONObject().put("type", "image").put(
                    "source", JSONObject()
                        .put("type", "base64")
                        .put("media_type", "image/jpeg")
                        .put("data", imageB64)
                )
            )
        }
        content.put(JSONObject().put("type", "text").put("text", userText(req)))

        val model = settings.apiModel
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", 16000)
            .put("stream", true)
            .put("system", DEFAULT_SYSTEM_PROMPT)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))

        val builder = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", settings.claudeKey)
            .header("anthropic-version", "2023-06-01")

        // Server-side refusal fallback: re-runs on another model if a safety classifier declines.
        if (SUPPORTS_FALLBACK.any { model.startsWith(it) }) {
            body.put("fallbacks", "default")
            builder.header("anthropic-beta", "server-side-fallback-2026-07-01")
        }
        return builder.post(body.toString().toRequestBody(JSON)).build()
    }

    private fun readClaude(json: JSONObject, onDelta: (String) -> Unit): String? {
        when (json.optString("type")) {
            "content_block_delta" -> {
                val delta = json.optJSONObject("delta")
                if (delta != null && delta.optString("type") == "text_delta") onDelta(delta.optString("text"))
            }
            "message_delta" -> {
                val stop = json.optJSONObject("delta")?.optString("stop_reason").orEmpty()
                if (stop.isNotEmpty()) return stop
            }
            "error" -> throw IOException(json.optJSONObject("error")?.optString("message") ?: "Stream error")
        }
        return null
    }

    // ---- OpenAI-compatible chat completions ----

    private fun openAiRequest(req: LlmRequest, imageB64: String?): Request {
        val userContent: Any = if (imageB64 == null) userText(req) else JSONArray()
            .put(JSONObject().put("type", "text").put("text", userText(req)))
            .put(
                JSONObject().put("type", "image_url").put(
                    "image_url", JSONObject().put("url", "data:image/jpeg;base64,$imageB64")
                )
            )
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", DEFAULT_SYSTEM_PROMPT))
            .put(JSONObject().put("role", "user").put("content", userContent))
        val body = JSONObject()
            .put("model", settings.apiModel)
            .put("stream", true)
            .put("messages", messages)
        val url = settings.openaiBaseUrl.trimEnd('/') + "/chat/completions"
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${settings.openaiKey}")
            .post(body.toString().toRequestBody(JSON))
            .build()
    }

    private fun readOpenAi(json: JSONObject, onDelta: (String) -> Unit): String? {
        json.optJSONObject("error")?.let { throw IOException(it.optString("message", "Stream error")) }
        val choices = json.optJSONArray("choices") ?: return null
        if (choices.length() == 0) return null
        val choice = choices.getJSONObject(0)
        val delta = choice.optJSONObject("delta")
        if (delta != null && !delta.isNull("content")) onDelta(delta.optString("content"))
        return choice.optString("finish_reason").takeIf { it.isNotEmpty() && it != "null" }
    }

    // ---- helpers ----

    private fun userText(req: LlmRequest): String {
        val prompt = req.prompt.trim()
        return when {
            req.text != null && prompt.isNotEmpty() -> "Text:\n\"\"\"\n${req.text}\n\"\"\"\n\n$prompt"
            req.text != null -> req.text
            prompt.isNotEmpty() -> prompt
            else -> "Describe what is in this screenshot."
        }
    }

    /** JPEG, longest side capped at 1568 px, base64 without line breaks. */
    private fun encodeImage(file: File): String {
        var bmp = BitmapFactory.decodeFile(file.path) ?: throw IOException("Could not read the screenshot")
        val longest = max(bmp.width, bmp.height)
        if (longest > MAX_IMAGE_SIDE) {
            val s = MAX_IMAGE_SIDE.toFloat() / longest
            bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt(), (bmp.height * s).toInt(), true)
        }
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun errorMessage(code: Int, body: String?): String {
        val detail = runCatching {
            JSONObject(body ?: "").optJSONObject("error")?.optString("message")
        }.getOrNull()
        return when {
            code == 401 -> "Invalid API key (401). Check it in AskAI settings."
            !detail.isNullOrBlank() -> "Error $code: $detail"
            else -> "Error $code"
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private const val MAX_IMAGE_SIDE = 1568
        private val SUPPORTS_FALLBACK = listOf("claude-opus-5", "claude-fable-5", "claude-sonnet-5-5")
    }
}
