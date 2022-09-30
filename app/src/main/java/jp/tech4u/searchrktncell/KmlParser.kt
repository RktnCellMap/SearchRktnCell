package jp.tech4u.searchrktncell

import android.util.Xml
import com.google.android.gms.maps.model.LatLng
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream

private val ns: String? = null

class KmlParser {
    val placemarks = mutableListOf<Placemark>()
    val kmlStyleMap = mutableMapOf<String, KmlStyle>()
    private val kmlStyleMapMap = mutableMapOf<String, String>()
    var name: String = ""
    var description: String = ""

    data class Placemark(
        val name: String,
        val description: String,
        val styleUrl: String,
        val ll: LatLng,
        var snippet: String
    )

    data class KmlStyle(
        var styleUrl: String?,
        var key: String?,
        var href: String?,
        var x: Int?,
        var y: Int?,
        var scale: Float?
    )

    @Throws(XmlPullParserException::class, IOException::class)
    fun parse(inputStream: InputStream) {
        placemarks.clear()
        kmlStyleMap.clear()
        kmlStyleMapMap.clear()
        name = ""
        description = ""
        inputStream.use { inputStream ->
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(inputStream, null)
            parser.nextTag()
            readKml(parser)
        }
        for ((k, v) in kmlStyleMapMap) {
            if (kmlStyleMap[v] != null) {
                kmlStyleMap[k] = kmlStyleMap[v]!!
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

    private fun createKmlStyle(): KmlParser.KmlStyle {
        return KmlStyle(null, null, null, null, null, null)
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun readStyle(parser: XmlPullParser) {
        var kmlStyle = createKmlStyle()
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
                "Placemark" -> placemarks.add(readPlacemark(parser))
                else -> skip(parser)
            }
        }
        parser.require(XmlPullParser.END_TAG, ns, "Folder")
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun readPlacemark(parser: XmlPullParser): Placemark {
        parser.require(XmlPullParser.START_TAG, ns, "Placemark")
        var name = ""
        var description = ""
        var styleUrl = ""
        var ll: LatLng = LatLng(0.0, 0.0)
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "name" -> name = readTag(parser, "name")
                "description" -> description = readTag(parser, "description")
                "styleUrl" -> styleUrl = readTag(parser, "styleUrl")
                "Point" -> ll = readPoint(parser)
                "address" -> ll = readAddress(parser)
                else -> skip(parser)
            }
        }
        parser.require(XmlPullParser.END_TAG, ns, "Placemark")
        styleUrl = styleUrl.substring(1)   // 先頭の#を取る
        return Placemark(name, description, styleUrl, ll, "")
    }

    @Throws(IOException::class, XmlPullParserException::class)
    private fun readPoint(parser: XmlPullParser): LatLng {
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
        var ll: LatLng = LatLng(0.0, 0.0)
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
    private fun readAddress(parser: XmlPullParser): LatLng {
        var ll: LatLng = LatLng(0.0, 0.0)

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