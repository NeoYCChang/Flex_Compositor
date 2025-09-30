package com.auo.flex_compositor.pSink

import android.app.Service.DISPLAY_SERVICE
import android.app.Service.WINDOW_SERVICE
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import com.auo.flex_compositor.R
import com.auo.flex_compositor.pFilter.cViewMux
import com.auo.flex_compositor.pFilter.cViewPiP
import com.auo.flex_compositor.pFilter.cViewSwitch
import com.auo.flex_compositor.pInterface.SerializablePointerCoords
import com.auo.flex_compositor.pInterface.SerializablePointerProperties
import com.auo.flex_compositor.pInterface.cMotionEvent
import com.auo.flex_compositor.pInterface.deWarp_Parameters
import com.auo.flex_compositor.pInterface.iSurfaceSource
import com.auo.flex_compositor.pInterface.iTouchMapper
import com.auo.flex_compositor.pInterface.vCropTextureArea
import com.auo.flex_compositor.pInterface.vPos_Size
import com.auo.flex_compositor.pInterface.vTouchMapping

class cDisplayPiPChildView(context: Context, override val e_name: String, override val e_id: Int, source: iSurfaceSource, displayID: Int,
                           posSize: vPos_Size, cropTextureArea: vCropTextureArea, touchMapping: vTouchMapping?, dewarpParameters: deWarp_Parameters?
) :cDisplayView(context, e_name,  e_id, source, displayID, posSize, cropTextureArea, touchMapping, dewarpParameters) {

    private var m_childSwitch: cViewSwitch? = null
    private var m_pip: cViewPiP? = null
    private val m_tag = "cDisplayPiPChildView"
    private var m_zoominButton: ImageButton? = null
    private var m_closeButton: ImageButton? = null
    private var m_expandButton: ImageButton? = null
    private var m_posX = 0
    private var m_posY= 0
    private val m_detectMenuTimer = Handler(Looper.getMainLooper())
    private var m_isShownMenu: Boolean = false
    private var m_isClosed: Boolean = false

    init {
        when (source) {
            is cViewSwitch -> {
                val viewSwitch = source as cViewSwitch
                m_childSwitch = viewSwitch
            }
        }
    }

    fun setPiP(viewPiP: cViewPiP){
        m_pip = viewPiP
    }

    /**
     * Wrap the displayView and three buttons inside a FrameLayout,
     * then add the FrameLayout to the screen.
     *
     * The three buttons are:
     * - zoominButton: responsible for zooming in (enlarging) the displayView
     * - closeButton: responsible for hiding the displayView
     * - expandButton: responsible for popping out (showing) the hidden displayView
     */
    override fun viewInit(): View {
        val container = FrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }
        this.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        container.addView(this)

        m_zoominButton = ImageButton(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setImageResource(R.drawable.zoomin)
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                dpToPx(context, 40),
                dpToPx(context, 40)
            ).apply {
                gravity = Gravity.START or Gravity.TOP
                topMargin = dpToPx(context, 4)
                leftMargin = dpToPx(context, 4)
            }
            setOnTouchListener(zoomInTouchListener())
        }
        container.addView(m_zoominButton)

        m_closeButton = ImageButton(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setImageResource(R.drawable.minus)
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                dpToPx(context, 40),
                dpToPx(context, 40)
            ).apply {
                gravity = Gravity.END or Gravity.TOP
                topMargin = dpToPx(context, 4)
                rightMargin = dpToPx(context, 4)
            }
            setOnTouchListener(closeTouchListener())
        }

        container.addView(m_closeButton)

        val shapeDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8f
            setColor(Color.rgb(110, 110, 110))  // background color
            setStroke(1  ,Color.WHITE)
        }

        m_expandButton = ImageButton(context).apply {
            background = shapeDrawable
            setImageResource(R.drawable.leftarrow)
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                dpToPx(context, 15),
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.START or Gravity.TOP
                topMargin = dpToPx(context, 0)
                leftMargin = dpToPx(context, 0)
            }
            setOnTouchListener(expandTouchListener())
        }
        container.addView(m_expandButton)
        m_mainView = container
        return container
    }

    fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    override fun onTouchEvent(motionEvent: MotionEvent): Boolean {
        Log.d(m_tag, "cDisplayPiPChildView onTouchEvent")
        detectLongPress(motionEvent)
        when (motionEvent.actionMasked) {
            MotionEvent.ACTION_UP -> {
                managerMenu()
            }
            MotionEvent.ACTION_DOWN -> {
                m_posX = motionEvent.getX(0).toInt()
                m_posY = motionEvent.getY(0).toInt()
            }
            MotionEvent.ACTION_MOVE -> {
                if(m_window_manager !== null && m_layoutParmas !== null) {
                    var x = motionEvent.getRawX(0).toInt() - m_posX
                    var y = motionEvent.getRawY(0).toInt() - m_posY
                    if(x < 0){
                        x = 0
                    }
                    else if(x > (m_display_width - m_posSize.width)){
                        x = m_display_width - m_posSize.width
                    }
                    if(y < 0){
                        y = 0
                    }
                    else if(y > (m_display_height - m_posSize.height)){
                        y = m_display_height - m_posSize.height
                    }
                    m_layoutParmas!!.x = x
                    m_layoutParmas!!.y = y
//                    m_layoutParmas!!.width = 100
//                    m_layoutParmas!!.height = 50
                    m_window_manager?.updateViewLayout(m_mainView, m_layoutParmas)
                }
            }
        }

        return true // Return true to indicate the event was handled
    }

    private fun triggerDelayCloseToolBar(){
        m_detectMenuTimer.removeCallbacksAndMessages(null)
        m_detectMenuTimer.postDelayed({
            m_isShownMenu = false
            m_zoominButton?.visibility = View.GONE
            m_closeButton?.visibility = View.GONE
        }, 3000)
    }

    private fun managerMenu(){
        if(!m_isShownMenu && ! m_isClosed){
            m_zoominButton?.visibility = View.VISIBLE
            m_closeButton?.visibility = View.VISIBLE
            m_isShownMenu = true
            triggerDelayCloseToolBar()
        }
    }

    private fun zoomInTouchListener(): View.OnTouchListener {
        return View.OnTouchListener { view, motionEvent ->
            when (motionEvent.action) {
                MotionEvent.ACTION_UP-> {
                    if(m_pip != null && m_childSwitch != null){
                        m_pip!!.exchangePiP(m_childSwitch!!)
                        view.performClick()
                    }
                }
            }
            triggerDelayCloseToolBar()
            true
        }
    }

    private fun closeTouchListener(): View.OnTouchListener {
        return View.OnTouchListener { view, motionEvent ->
            when (motionEvent.action) {
                MotionEvent.ACTION_UP-> {
                    m_isClosed = true
                    m_layoutParmas!!.x = m_display_width - 15
                    m_layoutParmas!!.y = m_display_height/2 - (m_posSize.height/2)
                    m_window_manager?.updateViewLayout(m_mainView, m_layoutParmas)
                    m_expandButton?.visibility = View.VISIBLE
                    view.performClick()
                }
            }
            true
        }
    }

    private fun expandTouchListener(): View.OnTouchListener {
        return View.OnTouchListener { view, motionEvent ->
            when (motionEvent.action) {
                MotionEvent.ACTION_UP-> {
                    m_isClosed = false
                    m_layoutParmas!!.x = m_display_width - m_posSize.width
                    m_layoutParmas!!.y = m_display_height/2 - (m_posSize.height/2)
                    m_window_manager?.updateViewLayout(m_mainView, m_layoutParmas)
                    m_expandButton?.visibility = View.GONE
                    view.performClick()
                }
            }
            true
        }
    }


}