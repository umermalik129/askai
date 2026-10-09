package com.technatix.askai

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** AI apps that text or screenshots can be handed to. Each must also be listed in the manifest <queries>. */
enum class AiApp(val label: String, val pkg: String) {
    CLAUDE("Claude", "com.anthropic.claude"),
    CHATGPT("ChatGPT", "com.openai.chatgpt"),
    GEMINI("Gemini", "com.google.android.apps.bard"),
    PERPLEXITY("Perplexity", "ai.perplexity.app.android"),
    GROK("Grok", "ai.x.grok"),
    COPILOT("Copilot", "com.microsoft.copilot");

    fun isInstalled(ctx: Context): Boolean =
        runCatching { ctx.packageManager.getPackageInfo(pkg, 0) }.isSuccess

    fun sendText(ctx: Context, text: String) = send(ctx, Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    })

    /**
     * Shares the image. Most AI apps ignore share text when an image is attached, so [message]
     * is also put on the clipboard for a quick paste into the chat box.
     */
    fun sendImage(ctx: Context, uri: Uri, message: String? = null) {
        if (!message.isNullOrBlank()) {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("AskAI message", message))
            Toast.makeText(
                ctx, "Message copied. Long-press the chat box in $label and tap Paste.",
                Toast.LENGTH_LONG
            ).show()
        }
        send(ctx, Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            if (!message.isNullOrBlank()) putExtra(Intent.EXTRA_TEXT, message)
            clipData = ClipData.newUri(ctx.contentResolver, "AskAI capture", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    }

    private fun send(ctx: Context, intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!isInstalled(ctx)) {
            Toast.makeText(ctx, "$label app is not installed", Toast.LENGTH_LONG).show()
            openStore(ctx)
            return
        }
        try {
            ctx.startActivity(Intent(intent).setPackage(pkg))
        } catch (e: ActivityNotFoundException) {
            // The app did not accept this content directly; let the user pick a target.
            ctx.startActivity(
                Intent.createChooser(intent, "Send to").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun openStore(ctx: Context) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(market) }.onFailure {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
