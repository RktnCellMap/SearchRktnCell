package jp.tech4u.searchrktncell

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.Xml
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import jp.tech4u.searchrktncell.util.EciUtil
import jp.tech4u.searchrktncell.util.ZipUtil
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

private val ns: String? = null

class KmlParser {
    companion object {
        private const val TAG = "KmlParser"
    }

    val placemarks = mutableListOf<Placemark>()
    val kmlStyleMap = mutableMapOf<String, KmlStyle>()
    var markerList = mutableListOf<Marker>()
    val eciMap = mutableMapOf<Long, Placemark>()
    lateinit var extDir: Path

    private val kmlStyleMapMap = mutableMapOf<String, String>()
    var name: String = ""
    var description: String = ""

    var eciDefined = false  // eNB-LCIDの定義が1つ以上あるかどうか

    data class Placemark(
        val name: String,
        val description: String,
        val styleUrl: String,
        val ll: LatLng,
        val eciAry: List<Long>,
        val snippet: String
    )

    data class KmlStyle(
        var styleUrl: String?,
        var key: String?,
        var href: String?,
        var x: Int?,
        var y: Int?,
        var scale: Float?
    )

    fun clear() {
        placemarks.clear()
        kmlStyleMap.clear()
        kmlStyleMapMap.clear()
        eciMap.clear()

        name = ""
        description = ""
    }


    fun loadBitmap(kmlStyle: KmlStyle): Bitmap? {
        val iconPath = Paths.get(extDir.toString(), kmlStyle.href)
        val path = iconPath.toString()
        return BitmapFactory.decodeFile(path)
    }

    // KML解析（裏スレッド）
    fun parseMyMap(cacheFile: File) {
        Log.i(TAG, "ＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸMyMap読み込み開始します")

        val name = cacheFile.nameWithoutExtension

        @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        val parent: String = cacheFile.parent.toString()
        extDir = Paths.get(parent, name)
        val kmlFilePath = Paths.get(parent, name, "doc.kml")

        try {
            ZipUtil.unzipFile(cacheFile.toPath(), extDir)

            this.parse(Files.newInputStream(kmlFilePath))
        } catch (e: Exception) {
            Log.w(TAG, "ＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸMyMap読み込み失敗しました")
            Log.w(TAG, e.toString())
            this.clear()
        }
        if (this.eciMap.isNotEmpty())
            Log.i(TAG, "ＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸMyMap読み込みました")
    }

    @Throws(XmlPullParserException::class, IOException::class)
    fun parse(inputStream: InputStream) {
        this.clear()
        inputStream.use {
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(it, null)
            parser.nextTag()
            readKml(parser)

            createEciMap()
        }
        for ((k, v) in kmlStyleMapMap) {
            if (kmlStyleMap[v] != null) {
                kmlStyleMap[k] = kmlStyleMap[v]!!
            }
        }
    }

    // placemarks から eciMap を作る
    private fun createEciMap() {
        for (placemark in placemarks) {
            for (eci in placemark.eciAry) {
                eciMap[eci] = placemark
            }
        }
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun readKml(parser: XmlPullParser) {

        parser.require(XmlPullParser.START_TAG, ns, "kml")
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (parser.name == "Document") readDocument(parser)
            else skip(parser)
        }
    }


