package com.auo.flex_compositor.pFilter

import android.os.Handler
import android.os.Looper
import android.util.Log
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.nio.ByteBuffer

class cWebSocketClient (private val mSocketCallback: SocketCallback?, serverUri: URI?) :
    WebSocketClient(serverUri) {

    private val m_tag = "AUOWebSocketClient"
    private val maxRetries: Int = 1000
    private val retryDelay: Long = 6000L // milliseconds
    private var retryCount = 0
    private val handler = Handler(Looper.getMainLooper())
    override fun onOpen(serverHandshake: ServerHandshake) {
        Log.d(m_tag, "onOpen")
        retryCount = 0 // reset retry count on successful connection
    }

    override fun onMessage(message: String) {}
    override fun onMessage(bytes: ByteBuffer) {
        val buf = ByteArray(bytes.remaining())
        bytes.get(buf)
        mSocketCallback?.onReceiveData(buf)
    }

    override fun onClose(code: Int, reason: String, remote: Boolean) {
        Log.d(m_tag, "onClose =$reason")
        attemptReconnect()
    }

    override fun onError(ex: Exception) {
        Log.d(m_tag, "onError =$ex")
        //attemptReconnect()
    }

    fun sendData(bytes: ByteArray?) {
        if (this.isOpen && bytes !== null) {
            this.send(bytes)
        }
    }

    fun getHostAddress(): String?{
        val socket = this.socket
        if (socket != null && socket.isConnected) {
            val localIp = socket.localAddress.hostAddress
            val localPort = socket.localPort
            return "${localIp}:${localPort}"
        }
        return null
    }

    private fun attemptReconnect() {
        if (retryCount < maxRetries) {
            retryCount++
            Log.d(m_tag, "Attempting to reconnect... ($retryCount/$maxRetries)")
            handler.postDelayed({
                try {
                    reconnect()
                } catch (e: Exception) {
                    Log.e(m_tag, "Reconnect failed: ${e.message}")
                    //attemptReconnect() // Recursive retry
                }
            }, retryDelay)
        } else {
            Log.e(m_tag, "Max reconnect attempts reached.")
        }
    }



    interface SocketCallback {
        fun onReceiveData(data: ByteArray?)
    }

}