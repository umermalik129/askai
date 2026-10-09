package com.technatix.askai

import android.app.Activity
import android.content.Intent
import android.graphics.Rect
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Invisible activity: asks for screen-capture consent, lets [CaptureService] grab one frame,
 * then opens [CropActivity] with the result.
 */
class CaptureActivity : Activity() {

    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val consent = if (Build.VERSION.SDK_INT >= 34) {
            // Only offer "entire screen"; skips the single-app picker.
            mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
            mpm.createScreenCaptureIntent()
        }
        @Suppress("DEPRECATION")
        startActivityForResult(consent, REQ_CAPTURE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_CAPTURE) return
        if (resultCode != RESULT_OK || data == null) {
            finish()
            return
        }
        CaptureService.listener = { file ->
            main.post {
                CaptureService.listener = null
                if (file != null) {
                    startActivity(
                        Intent(this, CropActivity::class.java)
                            .putExtra(CropActivity.EXTRA_PATH, file.absolutePath)
                    )
                } else {
                    Toast.makeText(this, "Screenshot failed", Toast.LENGTH_SHORT).show()
                }
                finish()
            }
        }
        val bars = systemBarInsets()
        // Let the consent dialog fade out before the frame is grabbed.
        main.postDelayed({
            ContextCompat.startForegroundService(
                this, CaptureService.intent(this, resultCode, data, bars)
            )
        }, 400)
    }

    /** Pixels taken by the status bar, navigation bar and display cutout on each side. */
    private fun systemBarInsets(): Rect {
        val insets = ViewCompat.getRootWindowInsets(window.decorView) ?: return Rect()
        val i = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        return Rect(i.left, i.top, i.right, i.bottom)
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        private const val REQ_CAPTURE = 41
    }
}
