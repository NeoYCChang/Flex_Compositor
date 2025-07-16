package com.auo.flex_compositor.pFilter

import android.util.Log
import com.auo.flex_compositor.pInterface.cMotionEvent
import com.auo.flex_compositor.pInterface.deWarp_Parameters
import com.auo.flex_compositor.pInterface.iSurfaceSource
import com.auo.flex_compositor.pInterface.vCropTextureArea
import com.auo.flex_compositor.pInterface.vPos_Size
import com.auo.flex_compositor.pInterface.vTouchMapping
import com.auo.flex_compositor.pView.cSurfaceTexture
import android.opengl.EGLContext

class cViewSwitch(override val e_name: String, override val e_id: Int,
                  sourceList: MutableList<iSurfaceSource?>, channel: MutableList<Int>,
                  posSize: MutableList<vPos_Size>,
                  crop_texture: MutableList<vCropTextureArea>,
                  touchmapping: MutableList<vTouchMapping>,
                  dewarpParameters: MutableList<deWarp_Parameters?>): iSurfaceSource  {
    private val m_sourceList: MutableList<iSurfaceSource?> = sourceList
    private val m_channels = channel
    private var m_notNullSourceChannel = 0
    private var m_nowChannel = 0
    private val m_posSize: MutableList<vPos_Size> = posSize
    private val m_crop_texture: MutableList<vCropTextureArea> = crop_texture
    private val m_touchmapping: MutableList<vTouchMapping> = touchmapping
    private val m_dewarpParameters: MutableList<deWarp_Parameters?> = dewarpParameters
    private val m_switchIsTriggered = mutableListOf<() -> Unit>()
    private var m_defaultChannel = 0
    private val m_renderTriggered = mutableListOf<(cSurfaceTexture?) -> Unit>()
    private var m_blackScreenMode = false
    private val m_tag = "cViewSwitch"

    init{
        var nowChannel = 0
        Log.d(m_tag, "$m_channels")
        for(i in 0 until m_channels.size){
            if(m_sourceList[i] != null){
                m_defaultChannel = m_channels[i]
                m_notNullSourceChannel = m_defaultChannel
                nowChannel = i
                break
            }
        }
        if(nowChannel < m_sourceList.size){
            m_nowChannel = nowChannel
            m_sourceList[m_nowChannel]!!.triggerRenderSubscribe(::onTriggerRenderCallback)
        }

    }

    override fun getEGLContext(): EGLContext?{
        if(m_nowChannel < m_sourceList.size){
            if(m_sourceList[m_nowChannel] != null) {
                return m_sourceList[m_nowChannel]!!.getEGLContext()
            }
            else{
                return m_sourceList[m_notNullSourceChannel]!!.getEGLContext()
            }
        }
        else
        {
            return m_sourceList[m_notNullSourceChannel]!!.getEGLContext()
        }
    }
    override fun getSurfaceTexture(): cSurfaceTexture{
        if(m_nowChannel < m_sourceList.size){
            if(m_sourceList[m_nowChannel] != null) {
                return m_sourceList[m_nowChannel]!!.getSurfaceTexture()
            }
            else{
                return m_sourceList[m_notNullSourceChannel]!!.getSurfaceTexture()
            }
        }
        else{
            return m_sourceList[m_notNullSourceChannel]!!.getSurfaceTexture()
        }
    }

    @Synchronized
    private fun onTriggerRenderCallback(surfaceTexture: cSurfaceTexture?) {
        if(m_blackScreenMode){
            m_renderTriggered.forEach { it(null) }
        }
        else{
            m_renderTriggered.forEach { it(surfaceTexture) }
        }
    }
    @Synchronized
    override fun triggerRenderSubscribe(handler: (cSurfaceTexture?) -> Unit){
        m_renderTriggered.add(handler)
    }
    @Synchronized
    override fun triggerRenderUnsubscribe(handler: (cSurfaceTexture?) -> Unit){
        m_renderTriggered.remove(handler)
    }

    override fun injectMotionEvent(motionEvent: cMotionEvent){
        if(m_nowChannel < m_sourceList.size){
            if(m_sourceList[m_nowChannel] != null) {
                return m_sourceList[m_nowChannel]!!.injectMotionEvent(motionEvent)
            }
            else{
                return m_sourceList[m_notNullSourceChannel]!!.injectMotionEvent(motionEvent)
            }
        }
        else{
            return m_sourceList[m_notNullSourceChannel]!!.injectMotionEvent(motionEvent)
        }
    }


    fun switchToChannel(channel: Int): Boolean{
        Log.d(m_tag, "$channel ${m_sourceList.size}")
        if(channel == 0){
            setBlackScreen()
            return true
        }
        var nowChannel = getChannelIndex(channel)

        if(nowChannel < m_sourceList.size){
            m_blackScreenMode = false
            //m_sourceList[m_nowChannel].getSurfaceTexture().switcherChange() //To release the condition_variable
            m_sourceList[m_nowChannel]!!.triggerRenderUnsubscribe(::onTriggerRenderCallback)
            if(m_sourceList[nowChannel] != null) {
                m_nowChannel = nowChannel
            }
            else{
                m_nowChannel = m_notNullSourceChannel
                setBlackScreen()
                return true
            }
            m_sourceList[m_nowChannel]!!.triggerRenderSubscribe(::onTriggerRenderCallback)
            onTriggerRenderCallback(getSurfaceTexture())
            invoke()
            return true
        }
        return false
    }

    fun switchToDefaultChannel(){
        switchToChannel(m_defaultChannel)
    }

    fun setDefaultChannel(channel: Int){
        m_defaultChannel = channel
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

    fun getChannelIndex(channel: Int): Int {
        var nowChannel = m_notNullSourceChannel
        for (i in 0 until m_channels.size) {
            if (m_channels[i] == channel) {
                nowChannel = i
                break
            }
        }
        return nowChannel
    }

    private fun setBlackScreen(){
        m_blackScreenMode = true
        m_sourceList[m_nowChannel]!!.triggerRenderUnsubscribe(::onTriggerRenderCallback)
        onTriggerRenderCallback(null)
    }

    private fun invoke() {
        for (handler in m_switchIsTriggered) {
            handler()
        }
    }
}