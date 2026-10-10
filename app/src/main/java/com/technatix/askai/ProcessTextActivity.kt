package com.technatix.askai

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * "AskAI" entry in the text-selection menu. Lets the user pick a prompt, then either opens
 * the chosen AI app with the text or asks the configured API and shows the answer.
 */
class ProcessTextActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.trim().orEmpty()
        if (text.isEmpty()) {
            Toast.makeText(this, "Nothing selected", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val settings = Settings(this)
        if (settings.mode == SendMode.API && !settings.apiConfigured()) {
            Toast.makeText(this, "Add an API key in AskAI settings first", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        pickPrompt(settings.prompts, onCancel = { finish() }) { prompt -> send(settings, text, prompt) }
    }

    private fun send(settings: Settings, text: String, prompt: Prompt?) {
        when (settings.mode) {
            SendMode.OPEN_APP -> {
                val body = if (prompt == null) text else "$text\n\n${prompt.text}"
                settings.app.sendText(this, body)
            }
            SendMode.API -> startActivity(AnswerActivity.forText(this, text, prompt))
        }
        finish()
    }
}
