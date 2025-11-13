package com.auo.flex_compositor.pParse

import android.content.Context
import android.util.Log
import com.auo.flex_compositor.pFilter.cViewMux
import com.auo.flex_compositor.pInterface.deWarp_Parameters
import com.auo.flex_compositor.pInterface.eCodecType
import java.io.File
import com.auo.flex_compositor.pInterface.vSize
import com.auo.flex_compositor.pInterface.vPos_Size
import com.auo.flex_compositor.pInterface.vTouchMapping
import com.auo.flex_compositor.pInterface.vCropTextureArea
import com.auo.flex_compositor.pSQLDataBase.cContentManager
import java.util.ArrayList
import kotlin.math.sin

enum class eElementType{
    VIRTUALDISPLAY, DISPLAYVIEW, PIPVIEW, STREAM, STREAMENCODER, STREAMDECODER, SWITCH, MUX, PIP, NULLTYPE
}

enum class ePiPType{
    PARENT, CHILD
}

open class cElementType(
    open var id: Int,
    open var name: String,
    open var type: eElementType,
    open var size: vSize,
    open var source: MutableList<Int>? = null,
    open var sink: MutableList<Int>? = null
)

open class cFlexStreamElement(
    override var id: Int,
    override var name: String,
    override var type: eElementType,
    override var size: vSize,
    override var source: MutableList<Int>? = null,
    override var sink: MutableList<Int>? = null,
    open var serverIP: String,
    open var serverPort: String,
    open var codecType: eCodecType,
    open var bitrate: Int
) : cElementType(id, name, type, size, source, sink)

data class cFlexVirtualDisplay(
    override var id: Int,
    override var name: String,
    override var type: eElementType,
    override var size: vSize,
    override var source: MutableList<Int>?,
    override var sink: MutableList<Int>?,
    var app: String
) : cElementType(id, name, type, size, source, sink)

data class cFlexDisplayView(
    override var id: Int,
    override var name: String,
    override var type: eElementType,
    override var source: MutableList<Int>?,
    override var sink: MutableList<Int>?,
    var displayid: Int,
    var posSize: MutableList<vPos_Size>,
    var crop_texture: MutableList<vCropTextureArea>,
    var touchmapping: MutableList<vTouchMapping>,
    var dewarpParameters: MutableList<deWarp_Parameters?>,
) : cElementType(id, name, type, vSize(0, 0), source, sink)

data class cFlexPIPView(
    override var id: Int,
    override var name: String,
    override var type: eElementType,
    override var source: MutableList<Int>?,
    override var sink: MutableList<Int>?,
    var crop_texture: vCropTextureArea
) : cElementType(id, name, type, vSize(0, 0), source, sink)

data class cFlexEncoder(
    override var id: Int,
    override var name: String,
    override var type: eElementType,
    override var size: vSize,
    override var source: MutableList<Int>?,
    override var sink: MutableList<Int>?,
    var crop_texture: vCropTextureArea,
    var touchmapping: vTouchMapping,
    override var serverIP: String,
    override var serverPort: String,
    override var codecType: eCodecType,
    override var bitrate: Int,
    var dewarpParameters: deWarp_Parameters?,
) : cFlexStreamElement(id, name, type, size, source, sink, serverIP, serverPort, codecType, bitrate)

data class cFlexDecoder(
    override var id: Int,
    override var name: String,
    override var type: eElementType,
    override var size: vSize,
    override var source: MutableList<Int>?,
    override var sink: MutableList<Int>?,
    override var serverIP: String,
    override var serverPort: String,
    override var codecType: eCodecType,
    override var bitrate: Int
) : cFlexStreamElement(id, name, type, size, source, sink, serverIP, serverPort, codecType, bitrate)

data class cSinkOption(
    var dewarpParameters: deWarp_Parameters?,
    var switch: cFlexSwitch?,
    var mux: cFlexMux?,
    var pip: cFlexPiP?
)

data class switchSrcParm(
    var source: Int,
    var channel: Int,
    var crop_texture: vCropTextureArea,
    var touchmapping: vTouchMapping,
    var dewarpParameters: deWarp_Parameters?)

data class cFlexSwitch(
    var id: Int,
    var name: String,
    var type: eElementType,
    var srcParms: MutableList<switchSrcParm>,
    var sink: Int,
    var posSize: vPos_Size
)

open class muxParm(
    open var switch: cFlexSwitch,
    open var channel: Int)

open class cFlexMux(
    override var id: Int,
    override var name: String,
    override var type: eElementType,
    override var size: vSize,
    override var source: MutableList<Int>?,
    override var sink: MutableList<Int>?,
    open var muxParms: MutableList<muxParm>,
    open var channel: Int
) : cElementType(id, name, type, size, source, sink)

data class cFlexPiP(
    override var id: Int,
    override var name: String,
    override var type: eElementType,
    override var size: vSize,
    override var source: MutableList<Int>?,
    override var sink: MutableList<Int>?,
    override var muxParms: MutableList<muxParm>,
    override var channel: Int
) : cFlexMux(id, name, type, size, source, sink,
    muxParms, channel)

enum class SpecialID(val id: Int) {
    DUMMY(-10)
}

class cParseFlexCompositor(context: Context, flexCompositorINI: String) {
    private val m_context = context
    private val m_flexCompositorINI = flexCompositorINI
    // Add the generated elements whose eElementType is one of:
    // VIRTUALDISPLAY, DISPLAYVIEW, STREAM, STREAMENCODER, or STREAMDECODER.
    private val m_elements = mutableListOf<cElementType>()
    // Add the generated elements whose eElementType is SWITCH:
    private val m_switches = mutableListOf<cFlexSwitch>()
    // Add the generated elements whose eElementType is MUX:
    private val m_muxs = mutableListOf<cFlexMux>()
    // Add the generated elements whose eElementType is PIP:
    private val m_pips = mutableListOf<cFlexPiP>()
    private val contentManager: cContentManager = cContentManager(context)
    private val m_envMap: MutableMap<String, String> = mutableMapOf<String, String>()
    private val m_tag = "cParseFlexCompositor"

