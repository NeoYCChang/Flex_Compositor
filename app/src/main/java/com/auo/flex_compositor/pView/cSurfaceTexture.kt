package com.auo.flex_compositor.pView

import android.graphics.SurfaceTexture
import android.util.Log
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class cSurfaceTexture(textureId: Int) : SurfaceTexture(textureId) {

    // Companion object to define static-like functions and properties
    companion object {
        // Static-like function
    }
    // A list to hold all event listeners
    private var m_listener: (() -> Unit)? = null
    private val m_tag = "AUOSurfaceTexture"
    private var m_width : Int = 960
    private var m_height : Int = 960
    private var m_textureId: Int = textureId


    init {
        setOnFrameAvailableListener(object : SurfaceTexture.OnFrameAvailableListener {
            override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
                val current = LocalDateTime.now()
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss:SSS")
                val formatted = current.format(formatter)
                Log.d(m_tag, "${formatted}")
                triggerEvent();
                //Log.d("MainActivity", "Frame available: processing frame")
            }
        })
    }


    // Add a listener
    fun setListener(listener: () -> Unit): Boolean {
        if(m_listener == null) {
            m_listener = listener
            return true
        }
        Log.d(m_tag, "listener has already been set.")
        return false
    }

    // Trigger the event (calls all listeners)
    fun triggerEvent() {
        //Log.d(m_tag,"triggerEvent")
        m_listener?.let { it() }
    }

    override fun setDefaultBufferSize(width: Int, height: Int) {
        super.setDefaultBufferSize(width, height)
        m_width = width
        m_height = height
    }

    fun getWidth(): Int{
        return m_width
    }

    fun getHeight(): Int{
        return m_height
    }

    fun getTextureID(): Int{
        return m_textureId
    }

}