package com.technatix.askai

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.WindowInsets
import android.view.WindowManager
import java.io.File

/**
 * Makes AskAI selectable as the phone's digital assistant, so the assistant gesture (corner swipe
 * or long-press Home) starts a capture. Nothing voice-related actually happens.
 */
class AssistService : VoiceInteractionService()

class AssistSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = AssistSession(this)
}

/**
 * One assistant invocation. When the system hands over a screenshot (user allowed "Use
 * screenshot" for the assistant) it goes straight to the crop screen with no consent dialog;
 * otherwise the normal MediaProjection flow runs.
 */
class AssistSession(ctx: Context) : VoiceInteractionSession(ctx) {

    private val main = Handler(Looper.getMainLooper())
    private var handled = false
    private val fallback = Runnable {
        if (!handled) {
            handled = true
            launch(Intent(context, CaptureActivity::class.java))
        }
    }

    init {
        setTheme(R.style.Theme_AskAI_Invisible)
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        handled = false
        // The screenshot, if any, arrives right after onShow. Give it a moment, then fall back.
        main.postDelayed(fallback, 600)
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        if (screenshot == null || handled) return
        handled = true
        main.removeCallbacks(fallback)
        try {
            val file = File(CaptureService.captureDir(context), "screen.png")
            CaptureService.stripBars(screenshot, systemBars()).let { bmp ->
                file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            launch(Intent(context, CropActivity::class.java).putExtra(CropActivity.EXTRA_PATH, file.absolutePath))
        } catch (e: Exception) {
            Log.e(TAG, "assist screenshot failed, falling back", e)
            launch(Intent(context, CaptureActivity::class.java))
        }
    }

    override fun onHide() {
        main.removeCallbacks(fallback)
        super.onHide()
    }

    private fun launch(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startAssistantActivity(intent)
        hide()
    }

    /** Pixels taken by status bar, navigation bar and cutout; zero rect if unknown. */
    private fun systemBars(): Rect {
        val insets = window?.window?.decorView?.rootWindowInsets
            ?: if (Build.VERSION.SDK_INT >= 30)
                context.getSystemService(WindowManager::class.java).currentWindowMetrics.windowInsets
            else null
        if (insets == null) return Rect()
        return if (Build.VERSION.SDK_INT >= 30) {
            val i = insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            Rect(i.left, i.top, i.right, i.bottom)
        } else {
            @Suppress("DEPRECATION")
            Rect(insets.stableInsetLeft, insets.stableInsetTop, insets.stableInsetRight, insets.stableInsetBottom)
        }
    }

    companion object {
        private const val TAG = "AskAI.Assist"
    }
}

/** Required by the voice-interaction manifest contract; AskAI does no speech recognition. */
class AssistRecognitionService : RecognitionService() {
    override fun onStartListening(intent: Intent?, listener: Callback?) {
        runCatching { listener?.error(SpeechRecognizer.ERROR_CLIENT) }
    }

    override fun onCancel(listener: Callback?) = Unit
    override fun onStopListening(listener: Callback?) = Unit
}
