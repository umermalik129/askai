package com.technatix.askai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProcessTextActivity : AppCompatActivity() {

    private lateinit var settings: Settings
    private lateinit var client: LlmClient
    private lateinit var provider: Provider

    private lateinit var selectedView: TextView
    private lateinit var responseView: TextView
    private lateinit var responseScroll: ScrollView
    private lateinit var input: EditText
    private lateinit var sendBtn: MaterialButton
    private lateinit var actionRow: View
    private lateinit var replaceBtn: MaterialButton
    private lateinit var chips: ChipGroup

    private var selectedText = ""
    private var readOnly = true
    private var isProcess = false
    private val history = mutableListOf<Msg>()
    private var busy = false
    private var lastResult = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ask)

        settings = Settings(this)
        client = LlmClient(settings)

        readIntent()
        sizeWindow()
        bindViews()

        selectedView.text = selectedText
        findViewById<TextView>(R.id.providerLabel).text =
            "${provider.label} · ${settings.modelFor(provider)}"

        if (settings.keyFor(provider).isBlank()) {
            showResponse("No ${provider.label} API key yet.\nOpen the AskAI app and add one in settings.")
            sendBtn.isEnabled = false
            chips.visibility = View.GONE
            return
        }

        buildChips()
        sendBtn.setOnClickListener { sendTyped() }
        input.requestFocus()
    }

    private fun readIntent() {
        isProcess = intent.action == Intent.ACTION_PROCESS_TEXT
        selectedText = if (isProcess) {
            intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
        } else {
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
        }
        readOnly = if (isProcess) {
            intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
        } else true

        val cls = intent.component?.className.orEmpty()
        provider = when {
            cls.endsWith("ClaudeAlias") -> Provider.CLAUDE
            cls.endsWith("ChatGptAlias") -> Provider.OPENAI
            else -> settings.defaultProvider
        }
    }

    private fun sizeWindow() {
        val h = resources.displayMetrics.heightPixels
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, (h * 0.78).toInt())
        window.setGravity(Gravity.BOTTOM)
    }

    private fun bindViews() {
        selectedView = findViewById(R.id.selectedText)
        responseView = findViewById(R.id.responseText)
        responseScroll = findViewById(R.id.responseScroll)
        input = findViewById(R.id.questionInput)
        sendBtn = findViewById(R.id.sendBtn)
        actionRow = findViewById(R.id.actionRow)
        replaceBtn = findViewById(R.id.replaceBtn)
        chips = findViewById(R.id.chipGroup)

        findViewById<ImageButton>(R.id.closeBtn).setOnClickListener { finish() }

        var expanded = false
        selectedView.setOnClickListener {
            expanded = !expanded
            selectedView.maxLines = if (expanded) 12 else 3
        }

        findViewById<MaterialButton>(R.id.copyBtn).setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("AskAI", lastResult))
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
        }
        findViewById<MaterialButton>(R.id.shareBtn).setOnClickListener {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, lastResult)
            }
            startActivity(Intent.createChooser(send, "Share"))
        }
        if (readOnly) {
            replaceBtn.visibility = View.GONE
        } else {
            replaceBtn.setOnClickListener {
                setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, lastResult))
                finish()
            }
        }
    }

    private fun buildChips() {
        settings.presets().forEach { preset ->
            val chip = Chip(this).apply {
                text = preset.label
                isCheckable = false
                setOnClickListener { send(preset.prompt) }
            }
            chips.addView(chip)
        }
    }

    private fun sendTyped() {
        val q = input.text.toString().trim()
        if (q.isEmpty()) return
        input.setText("")
        send(q)
    }

    private fun send(question: String) {
        if (busy) return
        hideKeyboard()

        val userContent = if (history.isEmpty()) {
            "Selected text:\n\"\"\"\n$selectedText\n\"\"\"\n\n$question"
        } else question
        history.add(Msg("user", userContent))

        busy = true
        sendBtn.isEnabled = false
        actionRow.visibility = View.GONE
        showResponse("…")

        val sb = StringBuilder()
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    client.stream(provider, history.toList()) { delta ->
                        sb.append(delta)
                        val snap = sb.toString()
                        runOnUiThread { showResponse(snap) }
                    }
                }
                lastResult = sb.toString().trim()
                if (lastResult.isEmpty()) {
                    history.removeAt(history.lastIndex)
                    showResponse("(empty response)")
                } else {
                    history.add(Msg("assistant", lastResult))
                    showResponse(lastResult)
                    actionRow.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                history.removeAt(history.lastIndex)
                val partial = sb.toString()
                showResponse(
                    (if (partial.isNotEmpty()) "$partial\n\n" else "") +
                        "⚠ " + (e.message ?: e.javaClass.simpleName)
                )
            } finally {
                busy = false
                sendBtn.isEnabled = true
            }
        }
    }

    private fun showResponse(text: String) {
        responseView.text = text
        responseScroll.post { responseScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(input.windowToken, 0)
    }

    override fun onDestroy() {
        client.cancel()
        super.onDestroy()
    }
}