    fun getElements(): MutableList<cElementType>
    {
        return m_elements
    }

    fun getSwitches(): MutableList<cFlexSwitch>
    {
        return m_switches
    }

    fun getMuxs(): MutableList<cFlexMux>
    {
        Log.d(m_tag, "m_muxs: ${m_muxs}")
        return m_muxs
    }

    fun getPips(): MutableList<cFlexPiP>
    {
        Log.d(m_tag, "m_pips: ${m_pips}")
        return m_pips
    }

    fun getEnvMap(): Map<String, String>
    {
        Log.d(m_tag, "m_envMap: ${m_envMap}")
        return m_envMap
    }

//    fun parse(){
//        m_elements.clear()
//        Log.d(m_tag,"${m_context.filesDir}")
//        //val file = File(m_context.filesDir, m_flexCompositorINI)
//        val file = File("/data/system", m_flexCompositorINI)
//
//        val config = """
//            ;====SA8295 flexCompositor.ini===
//            [screen]
//            ;screenName=interface-number,resolution,attrib(value…)
//            ;    interface : virtual/stream/hdmi/dp/lvds/vnc
//            ;    attribute enumeration
//            ;        touch(interface-num,x,y,width,height)
//            ;            interface: virTouch,streamTouch,hidTouch
//            ;        app(pathname)
//            virtualDisplay=virtual-1,1920x1080,\
//                touch(virtual-1,0,227,1365,853),\
//                app(org.cid.example/org.qtproject.qt.android.bindings.QtActivity)
//            dpDisplay=dp-0,5500x650
//            videoStream=stream-1,1920x1080,\
//                server(127.0.0.1,50000),\
//                touch(stream-1,0,0,1365,853)
//            videoStream2=stream-2,1920x1080,\
//                    server(127.0.0.1,50000)
//
//            [mapping]
//            ;screen routing map
//            ;source(x,y,width,height)->sink(x,y,width,height)
//            virtualDisplay(0,0,1920,227)->dpDisplay(500,500,5500,650)
//            virtualDisplay(0,227,1365,853)->videoStream(0,0,1920,1080)
//            videoStream2(960,540,960,540)->dpDisplay(0,0,960,540)
//        """.trimIndent()
//        //file.writeText(config)
////        val lines = config.split("\n")
//        if (file.exists()) {
//            val lines : List<String> = file.readLines()
//
//            val max_rows: Int = lines.size
//            var which_row: Int = 0
//
//            while (which_row < max_rows){
//                var line = removeComment(lines[which_row])
//                if(line.isEmpty()){
//                    which_row++
//                    continue
//                }
//
//                if(line.startsWith('[')){
//                    line = line.trim('[',']')
//                    if(line.equals("screen")) {
//                        which_row++
//                        which_row += parseScreenSection(lines, m_elements, max_rows, which_row)
//                    }
//                    if(line.equals("mapping")) {
//                        which_row++
//                        which_row += parseMappingSection(lines, m_elements, max_rows, which_row)
//                    }
//                }
//            }
//        } else {
//            Log.e(m_tag, "INI file not found in app storage")
//        }
//
//
//    }

    fun parse(){
        m_elements.clear()
        m_switches.clear()
        m_muxs.clear()
        m_pips.clear()
        m_envMap.clear()

        val lines : List<String> = contentManager.getCommandLines()

        val max_rows: Int = lines.size
        var which_row: Int = 0

        while (which_row < max_rows){
            var line = removeComment(lines[which_row])
            if(line.isEmpty()){
                which_row++
                continue
            }

            if(line.startsWith('[')){
                line = line.trim('[',']')
                if(line.equals("screen")) {
                    which_row++
                    which_row += parseScreenSection(lines, m_elements, max_rows, which_row)
                }
                else if(line.equals("mapping")) {
                    which_row++
                    which_row += parseMappingSection(lines, m_elements, max_rows, which_row)
                }
                else if(line.equals("env")){
                    which_row++
                    which_row += parseEnvSection(lines, m_envMap, max_rows, which_row)
                }
            }
        }

    }

    private fun removeComment(line: String): String{
        return line.substringBefore(';').trim()
    }

    private fun isNeedToCombine(line: String): Boolean{
        //next section
        if(line.startsWith('[')){
            return false
        }
        else if(line.isEmpty()){
            return false
        }
        else{
            return true
        }
    }

    private fun parseAmount(input: String): Int {
        val multiplier = when {
            input.endsWith("k", ignoreCase = true) -> 1_000
            input.endsWith("m", ignoreCase = true) -> 1_000_000
            input.endsWith("b", ignoreCase = true) -> 1_000_000_000
            else -> 1
        }
        val numberPart = input.dropLastWhile { it.isLetter() }.toInt()
        return numberPart * multiplier
    }

    private fun parseScreenSection(lines : List<String>, elementType_list : MutableList<cElementType>,
                                   max_rows: Int, start_line: Int): Int {
        var read_lines = 0
        while ((start_line + read_lines) < max_rows){
            var line = removeComment(lines[start_line + read_lines])

            //next section
            if(line.startsWith('[')){
                return read_lines
            }

            if(line.isEmpty()){
                read_lines++
                continue
            }

            while (line.endsWith("\\") && (start_line + read_lines + 1) < max_rows) {
                read_lines++
                var next_line = removeComment(lines[start_line + read_lines])
                if(isNeedToCombine(next_line)) {
                    line = line.substring(0, line.length - 1) + next_line
                }
                else
                {
                    line = line.substring(0, line.length - 1)
                    break
                }

            }
            parseScreenSection(line, elementType_list)

            read_lines++
        }
        return read_lines
    }

