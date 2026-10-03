package com.pixelbuddy.ai.capture

import android.graphics.Bitmap
import android.graphics.Color
import android.media.Image
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.roundToInt

object ScreenFrameProcessor {
    private const val MAX_SIDE = 1440

    fun toJpeg(image: Image): ByteArray {
        val sourceWidth = image.width
        val sourceHeight = image.height
        require(sourceWidth > 0 && sourceHeight > 0)

        val plane = image.planes.firstOrNull() ?: throw IOException("No screen pixels were received")
        val step = plane.pixelStride
        if (step < 3) throw IOException("Unsupported screen pixel format")

        val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(sourceWidth, sourceHeight))
        val width = (sourceWidth * scale).roundToInt().coerceAtLeast(1)
        val height = (sourceHeight * scale).roundToInt().coerceAtLeast(1)
        val pixels = IntArray(width * height)
        val buffer = plane.buffer
        val rowBytes = ByteArray(sourceWidth * step)

        // Sample directly from the ImageReader plane so 4K frames never need a 4K bitmap.
        for (y in 0 until height) {
            val sourceY = (y.toLong() * sourceHeight / height).toInt()
            buffer.position(sourceY * plane.rowStride)
            buffer.get(rowBytes, 0, rowBytes.size)

            for (x in 0 until width) {
                val sourceX = (x.toLong() * sourceWidth / width).toInt()
                val offset = sourceX * step
                val red = rowBytes[offset].toInt() and 0xFF
                val green = rowBytes[offset + 1].toInt() and 0xFF
                val blue = rowBytes[offset + 2].toInt() and 0xFF
                pixels[y * width + x] = Color.rgb(red, green, blue)
            }
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            ByteArrayOutputStream().use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)) {
                    throw IOException("Could not encode screen frame")
                }
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }
}