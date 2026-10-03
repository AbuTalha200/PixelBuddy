package com.pixelbuddy.ai.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import com.pixelbuddy.ai.R

class OverlayMenuController(
    context: Context,
    private val windowManager: WindowManager,
    private val onClose: () -> Unit,
    private val onSettings: () -> Unit,
    private val onTasks: () -> Unit,
    private val onToggleGender: () -> Unit
) {
    private val appContext = context.applicationContext
    private var menuView: View? = null

    fun toggle(anchorX: Int, anchorY: Int) {
        if (menuView != null) {
            dismiss()
            return
        }

        val view = LayoutInflater.from(appContext).inflate(R.layout.overlay_icon_menu, null)
        view.findViewById<ImageButton>(R.id.menuCloseButton).setOnClickListener {
            dismiss()
            onClose()
        }
        view.findViewById<ImageButton>(R.id.menuSettingsButton).setOnClickListener {
            dismiss()
            onSettings()
        }
        view.findViewById<ImageButton>(R.id.menuTasksButton).setOnClickListener {
            dismiss()
            onTasks()
        }
        view.findViewById<ImageButton>(R.id.menuGenderButton).setOnClickListener {
            dismiss()
            onToggleGender()
        }

        val width = appContext.resources.getDimensionPixelSize(R.dimen.menu_width)
        val height = appContext.resources.getDimensionPixelSize(R.dimen.menu_height)
        val smiley = appContext.resources.getDimensionPixelSize(R.dimen.smiley_size)
        val gap = appContext.resources.getDimensionPixelSize(R.dimen.overlay_gap)
        val screen = OverlayWindowBounds.size(windowManager)
        val preferredX = anchorX - width - gap

        val params = WindowManager.LayoutParams(
            width,
            height,
            OverlayWindowBounds.windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (if (preferredX >= 0) preferredX else anchorX + smiley + gap)
                .coerceIn(0, (screen.x - width).coerceAtLeast(0))
            y = (anchorY + smiley / 2 - height / 2)
                .coerceIn(0, (screen.y - height).coerceAtLeast(0))
        }

        windowManager.addView(view, params)
        menuView = view
    }

    fun dismiss() {
        menuView?.let { view ->
            if (view.parent != null) windowManager.removeView(view)
        }
        menuView = null
    }
}