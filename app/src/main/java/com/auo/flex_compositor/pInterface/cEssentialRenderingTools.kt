package com.auo.flex_compositor.pInterface

import android.view.Surface
import com.auo.flex_compositor.pEGLFunction.EGLRender
import android.opengl.EGLContext

data class deWarp_Parameters(val vertices : ArrayList<Float>, val textcoods : ArrayList<Float>,
                             var column : Int, var row : Int)

interface iEssentialRenderingTools {
    fun getSurface(): Surface?
    fun getEGLContext() : EGLContext?
    fun getEGLRender() : EGLRender?
    fun getSource() : iSurfaceSource
    fun Sync(sync_count: Int): Boolean
}