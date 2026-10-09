package com.technatix.askai

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/**
 * "AskAI" entry in the text-selection menu: forwards the selected text to the chosen AI app
 * with the saved message appended.
 */
class ProcessTextActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.trim().orEmpty()
        if (text.isEmpty()) {
            Toast.makeText(this, "Nothing selected", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val prompts = Settings(this).prompts()
        if (prompts.size <= 1) {
            send(text, prompts.firstOrNull())
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Send with message")
            .setItems(prompts.toTypedArray()) { _, i -> send(text, prompts[i]) }
            .setNegativeButton("Cancel", null)
            .setOnDismissListener { if (!isFinishing) finish() }
            .show()
    }

    private fun send(text: String, message: String?) {
        val body = if (message.isNullOrBlank()) text else "$text\n\n$message"
        Settings(this).app.sendText(this, body)
        finish()
    }
}
