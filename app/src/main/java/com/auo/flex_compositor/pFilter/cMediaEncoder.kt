package com.auo.flex_compositor.pFilter

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.MotionEvent.PointerCoords
import android.view.MotionEvent.PointerProperties
import android.view.Surface
import com.auo.flex_compositor.pEGLFunction.EGLRender
import com.auo.flex_compositor.pEGLFunction.EGLRender.Companion
import com.auo.flex_compositor.pEGLFunction.EGLRender.Texture_Size
import com.auo.flex_compositor.pEGLFunction.EGLThread
import com.auo.flex_compositor.pInterface.iElement
import com.auo.flex_compositor.pInterface.iEssentialRenderingTools
import com.auo.flex_compositor.pInterface.iSurfaceSource
import com.auo.flex_compositor.pInterface.iTouchMapper
import com.auo.flex_compositor.pInterface.vTouchMapping
import com.auo.flex_compositor.pInterface.vCropTextureArea
import com.auo.flex_compositor.pInterface.vPos_Size
import com.auo.flex_compositor.pSource.cVirtualDisplay
import java.io.IOException
import java.lang.ref.WeakReference
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import javax.microedition.khronos.egl.EGLContext
import com.auo.flex_compositor.pInterface.vSize
import kotlinx.serialization.json.Json
import com.auo.flex_compositor.pInterface.cMotionEvent
import com.auo.flex_compositor.pInterface.cParseH264Codec
import com.auo.flex_compositor.pInterface.cParseH265Codec
import com.auo.flex_compositor.pInterface.eBufferType
import com.auo.flex_compositor.pInterface.iParseCodec
import com.auo.flex_compositor.pInterface.eCodecType
import kotlinx.serialization.SerializationException

