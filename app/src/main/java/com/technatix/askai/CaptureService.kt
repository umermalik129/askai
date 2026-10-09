package com.technatix.askai

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.IntentCompat
import java.io.File

/** Foreground service (required for MediaProjection) that captures a single screen frame to a PNG. */
class CaptureService : Service() {

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var done = false
    private var bars = Rect()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        val code = intent?.getIntExtra(EXTRA_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_DATA, Intent::class.java) }
        bars = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_BARS, Rect::class.java) } ?: Rect()
        if (code != Activity.RESULT_OK || data == null) {
            finish(null)
            return START_NOT_STICKY
        }
        try {
            capture(code, data)
        } catch (e: Exception) {
            Log.e(TAG, "capture failed", e)
            finish(null)
        }
        return START_NOT_STICKY
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Screen capture", NotificationManager.IMPORTANCE_LOW)
        )
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_capture)
            .setContentTitle("Capturing screen")
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0
        ServiceCompat.startForeground(this, NOTIF_ID, n, type)
    }

    private fun capture(code: Int, data: Intent) {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val mp = mpm.getMediaProjection(code, data) ?: throw IllegalStateException("No projection")
        projection = mp

        val ht = HandlerThread("askai-capture").also { it.start() }
        thread = ht
        val handler = Handler(ht.looper)

        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                if (!done) finish(null)
            }
        }, handler)

        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY).getRealMetrics(dm)
        val w = dm.widthPixels
        val h = dm.heightPixels

        val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        reader = r
        r.setOnImageAvailableListener({ rd ->
            val img = rd.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                if (done) return@setOnImageAvailableListener
                val bmp = toBitmap(img, w, h)
                finish(save(bmp))
            } catch (e: Exception) {
                Log.e(TAG, "frame failed", e)
                finish(null)
            } finally {
                img.close()
            }
        }, handler)

        display = mp.createVirtualDisplay(
            "AskAI", w, h, dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            r.surface, null, handler
        )

        // Safety net: give up if no frame arrives.
        handler.postDelayed({ if (!done) finish(null) }, 4000)
    }

    private fun toBitmap(img: Image, w: Int, h: Int): Bitmap {
        val plane = img.planes[0]
        val pixelStride = plane.pixelStride
        val rowPadding = (plane.rowStride - pixelStride * w) / pixelStride
        val bmp = Bitmap.createBitmap(w + rowPadding, h, Bitmap.Config.ARGB_8888)
        bmp.copyPixelsFromBuffer(plane.buffer)
        val full = if (rowPadding == 0) bmp else Bitmap.createBitmap(bmp, 0, 0, w, h)
        return stripBars(full)
    }

    /** Cuts off the status bar, navigation bar and cutout so only app content remains. */
    private fun stripBars(bmp: Bitmap): Bitmap {
        val w = bmp.width - bars.left - bars.right
        val h = bmp.height - bars.top - bars.bottom
        if (w <= 0 || h <= 0 || (w == bmp.width && h == bmp.height)) return bmp
        return Bitmap.createBitmap(bmp, bars.left, bars.top, w, h)
    }

    private fun save(bmp: Bitmap): File {
        val dir = captureDir(this)
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "screen.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    @Synchronized
    private fun finish(file: File?) {
        if (done) return
        done = true
        display?.release()
        reader?.close()
        projection?.stop()
        thread?.quitSafely()
        display = null
        reader = null
        projection = null
        thread = null
        Handler(Looper.getMainLooper()).post {
            listener?.invoke(file)
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    companion object {
        private const val TAG = "AskAI.Capture"
        private const val CHANNEL = "capture"
        private const val NOTIF_ID = 1
        private const val EXTRA_CODE = "code"
        private const val EXTRA_DATA = "data"
        private const val EXTRA_BARS = "bars"

        /** Receives the captured PNG (or null on failure). Called on the main thread. */
        @Volatile
        var listener: ((File?) -> Unit)? = null

        /** [bars] = pixels occupied by system bars on each side; they are cropped away. */
        fun intent(ctx: Context, code: Int, data: Intent, bars: Rect): Intent =
            Intent(ctx, CaptureService::class.java)
                .putExtra(EXTRA_CODE, code)
                .putExtra(EXTRA_DATA, data)
                .putExtra(EXTRA_BARS, bars)

        fun captureDir(ctx: Context): File = File(ctx.cacheDir, "captures").apply { mkdirs() }
    }
}