    private fun parseScreenSection(line : String, elementType_list : MutableList<cElementType>){
        val parts = line.split("=")
        if (parts.size == 2) {
            val name = parts[0].trim()
            val value = parts[1].trim()
            //Only split at commas[','] that are not inside parentheses["(*)"]
            val split_value = value.split(Regex(""",(?=(?:[^()]*\([^)]*\))*[^)]*$)"""))

            if(split_value.size >= 2){
                val I_F_type = split_value[0]
                var type : eElementType = eElementType.NULLTYPE
                var newcElementType: cElementType? = null
                var elementID: Int = elementType_list.size
                //determine interface
                when {
                    I_F_type.contains("virtual", ignoreCase = true) -> {
                        type = eElementType.VIRTUALDISPLAY
                        newcElementType = parseVirtualDisplayElement(name, split_value, elementID)
                        Log.d(m_tag,"[Screen] Interface type is virtual")
                    }
                    I_F_type.contains("stream", ignoreCase = true) -> {
                        type = eElementType.STREAM
                        newcElementType = parseStreamElement(name, split_value, elementID)
                        Log.d(m_tag,"[Screen] Interface type is stream")
                    }
                    I_F_type.contains("hdmi", ignoreCase = true) ||
                            I_F_type.contains("dp", ignoreCase = true) ||
                            I_F_type.contains("lvds", ignoreCase = true) -> {
                        type = eElementType.DISPLAYVIEW
                        newcElementType = parseDisplayViewElement(name, split_value, elementID)
                        Log.d(m_tag,"[Screen] Interface type is display")
                    }
                    I_F_type.contains("PIP", ignoreCase = true)  -> {
                        type = eElementType.PIPVIEW
                        newcElementType = parsePIPViewElement(name, split_value, elementID)
                        Log.d(m_tag,"[Screen] Interface type is pip view")
                    }
                    else -> {
                        Log.d(m_tag,"[Screen] Cannot determine which interface it is")
                    }
                }
                if(type != eElementType.NULLTYPE && newcElementType != null){
                    elementType_list.add(newcElementType)
                }
                else
                {
                    return
                }

                //determine size
                val size_str = split_value[1]
                val split_size_str = size_str.split('x')

                if(split_size_str.size == 2) {
                    val width = split_size_str[0].toIntOrNull()
                    val height = split_size_str[1].toIntOrNull()

                    if (width != null && height != null) {
                        newcElementType.size = vSize(width, height)
                        Log.d(m_tag,"[Screen] set element ${newcElementType.size}}")
                    } else {
                        Log.d(m_tag,"[Screen] Invalid input for width or height.")
                    }
                }

            }



        } else {
            Log.d(m_tag,"Format error: Unable to separate key and value.")
        }
    }

    private fun parseVirtualDisplayElement(name: String, line_split: List<String>, elementID : Int) : cElementType{
        var type : eElementType = eElementType.VIRTUALDISPLAY
        var offset: Int = 2
        var newcElementType: cFlexVirtualDisplay = cFlexVirtualDisplay(elementID,name,type,vSize(960,540),null,null,"")
        while (offset < line_split.size){
            when {
                line_split[offset].contains("app", ignoreCase = true) -> {
                    val result = line_split[offset].substringAfter("(").substringBefore(")")
                    newcElementType.app = result.trim()
                    Log.d(m_tag,"Virtual Display app option")
                }
            }
            offset++
        }
        Log.d(m_tag,"parseVirtualDisplayElement \n $newcElementType")
        return  newcElementType
    }

    private fun parseDisplayViewElement(name: String, line_split: List<String>, elementID : Int) : cElementType{
        var type : eElementType = eElementType.DISPLAYVIEW
        var offset: Int = 0
        var newcElementType: cFlexDisplayView = cFlexDisplayView(elementID,name,type,null,null,
            0, mutableListOf<vPos_Size>(), mutableListOf<vCropTextureArea>(),
            mutableListOf<vTouchMapping>(), mutableListOf<deWarp_Parameters?>()
        )
        while (offset < line_split.size){
            when {
                line_split[offset].contains("hdmi", ignoreCase = true) ||
                        line_split[offset].contains("dp", ignoreCase = true) ||
                        line_split[offset].contains("lvds", ignoreCase = true) -> {
                    // remove all non-digit characters using regex,
                    // then try to convert the result to an integer. If conversion fails, return null.
                    val result = line_split[offset].replace(Regex("[^0-9]"), "").toIntOrNull()
                    if(result != null){
                        newcElementType.displayid = result
                    }
                    Log.d(m_tag,"Display View id option")
                }
            }
            offset++
        }
        Log.d(m_tag,"parseDisplayViewElement \n $newcElementType")
        return  newcElementType
    }

    private fun parsePIPViewElement(name: String, line_split: List<String>, elementID : Int) : cElementType{
        var type : eElementType = eElementType.PIPVIEW
        var newcElementType: cFlexPIPView = cFlexPIPView(elementID,name,type,null,null,
            vCropTextureArea(0,0,960,540) )

        Log.d(m_tag,"parsePIPViewElement \n $newcElementType")
        return  newcElementType
    }

