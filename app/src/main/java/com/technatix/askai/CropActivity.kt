package com.technatix.askai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import java.io.File

/** Full-screen view of the captured frame; adjust the box, then send that area on. */
class CropActivity : AppCompatActivity() {

    private lateinit var crop: CropView
    private lateinit var settings: Settings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop)
        settings = Settings(this)

        val path = intent.getStringExtra(EXTRA_PATH)
        val bmp = path?.let { BitmapFactory.decodeFile(it) }
        if (bmp == null) {
            Toast.makeText(this, "Screenshot not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        crop = findViewById(R.id.cropView)
        crop.bitmap = bmp

        findViewById<MaterialButton>(R.id.cancelBtn).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.sendBtn).apply {
            text = when (settings.mode) {
                SendMode.OPEN_APP -> "Send to ${settings.app.label}"
                SendMode.API -> "Ask ${settings.apiProvider.label.substringBefore(' ')}"
            }
            setOnClickListener { onSend() }
        }
    }

    private fun onSend() {
        if (settings.mode == SendMode.API && !settings.apiConfigured()) {
            Toast.makeText(this, "Add an API key in AskAI settings first", Toast.LENGTH_LONG).show()
            return
        }
        pickPrompt(settings.prompts, onCancel = {}) { prompt -> send(prompt) }
    }

    private fun send(prompt: Prompt?) {
        val cropped = crop.selectedBitmap()
        when (settings.mode) {
            SendMode.OPEN_APP -> {
                val message = prompt?.text
                val burnIn = !message.isNullOrBlank() && settings.burnIn
                val out = if (burnIn) Caption.apply(cropped, message!!) else cropped
                val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", save(out))
                // When the message is in the image there is nothing left to paste.
                settings.app.sendImage(this, uri, if (burnIn) null else message)
            }
            SendMode.API -> startActivity(AnswerActivity.forImage(this, save(cropped), prompt))
        }
        finish()
    }

    private fun save(bmp: Bitmap): File {
        val file = File(CaptureService.captureDir(this), "askai_${System.currentTimeMillis()}.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    companion object {
        const val EXTRA_PATH = "path"
    }
}
