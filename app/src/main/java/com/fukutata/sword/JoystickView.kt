package com.fukutata.sword

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class JoystickView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        alpha = 60
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    private val stickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        alpha = 150
        style = Paint.Style.FILL
    }

    private var centerX = 0f
    private var centerY = 0f
    private var baseRadius = 130f
    private var stickRadius = 60f

    private var stickX = 0f
    private var stickY = 0f

    private var isTouched = false

    var onMoveListener: ((x: Float, y: Float) -> Unit)? = null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                centerX = event.x
                centerY = event.y
                stickX = event.x
                stickY = event.y
                isTouched = true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - centerX
                val dy = event.y - centerY
                val distance = hypot(dx, dy)
                val angle = atan2(dy, dx)

                val limit = baseRadius
                if (distance <= limit) {
                    stickX = event.x
                    stickY = event.y
                } else {
                    stickX = centerX + cos(angle) * limit
                    stickY = centerY + sin(angle) * limit
                }

                val nx = (stickX - centerX) / limit
                val ny = (stickY - centerY) / limit
                onMoveListener?.invoke(nx, ny)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isTouched = false
                onMoveListener?.invoke(0f, 0f)
            }
        }
        invalidate()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        if (!isTouched) return
        canvas.drawCircle(centerX, centerY, baseRadius, basePaint)
        canvas.drawCircle(stickX, stickY, stickRadius, stickPaint)
    }
}