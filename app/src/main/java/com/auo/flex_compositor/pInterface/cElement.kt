package com.auo.flex_compositor.pInterface

import android.view.Surface
import com.auo.flex_compositor.pEGLFunction.EGLRender
import com.auo.flex_compositor.pSource.cVirtualDisplay
import com.auo.flex_compositor.pView.cSurfaceTexture
import android.opengl.EGLContext

data class vPos_Size(var x: Int, var y: Int, var width: Int, var height: Int)
data class vCropTextureArea(var offsetX: Int, var offsetY: Int, var width: Int, var height: Int)
data class vSize(var width: Int, var height: Int)
interface iElement {
    val e_name: String
    val e_id: Int
}

interface iSurfaceSource: iElement, iTouchMapper {
    fun getEGLContext(): EGLContext?
    fun getSurfaceTexture(): cSurfaceTexture
    fun triggerRenderSubscribe(handler: (cSurfaceTexture?) -> Unit)
    fun triggerRenderUnsubscribe(handler: (cSurfaceTexture?) -> Unit)
}
