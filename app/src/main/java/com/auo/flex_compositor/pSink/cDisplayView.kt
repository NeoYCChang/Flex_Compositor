package com.auo.flex_compositor.pSink

import android.app.Service.DISPLAY_SERVICE
import android.app.Service.WINDOW_SERVICE
import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.MotionEvent.PointerCoords
import android.view.MotionEvent.PointerProperties
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup.LayoutParams
import android.view.WindowManager
import java.lang.ref.WeakReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.microedition.khronos.egl.EGLContext
import kotlin.concurrent.read
import kotlin.concurrent.write
import com.auo.flex_compositor.pEGLFunction.EGLHelper
import com.auo.flex_compositor.pEGLFunction.EGLRender
import com.auo.flex_compositor.pEGLFunction.EGLRender.Texture_Size
import com.auo.flex_compositor.pEGLFunction.EGLThread
import com.auo.flex_compositor.pInterface.iElement
import com.auo.flex_compositor.pInterface.iEssentialRenderingTools
import com.auo.flex_compositor.pInterface.iSurfaceSource
import com.auo.flex_compositor.pInterface.iTouchMapper
import com.auo.flex_compositor.pInterface.vCropTextureArea
import com.auo.flex_compositor.pInterface.vPos_Size
import com.auo.flex_compositor.pInterface.vTouchMapping
import com.auo.flex_compositor.pSource.cVirtualDisplay



class cDisplayView(context: Context, override val e_name: String, override val e_id: Int, source: iSurfaceSource, displayID: Int,
                   posSize: vPos_Size, cropTextureArea: vCropTextureArea, touchMapping: vTouchMapping?, isDeWarp: Boolean) :
