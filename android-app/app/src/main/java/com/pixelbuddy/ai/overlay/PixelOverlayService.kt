package com.pixelbuddy.ai.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.pixelbuddy.ai.MainActivity
import com.pixelbuddy.ai.R
import com.pixelbuddy.ai.capture.ScreenCaptureService
import java.lang.ref.WeakReference
import kotlin.math.abs

class PixelOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var smileyParams: WindowManager.LayoutParams
    private lateinit var menu: OverlayMenuController
    private lateinit var tasks: TaskOverlayController
    private lateinit var bubble: SpeechBubbleOverlayController

    private var smiley: PixelSmileyView? = null
    private val smileyPrefs by lazy { SmileyPreferences(this) }
    private val mainHandler = Handler(Looper.getMainLooper())

    private val fade = Runnable {
        smiley?.animate()?.alpha(0.46f)?.setDuration(420L)?.start()
    }
    private val hideBubble = Runnable {
        if (::bubble.isInitialized) bubble.hide()
    }
    private val longPress = Runnable {
        longPressed = true
        smiley?.let { view ->
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            view.playReaction()
        }
    }

    // Keep a strong reference: SharedPreferences only holds listeners weakly.
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == SmileyPreferences.KEY_GENDER) {
            smiley?.let { view ->
                view.gender = smileyPrefs.gender
                view.playReaction()
            }
        }
    }

    private var downX = 0f
    private var downY = 0f
    private var originalX = 0
    private var originalY = 0
    private var dragging = false
    private var longPressed = false

    override fun onCreate() {
        super.onCreate()

        // A service started with startForegroundService() must call startForeground()
        // promptly, even when it is about to stop itself.
        makeChannel()
        startForeground(NOTIFICATION_ID, notification())

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        bubble = SpeechBubbleOverlayController(this, windowManager)
        tasks = TaskOverlayController(this, windowManager, ::analyzeScreen)
        menu = OverlayMenuController(
            this,
            windowManager,
            onClose = { stopSelf() },
            onSettings = {
                startActivity(Intent(this, MainActivity::class.java).apply {
                    action = MainActivity.ACTION_OPEN_SETTINGS
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                })
            },
            onTasks = {
                bubble.hide()
                tasks.toggleNear(smileyParams.x, smileyParams.y)
            },
            onToggleGender = ::toggleGender
        )

        val screen = OverlayWindowBounds.size(windowManager)
        val size = resources.getDimensionPixelSize(R.dimen.smiley_size)
        val margin = (22f * resources.displayMetrics.density).toInt()
        smileyParams = WindowManager.LayoutParams(
            size,
            size,
            OverlayWindowBounds.windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screen.x - size - margin).coerceAtLeast(0)
            y = (screen.y * 0.35f).toInt()
        }

        val view = PixelSmileyView(this).apply {
            gender = smileyPrefs.gender
            isClickable = true
            setOnTouchListener { touched, event -> onSmileyTouch(touched, event) }
        }

        try {
            windowManager.addView(view, smileyParams)
            smiley = view
            current = WeakReference(this)
            smileyPrefs.register(prefListener)
            scheduleFade()
        } catch (_: SecurityException) {
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    private fun onSmileyTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mainHandler.removeCallbacks(fade)
                view.animate().cancel()
                view.alpha = 1f
                downX = event.rawX
                downY = event.rawY
                originalX = smileyParams.x
                originalY = smileyParams.y
                dragging = false
                longPressed = false
                mainHandler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.rawX - downX
                val deltaY = event.rawY - downY
                val slop = ViewConfiguration.get(this).scaledTouchSlop
                if (!dragging && (abs(deltaX) > slop || abs(deltaY) > slop)) {
                    dragging = true
                    mainHandler.removeCallbacks(longPress)
                    menu.dismiss()
                }
                if (dragging) {
                    val screen = OverlayWindowBounds.size(windowManager)
                    smileyParams.x = (originalX + deltaX.toInt())
                        .coerceIn(0, (screen.x - view.width).coerceAtLeast(0))
                    smileyParams.y = (originalY + deltaY.toInt())
                        .coerceIn(0, (screen.y - view.height).coerceAtLeast(0))
                    windowManager.updateViewLayout(view, smileyParams)
                    tasks.repositionNear(smileyParams.x, smileyParams.y)
                    bubble.repositionNear(smileyParams.x, smileyParams.y)
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                mainHandler.removeCallbacks(longPress)
                if (!dragging && !longPressed) {
                    tasks.dismiss()
                    menu.toggle(smileyParams.x, smileyParams.y)
                    view.performClick()
                }
                scheduleFade()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                mainHandler.removeCallbacks(longPress)
                scheduleFade()
                return true
            }
        }
        return false
    }

    private fun toggleGender() {
        smileyPrefs.gender = smileyPrefs.gender.toggled()
    }

    private fun analyzeScreen() {
        if (ScreenCaptureService.isSessionActive) {
            displayMessage("Looking at your screen now...", AiMood.THINKING)
            ScreenCaptureService.requestAnalysis(this)
        } else {
            startActivity(Intent(this, MainActivity::class.java).apply {
                action = MainActivity.ACTION_ANALYZE_FROM_OVERLAY
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
        }
    }

    private fun displayMessage(text: String, mood: AiMood) {
        if (!::bubble.isInitialized || !::smileyParams.isInitialized) return
        menu.dismiss()
        bubble.showNear(smileyParams.x, smileyParams.y, text, mood)
        smiley?.let { view ->
            view.animate().cancel()
            view.alpha = 1f
            view.reactToMood(mood)
        }
        scheduleFade()
        mainHandler.removeCallbacks(hideBubble)
        mainHandler.postDelayed(hideBubble, BUBBLE_VISIBLE_MS)
    }

    private fun scheduleFade() {
        mainHandler.removeCallbacks(fade)
        mainHandler.postDelayed(fade, 3200L)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(fade)
        mainHandler.removeCallbacks(hideBubble)
        mainHandler.removeCallbacks(longPress)
        smileyPrefs.unregister(prefListener)
        if (::menu.isInitialized) menu.dismiss()
        if (::tasks.isInitialized) tasks.dismiss()
        if (::bubble.isInitialized) bubble.hide()
        smiley?.let { view ->
            view.animate().cancel()
            if (view.parent != null) windowManager.removeView(view)
        }
        smiley = null
        current = null
        stopService(Intent(this, ScreenCaptureService::class.java))
        super.onDestroy()
    }

    private fun makeChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Floating assistant",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply { action = MainActivity.ACTION_OPEN_SETTINGS },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_status)
            .setContentTitle("PixelBuddy is floating")
            .setContentText("Tap the smiley to open its controls. Long-press to poke it.")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "pixelbuddy_overlay"
        private const val NOTIFICATION_ID = 1101
        private const val BUBBLE_VISIBLE_MS = 14_000L

        @Volatile
        private var current: WeakReference<PixelOverlayService>? = null

        fun publish(text: String, mood: AiMood) {
            Handler(Looper.getMainLooper()).post {
                current?.get()?.displayMessage(text, mood)
            }
        }

        /** Makes the floating smiley play its gendered reaction (used by the app's preview button). */
        fun playReaction() {
            Handler(Looper.getMainLooper()).post {
                current?.get()?.smiley?.playReaction()
            }
        }
    }
}
