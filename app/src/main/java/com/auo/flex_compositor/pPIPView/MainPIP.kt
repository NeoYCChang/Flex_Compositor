package com.auo.flex_compositor.pPIPView

import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import com.auo.flex_compositor.pCMDJson.SecurityEvent
import com.auo.flex_compositor.pSink.cDisplayView
import java.io.OutputStream

class MainPIP(context: Context, displayView: cDisplayView) {
    inner class PIPViewBinder(displayView: cDisplayView) : Binder() {
        private val m_displayView: cDisplayView = displayView
        var securityEvent: ((List<SecurityEvent>, OutputStream) -> Unit)? = null
        fun getDisplayView(): cDisplayView = m_displayView
    }
    private lateinit var m_pipViewBinder: PIPViewBinder

    init {
        m_pipViewBinder = PIPViewBinder(displayView)
        val intent: Intent = Intent(context, UiParentActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK )
        val bundle = Bundle().apply {
            putBinder(IntentInfo.PIPBINDER.name, m_pipViewBinder)
        }
        intent.putExtras(bundle)
        context.startActivity(intent)
    }

    fun getPiPViewBinder(): PIPViewBinder{
        return m_pipViewBinder
    }
}