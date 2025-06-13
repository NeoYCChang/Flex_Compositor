package com.auo.flex_compositor.pFilter

import android.util.Log
import com.auo.flex_compositor.pInterface.cMotionEvent
import com.auo.flex_compositor.pInterface.deWarp_Parameters
import com.auo.flex_compositor.pInterface.iSurfaceSource
import com.auo.flex_compositor.pInterface.vCropTextureArea
import com.auo.flex_compositor.pInterface.vPos_Size
import com.auo.flex_compositor.pInterface.vTouchMapping
import com.auo.flex_compositor.pView.cSurfaceTexture
import javax.microedition.khronos.egl.EGLContext

class cViewSwitch(override val e_name: String, override val e_id: Int,
                  sourceList: MutableList<iSurfaceSource>, channel: MutableList<Int>,
                  posSize: MutableList<vPos_Size>,
                  crop_texture: MutableList<vCropTextureArea>,
                  touchmapping: MutableList<vTouchMapping>,
                  dewarpParameters: MutableList<deWarp_Parameters?>): iSurfaceSource  {
    private val m_sourceList: MutableList<iSurfaceSource> = sourceList
    private val m_channels = channel
    private var m_nowChannel = 0
    private val m_posSize: MutableList<vPos_Size> = posSize
    private val m_crop_texture: MutableList<vCropTextureArea> = crop_texture
    private val m_touchmapping: MutableList<vTouchMapping> = touchmapping
    private val m_dewarpParameters: MutableList<deWarp_Parameters?> = dewarpParameters
    private val m_switchIsTriggered = mutableListOf<() -> Unit>()
    private val m_tag = "cViewSwitch"

    init{
        var nowChannel = 0
        Log.d(m_tag, "$m_channels")
        for(i in 0 until m_channels.size){
            if(m_channels[i] == channel[0]){
                nowChannel = i
                break
            }
        }
        if(nowChannel < m_sourceList.size){
            m_nowChannel = nowChannel
        }

    }

    override fun getEGLContext(): EGLContext?{
        if(m_nowChannel < m_sourceList.size){
            return m_sourceList[m_nowChannel].getEGLContext()
        }
        else
        {
            return m_sourceList[0].getEGLContext()
        }
    }
    override fun getSurfaceTexture(): cSurfaceTexture{
        if(m_nowChannel < m_sourceList.size){
            return m_sourceList[m_nowChannel].getSurfaceTexture()
        }
        else{
            return m_sourceList[0].getSurfaceTexture()
        }
    }
    override fun injectMotionEvent(motionEvent: cMotionEvent){
        if(m_nowChannel < m_sourceList.size){
            return m_sourceList[m_nowChannel].injectMotionEvent(motionEvent)
        }
        else{
            return m_sourceList[0].injectMotionEvent(motionEvent)
        }
    }

    fun setChannel(channel: Int): Boolean{
        Log.d(m_tag, "$channel ${m_sourceList.size}")
        var nowChannel = 0
        for(i in 0 until m_channels.size){
            if(m_channels[i] == channel){
                nowChannel = i
                break
            }
        }

        if(nowChannel < m_sourceList.size){
            m_sourceList[m_nowChannel].getSurfaceTexture().switcherChange()
            m_nowChannel = nowChannel
            invoke()
            return true
        }
        return false
    }

    fun setDefaultChannel(){
        m_sourceList[m_nowChannel].getSurfaceTexture().switcherChange()
        m_nowChannel = 0
        invoke()
    }

    fun getCrop_texture(): vCropTextureArea{
        return m_crop_texture[m_nowChannel]
    }

    fun getTouchMapping(): vTouchMapping{
        return m_touchmapping[m_nowChannel]
    }

    fun triggerSubscribe(handler: () -> Unit) {
        m_switchIsTriggered.add(handler)
    }

    fun triggerUnsubscribe(handler: () -> Unit) {
        m_switchIsTriggered.remove(handler)
    }

    private fun invoke() {
        for (handler in m_switchIsTriggered) {
            handler()
        }
    }
}