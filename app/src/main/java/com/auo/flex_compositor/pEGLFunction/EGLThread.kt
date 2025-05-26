package com.auo.flex_compositor.pEGLFunction

import android.util.Log
import com.auo.flex_compositor.pInterface.iEssentialRenderingTools
import com.auo.flex_compositor.pSink.cDisplayView
import java.lang.ref.WeakReference
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.microedition.khronos.egl.EGLContext
import kotlin.concurrent.read
import kotlin.concurrent.write

class EGLThread(private var myRenderingTools: WeakReference<iEssentialRenderingTools>, private var isMainEGLthread: Boolean = false) :
    Thread() {
    private var eglHelper: EGLHelper? = null
    private var isExit = false
    private var isCreate: Boolean = true
    private var m_sync_count: Int =0
    private var m_hasRenderRequest: Boolean = true
    private val m_tag = "EGLThread"

    companion object {
        // Static-like function
        private val  m_ReadWriteLock: ReentrantReadWriteLock = ReentrantReadWriteLock()
        private val `subObject`: Any = Any()
    }

    // Used to control manual refresh
    private val `mainObject`: Any = Any()
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
            if(isMainEGLthread){
                synchronized(`mainObject`) {
                    if (!m_hasRenderRequest) {
                        try {
                            (`mainObject` as Object).wait(1024)
                        } catch (e: InterruptedException) {
                            e.printStackTrace()
                        }
                    }
                    m_hasRenderRequest = false // reset after being notified
                    onUpdateTexure()
                    requestSubRender()
                }
            }
            else{
                synchronized(`subObject`) {
                    if (!m_hasRenderRequest) {
                        try {
                            (`subObject` as Object).wait()
                        } catch (e: InterruptedException) {
                            e.printStackTrace()
                        }
                    }
                    m_hasRenderRequest = false // reset after being notified
                }
            }

            if(isMainEGLthread) {
                val current = LocalDateTime.now()
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss:SSS")
                val formatted = current.format(formatter)
                Log.d("thread", "main ${formatted}")
            }
            else{
                val current = LocalDateTime.now()
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss:SSS")
                val formatted = current.format(formatter)
                Log.d("thread", "sub ${formatted}")
            }
            //sleep(33,670000)
            onChange(width, height)
            if(myRenderingTools.get()!!.Sync(m_sync_count)){
                m_sync_count = 0
            }
            onDraw()
            updateSyncCount()
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
        if (myRenderingTools.get()!!.getEGLRender() != null) {
            myRenderingTools.get()!!.getEGLRender()!!.onSurfaceChanged(width, height)
        }
    }


    private fun onUpdateTexure() {
        m_ReadWriteLock.write {
            if(myRenderingTools.get()!!.getSource().getSurfaceTexture() != null) {
                if(!myRenderingTools.get()!!.getSource().getSurfaceTexture()!!.isReleased) {
                    myRenderingTools.get()!!.getSource().getSurfaceTexture()?.updateTexImage()
                }
            }
        }
    }

    /**
     * Draw, execute every loop
     */
    private fun onDraw() {
        if (myRenderingTools.get()!!.getEGLRender() != null && eglHelper != null) {
            m_ReadWriteLock.read {
                myRenderingTools.get()!!.getEGLRender()!!.onDrawFrame()
                eglHelper?.swapBuffers()
            }
        }
    }

    private fun updateSyncCount(){
//        val current = LocalDateTime.now()
//        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
//        val formatted = current.format(formatter)
//        Log.d("drawing", "drawing ${m_sync_count}  ${formatted}")
        if(m_sync_count == Int.MAX_VALUE){
            m_sync_count = 0
        }
        else {
            m_sync_count++
        }
    }

    /**
     * Manual refresh
     * Release the blocking wait in the thread
     */
    internal fun requestRender() {
        synchronized(`mainObject`) {
            m_hasRenderRequest = true
            (`mainObject` as Object).notifyAll()
        }
    }

    internal fun requestSubRender() {
        synchronized(`subObject`) {
            (`subObject` as Object).notifyAll()
        }
    }

    fun onDestory() {
        isExit = true
        if(isMainEGLthread){
            Log.d(m_tag, "Destory main EGLthread")
            requestRender()
        }
        else{
            Log.d(m_tag, "Destory Sub EGLthread")
            requestSubRender()
        }
    }

    private fun release() {
        if (eglHelper != null) {
            eglHelper?.destoryEgl()
            eglHelper = null
        }
    }

    fun getEglContext(): EGLContext? {
        if (eglHelper != null) {
            return eglHelper?.getmEglContext()
        }
        return null
    }
}