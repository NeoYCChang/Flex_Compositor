package com.auo.flex_compositor.pFilter

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.auo.flex_compositor.pEGLFunction.EGLRender
import com.auo.flex_compositor.pEGLFunction.EGLRender.Texture_Size
import com.auo.flex_compositor.pEGLFunction.EGLThread
import com.auo.flex_compositor.pInterface.cMotionEvent
import com.auo.flex_compositor.pInterface.cParseH264Codec
import com.auo.flex_compositor.pInterface.cParseH265Codec
import com.auo.flex_compositor.pInterface.deWarp_Parameters
import com.auo.flex_compositor.pInterface.eBufferType
import com.auo.flex_compositor.pInterface.eCodecType
import com.auo.flex_compositor.pInterface.iElement
import com.auo.flex_compositor.pInterface.iEssentialRenderingTools
import com.auo.flex_compositor.pInterface.iParseCodec
import com.auo.flex_compositor.pInterface.iSurfaceSource
import com.auo.flex_compositor.pInterface.iTouchMapper
import com.auo.flex_compositor.pInterface.vCropTextureArea
import com.auo.flex_compositor.pInterface.vSize
import com.auo.flex_compositor.pInterface.vTouchMapping
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.lang.ref.WeakReference
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.locks.ReentrantLock
import javax.microedition.khronos.egl.EGLContext
import kotlin.concurrent.withLock

