package com.pixelbuddy.ai.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

class MangaSpeechBubbleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var message = ""
    private var mood = AiMood.THINKING
    private var tailOnRight = true
    private var textLayout: StaticLayout? = null

    private val outline = Path()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    fun showMessage(text: String, state: AiMood, pointRight: Boolean) {
        message = text.take(380).ifBlank { "I don't have a response yet." }
        mood = state
        tailOnRight = pointRight
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize(dp(256f).toInt(), widthMeasureSpec)
        val bodyWidth = width - dp(16f).toInt()
        val textWidth = max(dp(80f).toInt(), bodyWidth - dp(34f).toInt())
        textPaint.typeface = style().typeface

        for (fontSp in 19 downTo 12) {
            textPaint.textSize = fontSp * resources.displayMetrics.scaledDensity
            val candidate = layoutFor(message, textWidth)
            textLayout = candidate
            if (candidate.lineCount <= 7 && candidate.height <= dp(200f)) break
        }

        val desiredHeight = max(dp(74f).toInt(), (textLayout?.height ?: 0) + dp(30f).toInt())
        setMeasuredDimension(width, resolveSize(desiredHeight, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val colors = style()
        fillPaint.color = colors.fill
        borderPaint.color = colors.border
        textPaint.color = colors.ink

        val inset = dp(2f)
        val tail = dp(16f)
        val left = if (tailOnRight) inset else tail + inset
        val right = if (tailOnRight) width - tail - inset else width - inset
        val top = inset
        val bottom = height - inset
        val radius = dp(if (mood == AiMood.ANGRY) 6f else 18f)
        val tailFraction = when (mood) {
            AiMood.HAPPY -> 0.31f
            AiMood.ANGRY -> 0.50f
            AiMood.EXCITED -> 0.23f
            AiMood.SAD -> 0.69f
            AiMood.THINKING -> 0.40f
        }
        val tailY = (height * tailFraction).coerceIn(top + radius + dp(9f), bottom - radius - dp(9f))

        outline.reset()
        outline.moveTo(left + radius, top)
        outline.lineTo(right - radius, top)
        outline.quadTo(right, top, right, top + radius)

        if (tailOnRight) {
            outline.lineTo(right, tailY - dp(9f))
            outline.lineTo(width - inset, tailY)
            outline.lineTo(right, tailY + dp(9f))
        }

        outline.lineTo(right, bottom - radius)
        outline.quadTo(right, bottom, right - radius, bottom)
        outline.lineTo(left + radius, bottom)
        outline.quadTo(left, bottom, left, bottom - radius)

        if (!tailOnRight) {
            outline.lineTo(left, tailY + dp(9f))
            outline.lineTo(inset, tailY)
            outline.lineTo(left, tailY - dp(9f))
        }

        outline.lineTo(left, top + radius)
        outline.quadTo(left, top, left + radius, top)
        outline.close()

        canvas.drawPath(outline, fillPaint)
        canvas.drawPath(outline, borderPaint)

        textLayout?.let { layout ->
            val saved = canvas.save()
            canvas.translate(left + dp(17f), (height - layout.height) / 2f)
            layout.draw(canvas)
            canvas.restoreToCount(saved)
        }
    }

    private fun layoutFor(value: String, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(value, 0, value.length, textPaint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.06f)
            .setIncludePad(false)
            .build()

    private fun style(): BubbleStyle = when (mood) {
        AiMood.HAPPY -> BubbleStyle(0xFFFFF2BB.toInt(), 0xFFB68016.toInt(), 0xFF443019.toInt(), Typeface.DEFAULT)
        AiMood.ANGRY -> BubbleStyle(0xFFFFD8D3.toInt(), 0xFFC43532.toInt(), 0xFF511512.toInt(), Typeface.DEFAULT_BOLD)
        AiMood.EXCITED -> BubbleStyle(0xFFEADFFF.toInt(), 0xFF8254C4.toInt(), 0xFF392358.toInt(), Typeface.create("sans-serif-condensed", Typeface.BOLD))
        AiMood.SAD -> BubbleStyle(0xFFDCEBFF.toInt(), 0xFF6486AD.toInt(), 0xFF263C59.toInt(), Typeface.SERIF)
        AiMood.THINKING -> BubbleStyle(0xFFF3F0EB.toInt(), 0xFF80838C.toInt(), 0xFF292C34.toInt(), Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC))
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private data class BubbleStyle(val fill: Int, val border: Int, val ink: Int, val typeface: Typeface)
}