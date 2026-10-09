package com.technatix.askai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

/** Full-screen view of the captured frame; adjust the box, then send that area to Claude. */
class CropActivity : AppCompatActivity() {

    private lateinit var crop: CropView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop)

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
        findViewById<MaterialButton>(R.id.sendBtn).setOnClickListener { chooseMessageAndSend() }
    }

    private fun chooseMessageAndSend() {
        val prompts = Settings(this).prompts()
        if (prompts.size <= 1) {
            send(prompts.firstOrNull())
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Send with message")
            .setItems(prompts.toTypedArray()) { _, i -> send(prompts[i]) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun send(message: String?) {
        val burnIn = !message.isNullOrBlank() && Settings(this).burnIn
        var out = crop.selectedBitmap()
        if (burnIn) out = Caption.apply(out, message!!)
        val file = File(CaptureService.captureDir(this), "askai_${System.currentTimeMillis()}.png")
        file.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        // When the message is in the image there is nothing left to paste.
        ClaudeApp.sendImage(this, uri, if (burnIn) null else message)
        finish()
    }

    companion object {
        const val EXTRA_PATH = "path"
    }
}
