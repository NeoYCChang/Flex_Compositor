package com.auo.flex_compositor.pInterface

import android.media.MediaCodec
import android.util.Log
import java.nio.ByteBuffer

enum class eCodecType{
    H265, H264
}

enum class eBufferType{
    BUFFER_FLAG_CODEC_CONFIG, BUFFER_FLAG_KEY_FRAME, BUFFER_OTHER, BUFFER_NULL
}

enum class eCodecState {
    CONFIGURED, STARTED, STOPPED, RELEASED, ERROR
}

interface iParseCodec {
    val BUFFER_FLAG_CODEC_CONFIG: Int
    val BUFFER_FLAG_KEY_FRAME: Int
    val BITRATE_MULTIPLIER: Int
    fun getBufferType(byteBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo): eBufferType
    fun getBufferType(data: ByteArray): eBufferType
}

class cParseH265Codec : iParseCodec{
    override val BUFFER_FLAG_CODEC_CONFIG: Int = 32
    override val BUFFER_FLAG_KEY_FRAME: Int = 19
    override val BITRATE_MULTIPLIER: Int = 2
    override fun getBufferType(byteBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo): eBufferType {
        if(bufferInfo.size > 0) {
            var offSet = 4
            if (byteBuffer[2].toInt() == 0x01) {
                offSet = 3
            }
            val type = (byteBuffer[offSet].toInt() and 0x7E) shr 1
            when (type) {
                BUFFER_FLAG_CODEC_CONFIG -> return eBufferType.BUFFER_FLAG_CODEC_CONFIG
                BUFFER_FLAG_KEY_FRAME -> return eBufferType.BUFFER_FLAG_KEY_FRAME
                else -> return eBufferType.BUFFER_OTHER
            }
        }
        return eBufferType.BUFFER_NULL
    }
    override fun getBufferType(data: ByteArray): eBufferType {
        if(data.size > 0) {
            var offSet = 4
            if (data[2].toInt() == 0x01) {
                offSet = 3
            }
            val type = (data[offSet].toInt() and 0x7E) shr 1

            when (type) {
                BUFFER_FLAG_CODEC_CONFIG -> return eBufferType.BUFFER_FLAG_CODEC_CONFIG
                BUFFER_FLAG_KEY_FRAME -> return eBufferType.BUFFER_FLAG_KEY_FRAME
                else -> return eBufferType.BUFFER_OTHER
            }
        }
        return eBufferType.BUFFER_NULL
    }
}

class cParseH264Codec : iParseCodec{
    override val BUFFER_FLAG_CODEC_CONFIG: Int = 103
    override val BUFFER_FLAG_KEY_FRAME: Int = 101
    override val BITRATE_MULTIPLIER: Int = 4
    override fun getBufferType(byteBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo): eBufferType {
        if(bufferInfo.size > 0) {
            var offSet = 4
            if (byteBuffer[2].toInt() == 0x01) {
                offSet = 3
            }
            val type = byteBuffer[offSet].toInt()
            when (type) {
                BUFFER_FLAG_CODEC_CONFIG -> return eBufferType.BUFFER_FLAG_CODEC_CONFIG
                BUFFER_FLAG_KEY_FRAME -> return eBufferType.BUFFER_FLAG_KEY_FRAME
                else -> return eBufferType.BUFFER_OTHER
            }
        }
        return eBufferType.BUFFER_NULL
    }
    override fun getBufferType(data: ByteArray): eBufferType {
//        Log.d("parseCodec", "${data.size}")
//        Log.d("parseCodec", "getBufferType")
        if(data.size > 0) {
            var offSet = 4
            if (data[2].toInt() == 0x01) {
                offSet = 3
            }
            val type = data[offSet].toInt()
//            getFirstNonAUDNalType(data)

//                Log.d("parseCodec", "${data[3]}")
//                Log.d("parseCodec", "${getFirstNonAUDNalType(data)}")


            when (type) {
                BUFFER_FLAG_CODEC_CONFIG -> return eBufferType.BUFFER_FLAG_CODEC_CONFIG
                BUFFER_FLAG_KEY_FRAME -> return eBufferType.BUFFER_FLAG_KEY_FRAME
                else -> return eBufferType.BUFFER_OTHER
            }
        }
        return eBufferType.BUFFER_NULL
    }

//    fun getFirstNonAUDNalType(data: ByteArray): Int {
//        var offset = 0
//        while (offset + 4 < data.size) {
//            val startCodeLen = if (data[offset] == 0x00.toByte() && data[offset + 1] == 0x00.toByte() &&
//                data[offset + 2] == 0x01.toByte()) {
//                3
//            } else if (data[offset] == 0x00.toByte() && data[offset + 1] == 0x00.toByte() &&
//                data[offset + 2] == 0x00.toByte() && data[offset + 3] == 0x01.toByte()) {
//                4
//            } else {
//                offset++
//                continue
//            }
//
//            val nalHeaderIndex = offset + startCodeLen
//            if (nalHeaderIndex >= data.size) break
//
//            val nalType = data[nalHeaderIndex].toInt() and 0x1F
//            if (nalType != 9 && nalType != 1) {
//                Log.d("parseCodec", "${nalType}")
//                return nalType
//            }
//
//            offset = nalHeaderIndex + 1
//        }
//
//        return -1 // 沒有找到非-AUD 的 NAL
//    }
}