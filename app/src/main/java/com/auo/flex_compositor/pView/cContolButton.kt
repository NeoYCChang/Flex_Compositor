package com.auo.flex_compositor.pView

import android.app.Service.DISPLAY_SERVICE
import android.app.Service.WINDOW_SERVICE
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.MotionPredictor
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import com.auo.flex_compositor.R

class cContolButton (context: Context, display: Display) : ImageButton(context) {

    private var downTime = 0L
    private var m_window_manager: WindowManager? = null
    private var m_layoutParmas: WindowManager.LayoutParams? = null
    private var m_posX = 0
    private var m_posY= 0
    private var m_display_width = 0
    private var m_display_height = 0
    private var m_display = display
    private val m_clickevent = mutableListOf<() -> Unit>()

    private val m_shapeDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 8f
        setColor(Color.rgb(160, 160, 160))  // background color
        setStroke(1  ,Color.WHITE)
    }

    init {
        // Set your icon resource
        setImageResource(R.drawable.home)

        background = m_shapeDrawable
        scaleType = ImageView.ScaleType.FIT_CENTER

        // Enable click and focus if needed
        isClickable = true
        isFocusable = true

        var size = Point(0,0)
        display.getRealSize(size)
        m_display_width = size.x
        m_display_height = size.y
        m_layoutParmas = newLayoutParams()
        val displayContext: Context = context.createDisplayContext(m_display)
        m_window_manager = displayContext.getSystemService(WINDOW_SERVICE) as WindowManager
        m_window_manager!!.addView(this, m_layoutParmas)
    }

    private fun newLayoutParams(): WindowManager.LayoutParams {
        val params = WindowManager.LayoutParams()
        params.x = 0
        params.y = 0
        params.width = 48
        params.height = 48
        params.flags =
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            params.type = WindowManager.LayoutParams.TYPE_PHONE
        }
        params.gravity = Gravity.START or Gravity.TOP
        params.format = PixelFormat.TRANSLUCENT
        return params
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                m_shapeDrawable.setColor(Color.rgb(80,80,80))
                downTime = System.currentTimeMillis()
                m_posX = event.getX(0).toInt()
                m_posY = event.getY(0).toInt()
                return true
            }
            MotionEvent.ACTION_UP -> {
                m_shapeDrawable.setColor(Color.rgb(160,160,160))
                val duration = System.currentTimeMillis() - downTime
                when {
                    duration < 200 -> {
                        for (event in m_clickevent) {
                            event()
                        }
                        Log.d("MyImageButton", "Tap detected")
                    }
                    duration in 200..500 -> {
                        Log.d("MyImageButton", "Press and release detected")
                        // custom logic here
                    }
                    else -> {
                        Log.d("MyImageButton", "Long press detected")
                    }
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if(m_window_manager !== null && m_layoutParmas !== null) {
                    var x = event.getRawX(0).toInt() - m_posX
                    var y = event.getRawY(0).toInt() - m_posY
                    if(x < 0){
                        x = 0
                    }
                    else if(x > (m_display_width - 49)){
                        x = m_display_width - 49
                    }
                    if(y < 0){
                        y = 0
                    }
                    else if(y > (m_display_height - 49)){
                        y = m_display_height - 49
                    }

                    m_layoutParmas!!.x = x
                    m_layoutParmas!!.y = y

                    m_window_manager?.updateViewLayout(this, m_layoutParmas)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                m_shapeDrawable.setColor(Color.rgb(160,160,160))
                return true
            }
            else -> return super.onTouchEvent(event)
        }
    }
    fun clickEventSubscribe(event: () -> Unit) {
        m_clickevent.add(event)
    }

    fun clickEventUnsubscribe(event: () -> Unit) {
        m_clickevent.remove(event)
    }

    fun destroyed(){
        m_window_manager?.removeViewImmediate(this)
    }
}