SurfaceView(context), SurfaceHolder.Callback, iElement, iEssentialRenderingTools {

    interface DisplayViewCallback {
        fun onTouchCallback(motionEvent: MotionEvent)
    }

    private val m_tag = "cDisplayView"
    private var m_DisplayViewCallback: DisplayViewCallback? = null
    // The surface can be passed from outside
    private var m_surface: Surface? = null

    private var eglThread: EGLThread? = null

    private var m_source: iSurfaceSource = source
    private val m_context: Context = context
    private var m_EGLRender: EGLRender?  = null
    private var m_isDeWarp: Boolean = isDeWarp
    private val m_displayID = displayID
    private val mTextureSize : Texture_Size = Texture_Size(
        960, 540, 0, 0,
        960,540
    )
    private val m_cropTextureArea = cropTextureArea
    private val m_touchMapping = touchMapping
    private val m_posSize: vPos_Size =  posSize
    private var m_window_manager: WindowManager? = null
    private var m_layoutParmas: WindowManager.LayoutParams? = null
    // EGL context
    private var eglContext: EGLContext? = m_source?.getEGLContext()
    private var m_responsible_updating_texture  = false

    init {
        holder.addCallback(this)
        val display_manager = context.getSystemService(DISPLAY_SERVICE) as DisplayManager
        val display: Display? = display_manager.getDisplay(m_displayID)
        if (display !== null) {
            // flag = 4 : Private Display
            if(display.flags != 4) {
                m_layoutParmas = newLayoutParams(m_posSize)
                val displayContext: Context = context.createDisplayContext(display)
                m_window_manager = displayContext.getSystemService(WINDOW_SERVICE) as WindowManager
                m_window_manager!!.addView(this, m_layoutParmas)
                if (m_source !== null) {
                    m_responsible_updating_texture =
                        !m_source!!.getSurfaceTexture()!!.get_already_binding()
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
        if(m_window_manager !== null && m_layoutParmas !== null) {
            m_layoutParmas!!.x = m_posSize.x
            m_layoutParmas!!.y = m_posSize.y

            m_window_manager?.updateViewLayout(this, m_layoutParmas)
        }
    }

    fun hide(){
        if(m_window_manager !== null && m_layoutParmas !== null) {
            m_layoutParmas?.x = -m_posSize.width
            m_layoutParmas?.y = -m_posSize.height

            m_window_manager?.updateViewLayout(this, m_layoutParmas)
        }
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
            return m_source!!.getSurfaceTexture()!!.getTextureID()
        }
        else{
            return  -1
        }
    }

    override fun getUpdatingTexture() : Boolean{
        return m_responsible_updating_texture
    }

    override fun getSource() : iSurfaceSource?{
        return m_source
    }

    override fun getEGLContext() : EGLContext?{
        return eglContext
    }

    override fun getEGLRender() : EGLRender?{
        return m_EGLRender
    }

    fun setSurfaceAndEglContext(surface: Surface?, eglContext: EGLContext?) {
        this.m_surface = surface
        this.eglContext = eglContext
    }

    fun setTextureCrop(cropArea: vCropTextureArea){
        mTextureSize.offsetX = cropArea.offsetX
        mTextureSize.offsetY = cropArea.offsetY
        mTextureSize.cropWidth  = cropArea.width
        mTextureSize.cropHeight = cropArea.height
    }

    fun requestRender() {
        if (eglThread != null) {
            //Log.d(m_tag,"requestRender")
            eglThread!!.requestRender()
        }
    }


    override fun surfaceCreated(holder: SurfaceHolder) {
        auosurfaceCreated(holder)
    }

    fun auosurfaceCreated(holder: SurfaceHolder?) {
        Log.d(m_tag,"surfaceCreated")
        if (m_surface == null) {
            m_surface = holder?.surface
        }
        if (m_surface == null) {
            return
        }
        if(m_source !== null) {
            m_EGLRender = EGLRender(
                m_context,
                m_source!!.getSurfaceTexture()!!.getTextureID(),
                m_isDeWarp
            )

            m_source!!.getSurfaceTexture()?.addListener { this.requestRender() }
            setTextureCrop(m_cropTextureArea)
            mTextureSize.width = m_source!!.getSurfaceTexture()!!.getWidth()
            mTextureSize.height = m_source!!.getSurfaceTexture()!!.getHeight()

            m_EGLRender!!.setTextureSize(mTextureSize)


            eglThread = EGLThread(WeakReference(this))
            eglThread!!.start()
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        eglThread!!.width = width
        eglThread!!.height = height
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        eglThread!!.onDestory()
        eglThread = null
        m_surface = null
        eglContext = null
    }

    fun destroyed(){
        m_window_manager?.removeViewImmediate(this)
    }

    // Override this method to handle touch events
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if(m_source !== null && m_touchMapping != null) {
            val touchmapper: iTouchMapper = m_source as iTouchMapper
            val pointerCount = event.pointerCount
            val pointerProperties =
                arrayOfNulls<PointerProperties>(pointerCount)
            val pointerCoords = arrayOfNulls<PointerCoords>(pointerCount)
            for (i in 0 until pointerCount) {
                pointerProperties[i] = PointerProperties()
                pointerProperties[i]!!.id = event.getPointerId(i)
                pointerProperties[i]!!.toolType = event.getToolType(i)
                pointerCoords[i] = PointerCoords()
                pointerCoords[i]!!.x =
                    event.getX(i) * (m_touchMapping.width - 1) / (this.width - 1) + m_touchMapping.offsetX
                pointerCoords[i]!!.y =
                    event.getY(i) * (m_touchMapping.height - 1) / (this.height - 1) + m_touchMapping.offsetY
                pointerCoords[i]!!.pressure = event.getPressure(i)
                pointerCoords[i]!!.size = event.getSize(i)
            }

            val newevent: MotionEvent = MotionEvent.obtain(
                event.downTime, event.eventTime, event.action, event.pointerCount,
                pointerProperties, pointerCoords, event.metaState, event.buttonState,
                event.xPrecision, event.yPrecision, event.deviceId, event.edgeFlags,
                event.source, event.flags
            )
            touchmapper.injectMotionEvent(newevent)
            m_DisplayViewCallback?.onTouchCallback(newevent)
        }
        return true // Return true to indicate the event was handled
    }


    override fun getSurface(): Surface? {
        return m_surface
    }


    fun setAUOGLSurfaceViewCallback(callback: DisplayViewCallback) {
        m_DisplayViewCallback = callback
    }

}