    private fun parseStreamElement(name: String, line_split: List<String>, elementID : Int) : cElementType{
        var type : eElementType = eElementType.STREAM
        var offset: Int = 2
        var newcElementType: cFlexStreamElement = cFlexStreamElement(elementID,name,type,vSize(960,540),null,null,"127.0.0.1","50000", eCodecType.H265, 5000000)
        while (offset < line_split.size){
            when {
                line_split[offset].contains("server", ignoreCase = true) -> {
                    val result = line_split[offset].substringAfter("(").substringBefore(")").split(',')
                    if(result.size == 2){
                        if(result[0].trim().matches(Regex("""^[0-9.]+$"""))){
                            newcElementType.serverIP = result[0].trim()
                        }
                        if(result[1].trim().matches(Regex("""^\d+$"""))){
                            newcElementType.serverPort = result[1].trim()
                        }
                        Log.d(m_tag,"Stream server option")
                    }
                }
                line_split[offset].contains("codec", ignoreCase = true) -> {
                    val result = line_split[offset].substringAfter("(").substringBefore(")")
                    when {
                        result.contains("h264", ignoreCase = true) ->{
                            newcElementType.codecType = eCodecType.H264
                        }
                        result.contains("h265", ignoreCase = true) ->{
                            newcElementType.codecType = eCodecType.H265
                        }
                    }
                    Log.d(m_tag,"Stream codec option")
                }
                line_split[offset].contains("bitrate", ignoreCase = true) -> {
                    val result = line_split[offset].substringAfter("(").substringBefore(")")
                    newcElementType.bitrate = parseAmount(result)
                    Log.d(m_tag,"Stream codec option")
                }
            }
            offset++
        }

        Log.d(m_tag,"parseStreamElement \n ${newcElementType.serverIP} ${newcElementType.serverPort}")
        return  newcElementType
    }

    private fun parseEnvSection(lines : List<String>, envMap: MutableMap<String, String>,
                                   max_rows: Int, start_line: Int): Int {
        var read_lines = 0
        while ((start_line + read_lines) < max_rows){
            var line = removeComment(lines[start_line + read_lines])

            //next section
            if(line.startsWith('[')){
                return read_lines
            }

            if(line.isEmpty()){
                read_lines++
                continue
            }

            while (line.endsWith("\\") && (start_line + read_lines + 1) < max_rows) {
                read_lines++
                var next_line = removeComment(lines[start_line + read_lines])
                if(isNeedToCombine(next_line)) {
                    line = line.substring(0, line.length - 1) + next_line
                }
                else
                {
                    line = line.substring(0, line.length - 1)
                    break
                }

            }
            parseEnvSection(line, envMap)

            read_lines++
        }
        return read_lines
    }

    private fun parseEnvSection(line : String, envMap: MutableMap<String, String>){
        val key_value : List<String> = line.split("=")
        if(key_value.size == 2){
            envMap[key_value[0]] = key_value[1]
        }
    }

    private fun parseMappingSection(lines : List<String>, elementType_list : MutableList<cElementType>,
                                   max_rows: Int, start_line: Int): Int {
        var read_lines = 0
        while ((start_line + read_lines) < max_rows){
            var line = removeComment(lines[start_line + read_lines])

            //next section
            if(line.startsWith('[')){
                return read_lines
            }

            if(line.isEmpty()){
                read_lines++
                continue
            }

            while (line.endsWith("\\") && (start_line + read_lines + 1) < max_rows) {
                read_lines++
                var next_line = removeComment(lines[start_line + read_lines])
                if(isNeedToCombine(next_line)) {
                    line = line.substring(0, line.length - 1) + next_line
                }
                else
                {
                    line = line.substring(0, line.length - 1)
                    break
                }
            }
            parseMappingSection(line, elementType_list)

            read_lines++
        }
        return read_lines
    }

    private fun parseMappingSection(line : String, elementType_list : MutableList<cElementType>){
        val parts = line.split("->")
        val final_elements = mutableListOf<cElementType>()
        if (parts.size == 2) {
            val source_with_view = parts[0].trim()
            val sink_with_view = parts[1].trim()
            val source = getElementNameOnMapping(source_with_view)
            val source_view = source_with_view.substringAfter("(").substringBefore(")")
            val sink = getElementNameOnMapping(sink_with_view)
            val sink_view = sink_with_view.substringAfter("(").substringBefore(")")
            val sinkOption: cSinkOption = cSinkOption(null, null, null, null)
            parseSinkOption(sink_with_view, sinkOption)
            //dummy is for mux
            if(source.equals("dummy") && sink.equals("dummy")){
                return
            }

            val src_view_split = source_view.split(',')
            val sink_view_split = sink_view.split(',')
            var src_view_crop: vCropTextureArea = vCropTextureArea(0,0,1,1)
            var sink_view_posSize: vPos_Size = vPos_Size(0,0 ,1,1)
            if(src_view_split.size == 4)
            {
                var x: Int? = src_view_split[0].trim().toIntOrNull()
                var y: Int? = src_view_split[1].trim().toIntOrNull()
                var width: Int? = src_view_split[2].trim().toIntOrNull()
                var height: Int? = src_view_split[3].trim().toIntOrNull()
                if(x == null || y == null || width == null || height == null){
                    return
                }
                src_view_crop  = vCropTextureArea(x,y,width,height)
            }

            if(sink_view_split.size == 4){
                var x: Int? = sink_view_split[0].trim().toIntOrNull()
                var y: Int? = sink_view_split[1].trim().toIntOrNull()
                var width: Int? = sink_view_split[2].trim().toIntOrNull()
                var height: Int? = sink_view_split[3].trim().toIntOrNull()
                if(x == null || y == null || width == null || height == null){
                    return
                }
                sink_view_posSize  = vPos_Size(x,y,width,height)
            }


            mappingSourceSink(elementType_list, source, sink, src_view_crop, sink_view_posSize, sinkOption)
        } else {
            Log.d(m_tag,"Format error: Unable to separate key and value.")
        }
    }

