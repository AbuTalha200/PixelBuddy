package com.pixelbuddy.ai.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.pixelbuddy.ai.R
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The floating pixel smiley.
 *
 * - FEMALE: when she gets shy, two hands slide in and cover her face.
 * - MALE: when he is disappointed, one big hand comes up for a facepalm and he shakes his head.
 * Both also bob gently and blink while idle.
 */
class PixelSmileyView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var gender: SmileyGender = SmileyGender.MALE
        set(value) {
            if (field == value) return
            field = value
            stopReaction()
            updateDescription()
            invalidate()
        }

    private val paint = Paint().apply { style = Paint.Style.FILL }
    private val defaultSize = resources.getDimensionPixelSize(R.dimen.smiley_size)

    private var reaction: ValueAnimator? = null
    private var reactionT = IDLE
    private var tick = 1

    private val ticker = object : Runnable {
        override fun run() {
            tick++
            invalidate()
            postDelayed(this, TICK_MS)
        }
    }

    init {
        updateDescription()
    }

    /** Plays the reaction that belongs to the current gender, unless one is already running. */
    fun playReaction() {
        if (reaction?.isRunning == true) return
        reaction = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = REACTION_MS
            interpolator = LinearInterpolator()
            addUpdateListener {
                reactionT = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    reactionT = IDLE
                    invalidate()
                }
            })
            start()
        }
    }

    /** She gets shy at good news; he is let down by bad news. */
    fun reactToMood(mood: AiMood) {
        val triggered = if (gender == SmileyGender.FEMALE) {
            mood == AiMood.HAPPY || mood == AiMood.EXCITED
        } else {
            mood == AiMood.SAD || mood == AiMood.ANGRY
        }
        if (triggered) playReaction()
    }

    private fun stopReaction() {
        reaction?.cancel()
        reaction = null
        reactionT = IDLE
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        removeCallbacks(ticker)
        postDelayed(ticker, TICK_MS)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(ticker)
        stopReaction()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize(defaultSize, widthMeasureSpec),
            resolveSize(defaultSize, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val grid = SmileySprites.GRID
        val unit = min(width, height) / grid.toFloat()
        val offsetX = (width - unit * grid) / 2f
        val offsetY = (height - unit * grid) / 2f

        val reacting = reactionT >= 0f
        val bob = if (!reacting && tick % 2 == 0) 1 else 0
        val shake = if (reacting && gender == SmileyGender.MALE && reactionT in HOLD_START..HOLD_END) {
            sin(((reactionT - HOLD_START) * SHAKE_SPEED).toDouble()).roundToInt()
        } else {
            0
        }

        val saved = canvas.save()
        canvas.translate(offsetX + shake * unit, offsetY - bob * unit)
        drawSprite(canvas, SmileySprites.face(gender, currentExpression()), 0, 0, unit)
        if (reacting) drawHands(canvas, unit)
        canvas.restoreToCount(saved)
    }

    private fun currentExpression(): FaceExpression {
        val t = reactionT
        if (t in SWAP_IN..SWAP_OUT) {
            return if (gender == SmileyGender.FEMALE) FaceExpression.SHY else FaceExpression.DISAPPOINTED
        }
        return if (t < 0f && tick % BLINK_EVERY == 0) FaceExpression.BLINK else FaceExpression.NORMAL
    }

    private fun drawHands(canvas: Canvas, unit: Float) {
        val t = reactionT
        val amount = handAmount(t)
        if (amount <= 0f) return

        if (gender == SmileyGender.FEMALE) {
            val squirm = if (t in HOLD_START..HOLD_END) {
                (sin(((t - HOLD_START) * SQUIRM_SPEED).toDouble()) * 0.7).roundToInt()
            } else {
                0
            }
            val top = lerp(9, 5, amount) + squirm
            drawSprite(canvas, SmileySprites.shyHand, lerp(-9, 2, amount), top, unit)
            drawSprite(canvas, SmileySprites.shyHand, lerp(20, 9, amount), top, unit)
        } else {
            drawSprite(
                canvas,
                SmileySprites.facepalmHand,
                lerp(9, 4, amount),
                lerp(19, 3, amount),
                unit
            )
        }
    }

    /** 0 = hands away, 1 = hands on the face. Eases in, holds, then eases out. */
    private fun handAmount(t: Float): Float = when {
        t < 0f -> 0f
        t < HOLD_START -> {
            val x = t / HOLD_START
            1f - (1f - x).pow(3)
        }
        t <= HOLD_END -> 1f
        else -> {
            val x = (t - HOLD_END) / (1f - HOLD_END)
            1f - x * x * x
        }
    }

    private fun lerp(from: Int, to: Int, amount: Float): Int =
        (from + (to - from) * amount).roundToInt()

    private fun drawSprite(canvas: Canvas, rows: Array<String>, left: Int, top: Int, unit: Float) {
        rows.forEachIndexed { row, line ->
            line.forEachIndexed { col, pixel ->
                val color = SmileySprites.colorOf(pixel)
                if (color != 0) {
                    paint.color = color
                    canvas.drawRect(
                        (left + col) * unit,
                        (top + row) * unit,
                        (left + col + 1) * unit,
                        (top + row + 1) * unit,
                        paint
                    )
                }
            }
        }
    }

    private fun updateDescription() {
        val kind = if (gender == SmileyGender.FEMALE) "female" else "male"
        contentDescription = "PixelBuddy floating assistant, $kind smiley"
    }

    private companion object {
        const val IDLE = -1f
        const val REACTION_MS = 2600L
        const val TICK_MS = 400L
        const val BLINK_EVERY = 9
        const val HOLD_START = 0.25f
        const val HOLD_END = 0.78f
        const val SWAP_IN = 0.2f
        const val SWAP_OUT = 0.86f
        const val SHAKE_SPEED = 70f
        const val SQUIRM_SPEED = 50f
    }
}
