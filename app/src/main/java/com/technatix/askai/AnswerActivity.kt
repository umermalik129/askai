package com.technatix.askai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Bottom-sheet style popup that streams the API answer and closes once the user copies it. */
class AnswerActivity : AppCompatActivity() {

    private lateinit var settings: Settings
    private lateinit var client: LlmClient
    private lateinit var responseView: TextView
    private lateinit var scroll: NestedScrollView
    private lateinit var progress: LinearProgressIndicator
    private lateinit var copyBtn: MaterialButton
    private lateinit var retryBtn: MaterialButton

    private var result = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_answer)
        settings = Settings(this)
        client = LlmClient(settings)
        sizeWindow()

        responseView = findViewById(R.id.responseText)
        scroll = findViewById(R.id.responseScroll)
        progress = findViewById(R.id.progress)
        copyBtn = findViewById(R.id.copyBtn)
        retryBtn = findViewById(R.id.retryBtn)

        findViewById<TextView>(R.id.promptLabel).text =
            intent.getStringExtra(EXTRA_LABEL)?.takeIf { it.isNotBlank() } ?: "AskAI"
        findViewById<TextView>(R.id.providerLabel).text =
            "${settings.apiProvider.label} · ${settings.apiModel}"
        findViewById<View>(R.id.closeBtn).setOnClickListener { finish() }
        retryBtn.setOnClickListener { run() }
        copyBtn.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("AskAI answer", result))
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
            finish()
        }

        run()
    }

    private fun request() = LlmRequest(
        prompt = intent.getStringExtra(EXTRA_PROMPT).orEmpty(),
        text = intent.getStringExtra(EXTRA_TEXT),
        image = intent.getStringExtra(EXTRA_IMAGE)?.let { File(it) }
    )

    private fun run() {
        progress.visibility = View.VISIBLE
        retryBtn.visibility = View.GONE
        copyBtn.isEnabled = false
        show("")

        val sb = StringBuilder()
        lifecycleScope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    client.stream(request()) { delta ->
                        sb.append(delta)
                        val snap = sb.toString()
                        runOnUiThread { show(snap) }
                    }
                }
                result = sb.toString().trim()
                when {
                    res.stopReason == "refusal" ->
                        show(result + (if (result.isEmpty()) "" else "\n\n") + "The request was declined by the provider's safety filter.")
                    result.isEmpty() -> show("(empty response)")
                    else -> {
                        show(result)
                        copyBtn.isEnabled = true
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val partial = sb.toString()
                show((if (partial.isNotEmpty()) "$partial\n\n" else "") + "⚠ " + (e.message ?: e.javaClass.simpleName))
                retryBtn.visibility = View.VISIBLE
            } finally {
                progress.visibility = View.GONE
            }
        }
    }

    private fun show(text: String) {
        responseView.text = text
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun sizeWindow() {
        val h = resources.displayMetrics.heightPixels
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, (h * 0.75).toInt())
        window.setGravity(Gravity.BOTTOM)
    }

    override fun onDestroy() {
        client.cancel()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_IMAGE = "image"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_PROMPT = "prompt"

        fun forText(ctx: Context, text: String, prompt: Prompt?): Intent =
            base(ctx, prompt).putExtra(EXTRA_TEXT, text)

        fun forImage(ctx: Context, image: File, prompt: Prompt?): Intent =
            base(ctx, prompt).putExtra(EXTRA_IMAGE, image.absolutePath)

        private fun base(ctx: Context, prompt: Prompt?) = Intent(ctx, AnswerActivity::class.java)
            .putExtra(EXTRA_LABEL, prompt?.label)
            .putExtra(EXTRA_PROMPT, prompt?.text)
    }
}