    private fun mappingSourceSink(elementType_list: MutableList<cElementType>, source: String, sink: String,
                                  src_view_crop: vCropTextureArea, sink_view_posSize: vPos_Size, sinkOption: cSinkOption){

        // First, use mappingOrganize_dummy() to process dummy elements; if not, proceed with further processing
        if(mappingOrganize_dummy(elementType_list, source, sink, src_view_crop, sink_view_posSize, sinkOption)){
            return
        }
        for (i in 0 until elementType_list.size) {
            var maybeSource = elementType_list[i]
            if( maybeSource.name.equals(source) &&
                (maybeSource.type == eElementType.VIRTUALDISPLAY ||
                        maybeSource.type == eElementType.STREAM ||
                        maybeSource.type == eElementType.STREAMDECODER))
            {
                for (j in 0 until elementType_list.size)
                {
                    var maybeSink = elementType_list[j]
                    if( j != i && maybeSink.name.equals(sink) &&
                        (maybeSink.type == eElementType.DISPLAYVIEW ||
                                maybeSink.type == eElementType.STREAM ||
                                maybeSink.type == eElementType.STREAMENCODER ||
                                maybeSink.type == eElementType.PIPVIEW )){

                        if(maybeSource.type == eElementType.STREAM){
                            val decoder: cFlexStreamElement = maybeSource as cFlexStreamElement
                            elementType_list[i] = cFlexDecoder(decoder.id, decoder.name,
                                eElementType.STREAMDECODER, decoder.size, null, null,
                                decoder.serverIP, decoder.serverPort, decoder.codecType, decoder.bitrate)
                        }

                        if(sinkOption.switch != null) {
                            srcSinkConnect_withSwitch(
                                elementType_list, i, j,
                                src_view_crop, sink_view_posSize, sinkOption
                            )
                        }
                        else if(sinkOption.mux != null || sinkOption.pip != null){
                            // PIP is also a kind of MUX, but it will eventually generate either cViewMux or cViewPiP.
                            // The cViewPiP includes a picture-in-picture mechanism.
                            srcSinkConnect_withMux(elementType_list, i, j,
                                src_view_crop, sink_view_posSize, sinkOption
                            )
                        }
                        else{
                            srcSinkConnect(
                                elementType_list, i, j,
                                src_view_crop, sink_view_posSize, sinkOption
                            )
                        }

                        Log.d(m_tag,"Match successful \n Source:${maybeSource.name}\n Sink:${maybeSink.name}\n")
                        return
                    }
                }
            }
        }
    }

