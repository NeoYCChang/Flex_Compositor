package com.auo.flex_compositor.pSink

import android.app.Service.DISPLAY_SERVICE
import android.app.Service.WINDOW_SERVICE
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.MotionEvent.PointerCoords
import android.view.MotionEvent.PointerProperties
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.view.WindowManager
import java.lang.ref.WeakReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import android.opengl.EGLContext
import android.os.RemoteException
import android.os.SystemClock
import androidx.compose.ui.geometry.Offset
import kotlin.concurrent.read
import kotlin.concurrent.write
import com.auo.flex_compositor.pEGLFunction.EGLHelper
import com.auo.flex_compositor.pEGLFunction.EGLRender
import com.auo.flex_compositor.pEGLFunction.EGLRender.Texture_Size
import com.auo.flex_compositor.pEGLFunction.EGLThread
import com.auo.flex_compositor.pFilter.cViewSwitch
import com.auo.flex_compositor.pInterface.SerializablePointerCoords
import com.auo.flex_compositor.pInterface.SerializablePointerProperties
import com.auo.flex_compositor.pInterface.cMotionEvent
import com.auo.flex_compositor.pInterface.deWarp_Parameters
import com.auo.flex_compositor.pInterface.iElement
import com.auo.flex_compositor.pInterface.iEssentialRenderingTools
import com.auo.flex_compositor.pInterface.iSurfaceSource
import com.auo.flex_compositor.pInterface.iTouchMapper
import com.auo.flex_compositor.pInterface.vCropTextureArea
import com.auo.flex_compositor.pInterface.vPos_Size
import com.auo.flex_compositor.pInterface.vTouchMapping
import com.auo.flex_compositor.pSource.cVirtualDisplay
import com.android.auoservice.auoCallback
import com.android.auoservice.auoManager
import com.android.auoservice.auoTouchDeviceInfo
import com.android.auoservice.auoTouchEvent
import com.auo.flex_compositor.pParse.cFlexTouchDevice


