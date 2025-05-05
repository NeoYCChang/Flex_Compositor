package com.auo.flex_compositor.pInterface

import android.view.Surface
import com.auo.flex_compositor.pEGLFunction.EGLRender
import javax.microedition.khronos.egl.EGLContext

interface iEssentialRenderingTools {
    fun getSurface(): Surface?
    fun getEGLContext() : EGLContext?
    fun getEGLRender() : EGLRender?
    fun getUpdatingTexture() : Boolean
    fun getSource() : iSurfaceSource?
}