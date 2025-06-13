package com.auo.flex_compositor.pView

import android.graphics.SurfaceTexture
import android.util.Log
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.locks.ReentrantReadWriteLock

class cSurfaceTexture(textureId: Int) : SurfaceTexture(textureId) {

    // Companion object to define static-like functions and properties
    companion object {
        // Static-like function
    }
    // A list to hold all event listeners
    private val m_tag = "AUOSurfaceTexture"
    private var m_width : Int = 960
    private var m_height : Int = 960
    private var m_textureId: Int = textureId
    private val `textureObject`: Any = Any()

    init {
        setOnFrameAvailableListener(object : SurfaceTexture.OnFrameAvailableListener {
            override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
                val current = LocalDateTime.now()
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss:SSS")
                val formatted = current.format(formatter)
                //Log.d(m_tag, "${formatted}")
                updateTexImage(surfaceTexture)
                //Log.d("MainActivity", "Frame available: processing frame")
            }
        })
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

    private fun updateTexImage(surfaceTexture: SurfaceTexture?){
        if(surfaceTexture != null) {
            synchronized(`textureObject`) {
                try {
                    surfaceTexture!!.updateTexImage()
                    (`textureObject` as Object).notifyAll()
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun switcherChange(){
        synchronized(`textureObject`) {
            try {
                (`textureObject` as Object).notifyAll()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
    }

    fun waitUpdateTexImage(){
        synchronized(`textureObject`) {
            try {
                (`textureObject` as Object).wait(1000)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
    }

}