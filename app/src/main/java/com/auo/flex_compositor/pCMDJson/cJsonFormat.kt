package com.auo.flex_compositor.pCMDJson

import kotlinx.serialization.json.Json
import java.io.OutputStream


class CmdProtocol{
    data class HeaderFormat(
        var Type: String? = null,
        var Length: Int? = null,
        var Checksum: Int? = null
    )


    companion object {

        private val HeaderType = arrayOf("jsonRequest", "jsonResponse")
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
            HeaderType[1] to { body -> processBodyJsonResponse(body) }
        )

        private fun processBodyJsonRequest(body: String) : JsonRoot?{
            try {
                val json = Json {
                    ignoreUnknownKeys = true
                }
                val obj = json.decodeFromString<jsonRequest>(body)
                return obj
            } catch (e: Exception) {
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
    val sourceSwitchers: List<SourceSwitcher>? = null
):JsonRoot

@kotlinx.serialization.Serializable
data class jsonResponse(
    val status: String? = null,
):JsonRoot

@kotlinx.serialization.Serializable
data class SourceSwitcher(
    val id: Int? = null,
    val channel: Int? = null
)

@kotlinx.serialization.Serializable
data class ReplyStatus(
    val status: String? = null,
    val message: String? = null
)