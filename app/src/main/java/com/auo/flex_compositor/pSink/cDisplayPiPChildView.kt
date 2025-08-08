package com.auo.flex_compositor.pSink

import android.app.Service.DISPLAY_SERVICE
import android.app.Service.WINDOW_SERVICE
import android.content.Context
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display
import android.view.MotionEvent
import android.view.Surface
import android.view.WindowManager
import com.auo.flex_compositor.pFilter.cViewMux
import com.auo.flex_compositor.pFilter.cViewSwitch
import com.auo.flex_compositor.pInterface.SerializablePointerCoords
import com.auo.flex_compositor.pInterface.SerializablePointerProperties
import com.auo.flex_compositor.pInterface.cMotionEvent
import com.auo.flex_compositor.pInterface.deWarp_Parameters
import com.auo.flex_compositor.pInterface.iSurfaceSource
import com.auo.flex_compositor.pInterface.iTouchMapper
import com.auo.flex_compositor.pInterface.vCropTextureArea
import com.auo.flex_compositor.pInterface.vPos_Size
import com.auo.flex_compositor.pInterface.vTouchMapping

class cDisplayPiPChildView(context: Context, override val e_name: String, override val e_id: Int, source: iSurfaceSource, displayID: Int,
                           posSize: vPos_Size, cropTextureArea: vCropTextureArea, touchMapping: vTouchMapping?, dewarpParameters: deWarp_Parameters?
) :cDisplayView(context, e_name,  e_id, source, displayID, posSize, cropTextureArea, touchMapping, dewarpParameters) {

    private var m_childSwitch: cViewSwitch? = null
    private var m_mux: cViewMux? = null

    init {
        when (source) {
            is cViewSwitch -> {
                val viewSwitch = source as cViewSwitch
                m_childSwitch = viewSwitch
            }
        }
    }

    fun setMux(viewMux: cViewMux){
        m_mux = viewMux
    }

    override fun onTouchEvent(motionEvent: MotionEvent): Boolean {
        Log.d("cDisplayPiPChildView", "cDisplayPiPChildView onTouchEvent")
        detectLongPress(motionEvent)
        when (motionEvent.actionMasked) {
            MotionEvent.ACTION_UP -> {
                if(m_mux != null && m_childSwitch != null){
                    m_mux!!.exchangePiP(m_childSwitch!!)
                }
            }
        }

        return true // Return true to indicate the event was handled
    }

}