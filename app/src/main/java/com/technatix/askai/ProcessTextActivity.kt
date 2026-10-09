package com.technatix.askai

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/** "AskAI" entry in the text-selection menu: forwards the selected text to the Claude app. */
class ProcessTextActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.trim().orEmpty()
        if (text.isEmpty()) {
            Toast.makeText(this, "Nothing selected", Toast.LENGTH_SHORT).show()
        } else {
            ClaudeApp.sendText(this, text)
        }
        finish()
    }
}
