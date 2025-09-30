package com.auo.flex_compositor.pCMDJson

import android.util.Log
import kotlinx.serialization.json.Json
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


class CmdProtocol{
    data class HeaderFormat(
        var Type: String? = null,
        var Length: Int? = null,
        var Checksum: Int? = null
    )


    companion object {

        private val HeaderType = arrayOf("jsonRequest", "jsonResponse", "jsonNotify")
        fun splitCmdToMap(cmd: String): Map<String, String>? {
            val cmdStrLine = cmd.toString().trim()
            val strSplit = cmdStrLine.split(':')
            if(strSplit.size == 2){
                val key = strSplit[0].trim()
                val value = strSplit[1].trim()
                return mapOf<String, String>(key to value)
            }
            return null
        }

        fun constructHeader(cmd_map: Map<String, String>, header: HeaderFormat){
            val key = cmd_map.keys.first()
            if(key in commandHeaderMap){
                commandHeaderMap[key]!!.invoke(cmd_map, header)
            }
        }

        private val commandHeaderMap: Map<String, (Map<String, String>, HeaderFormat) -> Unit> = mapOf(
            "Type" to { cmd_map, header -> processHeaderType(cmd_map, header) },
            "Length" to { cmd_map, header -> processHeaderLength(cmd_map, header) },
            "Checksum" to { cmd_map, header -> processHeaderChecksum(cmd_map, header) },
        )

        private fun processHeaderType(cmd_map: Map<String, String>, header: HeaderFormat){
            val key = cmd_map.keys.first()
            header.Type = cmd_map[key]
        }

        private fun processHeaderLength(cmd_map: Map<String, String>, header: HeaderFormat){
            val key = cmd_map.keys.first()
            header.Length = cmd_map[key]!!.toIntOrNull()
        }

        private fun processHeaderChecksum(cmd_map: Map<String, String>, header: HeaderFormat){
            val key = cmd_map.keys.first()
            header.Checksum = cmd_map[key]!!.toIntOrNull(16)
        }

        fun constructBody(body: String, header: HeaderFormat): JsonRoot?{
            val type = header.Type
            if(type in commandBodyMap){
                return commandBodyMap[type]!!.invoke(body)
            }
            return null
        }

        private val commandBodyMap: Map<String, (String) -> JsonRoot?> = mapOf(
            HeaderType[0] to { body -> processBodyJsonRequest(body) },
            HeaderType[1] to { body -> processBodyJsonResponse(body) },
            HeaderType[2] to { body -> processBodyJsonNotify(body) }
        )

        private fun processBodyJsonRequest(body: String) : JsonRoot?{
            try {
                val json = Json {
                    ignoreUnknownKeys = true
                }
                val obj = json.decodeFromString<jsonRequest>(body)
                return obj
            } catch (e: Exception) {
                Log.d("processBodyJsonRequest", "null")
                return null
            }
        }

        private fun processBodyJsonResponse(body: String) : JsonRoot?{
            try {
                val json = Json {
                    ignoreUnknownKeys = true
                }
                val obj = json.decodeFromString<jsonResponse>(body)
                return obj
            } catch (e: Exception) {
                return null
            }
        }

        private fun processBodyJsonNotify(body: String) : JsonRoot?{
            try {
                val json = Json {
                    ignoreUnknownKeys = true
                }
                val obj = json.decodeFromString<jsonNotify>(body)
                return obj
            } catch (e: Exception) {
                return null
            }
        }

        fun reply(output: OutputStream, status: String){
            val jsonresponse: jsonResponse = jsonResponse(status)
            val jsonmessage: String = Json.encodeToString<jsonResponse>(jsonresponse)
            val checksum = calJsonCheckSum(jsonmessage)
            val replyMessage: String = "Type:${HeaderType[1]}\r\n"+
                    "Length:${jsonmessage.length}\r\n"+
                    "Checksum:${checksum}\r\n"+
                    "\r\n${jsonmessage}"

            // Send response back as bytes
            output.write(replyMessage.toByteArray())
            output.flush()
        }

        /**
         * Reply to the client regarding the handling result of the Security Event.
         */
        fun replySecurityReply(jsonEvents: List<SecurityEvent>, output: OutputStream, isHandled: Boolean){
            Thread {
                val securityReplies: MutableList<SecurityReply> = mutableListOf<SecurityReply>()
                for(event in jsonEvents){
                    val securityReply: SecurityReply
                    val now = LocalDateTime.now()
                    val formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
                    val nowTime = now.format(formatter)
                    if(isHandled) {
                        securityReply = SecurityReply(
                            event.id, event.name,
                            nowTime, SecurityEventStatus.Handled
                        )
                    }
                    else{
                        securityReply = SecurityReply(
                            event.id, event.name,
                            nowTime, SecurityEventStatus.Unhandled
                        )
                    }
                    securityReplies.add(securityReply)
                }
                val jsonSecurityRPL: jsonResponse = jsonResponse(null,null,null, null, securityReplies)
                val jsonMessage: String = Json.encodeToString<jsonResponse>(jsonSecurityRPL)
                val checksum = calJsonCheckSum(jsonMessage)
                val replyMessage: String = "Type:${HeaderType[1]}\r\n"+
                        "Length:${jsonMessage.length}\r\n"+
                        "Checksum:${checksum}\r\n"+
                        "\r\n${jsonMessage}"

                // Send response back as bytes
                output.write(replyMessage.toByteArray())
                output.flush()
            }.start()
        }
//
//        fun reply(output: OutputStream, statues: List<String>){
//            val jsonresponse: jsonResponse = jsonResponse(statues)
//            val jsonmessage: String = Json.encodeToString<jsonResponse>(jsonresponse)
//            val checksum = calJsonCheckSum(jsonmessage)
//            val replyMessage: String = "Type:${HeaderType[1]}\r\n"+
//                    "Length:${jsonmessage.length}\r\n"+
//                    "Checksum:${checksum}\r\n"+
//                    "\r\n${jsonmessage}"
//
//            // Send response back as bytes
//            output.write(replyMessage.toByteArray())
//            output.flush()
//        }

        fun reply(output: OutputStream, response: jsonResponse){
            val jsonmessage: String = Json.encodeToString<jsonResponse>(response)
            val checksum = calJsonCheckSum(jsonmessage)
            val replyMessage: String = "Type:${HeaderType[1]}\r\n"+
                    "Length:${jsonmessage.length}\r\n"+
                    "Checksum:${checksum}\r\n"+
                    "\r\n${jsonmessage}"

            // Send response back as bytes
            output.write(replyMessage.toByteArray())
            output.flush()
        }

        fun notifyMessage(obj: String, id: Int) : String{
            val homeEvent: HomeEvent = HomeEvent(obj, id)
            val jsonnotify: jsonNotify = jsonNotify(listOf(homeEvent))
            val jsonmessage: String = Json.encodeToString<jsonNotify>(jsonnotify)
            val checksum = calJsonCheckSum(jsonmessage)
            val replyMessage: String = "Type:${HeaderType[1]}\r\n"+
                    "Length:${jsonmessage.length}\r\n"+
                    "Checksum:${checksum}\r\n"+
                    "\r\n${jsonmessage}"
            return replyMessage
        }

        fun calJsonCheckSum(msg: String): String {
            val value = msg.toByteArray()
            var sum = 0
            for (b in value) {
                sum += b.toInt() and 0xFF  // Convert byte to unsigned int before adding
            }
            val byteArray = byteArrayOf(
                (sum shr 8 and 0xFF).toByte(),
                (sum and 0xFF).toByte()
            )
            var hexString: String = ""
            for (b in byteArray) {
                hexString += "%02x".format(b)  // Convert byte to unsigned int before adding
            }
            return hexString
        }
    }
}

