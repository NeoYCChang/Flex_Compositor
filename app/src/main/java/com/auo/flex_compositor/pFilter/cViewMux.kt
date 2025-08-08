package com.auo.flex_compositor.pFilter

import android.util.Log
import com.auo.flex_compositor.pInterface.iElement

class cViewMux(override val e_name: String, override val e_id: Int,
               switchList: List<cViewSwitch?>, channel: List<Int>): iElement
{
    private val m_switchList: List<cViewSwitch?> = switchList
    private val m_channels = channel
    private var m_parentSwitch : cViewSwitch? = null

    fun switchMux(sourceChannel: Int, sinkChannel: Int): Boolean{
        var nowChannel = getChannelIndex(sinkChannel)
        if(nowChannel != -1){
            if(nowChannel < m_switchList.size){
                m_switchList[nowChannel]?.switchToChannel(sourceChannel)
                return true
            }
        }
        return false
    }

    fun setDefaultChannel(sourceChannel: Int, sinkChannel: Int){
        var nowChannel = getChannelIndex(sinkChannel)
        if(nowChannel != -1){
            if(nowChannel < m_switchList.size){
                m_switchList[nowChannel]?.setDefaultChannel(sourceChannel)
            }
        }
    }

    fun setPiP_parentSwitch(viewSwitch: cViewSwitch){
        m_parentSwitch = viewSwitch
    }

    fun exchangePiP(childSwitch: cViewSwitch){
        if(m_parentSwitch != null){
            if(childSwitch in m_switchList){
                val parentChannel = m_parentSwitch!!.getNowChannel()
                val childChannel = childSwitch.getNowChannel()
                m_parentSwitch!!.switchToChannel(childChannel)
                childSwitch.switchToChannel(parentChannel)
            }
        }
    }

    private fun getChannelIndex(channel: Int): Int {
        var nowChannel = -1
        for (i in 0 until m_channels.size) {
            if (m_channels[i] == channel) {
                nowChannel = i
                break
            }
        }
        return nowChannel
    }
}