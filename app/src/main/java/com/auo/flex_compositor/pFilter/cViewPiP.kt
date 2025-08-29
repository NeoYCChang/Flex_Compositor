package com.auo.flex_compositor.pFilter

import com.auo.flex_compositor.pInterface.iElement

open class cViewPiP(override val e_name: String, override val e_id: Int,
                    switchList: List<cViewSwitch?>, channel: List<Int>)
    :cViewMux(e_name, e_id, switchList, channel)
{
    private var m_parentSwitch : cViewSwitch? = null
    private var m_lastChildSwitch : cViewSwitch? = null
    private var m_lastChildChannel : Int = 0

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
                if(m_lastChildSwitch != null){
                    m_lastChildSwitch!!.switchToChannel(m_lastChildChannel)
                }
                m_lastChildSwitch = childSwitch
                m_lastChildChannel = childChannel
            }
        }
    }
}