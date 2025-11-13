package com.auo.flex_compositor.pEGLFunction

import android.util.Log
import com.auo.flex_compositor.pInterface.iEssentialRenderingTools
import com.auo.flex_compositor.pSink.cDisplayView
import java.lang.ref.WeakReference
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.locks.ReentrantReadWriteLock
import android.opengl.EGLContext
import com.auo.flex_compositor.pView.cSurfaceTexture
import kotlin.concurrent.read
import kotlin.concurrent.write

class EGLThread(private var myRenderingTools: WeakReference<iEssentialRenderingTools>) :
    Thread() {
    private var eglHelper: EGLHelper? = null
    private var isExit = false
    private var isCreate: Boolean = true
    private val m_textureQueue = RollingQueue<cSurfaceTexture?>(2)
    private var m_width: Int = 0
    private var m_height: Int = 0
    private var m_waitTextureCounter = 20
    private val m_tag = "EGLThread"


    var width: Int = 0
    var height: Int = 0

    override fun run() {
        super.run()

        isExit = false
        eglHelper = EGLHelper()
        if(myRenderingTools.get()!!.getSurface() != null && myRenderingTools.get()!!.getEGLContext() != null) {
            eglHelper?.initEgl(
                myRenderingTools.get()!!.getSurface(),
                myRenderingTools.get()!!.getEGLContext()
            )
            myRenderingTools.get()!!.getSource().triggerRenderSubscribe(::onTriggerRenderCallback)
        }
        else{
            Log.e("render_thread", "RenderingTool has no Surface or EGLContext")
            return
        }
        onCreate()
        while (true) {

            if (isExit) {
                // Release resources
                Log.d(m_tag, "isExit")
                release()
                break
            }
            onChange(width, height)
            if(m_textureQueue.isEmpty()){
                if(m_waitTextureCounter > 20) {
                    if (eglHelper!!.makeCurrent()) {
                        onDraw(null)
                    }
                }
                else{
                    m_waitTextureCounter++
                }
                Thread.sleep(17)
                //Log.d(m_tag, "loading draw")
                continue
            }
            m_waitTextureCounter  = 0
            val surfaceTexture: cSurfaceTexture? = m_textureQueue.poll()
            //sleep(33,670000)
            myRenderingTools.get()!!.Sync()
            if(eglHelper!!.makeCurrent()) {
                onDraw(surfaceTexture)
            }
        }
    }

    /**
     * Create, execute once
     */
    private fun onCreate() {
        if (isCreate && myRenderingTools.get()!!.getEGLRender() != null) {
            isCreate = false
            myRenderingTools!!.get()!!.getEGLRender()!!.onSurfaceCreated()
        }
    }

    /**
     * Change, execute once
     *
     * @param width
     * @param height
     */
    private fun onChange(width: Int, height: Int) {
        if(m_width != width || m_height != height) {
            m_width = width
            m_height = height
            if (myRenderingTools.get()!!.getEGLRender() != null) {
                myRenderingTools.get()!!.getEGLRender()!!.onSurfaceChanged(width, height)
            }
        }
    }

    //    private fun waitUpdateTexImage(){
//        if(myRenderingTools.get()!!.getSource().getSurfaceTexture() != null) {
//            if(!myRenderingTools.get()!!.getSource().getSurfaceTexture().isReleased) {
//                myRenderingTools.get()!!.getSource().getSurfaceTexture().waitUpdateTexImage()
//            }
//        }
//    }
    private fun textureLock(surfaceTexture: cSurfaceTexture){
        surfaceTexture.lock()
    }

    private fun textureUnLock(surfaceTexture: cSurfaceTexture){
        surfaceTexture.unlock()
    }

    /**
     * Draw, execute every loop
     */
    private fun onDraw(surfaceTexture: cSurfaceTexture?) {
        if (myRenderingTools.get()!!.getEGLRender() != null && eglHelper != null) {
            if(surfaceTexture != null) {
                textureLock(surfaceTexture)
                myRenderingTools.get()!!.getEGLRender()!!.onDrawFrame(surfaceTexture.getTextureID())
                eglHelper?.swapBuffers()
                textureUnLock(surfaceTexture)
            }else{
                myRenderingTools.get()!!.getEGLRender()!!.onDrawFrame(0)
                eglHelper?.swapBuffers()
            }
        }

    }


    fun onDestory() {
        isExit = true
    }

    private fun release() {
        Log.d(m_tag, "release")
        myRenderingTools.get()!!.getSource().triggerRenderUnsubscribe(::onTriggerRenderCallback)
        if (eglHelper != null) {
            eglHelper?.destroyEgl()
            eglHelper = null
        }
    }

    fun getEglContext(): EGLContext? {
        if (eglHelper != null) {
            return eglHelper?.getmEglContext()
        }
        return null
    }

    private fun onTriggerRenderCallback(surfaceTexture: cSurfaceTexture?) {
        m_textureQueue.add(surfaceTexture)
    }

    class RollingQueue<T>(private val maxSize: Int) {
        private val queue = ArrayDeque<T>()

        @Synchronized
        fun add(item: T) {
            if (queue.size >= maxSize) {
                queue.removeFirst()
            }
            queue.addLast(item)
        }

        @Synchronized
        fun poll(): T? = queue.removeFirstOrNull()

        @Synchronized
        fun peek(): T? = queue.firstOrNull()

        @Synchronized
        fun toList(): List<T> = queue.toList()

        @Synchronized
        fun size(): Int = queue.size

        @Synchronized
        fun isEmpty(): Boolean = queue.isEmpty()
    }
}