package com.pixelbuddy.ai.capture

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.pixelbuddy.ai.MainActivity
import com.pixelbuddy.ai.R
import com.pixelbuddy.ai.overlay.AiMood
import com.pixelbuddy.ai.overlay.PixelOverlayService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class ScreenCaptureService : Service() {
    private val workScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingPrompt = AtomicReference<String?>(null)
    private val analyzing = AtomicBoolean(false)
    private val captureThread = HandlerThread("pixelbuddy-screen-frames")
    private val mainHandler = Handler(Looper.getMainLooper())
    private var deferredAnalysis: Runnable? = null

    private lateinit var frameHandler: Handler
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var densityDpi = 0

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            PixelOverlayService.publish("Screen sharing stopped. Start it again to analyze a screen.", AiMood.SAD)
            stopSelf()
        }

        override fun onCapturedContentResize(width: Int, height: Int) {
            if (isSessionActive && width > 0 && height > 0) resizeSurface(width, height)
        }
    }

    private val frameListener = ImageReader.OnImageAvailableListener { reader ->
        val frame = try {
            reader.acquireLatestImage()
        } catch (_: IllegalStateException) {
            null
        } ?: return@OnImageAvailableListener

        frame.use { image ->
            if (!isSessionActive || analyzing.get()) return@use
            val prompt = pendingPrompt.getAndSet(null) ?: return@use
            if (!analyzing.compareAndSet(false, true)) {
                pendingPrompt.compareAndSet(null, prompt)
                return@use
            }

            val jpeg = try {
                ScreenFrameProcessor.toJpeg(image)
            } catch (_: Exception) {
                analyzing.set(false)
                if (isSessionActive) {
                    PixelOverlayService.publish("I could not read that screen. Please try again.", AiMood.SAD)
                }
                return@use
            }

            workScope.launch {
                try {
                    val key = VisionCredentialStore(this@ScreenCaptureService).read()
                    if (key.isNullOrBlank()) {
                        if (isSessionActive) {
                            PixelOverlayService.publish("Save your Gemini API key in PixelBuddy settings first.", AiMood.THINKING)
                        }
                        return@launch
                    }

                    val advice = GeminiVisionClient().analyze(jpeg, prompt, key)
                    if (isSessionActive) PixelOverlayService.publish(advice.text, advice.mood)
                } catch (_: CancellationException) {
                    // Stopping sharing cancels pending analysis without delivering stale advice.
                } catch (error: Exception) {
                    if (isSessionActive) {
                        val message = (error as? IOException)?.message
                            ?: "Screen analysis failed. Check your connection and try again."
                        PixelOverlayService.publish(message.take(180), AiMood.SAD)
                    }
                } finally {
                    jpeg.fill(0)
                    analyzing.set(false)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        captureThread.start()
        frameHandler = Handler(captureThread.looper)
        makeChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (isSessionActive) {
                    if (intent.getBooleanExtra(EXTRA_ANALYZE_ON_START, false)) scheduleInitialAnalysis()
                    return START_NOT_STICKY
                }

                val code = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val permission = permissionIntent(intent)
                if (code != Activity.RESULT_OK || permission == null) {
                    stopSelf(startId)
                    return START_NOT_STICKY
                }

                try {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        notification(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    )
                    startProjection(code, permission)
                    if (intent.getBooleanExtra(EXTRA_ANALYZE_ON_START, false)) scheduleInitialAnalysis()
                } catch (_: Exception) {
                    PixelOverlayService.publish("Screen sharing could not start. Please grant permission again.", AiMood.SAD)
                    stopSelf(startId)
                }
            }

            ACTION_ANALYZE -> {
                if (isSessionActive) {
                    queueAnalysis(intent.getStringExtra(EXTRA_PROMPT).orEmpty())
                } else {
                    PixelOverlayService.publish("Enable screen sharing to analyze this screen.", AiMood.THINKING)
                }
            }

            ACTION_STOP -> stopSelf()
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startProjection(code: Int, permission: Intent) {
        val manager = getSystemService(MediaProjectionManager::class.java)
        val session = manager.getMediaProjection(code, permission)
            ?: throw IllegalStateException("Permission did not create a capture session")

        projection = session
        session.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))
        densityDpi = resources.configuration.densityDpi
        val dimensions = displaySize()
        val reader = newReader(dimensions.x, dimensions.y)
        imageReader = reader

        virtualDisplay = session.createVirtualDisplay(
            "PixelBuddy screen awareness",
            dimensions.x,
            dimensions.y,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            null
        ) ?: throw IllegalStateException("Could not create a virtual display")

        isSessionActive = true
    }

    private fun displaySize(): Point {
        val windowManager = getSystemService(WindowManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            return Point(bounds.width(), bounds.height())
        }

        @Suppress("DEPRECATION")
        val display = windowManager.defaultDisplay
        return Point().also { display.getRealSize(it) }
    }

    private fun newReader(width: Int, height: Int): ImageReader =
        ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3).apply {
            setOnImageAvailableListener(frameListener, frameHandler)
        }

    private fun resizeSurface(width: Int, height: Int) {
        val display = virtualDisplay ?: return
        if (imageReader?.width == width && imageReader?.height == height) return

        val replacement = newReader(width, height)
        try {
            display.resize(width, height, densityDpi)
            display.setSurface(replacement.surface)
            imageReader?.setOnImageAvailableListener(null, null)
            imageReader?.close()
            imageReader = replacement
        } catch (_: Exception) {
            replacement.close()
            PixelOverlayService.publish("The screen size changed. Restart screen sharing to continue.", AiMood.SAD)
            stopSelf()
        }
    }

    private fun queueAnalysis(request: String) {
        if (VisionCredentialStore(this).hasKey()) {
            pendingPrompt.set(request.take(500).ifBlank { DEFAULT_PROMPT })
            PixelOverlayService.publish("Looking at your screen now...", AiMood.THINKING)
        } else {
            PixelOverlayService.publish("Save your Gemini API key in PixelBuddy settings first.", AiMood.THINKING)
        }
    }

    private fun scheduleInitialAnalysis() {
        deferredAnalysis?.let { mainHandler.removeCallbacks(it) }
        val action = Runnable {
            deferredAnalysis = null
            if (isSessionActive) queueAnalysis(DEFAULT_PROMPT)
        }
        deferredAnalysis = action
        mainHandler.postDelayed(action, 1100L)
    }

    @Suppress("DEPRECATION")
    private fun permissionIntent(source: Intent): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            source.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            source.getParcelableExtra(EXTRA_RESULT_DATA)
        }
    }

    override fun onDestroy() {
        isSessionActive = false
        deferredAnalysis?.let { mainHandler.removeCallbacks(it) }
        deferredAnalysis = null
        pendingPrompt.set(null)
        workScope.cancel()
        projection?.unregisterCallback(projectionCallback)
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        imageReader = null
        projection?.stop()
        projection = null
        frameHandler.removeCallbacksAndMessages(null)
        captureThread.quitSafely()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun makeChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Screen sharing", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).apply { action = MainActivity.ACTION_OPEN_SETTINGS },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this,
            2,
            Intent(this, ScreenCaptureService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_status)
            .setContentTitle("PixelBuddy is sharing your screen")
            .setContentText("Frames are analyzed only when requested")
            .setContentIntent(open)
            .addAction(R.drawable.ic_close, "Stop sharing", stop)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "pixelbuddy_screen_sharing"
        private const val NOTIFICATION_ID = 1102
        private const val ACTION_START = "com.pixelbuddy.ai.capture.START"
        private const val ACTION_ANALYZE = "com.pixelbuddy.ai.capture.ANALYZE"
        private const val ACTION_STOP = "com.pixelbuddy.ai.capture.STOP"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"
        private const val EXTRA_ANALYZE_ON_START = "analyze_on_start"
        private const val EXTRA_PROMPT = "prompt"
        private const val DEFAULT_PROMPT =
            "Look at this screen and suggest the best next action. For a game, give a specific gameplay tactic."

        @Volatile
        var isSessionActive = false
            private set

        fun startIntent(context: Context, resultCode: Int, data: Intent, analyzeOnStart: Boolean): Intent =
            Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
                putExtra(EXTRA_ANALYZE_ON_START, analyzeOnStart)
            }

        fun requestAnalysis(context: Context, prompt: String = DEFAULT_PROMPT) {
            context.startService(Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_ANALYZE
                putExtra(EXTRA_PROMPT, prompt)
            })
        }

        fun stopIntent(context: Context): Intent =
            Intent(context, ScreenCaptureService::class.java).apply { action = ACTION_STOP }
    }
}