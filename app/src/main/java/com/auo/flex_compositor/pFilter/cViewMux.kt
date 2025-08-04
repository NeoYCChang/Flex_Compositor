package com.auo.flex_compositor.pFilter

import android.util.Log
import com.auo.flex_compositor.pInterface.iElement

class cViewMux(override val e_name: String, override val e_id: Int,
               switchList: MutableList<cViewSwitch?>, channel: MutableList<Int>): iElement
{
    private val m_switchList: MutableList<cViewSwitch?> = switchList
    private val m_channels = channel

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

    fun addSwitch(switch: cViewSwitch){
        m_switchList.add(switch)
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