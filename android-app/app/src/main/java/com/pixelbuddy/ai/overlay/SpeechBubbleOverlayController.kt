package com.pixelbuddy.ai.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import com.pixelbuddy.ai.R

class SpeechBubbleOverlayController(
    context: Context,
    private val windowManager: WindowManager
) {
    private val appContext = context.applicationContext
    private var bubble: MangaSpeechBubbleView? = null
    private var params: WindowManager.LayoutParams? = null
    private var lastMessage = ""
    private var lastMood = AiMood.THINKING

    fun showNear(smileyX: Int, smileyY: Int, message: String, mood: AiMood) {
        lastMessage = message
        lastMood = mood
        if (bubble == null) {
            val view = MangaSpeechBubbleView(appContext)
            val layout = WindowManager.LayoutParams(
                appContext.resources.getDimensionPixelSize(R.dimen.bubble_width),
                WindowManager.LayoutParams.WRAP_CONTENT,
                OverlayWindowBounds.windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.START }
            bubble = view
            params = layout
            position(smileyX, smileyY)
            windowManager.addView(view, layout)
        } else {
            position(smileyX, smileyY)
            val view = bubble ?: return
            val layout = params ?: return
            windowManager.updateViewLayout(view, layout)
        }
    }

    fun repositionNear(smileyX: Int, smileyY: Int) {
        if (bubble == null) return
        position(smileyX, smileyY)
        val view = bubble ?: return
        val layout = params ?: return
        windowManager.updateViewLayout(view, layout)
    }

    private fun position(smileyX: Int, smileyY: Int) {
        val layout = params ?: return
        val width = appContext.resources.getDimensionPixelSize(R.dimen.bubble_width)
        val smiley = appContext.resources.getDimensionPixelSize(R.dimen.smiley_size)
        val gap = appContext.resources.getDimensionPixelSize(R.dimen.overlay_gap)
        val screen = OverlayWindowBounds.size(windowManager)
        val fitsOnLeft = smileyX >= width + gap

        layout.x = (if (fitsOnLeft) smileyX - width - gap else smileyX + smiley + gap)
            .coerceIn(0, (screen.x - width).coerceAtLeast(0))
        val estimatedHeight = (220f * appContext.resources.displayMetrics.density).toInt()
        layout.y = (smileyY - gap).coerceIn(0, (screen.y - estimatedHeight).coerceAtLeast(0))
        bubble?.showMessage(lastMessage, lastMood, fitsOnLeft)
    }

    fun hide() {
        bubble?.let { view ->
            if (view.parent != null) windowManager.removeView(view)
        }
        bubble = null
        params = null
    }
}