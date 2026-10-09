package com.technatix.askai

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private val claudeAlias get() = ComponentName(this, "com.technatix.askai.ClaudeAlias")
    private val chatGptAlias get() = ComponentName(this, "com.technatix.askai.ChatGptAlias")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val s = Settings(this)

        val rbClaude = findViewById<RadioButton>(R.id.rbClaude)
        val rbOpenai = findViewById<RadioButton>(R.id.rbOpenai)
        val keyClaude = findViewById<EditText>(R.id.etKeyClaude)
        val modelClaude = findViewById<EditText>(R.id.etModelClaude)
        val keyOpenai = findViewById<EditText>(R.id.etKeyOpenai)
        val modelOpenai = findViewById<EditText>(R.id.etModelOpenai)
        val baseUrl = findViewById<EditText>(R.id.etBaseUrl)
        val system = findViewById<EditText>(R.id.etSystem)
        val presets = findViewById<EditText>(R.id.etPresets)
        val cbClaude = findViewById<CheckBox>(R.id.cbClaudeEntry)
        val cbOpenai = findViewById<CheckBox>(R.id.cbOpenaiEntry)

        rbClaude.isChecked = s.defaultProvider == Provider.CLAUDE
        rbOpenai.isChecked = s.defaultProvider == Provider.OPENAI
        keyClaude.setText(s.claudeKey)
        modelClaude.setText(s.claudeModel)
        keyOpenai.setText(s.openaiKey)
        modelOpenai.setText(s.openaiModel)
        baseUrl.setText(s.openaiBaseUrl)
        system.setText(s.systemPrompt)
        presets.setText(s.presetsText)
        cbClaude.isChecked = isEnabled(claudeAlias)
        cbOpenai.isChecked = isEnabled(chatGptAlias)

        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener {
            s.defaultProvider = if (rbOpenai.isChecked) Provider.OPENAI else Provider.CLAUDE
            s.claudeKey = keyClaude.text.toString()
            s.claudeModel = modelClaude.text.toString().ifBlank { "claude-sonnet-5-5" }
            s.openaiKey = keyOpenai.text.toString()
            s.openaiModel = modelOpenai.text.toString().ifBlank { "gpt-4o-mini" }
            s.openaiBaseUrl = baseUrl.text.toString().ifBlank { "https://api.openai.com/v1" }
            s.systemPrompt = system.text.toString().ifBlank { DEFAULT_SYSTEM_PROMPT }
            s.presetsText = presets.text.toString().ifBlank { DEFAULT_PRESETS }
            setEnabled(claudeAlias, cbClaude.isChecked)
            setEnabled(chatGptAlias, cbOpenai.isChecked)
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isEnabled(c: ComponentName) =
        packageManager.getComponentEnabledSetting(c) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    private fun setEnabled(c: ComponentName, on: Boolean) {
        packageManager.setComponentEnabledSetting(
            c,
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }
}