interface JsonRoot {

}

@kotlinx.serialization.Serializable
data class jsonRequest(
    val sourceSwitchers: List<SourceSwitcher>? = null,
    val sourceMuxs: List<SourceMux>? = null,
    val getEnv: List<GetEnv>? = null,
    val securityEvents: List<SecurityEvent>? = null
):JsonRoot

@kotlinx.serialization.Serializable
data class jsonResponse(
    val status: String?,
    var sourceSwitchers: List<jsonStatus>? = null,
    var sourceMuxs: List<jsonStatus>? = null,
    var getEnv: Map<String, String>? = null,
    val securityReplies: List<SecurityReply>? = null
):JsonRoot

@kotlinx.serialization.Serializable
data class jsonStatus(
    val status: String? = null,
)

@kotlinx.serialization.Serializable
data class jsonNotify(
    val homeEvent: List<HomeEvent>? = null
):JsonRoot

enum class SwitcherStatus(val status: String) {
    OK("ok"),
    ID_DOSE_NOT_EXIST("switch_id_dose_not_exist"),
    CHANNEL_OUT_OF_RANGE("channel_out_of_range")
}

@kotlinx.serialization.Serializable
data class SourceSwitcher(
    val id: Int? = null,
    val channel: Int? = null,
    val homeSourceChannel: Int? = null,
    val homeStyle: Int? = null
)

enum class MuxStatus(val status: String) {
    OK("ok"),
    ID_DOSE_NOT_EXIST("mux_id_dose_not_exist"),
    CHANNEL_OUT_OF_RANGE("channel_out_of_range")
}

@kotlinx.serialization.Serializable
data class SourceMux(
    val id: Int? = null,
    val sourceChannel: Int? = null,
    val sinkChannel: Int? = null,
    val homeSourceChannel: Int? = null,
    val homeStyle: Int? = null
)

@kotlinx.serialization.Serializable
data class ReplyStatus(
    val status: String? = null,
    val message: String? = null
)

@kotlinx.serialization.Serializable
data class HomeEvent(
    val obj: String? = null,
    val id: Int? = null
)

@kotlinx.serialization.Serializable
data class GetEnv(
    val obj: String? = null
)

@kotlinx.serialization.Serializable
enum class SecurityEventStatus {
    Handled,
    Unhandled
}

@kotlinx.serialization.Serializable
data class SecurityEvent(
    val id: Int? = null,
    val name: String? = null,
    val time: String? = null,
    val event: String? = null
)

@kotlinx.serialization.Serializable
data class SecurityReply(
    val id: Int? = null,
    val name: String? = null,
    val time: String? = null,
    val event: SecurityEventStatus? = null
)