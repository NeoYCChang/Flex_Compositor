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
        if(data.size > 0) {
            var offSet = 4
            if (data[2].toInt() == 0x01) {
                offSet = 3
            }
            val type = data[offSet].toInt()
            when (type) {
                BUFFER_FLAG_CODEC_CONFIG -> return eBufferType.BUFFER_FLAG_CODEC_CONFIG
                BUFFER_FLAG_KEY_FRAME -> return eBufferType.BUFFER_FLAG_KEY_FRAME
                else -> return eBufferType.BUFFER_OTHER
            }
        }
        return eBufferType.BUFFER_NULL
    }
}