class cMediaEncoder(context: Context, override val e_name: String, override val e_id: Int, source: iSurfaceSource,
                    size: vSize, cropTextureArea: vCropTextureArea, touchMapping: vTouchMapping?, serverport: Int,isDeWarp: Boolean,
                    codecType: eCodecType = eCodecType.H264)
    : iElement, iEssentialRenderingTools {

    private var m_source: iSurfaceSource = source
    private var m_responsible_updating_texture  = if (m_source !== null) !m_source!!.getSurfaceTexture()!!.get_already_binding() else false
    private val m_context: Context = context
    private var eglContext: EGLContext? = m_source?.getEGLContext()
    private var m_EGLRender: EGLRender?  = null
    private var m_surface: Surface? = null
    private val m_cropTextureArea = cropTextureArea
    private var m_isDeWarp: Boolean = isDeWarp
    private val m_touchMapping = touchMapping
    private val m_vsize: vSize =  size
    private val mTextureSize : Texture_Size = Texture_Size(
        960, 540, 0, 0,
        960,540
    )

    private var eglThread: EGLThread? = null

    private val SCREEN_FRAME_RATE: Int = 20
    private val SCREEN_FRAME_INTERVAL: Int = 1
    private val SOCKET_TIME_OUT: Long = 10000
    private val m_codecType: eCodecType = codecType
    private var m_parseCodec: iParseCodec? = null
    private var mMediaCodec: MediaCodec? = null
    private var m_playing = false

    // 记录vps pps sps
    private var m_vps_pps_sps: ByteArray? = null

    // websocket server
    private val m_socket_port: Int = serverport
    private var m_webSocketServer: cWebSocketServer? = null
    private var m_downTime: Long = 0
    private var m_eventTime: Long = 0

    private val m_tag: String  = "cMediaEncoder"

    init {
        val callback = object : cWebSocketServer.SocketCallback{
            override fun onReceiveData(data: ByteArray?) {
                if(data != null) {
                    onTouchEvent(data)
                }
            }
        }
        m_webSocketServer = cWebSocketServer(callback, InetSocketAddress(m_socket_port))
        m_webSocketServer?.start()

        startEncode()

        m_EGLRender = EGLRender(
            m_context,
            m_source!!.getSurfaceTexture()!!.getTextureID(),
            m_isDeWarp
        )

        mTextureSize.offsetX = m_cropTextureArea.offsetX
        mTextureSize.offsetY = m_cropTextureArea.offsetY
        mTextureSize.cropWidth  = m_cropTextureArea.width
        mTextureSize.cropHeight = m_cropTextureArea.height
        mTextureSize.width = m_source!!.getSurfaceTexture()!!.getWidth()
        mTextureSize.height = m_source!!.getSurfaceTexture()!!.getHeight()

        m_EGLRender!!.setTextureSize(mTextureSize)

        m_source!!.getSurfaceTexture()?.addListener { this.requestRender() }
        eglThread = EGLThread(WeakReference(this))
        eglThread!!.width = m_vsize.width
        eglThread!!.height = m_vsize.height
        eglThread!!.start()
    }

    fun getTextureSize() : Texture_Size{
        return mTextureSize
    }

    fun getTouchMapping() : vTouchMapping?{
        return m_touchMapping
    }

    fun getvSize() : vSize
    {
        return m_vsize
    }

    fun getVirtualDisplayName() : String?{
        return m_source?.e_name
    }

    fun getTextureID() : Int{
        if(m_source !== null) {
            return m_source!!.getSurfaceTexture()!!.getTextureID()
        }
        else{
            return  -1
        }
    }

    private fun encodeData(byteBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        if(bufferInfo.size > 0) {
            var type = eBufferType.BUFFER_NULL
            if(m_parseCodec != null) {
                type = m_parseCodec!!.getBufferType(byteBuffer, bufferInfo)
            }
            if (type == eBufferType.BUFFER_FLAG_CODEC_CONFIG) {
                m_vps_pps_sps = ByteArray(bufferInfo.size)
                byteBuffer.get(m_vps_pps_sps)
            } else if (type == eBufferType.BUFFER_FLAG_KEY_FRAME) {
                val bytes = ByteArray(bufferInfo.size)
                byteBuffer.get(bytes)
                var newBytes: ByteArray? = null
                if (m_vps_pps_sps != null) {
                    newBytes = ByteArray(m_vps_pps_sps!!.size + bytes.size)
                    System.arraycopy(m_vps_pps_sps, 0, newBytes, 0, m_vps_pps_sps!!.size)
                    System.arraycopy(bytes, 0, newBytes, m_vps_pps_sps!!.size, bytes.size)
                    m_webSocketServer?.sendData(newBytes)
                } else if (type == eBufferType.BUFFER_OTHER) {
                    m_webSocketServer?.sendData(bytes)
                }
            } else {
                val bytes = ByteArray(bufferInfo.size)
                byteBuffer[bytes]
                m_webSocketServer?.sendData(bytes)
            }
        }
    }

    fun startEncode() {
        var mimetype: String = MediaFormat.MIMETYPE_VIDEO_HEVC
        when (m_codecType) {
            eCodecType.H265 -> {
                m_parseCodec = cParseH265Codec()
                mimetype = MediaFormat.MIMETYPE_VIDEO_HEVC
                Log.d(m_tag, "Construct h265 Encoder")
            }
            eCodecType.H264 -> {
                m_parseCodec = cParseH264Codec()
                mimetype = MediaFormat.MIMETYPE_VIDEO_AVC
                Log.d(m_tag, "Construct h264 Encoder")
            }
        }
        val mediaFormat = MediaFormat.createVideoFormat(mimetype, m_vsize.width, m_vsize.height).apply {
            // Set the color format to Surface format
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            // Set the bitrate (bits per second)
            setInteger(MediaFormat.KEY_BIT_RATE, m_vsize.width * m_vsize.height * 2)
            // Set the frame rate
            setInteger(MediaFormat.KEY_FRAME_RATE, SCREEN_FRAME_RATE)
            // Set I-frame interval
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, SCREEN_FRAME_INTERVAL)
        }

        try {
            // Create the MediaCodec encoder
            mMediaCodec = MediaCodec.createEncoderByType(mimetype)
        } catch (e: IOException) {
            e.printStackTrace()
        }

        mMediaCodec?.setCallback(object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                // There's no need to handle it, because the input is the Surface.
            }

            override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                if (index >= 0) {
                    val byteBuffer = codec.getOutputBuffer(index)
                    if (byteBuffer != null) {
                        encodeData(byteBuffer, info)
                    }
                    codec.releaseOutputBuffer(index, false)
                }
            }

            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                Log.d(m_tag, "Encoder output format changed: $format")
            }

            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                Log.e(m_tag, "Encoder error: ${e.message}")
                stopEncode()
            }
        })
        //mMediaCodec = MediaCodec.createByCodecName("c2.android.hevc.encoder")
        mMediaCodec?.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

        // Create the input surface for encoding
        m_surface = mMediaCodec?.createInputSurface()
        mMediaCodec?.start()
    }

    fun stopEncode() {
        m_playing = false
        if (mMediaCodec != null) {
            mMediaCodec!!.release()
        }
        eglThread!!.onDestory()
        eglThread = null
        m_webSocketServer?.close()
        m_webSocketServer?.stop()
    }

    fun onTouchEvent(data: ByteArray) {
        if(data.size > 0) {
            try {
                val jsonString = data.toString(Charsets.UTF_8)
                val event: cMotionEvent = Json.decodeFromString<cMotionEvent>(jsonString)
                if(event.start == com.auo.flex_compositor.pInterface.start_byte){
                    if(m_source !== null && m_touchMapping != null) {
                        val touchmapper: iTouchMapper = m_source as iTouchMapper
                        val pointerCount = event.pointerCount
                        val pointerProperties =
                            arrayOfNulls<PointerProperties>(pointerCount)
                        val pointerCoords = arrayOfNulls<PointerCoords>(pointerCount)
                        for (i in 0 until pointerCount) {
                            pointerProperties[i] = PointerProperties()
                            pointerProperties[i]!!.id = event.pointerProperties[i].id
                            pointerProperties[i]!!.toolType = event.pointerProperties[i].toolType
                            pointerCoords[i] = PointerCoords()
                            pointerCoords[i]!!.x =
                                event.pointerCoords[i].x * (m_touchMapping.width - 1) / (event.decoder_width - 1) + m_touchMapping.offsetX
                            pointerCoords[i]!!.y =
                                event.pointerCoords[i].y * (m_touchMapping.height - 1) / (event.decoder_height - 1) + m_touchMapping.offsetY
                            pointerCoords[i]!!.pressure = event.pointerCoords[i].pressure
                            pointerCoords[i]!!.size = event.pointerCoords[i].size
                        }
                        val maskedAction = event.action and MotionEvent.ACTION_MASK
                        if(maskedAction == MotionEvent.ACTION_DOWN){
                            m_downTime = SystemClock.uptimeMillis()
                        }

                        m_eventTime = m_downTime + (event.eventTime - event.downTime)

                        val newevent: MotionEvent = MotionEvent.obtain(
                            m_downTime, m_eventTime, event.action, event.pointerCount,
                            pointerProperties, pointerCoords, event.metaState, event.buttonState,
                            event.xPrecision, event.yPrecision, event.deviceId, event.edgeFlags,
                            event.source, event.flags
                        )
                        touchmapper.injectMotionEvent(newevent)
                    }
                }

            }catch (e: SerializationException) {
                Log.e(m_tag, "decodeFromString failed: ${e.message}")
            }

        }

//        if(m_source !== null && m_touchMapping != null) {
//            val touchmapper: iTouchMapper = m_source as iTouchMapper
//            val pointerCount = event.pointerCount
//            val pointerProperties =
//                arrayOfNulls<PointerProperties>(pointerCount)
//            val pointerCoords = arrayOfNulls<PointerCoords>(pointerCount)
//            for (i in 0 until pointerCount) {
//                pointerProperties[i] = PointerProperties()
//                pointerProperties[i]!!.id = event.getPointerId(i)
//                pointerProperties[i]!!.toolType = event.getToolType(i)
//                pointerCoords[i] = PointerCoords()
//                pointerCoords[i]!!.x =
//                    event.getX(i) * (m_touchMapping.width - 1) / (this.width - 1) + m_touchMapping.offsetX
//                pointerCoords[i]!!.y =
//                    event.getY(i) * (m_touchMapping.height - 1) / (this.height - 1) + m_touchMapping.offsetY
//                pointerCoords[i]!!.pressure = event.getPressure(i)
//                pointerCoords[i]!!.size = event.getSize(i)
//            }
//
//            val newevent: MotionEvent = MotionEvent.obtain(
//                event.downTime, event.eventTime, event.action, event.pointerCount,
//                pointerProperties, pointerCoords, event.metaState, event.buttonState,
//                event.xPrecision, event.yPrecision, event.deviceId, event.edgeFlags,
//                event.source, event.flags
//            )
//            touchmapper.injectMotionEvent(newevent)
//            m_DisplayViewCallback?.onTouchCallback(newevent)
//        }
//        return true // Return true to indicate the event was handled
    }

    override fun getUpdatingTexture() : Boolean{
        return m_responsible_updating_texture
    }

    override fun getSource() : iSurfaceSource?{
        return m_source
    }

    override fun getEGLContext() : EGLContext?{
        return eglContext
    }

    override fun getEGLRender() : EGLRender?{
        return m_EGLRender
    }

    override fun getSurface(): Surface? {
        return m_surface
    }

    fun requestRender() {
        if (eglThread != null) {
            //Log.d(m_tag,"requestRender")
            eglThread!!.requestRender()
        }
    }
}