class cMediaEncoder(context: Context, override val e_name: String, override val e_id: Int, source: iSurfaceSource,
                    size: vSize, cropTextureArea: vCropTextureArea, touchMapping: vTouchMapping?, serverport: Int,
                    dewarpParameters: deWarp_Parameters?, codecType: eCodecType = eCodecType.H264)
    : iElement, iEssentialRenderingTools {

    private var m_source: iSurfaceSource = source
    private val m_context: Context = context
    private var eglContext: EGLContext? = m_source?.getEGLContext()
    private var m_EGLRender: EGLRender?  = null
    private var m_surface: Surface? = null
    private var m_cropTextureArea = cropTextureArea
    private var m_dewarpParameters: deWarp_Parameters? = dewarpParameters
    private var m_touchMapping = touchMapping
    private val m_vsize: vSize =  size
    private val mTextureSize : Texture_Size = Texture_Size(
        960, 540, 0, 0,
        960,540
    )

    private var eglThread: EGLThread? = null

    private val SCREEN_FRAME_RATE: Int = 60
    private val SCREEN_FRAME_INTERVAL: Int = 1
    private val SOCKET_TIME_OUT: Long = 10000
    private val m_codecType: eCodecType = codecType
    private var m_parseCodec: iParseCodec? = null
    private var mMediaCodec: MediaCodec? = null
    private var m_playing = false

    // record vps pps sps
    private var m_vps_pps_sps: ByteArray? = null

    // websocket server
    private val m_socket_port: Int = serverport
    private var m_webSocketServer: cWebSocketServer? = null

    // codec thread
    private val m_codecThread: HandlerThread = HandlerThread("MediaCodecCallbackThread");
    private var m_codecHandle: Handler? = null

    private val m_tag: String  = "cMediaEncoder"
    private var m_sync_count: Int =0

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
            m_dewarpParameters
        )

        when (m_source) {
            is cViewSwitch -> {
                val viewSwitch = m_source as cViewSwitch
                m_cropTextureArea = viewSwitch.getCrop_texture()
                m_touchMapping = viewSwitch.getTouchMapping()
            }
        }

        mTextureSize.offsetX = m_cropTextureArea.offsetX
        mTextureSize.offsetY = m_cropTextureArea.offsetY
        mTextureSize.cropWidth  = m_cropTextureArea.width
        mTextureSize.cropHeight = m_cropTextureArea.height
        mTextureSize.width = m_source.getSurfaceTexture().getWidth()
        mTextureSize.height = m_source.getSurfaceTexture().getHeight()

        m_EGLRender!!.setTextureSize(mTextureSize)

        eglThread = EGLThread(WeakReference(this))
        eglThread!!.width = m_vsize.width
        eglThread!!.height = m_vsize.height
        eglThread!!.start() // need to run it after startEncode()

        when (m_source) {
            is cViewSwitch -> {
                val handler: () -> Unit = {
                    Log.d(m_tag,"trigger")
                    val viewSwitch = m_source as cViewSwitch
                    m_cropTextureArea = viewSwitch.getCrop_texture()
                    m_touchMapping = viewSwitch.getTouchMapping()
                    mTextureSize.offsetX = m_cropTextureArea.offsetX
                    mTextureSize.offsetY = m_cropTextureArea.offsetY
                    mTextureSize.cropWidth  = m_cropTextureArea.width
                    mTextureSize.cropHeight = m_cropTextureArea.height
                    mTextureSize.width = m_source.getSurfaceTexture().getWidth()
                    mTextureSize.height = m_source.getSurfaceTexture().getHeight()
                    m_EGLRender!!.setTextureSize(mTextureSize, true)
                }
                val viewSwitch = m_source as cViewSwitch
                viewSwitch.triggerSubscribe(handler)
            }
        }
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
            return m_source.getSurfaceTexture().getTextureID()
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

    private fun startEncode() {
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
            setInteger(MediaFormat.KEY_BIT_RATE, m_vsize.width * m_vsize.height * m_parseCodec!!.BITRATE_MULTIPLIER)
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
        m_codecHandle = startThread(m_codecThread)

        mMediaCodec?.setCallback(object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                // There's no need to handle it, because the input is the Surface.
            }

            override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                try {
                    if (index >= 0) {
                        val byteBuffer = codec.getOutputBuffer(index)
                        if (byteBuffer != null) {
                            encodeData(byteBuffer, info)
                        }
                        codec.releaseOutputBuffer(index, false)
                        updateSyncCount()
                    }
                } catch (e: IllegalStateException) {
                    Log.e(m_tag, "IllegalStateException: ${e.message}")
                } catch (e: MediaCodec.CodecException) {
                    Log.e(m_tag, "CodecException: ${e.diagnosticInfo}")
                }
            }

            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                Log.d(m_tag, "Encoder output format changed: $format")
            }

            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                Log.e(m_tag, "Encoder error: ${e.message}")
                stopEncode()
            }
        }, m_codecHandle)
        //mMediaCodec = MediaCodec.createByCodecName("c2.android.hevc.encoder")
        mMediaCodec?.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

        // Create the input surface for encoding
        m_surface = mMediaCodec?.createInputSurface()
        mMediaCodec?.start()
    }

    private fun startThread(thread: HandlerThread) : Handler{
        thread.start();
        return Handler(thread.getLooper())
    }

    fun stopEncode() {
        m_playing = false
        if (mMediaCodec != null) {
            mMediaCodec!!.stop()
            mMediaCodec!!.release()
            mMediaCodec = null
        }
        eglThread?.onDestory()
        eglThread?.join()
        eglThread = null
        m_webSocketServer?.close()
        m_webSocketServer?.stop()
        m_webSocketServer = null
        m_codecThread?.quitSafely()
        m_surface?.release()
        m_surface = null
    }

    private fun onTouchEvent(data: ByteArray) {
        if(data.size > 0) {
            try {
                val jsonString = data.toString(Charsets.UTF_8)
                val event: cMotionEvent = Json.decodeFromString<cMotionEvent>(jsonString)
                if(event.start == com.auo.flex_compositor.pInterface.start_byte){
                    if(m_source !== null && m_touchMapping != null) {
                        val touchmapper: iTouchMapper = m_source as iTouchMapper
                        val pointerCount = event.pointerCount

                        for (i in 0 until pointerCount) {
                            val x = event.pointerCoords[i].x
                            val y = event.pointerCoords[i].y
                            event.pointerCoords[i].x = x * (m_touchMapping!!.width - 1) / (event.decoder_width - 1) + m_touchMapping!!.offsetX
                            event.pointerCoords[i].y = y * (m_touchMapping!!.height - 1) / (event.decoder_height - 1) + m_touchMapping!!.offsetY
                        }

                        touchmapper.injectMotionEvent(event)
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


    override fun getSource() : iSurfaceSource{
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

    // Detect the difference between the encoder's encoding count
    // and the EGLThread's draw count to determine whether to clear the encoder cache.
    override fun Sync(sync_count: Int): Boolean {
        val Subtract_sync_count = sync_count - m_sync_count
        if (kotlin.math.abs(Subtract_sync_count) >= 3) {
            Log.d(m_tag, "Synchronization between cMediaEncoder and EGLThread ${Subtract_sync_count} ")
            m_sync_count = 0
            mMediaCodec?.flush()
            mMediaCodec?.start()
            return true
        }
        return false
    }

    private fun updateSyncCount(){
//        val current = LocalDateTime.now()
//        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
//        val formatted = current.format(formatter)
//        Log.d(m_tag, "encoder ${m_sync_count}  ${formatted}")
        if(m_sync_count == Int.MAX_VALUE){
            m_sync_count = 0
        }
        else {
            m_sync_count++
        }
    }

}
