package com.auo.flex_compositor.pEGLFunction

import android.util.Log
import com.auo.flex_compositor.pInterface.iEssentialRenderingTools
import com.auo.flex_compositor.pSink.cDisplayView
import java.lang.ref.WeakReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.microedition.khronos.egl.EGLContext
import kotlin.concurrent.read
import kotlin.concurrent.write

class EGLThread(private var myRenderingTools: WeakReference<iEssentialRenderingTools>?) :
    Thread() {
    private var eglHelper: EGLHelper? = null
    private var isExit = false
    private var isCreate: Boolean = true
    private var isStart = false

    companion object {
        // Static-like function
        private val  m_ReadWriteLock: ReentrantReadWriteLock = ReentrantReadWriteLock()
    }

    // Used to control manual refresh
    private var `object`: Any? = null
    var width: Int = 0
    var height: Int = 0
    override fun run() {
        super.run()
        isExit = false
        isStart = false
        `object` = Any()
        eglHelper = EGLHelper()
        eglHelper?.initEgl(
            myRenderingTools!!.get()!!.getSurface(),
            myRenderingTools!!.get()!!.getEGLContext()
        )
        onCreate()
        while (true) {
            if (isExit) {
                // Release resources
                release()
                break
            }
            synchronized(`object`!!) {
                try {
                    (`object` as Object).wait((1000.0f / 60.0f).toLong())
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
            onUpdateTexure()
            onChange(width, height)
            onDraw()
            isStart = true

        }
    }

    /**
     * Create, execute once
     */
    private fun onCreate() {
        if (isCreate && myRenderingTools!!.get()!!.getEGLRender() != null) {
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
        if (myRenderingTools!!.get()!!.getEGLRender() != null) {
            myRenderingTools!!.get()!!.getEGLRender()!!.onSurfaceChanged(width, height)
        }
    }


    private fun onUpdateTexure() {
        if (myRenderingTools!!.get()!!.getUpdatingTexture()) {
            m_ReadWriteLock.write {
                if(myRenderingTools!!.get()!!.getSource()!!.getSurfaceTexture() != null) {
                    if(!myRenderingTools!!.get()!!.getSource()!!.getSurfaceTexture()!!.isReleased) {
                        myRenderingTools!!.get()!!.getSource()!!.getSurfaceTexture()?.updateTexImage()
                    }
                }
            }
        }
    }

    /**
     * Draw, execute every loop
     */
    private fun onDraw() {
        if (myRenderingTools!!.get()!!.getEGLRender() != null && eglHelper != null) {
            m_ReadWriteLock.read {
                myRenderingTools!!.get()!!.getEGLRender()!!.onDrawFrame()
                //The first time you refresh, you need to refresh twice.
                if (!isStart) {
                    myRenderingTools!!.get()!!.getEGLRender()!!.onDrawFrame()
                }
                eglHelper?.swapBuffers()
            }
        }
    }

    /**
     * Manual refresh
     * Release the blocking wait in the thread
     */
    internal fun requestRender() {
        if (`object` != null) {
            synchronized(`object`!!) {
                (`object` as Object).notifyAll()
                //Log.d("requestRender","notifyAll")
            }
        }
    }

    fun onDestory() {
        isExit = true
        requestRender()
    }

    fun release() {
        if (eglHelper != null) {
            eglHelper?.destoryEgl()
            eglHelper = null
            `object` = null
            myRenderingTools = null
        }
    }

    fun getEglContext(): EGLContext? {
        if (eglHelper != null) {
            return eglHelper?.getmEglContext()
        }
        return null
    }
}