open class cDisplayView(context: Context, override val e_name: String, override val e_id: Int, source: iSurfaceSource, displayID: Int,
                   posSize: vPos_Size, cropTextureArea: vCropTextureArea, touchMapping: vTouchMapping?,
                        dewarpParameters: deWarp_Parameters?, touchDevice: cFlexTouchDevice?
) :
SurfaceView(context), SurfaceHolder.Callback, iElement, iEssentialRenderingTools {

    interface DisplayViewCallback {
        fun onTouchCallback(motionEvent: MotionEvent)
    }

    private val m_tag = "cDisplayView"
    private var m_DisplayViewCallback: DisplayViewCallback? = null
    // The surface can be passed from outside
    private var m_surface: Surface? = null

    protected var eglThread: EGLThread? = null

    private var m_source: iSurfaceSource = source
    private val m_context: Context = context
    protected var m_EGLRender: EGLRender?  = null
    private var m_dewarpParameters: deWarp_Parameters? = dewarpParameters
    private var m_touchDevice: cFlexTouchDevice? = touchDevice
    private val m_displayID = displayID
    private val mTextureSize : Texture_Size = Texture_Size(
        960, 540, 0, 0,
        960,540
    )
    private var m_cropTextureArea = cropTextureArea
    private var m_touchMapping = touchMapping
    protected val m_posSize: vPos_Size =  posSize
    protected var m_display_width = 0
    protected var m_display_height = 0
    protected var m_window_manager: WindowManager? = null
    protected var m_layoutParmas: WindowManager.LayoutParams? = null
    protected var m_mainView: View? = null

    // Detect Long Press
    private val m_longPressHandler = Handler(Looper.getMainLooper())
    private var m_isLongPress = false
    protected var m_isDestroyed = false

    private var m_auoManager: auoManager? = null
    private var m_auoCallback: auoCallback? = null

    init {
        holder.addCallback(this)
        displayViewInit()
    }

    protected open fun displayViewInit(){
        val display_manager = m_context.getSystemService(DISPLAY_SERVICE) as DisplayManager

        val display: Display? = display_manager.getDisplay(m_displayID)
        if (display !== null) {
            Log.d(m_tag, "display flag: ${display.flags}")
            // flag = 4 : Private Display
            if(display.flags != 132) {
                var size = Point(0,0)
                display.getRealSize(size)
                m_display_width = size.x
                m_display_height = size.y

                m_layoutParmas = newLayoutParams(m_posSize)
                val displayContext: Context = m_context.createDisplayContext(display)
                m_window_manager = displayContext.getSystemService(WINDOW_SERVICE) as WindowManager
                m_window_manager!!.addView(viewInit(), m_layoutParmas)


            }
        }
    }

    protected open fun viewInit(): View{
        m_mainView = this
        return this
    }

    private fun touchDeviceInit() {
        if(m_touchDevice != null){
            m_auoManager = m_context.getSystemService(auoManager.SERVICE) as auoManager

            m_auoCallback = object : auoCallback.Stub() {
                @Throws(RemoteException::class)
                override fun onTouchEventReceived(touchEvent: Array<auoTouchEvent?>) {
//                        var handled = false
//                        for (event in touchEvent) {
//                            if (handled) break
//
//                            event?.let {
//                                Log.d(
//                                    "auoManager",
//                                    "Slot=${it.slot} action=${it.action} x=${it.x} y=${it.y}"
//                                )
//                            }
//                        }
                    if(m_touchDevice != null) {
                        val motionEvents: List<MotionEvent> =
                            convertAuoTouchEventsToMotionEvents(touchEvent, m_touchDevice!!)
                        motionEvents.forEachIndexed { index, e ->
                            onTransitionTouchEvent(e)
                        }
                    }
                }
            }
            if(m_auoManager != null) {
                val touchDeviceInfo: Array<auoTouchDeviceInfo> = m_auoManager!!.touchDeviceInfo
                for(device in touchDeviceInfo) {
                    if(device.vendor == m_touchDevice!!.vid && device.product == m_touchDevice!!.pid){
                        Log.d(m_tag, "${e_name}:${e_id} read touch event from ${device.event_name}")
                        m_auoManager?.register(device.event_name, m_auoCallback)
                        break;
                    }
                }
            }
        }
    }


    private fun newLayoutParams(posSize: vPos_Size): WindowManager.LayoutParams {
        val params = WindowManager.LayoutParams()
        params.x = posSize.x
        params.y = posSize.y
        params.width = posSize.width
        params.height = posSize.height
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

    fun show(){
        if(m_window_manager !== null && m_layoutParmas !== null && !m_isDestroyed) {
            m_layoutParmas!!.x = m_posSize.x
            m_layoutParmas!!.y = m_posSize.y

            m_window_manager?.updateViewLayout(m_mainView, m_layoutParmas)
        }
        //this.visibility = View.VISIBLE
    }

    fun hide(){
        if(m_window_manager !== null && m_layoutParmas !== null && !m_isDestroyed) {
            m_layoutParmas?.x = -m_posSize.width
            m_layoutParmas?.y = -m_posSize.height

            m_window_manager?.updateViewLayout(m_mainView, m_layoutParmas)
        }
        //this.visibility = View.GONE
    }


    fun getDisplayID(): Int{
        return m_displayID
    }

    fun getTextureSize() : Texture_Size{
        return mTextureSize
    }

    fun getTouchMapping() : vTouchMapping?{
        return m_touchMapping
    }

    fun getPosSize() : vPos_Size{
        return m_posSize
    }

    fun getSorceName() : String?{
        return m_source?.e_name
    }

    fun getTextureID() : Int{
        if(m_source !== null) {
            return m_source.getSurfaceTexture().getTextureID()
        }
        else{
            return  -1
        }
    }

    override fun getSource() : iSurfaceSource{
        return m_source
    }

    override fun getEGLContext() : EGLContext?{
        return m_source.getEGLContext()
    }

    override fun getEGLRender() : EGLRender?{
        return m_EGLRender
    }

//    fun setSurfaceAndEglContext(surface: Surface?, eglContext: EGLContext?) {
//        this.m_surface = surface
//        this.eglContext = eglContext
//    }

    fun setTextureCrop(cropArea: vCropTextureArea){
        mTextureSize.offsetX = cropArea.offsetX
        mTextureSize.offsetY = cropArea.offsetY
        mTextureSize.cropWidth  = cropArea.width
        mTextureSize.cropHeight = cropArea.height
    }


    override fun surfaceCreated(holder: SurfaceHolder) {
        auosurfaceCreated(holder)
    }

    fun auosurfaceCreated(holder: SurfaceHolder?) {
        Log.d(m_tag,"surfaceCreated")
        m_isDestroyed = false
        if (m_surface == null) {
            m_surface = holder?.surface
        }
        if (m_surface == null) {
            return
        }
        if(m_source !== null) {
            Log.d(m_tag, "dewarp ${m_dewarpParameters}")
            m_EGLRender = EGLRender(
                m_context,
                m_dewarpParameters
            )

            when (m_source) {
                is cViewSwitch -> {
                    val viewSwitch = m_source as cViewSwitch
                    m_cropTextureArea = viewSwitch.getCrop_texture()
                    m_touchMapping = viewSwitch.getTouchMapping()
                }
            }

            setTextureCrop(m_cropTextureArea)
            mTextureSize.width = m_source.getSurfaceTexture().getWidth()
            mTextureSize.height = m_source.getSurfaceTexture().getHeight()

            m_EGLRender!!.setTextureSize(mTextureSize)


            eglThread = EGLThread(WeakReference(this))
            eglThread!!.start()

            when (m_source) {
                is cViewSwitch -> {
                    val handler: () -> Unit = {
                        val viewSwitch = m_source as cViewSwitch
                        m_cropTextureArea = viewSwitch.getCrop_texture()
                        m_touchMapping = viewSwitch.getTouchMapping()
                        setTextureCrop(m_cropTextureArea)
                        mTextureSize.width = m_source.getSurfaceTexture().getWidth()
                        mTextureSize.height = m_source.getSurfaceTexture().getHeight()

                        m_EGLRender!!.setTextureSize(mTextureSize, true)
                    }
                    val viewSwitch = m_source as cViewSwitch
                    viewSwitch.triggerSubscribe(handler)
                }
            }
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        eglThread!!.width = width
        eglThread!!.height = height
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d(m_tag, "surfaceDestroyed: ${holder.surface}")
        destroyed()
    }

    open fun destroyed(){
        Log.d(m_tag, "destroyed")
        Thread {
            if (!m_isDestroyed) {
                eglThread?.onDestory()
                eglThread?.join()
                m_EGLRender?.release()
                m_EGLRender = null
                Handler(Looper.getMainLooper()).post {
                    m_window_manager?.removeViewImmediate(m_mainView)
                }
//                m_window_manager?.removeView(this)
                m_isDestroyed = true
            }
        }.start()
        m_auoManager?.unregister(m_auoCallback)
    }

    // Override this method to handle touch events
    override fun onTouchEvent(motionEvent: MotionEvent): Boolean {
        if(m_touchDevice == null) {
            return onTransitionTouchEvent(motionEvent)
        }
        return true
    }

    fun onTransitionTouchEvent(motionEvent: MotionEvent): Boolean {
        detectLongPress(motionEvent)
        if(m_source !== null && m_touchMapping != null) {
            val touchmapper: iTouchMapper = m_source as iTouchMapper
            val pointerCount = motionEvent.pointerCount
            val pointerProperties =Array(pointerCount) { i ->
                SerializablePointerProperties(
                    id =  motionEvent.getPointerId(i),
                    toolType = motionEvent.getToolType(i)
                )
            }
            val pointerCoords = Array(pointerCount) { i ->
                SerializablePointerCoords(
                    x = motionEvent.getX(i) * (m_touchMapping!!.width - 1) / (this.width - 1) + m_touchMapping!!.offsetX,
                    y = motionEvent.getY(i) * (m_touchMapping!!.height - 1) / (this.height - 1) + m_touchMapping!!.offsetY,
                    pressure = motionEvent.getPressure(i),
                    size = motionEvent.getSize(i)
                )
            }

            val cMotionEvent: cMotionEvent = cMotionEvent(
                com.auo.flex_compositor.pInterface.start_byte, e_name,m_posSize.width, m_posSize.height,
                motionEvent.downTime, motionEvent.eventTime,
                motionEvent.action,motionEvent.pointerCount,pointerProperties,pointerCoords,
                motionEvent.metaState,motionEvent.buttonState,motionEvent.xPrecision,motionEvent.yPrecision,
                0,motionEvent.edgeFlags,motionEvent.source,motionEvent.flags)

            touchmapper.injectMotionEvent(cMotionEvent)
        }
        return true // Return true to indicate the event was handled
    }

    //Remove the DisplayView after pressing and holding the top-left corner of
    // the DisplayView for 5 seconds
    protected fun detectLongPress(motionEvent: MotionEvent){
        when (motionEvent.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val x = motionEvent.getX(0)
                val y = motionEvent.getY(0)
                if(x <= 100 && y <= 100) {
                    m_isLongPress = false
                    m_longPressHandler.postDelayed({
                        m_isLongPress = true
                        destroyed()
                        Log.d(m_tag, "Long press exceeded 5 seconds — destroying $e_name")
                    }, 5000)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                m_longPressHandler.removeCallbacksAndMessages(null)
            }
        }
    }

    private fun addMTproperty(event: auoTouchEvent, propertiesList: MutableList<PointerProperties>,
                              coordsList: MutableList<PointerCoords>, touchAffine: cFlexTouchDevice){
        val prop = MotionEvent.PointerProperties().apply {
            id = event.slot
        }
        val coord = MotionEvent.PointerCoords().apply {
            val tmpX = event.x.toFloat()
            val tmpY = event.y.toFloat()
            x = tmpX * touchAffine.a11 + tmpY * touchAffine.a12 + touchAffine.a13
            y = tmpX * touchAffine.a21 + tmpY * touchAffine.a22 + touchAffine.a23
            pressure = 1f
            size = 1f
        }

        propertiesList.add(prop)
        coordsList.add(coord)
    }

    private fun removeMTproperty(event: auoTouchEvent, propertiesList: MutableList<PointerProperties>, coordsList: MutableList<PointerCoords>){
        val index = propertiesList.indexOfFirst { it.id == event.slot }

        if (index != -1) {
            propertiesList.removeAt(index)
            coordsList.removeAt(index)
        }
    }

    private fun getMTpropertyIndex(event: auoTouchEvent, propertiesList: MutableList<PointerProperties>): Int{
        return propertiesList.indexOfFirst { it.id == event.slot }
    }

    private fun convertAuoTouchEventsToMotionEvents(events: Array<auoTouchEvent?>, touchAffine: cFlexTouchDevice): List<MotionEvent> {
        val motionEvents = mutableListOf<MotionEvent>()

        val validEvents = events.filterNotNull()
        if (validEvents.isEmpty()) return motionEvents

        val downTime = SystemClock.uptimeMillis()
        val eventTime = SystemClock.uptimeMillis()
        val pointerCount = validEvents.size
        var hasMoveEvent = false

        val propertiesList = mutableListOf<MotionEvent.PointerProperties>()
        val coordsList = mutableListOf<MotionEvent.PointerCoords>()

        for (event in validEvents) {
            when (event.action) {
                0 -> { // non
                    addMTproperty(event, propertiesList, coordsList, touchAffine)
                }

                1 -> { // up
                    addMTproperty(event, propertiesList, coordsList, touchAffine)
                }

                3 -> { // move
                    addMTproperty(event, propertiesList, coordsList, touchAffine)
                    hasMoveEvent = true
                }
            }
        }
        if(hasMoveEvent) {
            val me = MotionEvent.obtain(
                downTime,
                eventTime,
                MotionEvent.ACTION_MOVE,
                propertiesList.count(),
                propertiesList.toTypedArray(),
                coordsList.toTypedArray(),
                0, 0, 1f, 1f, 0, 0, 0, 0
            )
            motionEvents.add(me)
        }

//        val properties = Array(pointerCount) { i ->
//            MotionEvent.PointerProperties().apply { id = validEvents[i].slot }
//        }
//
//        val coords = Array(pointerCount) { i ->
//            MotionEvent.PointerCoords().apply {
//                val tmp_x = validEvents[i].x.toFloat()
//                val tmp_y = validEvents[i].y.toFloat()
//                x = tmp_x * 0.001391f + tmp_y * 0.151693f - 301.400076f
//                y = tmp_x * -0.136053f + tmp_y * 0.000133f + 1623.312278f
//                pressure = 1f
//                size = 1f
//            }
//        }

        for (event in validEvents) {
            when (event.action) {
                2 -> { // down
                    val action = if (propertiesList.count() == 0) {
                        MotionEvent.ACTION_DOWN
                    } else {
                        MotionEvent.ACTION_POINTER_DOWN or (propertiesList.count() shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
                    }
                    addMTproperty(event, propertiesList, coordsList, touchAffine)
                    val me = MotionEvent.obtain(
                        downTime,
                        eventTime,
                        action,
                        propertiesList.count(),
                        propertiesList.toTypedArray(),
                        coordsList.toTypedArray(),
                        0, 0, 1f, 1f, 0, 0, 0, 0
                    )
                    motionEvents.add(me)
                }

                1 -> { // up
                    val index = getMTpropertyIndex(event, propertiesList)
                    if(index == -1){
                        continue
                    }
                    val action = if (propertiesList.count() == 1) {
                        MotionEvent.ACTION_UP
                    } else {
                        MotionEvent.ACTION_POINTER_UP or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
                    }
                    val me = MotionEvent.obtain(
                        downTime,
                        eventTime,
                        action,
                        propertiesList.count(),
                        propertiesList.toTypedArray(),
                        coordsList.toTypedArray(),
                        0, 0, 1f, 1f, 0, 0, 0, 0
                    )
                    motionEvents.add(me)
                    removeMTproperty(event, propertiesList, coordsList)
                }
            }

        }

        return motionEvents
    }



    override fun getSurface(): Surface? {
        return m_surface
    }

    override fun Sync() {

    }


    fun setAUOGLSurfaceViewCallback(callback: DisplayViewCallback) {
        m_DisplayViewCallback = callback
    }

}