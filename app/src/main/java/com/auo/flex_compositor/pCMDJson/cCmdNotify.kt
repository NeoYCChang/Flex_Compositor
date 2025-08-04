package com.auo.flex_compositor.pCMDJson

import android.util.Log
import com.auo.flex_compositor.pCMDJson.cCmdReqRes.callbackCmd
import com.auo.flex_compositor.pFilter.cViewSwitch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException

class cCmdNotify: Thread() {
    private val m_tag = "cCmdNotify"
    private var m_callbackCmd: callbackCmd? = null
    private var m_serverSocket: ServerSocket? = null
    private val m_clients: MutableList<Socket> = mutableListOf<Socket>()

    override fun run() {
        super.run()
        m_serverSocket = ServerSocket(52001)
        Log.d(m_tag, "Server is listening on port 52001")
        while (true) {
            try {
                val socket = m_serverSocket!!.accept()
                Log.d(m_tag, "New client connected")
                m_clients.add(socket)

                //multi thread to handle multi clients
//                Thread {
//                    val input = socket.getInputStream()
//                    val output = socket.getOutputStream()
//
//                    try {
////                        var jsonRoot: JsonRoot?
////                        while (readCmd(input, output).also { jsonRoot = it } != null) {
////                            Log.d(m_tag, "${jsonRoot}")
////                            m_callbackCmd?.onReceiveJson(jsonRoot!!)
////                        }
//                        while (!socket.isClosed) {
//                            //clearBuffer(input)
//                            val header: CmdProtocol.HeaderFormat? =
//                                readHeader(socket, input, output)
//                            if (header != null) {
//                                val jsonRoot: JsonRoot? = readBody(socket, input, output, header)
//                                if (jsonRoot != null && header.Type != null) {
//                                    if (header.Type == "jsonRequest") {
//                                        CmdProtocol.reply(output, "ok")
//                                        val request: jsonRequest = jsonRoot as jsonRequest
//                                        m_callbackCmd?.onReceiveJson(request)
//                                    }
//                                }
//                            }
//                        }
//                    } catch (e: Exception) {
//                        Log.e(m_tag, "Error: ${e.message}")
//                    } catch (e: IOException) {
//                        Log.d(m_tag, "I/O error occurred during socket operation. ${e.message}")
//                    } finally {
//                        input.close()
//                        output.close()
//                        socket.close()
//                    }
//                }.start()
            } catch (e: IOException) {
                Log.d(m_tag, "I/O error occurred during socket operation. ${e.message}")
                break
            }
        }
    }

    fun sendBackHome(cViewSwitch: cViewSwitch) {
        Thread {
            m_clients.removeIf { it.isClosed }
            val msg = CmdProtocol.notifyMessage("switch", cViewSwitch.e_id).toByteArray()
            Log.d(m_tag, "sendBackHome ${m_clients.count()}")
            for(client in m_clients){
                val output = client.getOutputStream()
                output.write(msg)
            }
        }.start()
    }

    fun close(){
        m_serverSocket?.close()
        m_clients.clear()
    }

}