    private fun mappingOrganize_dummy(elementType_list: MutableList<cElementType>, source: String, sink: String,
                                      src_view_crop: vCropTextureArea, sink_view_posSize: vPos_Size, sinkOption: cSinkOption): Boolean{
        if(source.equals("dummy") && sink.equals("dummy")){
            return false
        }
        if(sinkOption.mux != null || sinkOption.pip != null){
            if(source.equals("dummy")){
                for (j in 0 until elementType_list.size)
                {
                    var maybeSink = elementType_list[j]
                    if( maybeSink.name.equals(sink) &&
                        (maybeSink.type == eElementType.DISPLAYVIEW ||
                                maybeSink.type == eElementType.STREAM ||
                                maybeSink.type == eElementType.STREAMENCODER )){
                        dummySinkConnect_withMux(elementType_list, j, sink_view_posSize, sinkOption)
                        return true
                    }
                }
            }
            else if(sink.equals("dummy")){
                for (i in 0 until elementType_list.size) {
                    var maybeSource = elementType_list[i]
                    if (maybeSource.name.equals(source) &&
                        (maybeSource.type == eElementType.VIRTUALDISPLAY ||
                                maybeSource.type == eElementType.STREAM ||
                                maybeSource.type == eElementType.STREAMDECODER)) {
                        srcDummyConnect_withMux(elementType_list, i, src_view_crop, sinkOption)
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun srcSinkConnect(elementType_list: MutableList<cElementType>, source_index: Int,
                                        sink_index: Int, src_view_crop: vCropTextureArea,
                                        sink_view_posSize: vPos_Size, sinkOption: cSinkOption){
        var maybeSource = elementType_list[source_index]
        var maybeSink = elementType_list[sink_index]
        if(maybeSink.type == eElementType.STREAMENCODER){
            return
        }
        if(maybeSink.type == eElementType.STREAM){
            val encoder:  cFlexStreamElement = maybeSink as cFlexStreamElement
            elementType_list[sink_index] = cFlexEncoder(encoder.id, encoder.name,
                eElementType.STREAMENCODER, maybeSink.size, null, null,
                src_view_crop,
                vTouchMapping(src_view_crop.offsetX,src_view_crop.offsetY,src_view_crop.width,src_view_crop.height),
                encoder.serverIP, encoder.serverPort, encoder.codecType, encoder.bitrate, sinkOption.dewarpParameters)
            maybeSink = elementType_list[sink_index]
        }
        else if(maybeSink.type == eElementType.DISPLAYVIEW){
            val displayview: cFlexDisplayView = maybeSink as cFlexDisplayView
            displayview.posSize.add(sink_view_posSize)
            displayview.crop_texture.add(src_view_crop)
            displayview.touchmapping.add(vTouchMapping(src_view_crop.offsetX,src_view_crop.offsetY,src_view_crop.width,src_view_crop.height))
            displayview.dewarpParameters.add(sinkOption.dewarpParameters)
        }
        else if(maybeSink.type == eElementType.PIPVIEW){
            val pipview: cFlexPIPView = maybeSink as cFlexPIPView
            pipview.crop_texture = src_view_crop
        }

        if(maybeSource.sink == null)
        {
            maybeSource.sink = mutableListOf<Int>()
        }
        if(maybeSink.source == null)
        {
            maybeSink.source = mutableListOf<Int>()
        }
        maybeSource.sink!!.add(maybeSink.id)
        maybeSink.source!!.add(maybeSource.id)
    }

    private fun srcSinkConnect_withSwitch(elementType_list: MutableList<cElementType>, source_index: Int,
                                        sink_index: Int, src_view_crop: vCropTextureArea,
                                        sink_view_posSize: vPos_Size, sinkOption: cSinkOption){
        var maybeSource = elementType_list[source_index]
        var maybeSink = elementType_list[sink_index]
        if(sinkOption.switch == null){
            return
        }

        setSwitchParm(sinkOption.switch!!, sinkOption.switch!!.srcParms[0], maybeSource.id, maybeSink.id, src_view_crop, sink_view_posSize, sinkOption.dewarpParameters)

        if(maybeSink.type == eElementType.STREAM){
            // src(x,y,w,h)->sink(x,y,w,h),switch(0,1)
            //                               ↑
            //                           sinkOption
            // The sink's eElementType.STREAM hasn't been changed to eElementType.STREAMENCODER yet —
            // it's the first time being created, so the switch directly uses the sinkOption.
            val encoder:  cFlexStreamElement = maybeSink as cFlexStreamElement
            if(sinkOption.switch != null) {
                m_switches.add(sinkOption.switch!!)
            }
            elementType_list[sink_index] = cFlexEncoder(encoder.id, encoder.name,
                eElementType.STREAMENCODER, maybeSink.size, null, null,
                src_view_crop,
                vTouchMapping(src_view_crop.offsetX,src_view_crop.offsetY,src_view_crop.width,src_view_crop.height),
                encoder.serverIP, encoder.serverPort, encoder.codecType, encoder.bitrate, sinkOption.dewarpParameters)
            maybeSink = elementType_list[sink_index]
        }
        else if(maybeSink.type == eElementType.STREAMENCODER){
            // It has already been changed to eElementType.STREAMENCODER,
            // and this type can only be connected to one switch.
            // Therefore, we need to first check whether the current sinkOption's switch has the same ID.
            // If it does, add the current srcParam.
            val switch = classifySwitch(m_switches, sinkOption.switch!!)
            if(switch != null){
                switch.srcParms.add(sinkOption.switch!!.srcParms[0])
            }
        }
        else if(maybeSink.type == eElementType.DISPLAYVIEW){
            // When the type is eElementType.DISPLAYVIEW, it can be connected to multiple switches.
            // If the current sinkOption's switch ID is not in m_switches, add the switch.
            // If it already exists, add the sinkOption's srcParam to the corresponding switch.
            if(sinkOption.switch != null) {
                var switch = classifySwitch(m_switches, sinkOption.switch!!)
                if(switch == null) {
                    switch = sinkOption.switch
                    m_switches.add(switch!!)
                }
                else{
                    switch.srcParms.add(sinkOption.switch!!.srcParms[0])
                }
            }
        }

        if(maybeSource.sink == null)
        {
            maybeSource.sink = mutableListOf<Int>()
        }
        maybeSource.sink!!.add(maybeSink.id)
    }

    private fun srcSinkConnect_withMux(elementType_list: MutableList<cElementType>, source_index: Int,
                                          sink_index: Int, src_view_crop: vCropTextureArea,
                                          sink_view_posSize: vPos_Size, sinkOption: cSinkOption){
        var maybeSource = elementType_list[source_index]
        var maybeSink = elementType_list[sink_index]
        val now_mux: cFlexMux
        val switch: cFlexSwitch
        val srcParm: switchSrcParm
        if(sinkOption.mux == null && sinkOption.pip  == null){
            return
        }
        else if(sinkOption.mux != null){
            // Use a new mux(sinkOption.mux), or look for an existing mux with the same ID in the group(m_muxs).
            now_mux = managerMuxs(m_muxs, sinkOption.mux!!)
            // Generate a new switch based on the mux.
            val (s, p) = managerSwitchOfMux(now_mux, sinkOption.mux!!.channel)
            switch = s
            srcParm = p
        }
        else{
            // Use a new mux(sinkOption.pip), or look for an existing mux with the same ID in the group(m_pips).
            now_mux = managerMuxs(m_pips, sinkOption.pip!!)
            // Generate a new switch based on the mux.
            val (s, p) = managerSwitchOfMux(now_mux, sinkOption.pip!!.channel)
            switch = s
            srcParm = p
        }

        setSwitchParm(switch, srcParm,
            maybeSource.id, maybeSink.id, src_view_crop, sink_view_posSize, sinkOption.dewarpParameters)

        if(maybeSink.type == eElementType.STREAM){
            val encoder:  cFlexStreamElement = maybeSink as cFlexStreamElement

            elementType_list[sink_index] = cFlexEncoder(encoder.id, encoder.name,
                eElementType.STREAMENCODER, maybeSink.size, null, null,
                src_view_crop,
                vTouchMapping(src_view_crop.offsetX,src_view_crop.offsetY,src_view_crop.width,src_view_crop.height),
                encoder.serverIP, encoder.serverPort, encoder.codecType, encoder.bitrate, sinkOption.dewarpParameters)
            maybeSink = elementType_list[sink_index]
        }
        if(maybeSource.sink == null)
        {
            maybeSource.sink = mutableListOf<Int>()
        }
        maybeSource.sink!!.add(maybeSink.id)
    }

    private fun dummySinkConnect_withMux(elementType_list: MutableList<cElementType>,
                                       sink_index: Int, sink_view_posSize: vPos_Size, sinkOption: cSinkOption){
        var maybeSink = elementType_list[sink_index]
        val now_mux: cFlexMux
        val switch: cFlexSwitch
        val srcParm: switchSrcParm
        if(sinkOption.mux == null && sinkOption.pip  == null){
            return
        }
        else if(sinkOption.mux != null){
            // Use a new mux(sinkOption.mux), or look for an existing mux with the same ID in the group(m_muxs).
            now_mux = managerMuxs(m_muxs, sinkOption.mux!!)
            // Generate a new switch based on the mux.
            val (s, p) = managerSwitchOfMux(now_mux, sinkOption.mux!!.channel)
            switch = s
            srcParm = p
        }
        else{
            // Use a new mux(sinkOption.pip), or look for an existing mux with the same ID in the group(m_pips).
            now_mux = managerMuxs(m_pips, sinkOption.pip!!)
            // Generate a new switch based on the mux.
            val (s, p) = managerSwitchOfMux(now_mux, sinkOption.pip!!.channel)
            switch = s
            srcParm = p
        }
        setSwitchParm(switch, srcParm,
            SpecialID.DUMMY.id, maybeSink.id, vCropTextureArea(0,0,1,1), sink_view_posSize, sinkOption.dewarpParameters)

        if(maybeSink.type == eElementType.STREAM){
            val encoder:  cFlexStreamElement = maybeSink as cFlexStreamElement

            elementType_list[sink_index] = cFlexEncoder(encoder.id, encoder.name,
                eElementType.STREAMENCODER, maybeSink.size, null, null,
                vCropTextureArea(0,0,1,1),
                vTouchMapping(0,0,1,1),
                encoder.serverIP, encoder.serverPort, encoder.codecType, encoder.bitrate, sinkOption.dewarpParameters)
        }
    }

    private fun srcDummyConnect_withMux(elementType_list: MutableList<cElementType>, source_index: Int,
                                       src_view_crop: vCropTextureArea, sinkOption: cSinkOption){
        val now_mux: cFlexMux
        val switch: cFlexSwitch
        val srcParm: switchSrcParm
        if(sinkOption.mux == null && sinkOption.pip  == null){
            return
        }
        else if(sinkOption.mux != null){
            // Use a new mux(sinkOption.mux), or look for an existing mux with the same ID in the group(m_muxs).
            now_mux = managerMuxs(m_muxs, sinkOption.mux!!)
            // Generate a new switch based on the mux.
            val (s, p) = managerSwitchOfMux(now_mux, sinkOption.mux!!.channel)
            switch = s
            srcParm = p
        }
        else{
            // Use a new mux(sinkOption.pip), or look for an existing mux with the same ID in the group(m_pips).
            now_mux = managerMuxs(m_pips, sinkOption.pip!!)
            // Generate a new switch based on the mux.
            val (s, p) = managerSwitchOfMux(now_mux, sinkOption.pip!!.channel)
            switch = s
            srcParm = p
        }
        var maybeSource = elementType_list[source_index]
        if(maybeSource.type == eElementType.STREAM){
            val decoder: cFlexStreamElement = maybeSource as cFlexStreamElement
            elementType_list[source_index] = cFlexDecoder(decoder.id, decoder.name,
                eElementType.STREAMDECODER, decoder.size, null, null,
                decoder.serverIP, decoder.serverPort, decoder.codecType, decoder.bitrate)
        }

        setSwitchParm(switch, srcParm,
            maybeSource.id, SpecialID.DUMMY.id, src_view_crop, vPos_Size(0,0,1,1),sinkOption.dewarpParameters)
    }

    private fun getElementNameOnMapping(str: String):String{
        val indexOfParen = str.indexOf('(')
        val indexOfComma = str.indexOf(',')

        val firstIndex = listOf(indexOfParen, indexOfComma)
            .filter { it > 0 } // Filter out cases where it doesn't appear or appears at index 0
            .minOrNull()

        val name = if (firstIndex != null) {
            str.substring(0, firstIndex)
        } else {
            str
        }

        return name
    }

    private fun setSwitchParm(switch: cFlexSwitch, srcParm: switchSrcParm, sourceID: Int, sinkID: Int, src_view_crop: vCropTextureArea,
                              sink_view_posSize: vPos_Size, dewarpParameters: deWarp_Parameters?){
        srcParm.source = sourceID
        srcParm.crop_texture = src_view_crop
        srcParm.touchmapping = vTouchMapping(src_view_crop.offsetX,src_view_crop.offsetY,src_view_crop.width,src_view_crop.height)
        srcParm.dewarpParameters = dewarpParameters
        switch.sink= sinkID
        switch.posSize = sink_view_posSize
    }

    // Check if there is a mux with the same ID in the group(muxs: MutableList<T>).
    // If there is, return the one from the group with the matching ID;
    // if not, return the one used for comparison and the one add to group.
    private fun <T : cFlexMux> managerMuxs(muxs: MutableList<T>, mux: T) : cFlexMux{
        val already_exists_mux = classifyMux(muxs, mux)
        val now_mux: cFlexMux
        if(already_exists_mux != null){
            now_mux = already_exists_mux
        }
        else{
            now_mux = mux
            muxs.add(now_mux)
        }
        return now_mux
    }

    // Create a new switch in mux. All switches in the same mux share a mutableListOf<switchSrcParam>.
    private fun managerSwitchOfMux(mux: cFlexMux, addChannel: Int) : Pair<cFlexSwitch, switchSrcParm>{
        val switch_size = mux.muxParms.size
        val switch : cFlexSwitch
        val srcParm: switchSrcParm
        if(switch_size == 0){
            srcParm = switchSrcParm(-1, addChannel,
                vCropTextureArea(0,0,1,1),
                vTouchMapping(0,0,1,1),null
            )
            switch = cFlexSwitch(-1, "switch_$switch_size", eElementType.SWITCH,
                mutableListOf<switchSrcParm>(srcParm), -1 , vPos_Size(0,0,1,1)
            )
            val muxparm: muxParm = muxParm(switch, addChannel)
            mux.muxParms.add(muxparm)
        }
        else{
            // Share the object with the first-created switch
            srcParm = switchSrcParm(-1, addChannel,
                vCropTextureArea(0,0,1,1),
                vTouchMapping(0,0,1,1),null
            )
            mux.muxParms[0].switch.srcParms.add(srcParm)
            switch = cFlexSwitch(-1, "switch_$switch_size", eElementType.SWITCH,
                mux.muxParms[0].switch.srcParms, -1 , vPos_Size(0,0,1,1))
            val muxparm: muxParm = muxParm(switch, addChannel)
            mux.muxParms.add(muxparm)
        }
        return Pair(switch, srcParm)
    }

    private fun classifySwitch(beSwitch: cFlexSwitch?, switch: cFlexSwitch): cFlexSwitch?{
        if(beSwitch != null) {
            if (beSwitch.id == switch.id) {
                return beSwitch
            }
        }
        return null
    }

    private fun classifySwitch(switches: MutableList<cFlexSwitch>, switch: cFlexSwitch): cFlexSwitch?{
        for(sw in switches){
            if (sw.id == switch.id) {
                return sw
            }
        }
        return null
    }

    private fun <T : cFlexMux> classifyMux(muxs: MutableList<T>, mux: cFlexMux): cFlexMux?{
        for(m in muxs){
            if (m.id == mux.id) {
                return m
            }
        }
        return null
    }

    private fun parseSinkOption(line : String, sinkOption: cSinkOption) {
        //Only split at commas[','] that are not inside parentheses["(*)"]
        val split_value = line.split(Regex(""",(?=(?:[^()]*\([^)]*\))*[^)]*$)"""))
        if (split_value.size > 1) {
            for (i in 1 until split_value.size) {
                when {
                    split_value[i].contains("dewarp", ignoreCase = true) -> {
                        parseSinkOption_dewarp(split_value[i], sinkOption)
                    }
                    split_value[i].contains("switch", ignoreCase = true) -> {
                        parseSinkOption_switch(split_value[i], sinkOption)
                    }
                    split_value[i].contains("mux", ignoreCase = true) -> {
                        parseSinkOption_mux(split_value[i], sinkOption)
                    }
                    split_value[i].contains("pip", ignoreCase = true) -> {
                        parseSinkOption_pip(split_value[i], sinkOption)
                    }
                }
            }
        }
    }

    //parse "dewarp(enable, WxH, vertices...) / dewarp(true/false, 27x7, x0,y0,x1,y1...,xn,yn)" option
    private fun parseSinkOption_dewarp(option : String, sinkOption: cSinkOption){
        val result = option.substringAfter("(").substringBefore(")")
        val result_split = result.split(',')
        if(result_split.size >= 2){
            val enable = result_split[0].trim().toBoolean()
            if(enable) {
                val split_size_str = result_split[1].trim().split('x')

                if (split_size_str.size == 2) {
                    val column = split_size_str[0].trim().toIntOrNull()
                    val row = split_size_str[1].trim().toIntOrNull()

                    if (column != null && row != null) {
                        sinkOption.dewarpParameters =
                            deWarp_Parameters(ArrayList<Float>(), ArrayList<Float>(), column, row)
                        val vertices = sinkOption.dewarpParameters!!.vertices
                        for (j in 2 until (result_split.size - 1) step 2) {
                            vertices.add(result_split[j].trim().toFloatOrNull()?:0.0f)
                            vertices.add(result_split[j+1].trim().toFloatOrNull()?:0.0f)
                        }
                    }
                }
            }
        }
    }

    //parse "switch(id,channel) / switch(0,0)" option
    private fun parseSinkOption_switch(option : String, sinkOption: cSinkOption){
        val result = option.substringAfter("(").substringBefore(")")
        val result_split = result.split(',')
        if(result_split.size == 2){
            val id = result_split[0].trim().toIntOrNull()
            val channel = result_split[1].trim().toIntOrNull()

            if (id != null && channel != null) {
                val srcParm: switchSrcParm = switchSrcParm(-1, channel,
                    vCropTextureArea(0,0,1,1),
                    vTouchMapping(0,0,1,1),null
                )
                sinkOption.switch = cFlexSwitch(id, "switch_$id", eElementType.SWITCH,
                    mutableListOf<switchSrcParm>(srcParm), -1, vPos_Size(0,0,1,1)
                )
            }
        }
    }

    //parse "mux(id,channel,defaultchannel) / mux(0,1,1)" option
    private fun parseSinkOption_mux(option : String, sinkOption: cSinkOption){
        val result = option.substringAfter("(").substringBefore(")")
        val result_split = result.split(',')
        if(result_split.size >= 2){
            val id = result_split[0].trim().toIntOrNull()
            val channel = result_split[1].trim().toIntOrNull()

            if (id != null && channel != null) {
                if(sinkOption.mux == null) {
                    sinkOption.mux = cFlexMux(
                        id, "mux_$id", eElementType.MUX,
                        vSize(0, 0), null, null, mutableListOf<muxParm>(),
                        channel)
                }
            }
        }
    }

    //parse "pip(id,channel,defaultchannel) / pip(0,1,1)" option
    private fun parseSinkOption_pip(option : String, sinkOption: cSinkOption){
        val result = option.substringAfter("(").substringBefore(")")
        val result_split = result.split(',')
        if(result_split.size >= 2){
            val id = result_split[0].trim().toIntOrNull()
            val channel = result_split[1].trim().toIntOrNull()

            if (id != null && channel != null) {
                if(sinkOption.pip == null) {
                    sinkOption.pip = cFlexPiP(
                        id, "mux_$id", eElementType.PIP,
                        vSize(0, 0), null, null, mutableListOf<muxParm>(),
                        channel)
                }
            }
        }
    }


}