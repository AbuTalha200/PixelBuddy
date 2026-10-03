package com.pixelbuddy.ai.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import com.pixelbuddy.ai.R

class TaskOverlayController(
    context: Context,
    private val windowManager: WindowManager,
    private val onAnalyze: () -> Unit
) {
    private val appContext = context.applicationContext
    private var view: View? = null
    private var params: WindowManager.LayoutParams? = null

    fun toggleNear(smileyX: Int, smileyY: Int) {
        if (view != null) {
            dismiss()
            return
        }

        val panel = LayoutInflater.from(appContext).inflate(R.layout.overlay_tasks_panel, null)
        panel.findViewById<Button>(R.id.analyzeTaskButton).setOnClickListener {
            dismiss()
            onAnalyze()
        }

        val layout = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            OverlayWindowBounds.windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        place(layout, smileyX, smileyY)
        windowManager.addView(panel, layout)
        view = panel
        params = layout
    }

    fun repositionNear(smileyX: Int, smileyY: Int) {
        val panel = view ?: return
        val layout = params ?: return
        place(layout, smileyX, smileyY)
        windowManager.updateViewLayout(panel, layout)
    }

    private fun place(layout: WindowManager.LayoutParams, smileyX: Int, smileyY: Int) {
        val screen = OverlayWindowBounds.size(windowManager)
        val width = (224f * appContext.resources.displayMetrics.density).toInt()
        val height = (160f * appContext.resources.displayMetrics.density).toInt()
        val smiley = appContext.resources.getDimensionPixelSize(R.dimen.smiley_size)
        val gap = appContext.resources.getDimensionPixelSize(R.dimen.overlay_gap)

        val preferred = smileyX - width - gap
        layout.x = (if (preferred >= 0) preferred else smileyX + smiley + gap)
            .coerceIn(0, (screen.x - width).coerceAtLeast(0))
        layout.y = smileyY.coerceIn(0, (screen.y - height).coerceAtLeast(0))
    }

    fun dismiss() {
        view?.let { panel ->
            if (panel.parent != null) windowManager.removeView(panel)
        }
        view = null
        params = null
    }
}