    @Throws(XmlPullParserException::class, IOException::class)
    private fun readDocument(parser: XmlPullParser) {
        parser.require(XmlPullParser.START_TAG, ns, "Document")
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "name" -> name = readTag(parser, "name")
                "description" -> description = readTag(parser, "description")
                "Style" -> readStyle(parser)
                "StyleMap" -> readStyleMap(parser)
                "Folder" -> readFolder(parser)
                else -> skip(parser)
            }
        }
        parser.require(XmlPullParser.END_TAG, ns, "Document")
    }

    private fun createKmlStyle(): KmlStyle {
        return KmlStyle(null, null, null, null, null, null)
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun readStyle(parser: XmlPullParser) {
        val kmlStyle = createKmlStyle()
        parser.require(XmlPullParser.START_TAG, ns, "Style")
        val id = parser.getAttributeValue(null, "id")
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "IconStyle" -> readIconStyle(parser, kmlStyle)
                //"LabelStyle" -> readLabelStyle(parser, kmlStyle)
                else -> {
                    skip(parser)
                }
            }
        }
        parser.require(XmlPullParser.END_TAG, ns, "Style")
        kmlStyleMap[id] = kmlStyle
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun readIconStyle(parser: XmlPullParser, kmlStyle: KmlStyle) {
        kmlStyle.scale = 1f
        kmlStyle.x = -1
        kmlStyle.y = -1

        parser.require(XmlPullParser.START_TAG, ns, "IconStyle")
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "scale" -> {
                    kmlStyle.scale = readTag(parser, "scale").toFloat()
                }
                "Icon" -> {
                    while (parser.next() != XmlPullParser.END_TAG) {
                        if (parser.eventType != XmlPullParser.START_TAG) {
                            continue
                        }
                        when (parser.name) {
                            "href" -> kmlStyle.href = readTag(parser, "href")
                            else -> skip(parser)
                        }
                    }
                    parser.require(XmlPullParser.END_TAG, ns, "Icon")
                }
                "hotSpot" -> {
                    kmlStyle.x = parser.getAttributeValue(null, "x").toInt()
                    kmlStyle.y = parser.getAttributeValue(null, "y").toInt()
                    readTag(parser, "hotSpot")  // 終了タグ読み飛ばし
                }
                else -> {
                    skip(parser)
                }
            }
        }
        parser.require(XmlPullParser.END_TAG, ns, "IconStyle")
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun readStyleMap(parser: XmlPullParser) {
        parser.require(XmlPullParser.START_TAG, ns, "StyleMap")
        val styleMapId = parser.getAttributeValue(null, "id")
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "Pair" -> {
                    var key = ""
                    var styleUrl = ""
                    while (parser.next() != XmlPullParser.END_TAG) {
                        if (parser.eventType != XmlPullParser.START_TAG) {
                            continue
                        }
                        when (parser.name) {
                            "key" -> key = readTag(parser, "key")
                            "styleUrl" -> styleUrl = readTag(parser, "styleUrl")
                            else -> skip(parser)
                        }
                    }

                    if (key == "normal") {
                        styleUrl = styleUrl.substring(1)   // 先頭の#を取る
                        kmlStyleMapMap[styleMapId] = styleUrl
                    }
                    parser.require(XmlPullParser.END_TAG, ns, "Pair")
                }
                else -> {
                    skip(parser)
                }
            }
        }
        parser.require(XmlPullParser.END_TAG, ns, "StyleMap")
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun readFolder(parser: XmlPullParser) {
        parser.require(XmlPullParser.START_TAG, ns, "Folder")
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "name" -> readTag(parser, parser.name)
                "Placemark" -> {
                    val placemark = readPlacemark(parser)
                    if (placemark != null) {
                        placemarks.add(placemark)
                    }
                }
                else -> skip(parser)
            }
        }
        parser.require(XmlPullParser.END_TAG, ns, "Folder")
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun readPlacemark(parser: XmlPullParser): Placemark? {
        parser.require(XmlPullParser.START_TAG, ns, "Placemark")
        var name = ""
        var description = ""
        var styleUrl = ""
        var ll: LatLng? = null
        var eciMutableList = mutableListOf<Long>()
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "name" -> name = readTag(parser, "name").trim()
                "description" -> description = readTag(parser, "description").trim()
                "styleUrl" -> styleUrl = readTag(parser, "styleUrl")
                "Point" -> ll = readPoint(parser)
                "address" -> ll = readAddress(parser)
                "ExtendedData" -> eciMutableList = readExtendedData(parser)
                else -> skip(parser)
            }
        }
        parser.require(XmlPullParser.END_TAG, ns, "Placemark")

        // 座標不明なら非対象
        if (ll == null) return null

        if (eciMutableList.count() == 0) {
            // ExtendedData にデータが無ければ、name,descriptionから取得
        	eciMutableList.addAll(EciUtil.getEciFromMemo(name).asIterable())
        	eciMutableList.addAll(EciUtil.getEciFromMemo(description).asIterable())
        }
        val eciList = eciMutableList.sorted().distinct()
        if (!eciDefined && 0 < eciList.count()) {
            eciDefined = true
        }

        styleUrl = styleUrl.substring(1)   // 先頭の#を取る
        return Placemark(name, description, styleUrl, ll, eciList, EciUtil.getEnbLcid(eciList))
    }

    @Throws(IOException::class, XmlPullParserException::class)
    private fun readExtendedData(parser: XmlPullParser): MutableList<Long> {
        // 参考: https://twitter.com/nullpon44011/status/1353901694015311872
        var b3enb = ""
        var b3ci = ""
        parser.require(XmlPullParser.START_TAG, ns, "ExtendedData")
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "Data" -> {
                    val name = parser.getAttributeValue(null, "name")
                    val value = readChildTag(parser, "value")
                    if (name == "B3 eNB") {
                        b3enb = value
                    } else if (name == "B3 CI") {
                        b3ci = value
                    }
                }
                else -> skip(parser)
            }
        }
        parser.require(XmlPullParser.END_TAG, ns, "ExtendedData")

        val eciList = mutableListOf<Long>()
        if (b3enb != "" && b3ci != "") {
            val enb = b3enb.toFloat().toLong()  // 小数点がついている場合があるので、一旦floatにしています
            val ciAry = b3ci.split('/')
            for (ciStr in ciAry) {
                val ci = ciStr.toInt()
                val eci = enb * 256 + ci
                eciList.add(eci)
            }
        }
        return eciList
    }

    @Throws(IOException::class, XmlPullParserException::class)
    private fun readPoint(parser: XmlPullParser): LatLng? {
        parser.require(XmlPullParser.START_TAG, ns, "Point")

        var coordinates: String? = null
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "coordinates" -> coordinates = readTag(parser, "coordinates")
                else -> skip(parser)
            }
        }
        parser.require(XmlPullParser.END_TAG, ns, "Point")
        var ll: LatLng? = null
        if (coordinates != null) {
            val cols = coordinates.split(",")
            if (2 <= cols.size) {
                // lngが先
                val lng = cols[0].toDouble()
                val lat = cols[1].toDouble()
                ll = LatLng(lat, lng)
            }
        }
        return ll
    }

    @Throws(IOException::class, XmlPullParserException::class)
    private fun readAddress(parser: XmlPullParser): LatLng? {
        var ll: LatLng? = null

        parser.require(XmlPullParser.START_TAG, ns, "address")
        val value = readText(parser)
        val cols = value.split(",")
        if (2 <= cols.size) {
            // latが先
            val lat = cols[0].toDouble()
            val lng = cols[1].toDouble()
            ll = LatLng(lat, lng)
        }

        parser.require(XmlPullParser.END_TAG, ns, "address")
        return ll
    }

    @Throws(IOException::class, XmlPullParserException::class)
    private fun readChildTag(parser: XmlPullParser, tag: String): String {
        var value = ""
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                tag -> value = readTag(parser, tag)
                else -> skip(parser)
            }
        }
        return value
    }

    @Throws(IOException::class, XmlPullParserException::class)
    private fun readTag(parser: XmlPullParser, tag: String): String {
        parser.require(XmlPullParser.START_TAG, ns, tag)
        val value = readText(parser)
        parser.require(XmlPullParser.END_TAG, ns, tag)
        return value
    }

    @Throws(IOException::class, XmlPullParserException::class)
    private fun readText(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text
            parser.nextTag()
        }
        return result
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) {
            throw IllegalStateException()
        }
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }
}