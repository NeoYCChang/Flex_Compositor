package com.auo.flex_compositor.pView

import android.graphics.SurfaceTexture
import android.util.Log

class cSurfaceTexture(textureId: Int) : SurfaceTexture(textureId) {

    // Companion object to define static-like functions and properties
    companion object {
        // Static-like function
    }
    // A list to hold all event listeners
    private val listeners = mutableListOf<() -> Unit>()
    private val m_tag = "AUOSurfaceTexture"
    private var m_width : Int = 960
    private var m_height : Int = 960
    private var m_textureId: Int = textureId
    private var already_binding : Boolean = false


    init {
        setOnFrameAvailableListener(object : SurfaceTexture.OnFrameAvailableListener {
            override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
                triggerEvent();
                //Log.d("MainActivity", "Frame available: processing frame")
            }
        })
    }

    fun get_already_binding(): Boolean{
        val isbinding = already_binding
        if(!already_binding){
            already_binding = true
        }
        return isbinding
    }

    // Add a listener
    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    // Trigger the event (calls all listeners)
    fun triggerEvent() {
        //Log.d(m_tag,"triggerEvent")
        listeners.forEach { it() }
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