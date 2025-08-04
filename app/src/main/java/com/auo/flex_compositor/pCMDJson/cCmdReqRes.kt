package com.auo.flex_compositor.pCMDJson

import android.util.Log
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException

class cCmdReqRes: Thread() {
    private val m_tag = "cCmdReqRes"
    private var m_callbackCmd: callbackCmd? = null
    private var m_serverSocket: ServerSocket? = null

    override fun run() {
        super.run()
        m_serverSocket = ServerSocket(52000)
        Log.d(m_tag, "Server is listening on port 52000")
        while (true){
            try {
                val socket = m_serverSocket!!.accept()
                Log.d(m_tag, "New client connected")

                //multi thread to handle multi clients
                Thread {
                    val input = socket.getInputStream()
                    val output = socket.getOutputStream()

                    try {
//                        var jsonRoot: JsonRoot?
//                        while (readCmd(input, output).also { jsonRoot = it } != null) {
//                            Log.d(m_tag, "${jsonRoot}")
//                            m_callbackCmd?.onReceiveJson(jsonRoot!!)
//                        }
                        while (!socket.isClosed) {
                            //clearBuffer(input)
                            val header: CmdProtocol.HeaderFormat? =
                                readHeader(socket, input, output)
                            if (header != null) {
                                val jsonRoot: JsonRoot? = readBody(socket, input, output, header)
                                if(jsonRoot != null &&  header.Type != null){
                                    if(header.Type  == "jsonRequest"){
                                        val request: jsonRequest = jsonRoot as jsonRequest
                                        if(m_callbackCmd != null) {
                                            val response: jsonResponse =
                                                m_callbackCmd!!.onReceiveJson(request)
                                            CmdProtocol.reply(output, response)
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(m_tag, "Error: ${e.message}")
                    } catch (e: IOException) {
                        Log.d(m_tag, "I/O error occurred during socket operation. ${e.message}")
                    } finally {
                        input.close()
                        output.close()
                        socket.close()
                    }
                }.start()
            } catch (e: IOException) {
                Log.d(m_tag, "I/O error occurred during socket operation. ${e.message}")
                break
            }
        }
    }

    private fun readHeader(socket: Socket, input: InputStream, output: OutputStream): CmdProtocol.HeaderFormat?{
        socket.soTimeout = 0
        val header: CmdProtocol.HeaderFormat = CmdProtocol.HeaderFormat()
        val cmdLine: StringBuilder = StringBuilder()
        var byteRead = ByteArray(1)
        while (input.read(byteRead) != -1){
            val recv = String(byteRead, Charsets.UTF_8)

            if(recv == "\n"){
                if(cmdLine.length == 0){
                    if(header.Type != null && header.Length != null){
                        return header
                    }
                }
                else {
                    val cmd_map: Map<String, String>? = CmdProtocol.splitCmdToMap(cmdLine.toString())
                    if (cmd_map != null) {
                        CmdProtocol.constructHeader(cmd_map, header)
                    }
                }

                cmdLine.clear()
            }
            else if(recv != "\r"){
                cmdLine.append(recv)
            }
        }
        return null
    }

    private fun readBody(socket: Socket, input: InputStream, output: OutputStream, header: CmdProtocol.HeaderFormat):JsonRoot?{
        if(header.Length == null){
            return null
        }else if(header.Length == 0){
            return null
        }
        socket.soTimeout = 100
        val cmdLine: StringBuilder = StringBuilder()
        var byteRead = ByteArray(1)
        try {
            while (input.read(byteRead) != -1){
                val recv = String(byteRead, Charsets.UTF_8)
                cmdLine.append(recv)
                if(header.Length == cmdLine.length){
                    if(header.Checksum != null){
                        val calCheckSum = CmdProtocol.calJsonCheckSum(cmdLine.toString()).toIntOrNull(16)
                        if(calCheckSum != null){
                            if(calCheckSum != header.Checksum){
                                CmdProtocol.reply(output,"checksum error")
                                return null
                            }
                        }
                    }
                    return CmdProtocol.constructBody(cmdLine.toString(), header)
                }
            }
        }catch (e: SocketTimeoutException) {
            CmdProtocol.reply(output,"timeout")
            Log.d(m_tag, "Read timed out: ${e.message}")
            return null
        }

        return null
    }

    private fun readCmd(input: InputStream, output: OutputStream) : JsonRoot?{
        val startBuffer = ByteArray(8)
        var bytesRead: Int
        var jsonLength: Int
        var jsonCheckSum: Int
        while (input.read(startBuffer,0,  8).also { bytesRead = it } != -1) {
            if(bytesRead == 8){
                Log.d(m_tag, "startBuffer: ${startBuffer[0]},${startBuffer[1]}")
                if(startBuffer[0].toInt() == 0x53 && startBuffer[1].toInt() == 0x54){
                    jsonLength = (startBuffer[2].toInt() and 0xFF shl 24) or
                            (startBuffer[3].toInt() and 0xFF shl 16) or
                            (startBuffer[4].toInt() and 0xFF shl 8) or
                            (startBuffer[5].toInt() and 0xFF)
                    jsonCheckSum = (startBuffer[6].toInt() and 0xFF shl 8) or
                            (startBuffer[7].toInt() and 0xFF)
                    Log.d(m_tag, "jsonLength: ${jsonLength}, jsonCheckSum: ${jsonCheckSum}")

                    val jsonBuffer = ByteArray(jsonLength)
                    bytesRead = input.read(jsonBuffer,0,  jsonLength)
                    Log.d(m_tag, "bytesRead: ${bytesRead}")
                    if(bytesRead == jsonLength) {
                        var sum = 0
                        for (b in jsonBuffer) {
                            sum += b.toInt() and 0xFF  // Convert byte to unsigned int before adding
                        }
                        Log.d(m_tag, "sum: ${sum and 0xFFFF}")
                        if(jsonCheckSum == (sum and 0xFFFF)){
                            val jsonString = jsonBuffer.toString(Charsets.UTF_8)
                            val json = Json {
                                ignoreUnknownKeys = true
                            }
                            try {
                                val obj = json.decodeFromString<JsonRoot>(jsonString)
                                replyOk(output)
                                return obj
                            } catch (e: Exception) {
                                e.printStackTrace()
                                replyError(output, "Invalid Json data")
                                clearBuffer(input)
                            }
                        }
                        else
                        {
                            replyError(output, "Invalid checksum")
                            clearBuffer(input)
                        }
                    }
                    else
                    {
                        replyError(output, "Length byte does not match actual data size")
                        clearBuffer(input)
                    }
                }
                else{
                    replyError(output, "Invalid start bytes")
                    clearBuffer(input)
                }
            }
            else{
                replyError(output, "Invalid start bytes")
                clearBuffer(input)
            }
        }
        return null
    }

    private fun clearBuffer(input: InputStream){
        val clearbuffer = ByteArray(1024)
        while (input.available() > 0) {
            input.read(clearbuffer, 0, minOf(clearbuffer.size, input.available()))
        }
    }

    private fun replyOk(output: OutputStream){
//        val replyStatus = ReplyStatus("ok", "Received and validated")
//        val jsonRoot = JsonRoot(null, replyStatus)
//        val jsonRootBytes = Json.encodeToString(jsonRoot).encodeToByteArray()
//        reply(output, jsonRootBytes)
    }

    private fun replyError(output: OutputStream, errorMessage: String){
//        val replyStatus = ReplyStatus("error", errorMessage)
//        val jsonRoot = JsonRoot(null, replyStatus)
//        val jsonRootBytes = Json.encodeToString(jsonRoot).encodeToByteArray()
//        reply(output, jsonRootBytes)
    }

    private fun reply(output: OutputStream, jsonBuffer: ByteArray){
        val replyBuffer = ByteArray(8 + jsonBuffer.size)
        val jsonLengthBuffer = sizeToBytes(jsonBuffer.size)
        val jsonCheckSum = calJsonCheckSum(jsonBuffer)
        replyBuffer[0] = 0x53
        replyBuffer[1] = 0x54
        for(i in 0 until 4) {
            replyBuffer[i+2] = jsonLengthBuffer[i]
        }
        for(i in 0 until 2) {
            replyBuffer[i+6] = jsonCheckSum[i]
        }
        jsonBuffer.copyInto(replyBuffer, 8)

        // Send response back as bytes
        output.write(replyBuffer)
        output.flush()
    }



    private fun sizeToBytes(value: Int): ByteArray {
        return byteArrayOf(
            (value shr 24 and 0xFF).toByte(),
            (value shr 16 and 0xFF).toByte(),
            (value shr 8 and 0xFF).toByte(),
            (value and 0xFF).toByte()
        )
    }

    private fun calJsonCheckSum(value: ByteArray): ByteArray {
        var sum = 0
        for (b in value) {
            sum += b.toInt() and 0xFF  // Convert byte to unsigned int before adding
        }
        return byteArrayOf(
            (sum shr 8 and 0xFF).toByte(),
            (sum and 0xFF).toByte()
        )
    }

    fun setCallbackCmd(icallbackCmd: callbackCmd){
        m_callbackCmd = icallbackCmd
    }

    fun close(){
        m_serverSocket?.close()
    }

    interface callbackCmd {
        fun onReceiveJson(request: jsonRequest): jsonResponse
    }


}