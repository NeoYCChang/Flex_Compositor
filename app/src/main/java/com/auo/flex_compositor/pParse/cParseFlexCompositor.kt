package com.auo.flex_compositor.pParse

import android.content.Context
import android.util.Log
import com.auo.flex_compositor.pInterface.eCodecType
import java.io.File
import com.auo.flex_compositor.pInterface.vSize
import com.auo.flex_compositor.pInterface.vPos_Size
import com.auo.flex_compositor.pInterface.vTouchMapping
import com.auo.flex_compositor.pInterface.vCropTextureArea

enum class eElementType{
    VIRTUALDISPLAY, DISPLAYVIEW, STREAM, STREAMENCODER, STREAMDECODER, NULLTYPE
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
    open var codecType: eCodecType
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
    var touchmapping: MutableList<vTouchMapping>
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
    override var codecType: eCodecType
) : cFlexStreamElement(id, name, type, size, source, sink, serverIP, serverPort, codecType)

data class cFlexDecoder(
    override var id: Int,
    override var name: String,
    override var type: eElementType,
    override var size: vSize,
    override var source: MutableList<Int>?,
    override var sink: MutableList<Int>?,
    override var serverIP: String,
    override var serverPort: String,
    override var codecType: eCodecType
) : cFlexStreamElement(id, name, type, size, source, sink, serverIP, serverPort, codecType)



class cParseFlexCompositor(context: Context, flexCompositorINI: String) {
    private val m_context = context
    private val m_flexCompositorINI = flexCompositorINI
    private val m_elements = mutableListOf<cElementType>()
    private val m_tag = "cParseFlexCompositor"

    fun getElements(): MutableList<cElementType>
    {
        return m_elements
    }

    fun parse(){
        m_elements.clear()
        Log.d(m_tag,"${m_context.filesDir}")
        //val file = File(m_context.filesDir, m_flexCompositorINI)
        val file = File("/data/system", m_flexCompositorINI)

        val config = """
            ;====SA8295 flexCompositor.ini===
            [screen]
            ;screenName=interface-number,resolution,attrib(value…)
            ;    interface : virtual/stream/hdmi/dp/lvds/vnc
            ;    attribute enumeration
            ;        touch(interface-num,x,y,width,height)
            ;            interface: virTouch,streamTouch,hidTouch
            ;        app(pathname)
            virtualDisplay=virtual-1,1920x1080,\
                touch(virtual-1,0,227,1365,853),\
                app(org.cid.example/org.qtproject.qt.android.bindings.QtActivity)
            dpDisplay=dp-0,5500x650
            videoStream=stream-1,1920x1080,\
                server(127.0.0.1,50000),\
                touch(stream-1,0,0,1365,853)
            videoStream2=stream-2,1920x1080,\
                    server(127.0.0.1,50000)
                
            [mapping]
            ;screen routing map
            ;source(x,y,width,height)->sink(x,y,width,height)
            virtualDisplay(0,0,1920,227)->dpDisplay(500,500,5500,650)
            virtualDisplay(0,227,1365,853)->videoStream(0,0,1920,1080)
            videoStream2(960,540,960,540)->dpDisplay(0,0,960,540)
        """.trimIndent()
        //file.writeText(config)
//        val lines = config.split("\n")
        if (file.exists()) {
            val lines : List<String> = file.readLines()

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
                    if(line.equals("mapping")) {
                        which_row++
                        which_row += parseMappingSection(lines, m_elements, max_rows, which_row)
                    }
                }
            }
        } else {
            Log.e(m_tag, "INI file not found in app storage")
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
            mutableListOf<vTouchMapping>()
        )
        while (offset < line_split.size){
            when {
                line_split[offset].contains("hdmi", ignoreCase = true) ||
                        line_split[offset].contains("dp", ignoreCase = true) ||
                        line_split[offset].contains("lvds", ignoreCase = true) -> {
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

    private fun parseStreamElement(name: String, line_split: List<String>, elementID : Int) : cElementType{
        var type : eElementType = eElementType.STREAM
        var offset: Int = 2
        var newcElementType: cFlexStreamElement = cFlexStreamElement(elementID,name,type,vSize(960,540),null,null,"127.0.0.1","50000", eCodecType.H265)
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
            }
            offset++
        }

        Log.d(m_tag,"parseStreamElement \n ${newcElementType.serverIP} ${newcElementType.serverPort}")
        return  newcElementType
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
            val source = source_with_view.substringBefore("(")
            val source_view = source_with_view.substringAfter("(").substringBefore(")")
            val sink = sink_with_view.substringBefore("(")
            val sink_view = sink_with_view.substringAfter("(").substringBefore(")")

            val src_view_split = source_view.split(',')
            val sink_view_split = sink_view.split(',')
            var src_view_crop: vCropTextureArea
            var sink_view_posSize: vPos_Size
            if(src_view_split.size != 4 || sink_view_split.size != 4)
            {
                return
            }
            else
            {
                var x: Int? = src_view_split[0].trim().toIntOrNull()
                var y: Int? = src_view_split[1].trim().toIntOrNull()
                var width: Int? = src_view_split[2].trim().toIntOrNull()
                var height: Int? = src_view_split[3].trim().toIntOrNull()
                if(x == null || y == null || width == null || height == null){
                    return
                }
                src_view_crop  = vCropTextureArea(x,y,width,height)
                x = sink_view_split[0].trim().toIntOrNull()
                y = sink_view_split[1].trim().toIntOrNull()
                width = sink_view_split[2].trim().toIntOrNull()
                height = sink_view_split[3].trim().toIntOrNull()
                if(x == null || y == null || width == null || height == null){
                    return
                }
                sink_view_posSize  = vPos_Size(x,y,width,height)
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
                            maybeSink.type == eElementType.STREAM) ){

                            if(maybeSource.type == eElementType.STREAM){
                                val decoder: cFlexStreamElement = maybeSource as cFlexStreamElement
                                elementType_list[i] = cFlexDecoder(decoder.id, decoder.name,
                                    eElementType.STREAMDECODER, decoder.size, null, null,
                                    decoder.serverIP, decoder.serverPort, decoder.codecType)
                                maybeSource = elementType_list[i]
                            }
                            if(maybeSink.type == eElementType.STREAM){
                                val encoder:  cFlexStreamElement = maybeSink as cFlexStreamElement
                                elementType_list[j] = cFlexEncoder(encoder.id, encoder.name,
                                    eElementType.STREAMENCODER, maybeSink.size, null, null,
                                    src_view_crop,
                                    vTouchMapping(src_view_crop.offsetX,src_view_crop.offsetY,src_view_crop.width,src_view_crop.height),
                                    encoder.serverIP, encoder.serverPort, encoder.codecType)
                                maybeSink = elementType_list[j]
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
                            if(maybeSink.type == eElementType.DISPLAYVIEW){
                                val displayview: cFlexDisplayView = maybeSink as cFlexDisplayView
                                displayview.posSize.add(sink_view_posSize)
                                displayview.crop_texture.add(src_view_crop)
                                displayview.touchmapping.add(vTouchMapping(src_view_crop.offsetX,src_view_crop.offsetY,src_view_crop.width,src_view_crop.height))
                            }

                            Log.d(m_tag,"Match successful \n Source:${maybeSource}\n Sink:${maybeSink}\n")
                        }
                    }
                }
            }
        } else {
            Log.d(m_tag,"Format error: Unable to separate key and value.")
        }
    }


}