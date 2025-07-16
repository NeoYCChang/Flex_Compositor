package com.auo.flex_compositor.pEGLFunction

import android.opengl.*
import android.view.Surface
import android.view.SurfaceControl


/**
 * 1. Get the EGL instance:
 * 2. Get the default display device (the window):
 * 3. Initialize the default display device:
 * 4. Set the attributes for the display device:
 * 5. Get the corresponding configuration with the specified attributes from the system:
 * 6. Create the EGLContext:
 * 7. Create the rendering Surface:
 * 8. Bind the EGLContext and Surface to the display device:
 * 9. Refresh the data and display the rendered scene:
 */
class EGLHelper {
    private var mEglDisplay: EGLDisplay? = null
    private var mEglContext: EGLContext? = null
    private var mEglSurface: EGLSurface? = null

    fun initEgl(surface: Surface?, sharedContext: EGLContext?) {
        // 1. Get EGL display
        mEglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (mEglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("Unable to get EGL14 display")
        }

        // 2. Initialize EGL
        val version = IntArray(2)
        if (!EGL14.eglInitialize(mEglDisplay, version, 0, version, 1)) {
            throw RuntimeException("Unable to initialize EGL14")
        }

        // 3. Choose EGL config
        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 8,
            EGL14.EGL_STENCIL_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_NONE
        )

        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(mEglDisplay, attribList, 0, configs, 0, configs.size, numConfigs, 0)) {
            throw RuntimeException("Unable to choose EGL config")
        }
        val eglConfig = configs[0]!!

        // 4. Create EGL context
        val contextAttribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )

        mEglContext = EGL14.eglCreateContext(
            mEglDisplay, eglConfig,
            sharedContext ?: EGL14.EGL_NO_CONTEXT,
            contextAttribs, 0
        )
        if (mEglContext == null || mEglContext == EGL14.EGL_NO_CONTEXT) {
            throw RuntimeException("Failed to create EGL context")
        }

        // 5. Create EGL surface
        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        mEglSurface = EGL14.eglCreateWindowSurface(mEglDisplay, eglConfig, surface, surfaceAttribs, 0)
        if (mEglSurface == null || mEglSurface == EGL14.EGL_NO_SURFACE) {
            throw RuntimeException("Failed to create EGL surface")
        }

        // 6. Make current
        if (!EGL14.eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext)) {
            throw RuntimeException("Failed to make EGL context current")
        }
    }

    fun swapBuffers(): Boolean {
        return EGL14.eglSwapBuffers(mEglDisplay, mEglSurface)
    }

    fun getmEglContext(): EGLContext? {
        return mEglContext
    }

    fun makeCurrent(): Boolean {
        return EGL14.eglMakeCurrent(
            mEglDisplay, mEglSurface, mEglSurface, mEglContext
        )
    }

    fun destroyEgl() {
        if (mEglDisplay != null) {
            EGL14.eglMakeCurrent(
                mEglDisplay, EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT
            )
            EGL14.eglDestroySurface(mEglDisplay, mEglSurface)
            EGL14.eglDestroyContext(mEglDisplay, mEglContext)
            EGL14.eglTerminate(mEglDisplay)

            mEglDisplay = null
            mEglSurface = null
            mEglContext = null
        }
    }
}

class StaticVariable {
    companion object {
        // Static-like function
        private val m_surfaceControl = SurfaceControl.Builder()
            .setName("AUOSurface")
            .setBufferSize(20, 20)
            .build()
        private val m_surface = Surface(m_surfaceControl)
        private val m_eglHelper: EGLHelper? = EGLHelper()
        var public_eglcontext: EGLContext? = null

        init {
            m_eglHelper?.initEgl(m_surface, null)
            public_eglcontext = m_eglHelper!!.getmEglContext()
        }
    }
}