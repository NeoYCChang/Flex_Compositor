package com.auo.flex_compositor.pView

import android.graphics.SurfaceTexture
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
    private var m_callback: ((cSurfaceTexture) -> Unit)? = null
    private val m_lock = ReentrantReadWriteLock()
    private val m_readLock = m_lock.readLock()
    private val m_writeLock = m_lock.writeLock()
//    private val `textureObject`: Any = Any()

    init {
        setOnFrameAvailableListener(object : SurfaceTexture.OnFrameAvailableListener {
            override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
                //Log.d(m_tag, "${formatted}")
                m_writeLock.lock()
                if(surfaceTexture != null) {
                    if(!surfaceTexture.isReleased) {
                        updateTexImage(surfaceTexture)
                    }
                }
                m_writeLock.unlock()
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
//            synchronized(`textureObject`) {
//                try {
                    if(!surfaceTexture.isReleased) {
                        surfaceTexture.updateTexImage()
                        m_callback?.invoke(this)
//                        (`textureObject` as Object).notifyAll()
                    }

//                } catch (e: InterruptedException) {
//                    e.printStackTrace()
//                }
//            }
        }
    }

//    fun switcherChange(){
//        synchronized(`textureObject`) {
//            try {
//                (`textureObject` as Object).notifyAll()
//            } catch (e: InterruptedException) {
//                e.printStackTrace()
//            }
//        }
//    }

//    fun waitUpdateTexImage(){
//        synchronized(`textureObject`) {
//            try {
//                (`textureObject` as Object).wait(1000)
//            } catch (e: InterruptedException) {
//                e.printStackTrace()
//            }
//        }
//    }

    fun setTriggerRender(handler: ((cSurfaceTexture) -> Unit)?){
        m_callback = handler
    }

    fun lock(){
        m_readLock.lock()
    }

    fun unlock(){
        m_readLock.unlock()
    }

}