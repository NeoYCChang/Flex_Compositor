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
                  switchesParm: MutableList<viewSwitchParm>, defaultChannel: Int = 0): iSurfaceSource  {

    data class viewSwitchParm(val surfaceSource: iSurfaceSource?, val channel: Int,
                              val crop_texture: vCropTextureArea, val touchMapping: vTouchMapping,
                              val dewarpParameters: deWarp_Parameters?)

    private val m_sourceList: MutableList<iSurfaceSource?> = mutableListOf<iSurfaceSource?>()
    private val m_channels: MutableList<Int> = mutableListOf<Int>()
    private var m_notNullSourceChannelIndex = 0
    private var m_nowChannelIndex = 0
    private var m_nowChannel = 0
    //private var m_nowChannel = 0
    private val m_crop_texture: MutableList<vCropTextureArea> = mutableListOf<vCropTextureArea>()
    private val m_touchmapping: MutableList<vTouchMapping> = mutableListOf<vTouchMapping>()
    private val m_dewarpParameters: MutableList<deWarp_Parameters?> = mutableListOf<deWarp_Parameters?>()
    private val m_switchIsTriggered = mutableListOf<() -> Unit>()
    private var m_defaultChannel = 0
    private val m_renderTriggered = mutableListOf<(cSurfaceTexture?) -> Unit>()
    private var m_blackScreenMode = false
    private val m_tag = "cViewSwitch"

    init{
        for(param in switchesParm){
            m_sourceList.add(param.surfaceSource)
            m_channels.add(param.channel)
            m_crop_texture.add(param.crop_texture)
            m_touchmapping.add(param.touchMapping)
            m_dewarpParameters.add(param.dewarpParameters)
        }

        var defaultIndex = getChannelIndex(defaultChannel)
        if(defaultIndex == -1){
            defaultIndex = 0
        }
        var nowChannelIndex = 0
        Log.d(m_tag, "$m_channels")
        for(i in defaultIndex until m_channels.size){
            if(m_sourceList[i] != null){
                m_defaultChannel = m_channels[i]
                m_nowChannel = m_channels[i]
                nowChannelIndex = i
                break
            }
        }
        if(nowChannelIndex < m_sourceList.size){
            m_nowChannelIndex = nowChannelIndex
            m_notNullSourceChannelIndex = nowChannelIndex
            m_sourceList[m_nowChannelIndex]!!.triggerRenderSubscribe(::onTriggerRenderCallback)
        }

    }

    override fun getEGLContext(): EGLContext?{
        if(m_nowChannelIndex < m_sourceList.size){
            if(m_sourceList[m_nowChannelIndex] != null) {
                return m_sourceList[m_nowChannelIndex]!!.getEGLContext()
            }
            else{
                return m_sourceList[m_notNullSourceChannelIndex]!!.getEGLContext()
            }
        }
        else
        {
            return m_sourceList[m_notNullSourceChannelIndex]!!.getEGLContext()
        }
    }
    override fun getSurfaceTexture(): cSurfaceTexture{
        if(m_nowChannelIndex < m_sourceList.size){
            if(m_sourceList[m_nowChannelIndex] != null) {
                return m_sourceList[m_nowChannelIndex]!!.getSurfaceTexture()
            }
            else{
                Log.d(m_tag, "m_sourceList[m_notNullSourceChannelIndex]!!.getSurfaceTexture()")
                return m_sourceList[m_notNullSourceChannelIndex]!!.getSurfaceTexture()
            }
        }
        else{
            return m_sourceList[m_notNullSourceChannelIndex]!!.getSurfaceTexture()
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
        if(m_nowChannelIndex < m_sourceList.size){
            if(m_sourceList[m_nowChannelIndex] != null) {
                return m_sourceList[m_nowChannelIndex]!!.injectMotionEvent(motionEvent)
            }
            else{
                return m_sourceList[m_notNullSourceChannelIndex]!!.injectMotionEvent(motionEvent)
            }
        }
        else{
            return m_sourceList[m_notNullSourceChannelIndex]!!.injectMotionEvent(motionEvent)
        }
    }


    fun switchToChannel(channel: Int): Boolean{
        Log.d(m_tag, "$channel ${m_sourceList.size}")
        //If channel is set 0, screen set to black.
        if(channel == 0){
            setBlackScreen()
            return true
        }
        var nowChannel = getChannelIndex(channel)
        if(nowChannel == -1){
            return false
        }

        if(nowChannel < m_sourceList.size){
            m_blackScreenMode = false
            //m_sourceList[m_nowChannel].getSurfaceTexture().switcherChange() //To release the condition_variable
            m_sourceList[m_nowChannelIndex]!!.triggerRenderUnsubscribe(::onTriggerRenderCallback)
            if(m_sourceList[nowChannel] != null) {
                m_nowChannelIndex = nowChannel
                m_nowChannel = channel
            }
            else{
                setBlackScreen()
                return true
            }
            //onTriggerRenderCallback(null)
            m_sourceList[m_nowChannelIndex]!!.triggerRenderSubscribe(::onTriggerRenderCallback)
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
        return m_crop_texture[m_nowChannelIndex]
    }

    fun getTouchMapping(): vTouchMapping{
        return m_touchmapping[m_nowChannelIndex]
    }

    fun triggerSubscribe(handler: () -> Unit) {
        m_switchIsTriggered.add(handler)
    }

    fun triggerUnsubscribe(handler: () -> Unit) {
        m_switchIsTriggered.remove(handler)
    }

    fun getChannelIndex(channel: Int): Int {
        var nowChannelIndex = -1
        for (i in 0 until m_channels.size) {
            if (m_channels[i] == channel) {
                nowChannelIndex = i
                break
            }
        }
        return nowChannelIndex
    }

    fun getNowChannel(): Int{
        return m_nowChannel
    }

    private fun setBlackScreen(){
        m_blackScreenMode = true
        m_sourceList[m_nowChannelIndex]!!.triggerRenderUnsubscribe(::onTriggerRenderCallback)
        onTriggerRenderCallback(null)
    }

    private fun invoke() {
        for (handler in m_switchIsTriggered) {
            handler()
        }
    }
}