package com.wais.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import com.wais.R

class FloatingButtonController(
    private val context: Context,
    private val onTap: (FloatingAnchor) -> Unit,
    private val onDoubleTap: (FloatingAnchor) -> Unit = {}
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var rootView: View? = null

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (rootView != null) return

        val view = LayoutInflater.from(context).inflate(R.layout.overlay_floating_button, null)
        val button = view.findViewById<ImageButton>(R.id.floatingScanButton)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 280
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var moved = false

        fun currentAnchor() = FloatingAnchor(
            x = params.x,
            y = params.y,
            width = button.width,
            height = button.height
        )

        // Single tap fires only after the double-tap window closes, so a double tap never also runs the text check.
        val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (!moved) onTap(currentAnchor())
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (!moved) onDoubleTap(currentAnchor())
                return true
            }
        })

        button.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    moved = false
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (kotlin.math.abs(dx) > 5 || kotlin.math.abs(dy) > 5) moved = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(view, params)
                    true
                }

                MotionEvent.ACTION_UP -> true

                else -> false
            }
        }

        windowManager.addView(view, params)
        rootView = view
    }

    /** Hides the bubble without removing it (e.g. so it is not captured in a screenshot). */
    fun setVisible(visible: Boolean) {
        rootView?.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    fun hide() {
        rootView?.let { view ->
            windowManager.removeView(view)
            rootView = null
        }
    }
}

data class FloatingAnchor(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)
