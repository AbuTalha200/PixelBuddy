package com.pixelbuddy.ai.overlay

import android.graphics.Point
import android.os.Build
import android.view.WindowManager

internal object OverlayWindowBounds {
    val windowType: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

    fun size(windowManager: WindowManager): Point {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            return Point(bounds.width(), bounds.height())
        }

        @Suppress("DEPRECATION")
        val display = windowManager.defaultDisplay
        return Point().also { display.getRealSize(it) }
    }
}