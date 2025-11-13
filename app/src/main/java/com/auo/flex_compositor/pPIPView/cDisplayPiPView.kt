package com.auo.flex_compositor.pPIPView

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import com.auo.flex_compositor.pInterface.SerializablePointerCoords
import com.auo.flex_compositor.pInterface.SerializablePointerProperties
import com.auo.flex_compositor.pInterface.cMotionEvent
import com.auo.flex_compositor.pInterface.deWarp_Parameters
import com.auo.flex_compositor.pInterface.iSurfaceSource
import com.auo.flex_compositor.pInterface.iTouchMapper
import com.auo.flex_compositor.pInterface.vCropTextureArea
import com.auo.flex_compositor.pInterface.vPos_Size
import com.auo.flex_compositor.pInterface.vTouchMapping
import com.auo.flex_compositor.pSink.cDisplayView

class cDisplayPiPView(context: Context, override val e_name: String, override val e_id: Int, source: iSurfaceSource, displayID: Int,
                      posSize: vPos_Size, cropTextureArea: vCropTextureArea, touchMapping: vTouchMapping?, dewarpParameters: deWarp_Parameters?
) : cDisplayView(context, e_name,  e_id, source, displayID, posSize, cropTextureArea, touchMapping, dewarpParameters, null) {

    override fun displayViewInit(){

    }

    override fun destroyed(){
        Thread {
            if (!m_isDestroyed) {
                eglThread?.onDestory()
                eglThread?.join()
                m_EGLRender?.release()
                m_EGLRender = null
                m_isDestroyed = true
            }
        }.start()
    }

    override fun onTouchEvent(motionEvent: MotionEvent): Boolean {
        return true // Return true to indicate the event was handled
    }
}