package jp.tech4u.searchrktncell

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import jp.tech4u.searchrktncell.util.EciUtil
import jp.tech4u.searchrktncell.util.Utils
import jp.tech4u.searchrktncell.util.ZipUtil
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class MyMapManager() {
    private lateinit var mMap: GoogleMap
    private var kmlParser = KmlParser()
    private var markerList = mutableListOf<Marker>()
    private val eciMap = mutableMapOf<Int, KmlParser.Placemark>()

    private lateinit var extDir: Path
    private var locLine: Polyline? = null    // 基地局-現在地の線
    var myMapLL: LatLng? = null           // 掴んでいる基地局の位置

    fun init(map: GoogleMap) {
        mMap = map
    }

    fun readMyMap(mid: String, cacheFile: File, bReload: Boolean) {
        val cutOff: Long = System.currentTimeMillis() - 60 * 60 * 1000
        if (bReload || !cacheFile.exists() || cacheFile.lastModified() < cutOff) {
            // 明示的なリロード、キャッシュが存在しない、古い場合　はサーバーから取得
            println("ＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸMyMapダウンロード開始します")
            val urlString = "https://www.google.com/maps/d/kml?mid=$mid"
            val inputStream = Utils.getInputStreamFromUrlString(urlString)
            if (inputStream != null) {
                Utils.saveFromInputStream(inputStream, cacheFile)
                println("ＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸMyMapダウンロードし、キャッシュを保存しますた")
            }
        }

        if (cacheFile.exists()) {
            parseMyMap(cacheFile)
        }
    }

    // KML解析（裏スレッド）
    private fun parseMyMap(cacheFile: File) {
        println("ＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸMyMap読み込み開始します")

        eciMap.clear()

        val name = cacheFile.nameWithoutExtension
        val parent = cacheFile.parent.toString()
        extDir = Paths.get(parent, name)
        val kmlFilePath = Paths.get(parent, name, "doc.kml")

        try {
            ZipUtil.unzipFile(cacheFile.toPath(), extDir)
            kmlParser.parse(Files.newInputStream(kmlFilePath))
            createEciMap(name)
        } catch (e: Exception) {
            println("ＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸMyMap読み込み失敗しました")
            println(e)
        }
        println("ＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸMyMap読み込みました")

    }

    // kmlParser.placemarks から eciMap を作る
    private fun createEciMap(name:String) {
        if (kmlParser.placemarks == null) return

        val getEciFn = if (name.contains("1Cndr7XT1tk7sB-_mZlJVGoujk1RdgW79"))
            EciUtil::getEciFromNullpon  // 秋田対応
        else
            EciUtil::getEciFromMemo // 通常

        for (placemark in kmlParser.placemarks) {
            val eciArrayN = getEciFn(placemark.name)
            for (i in eciArrayN) {
                eciMap[i] = placemark
                //println(i)
            }
            val eciArrayD = getEciFn(placemark.description)
            for (i in eciArrayD) {
                eciMap[i] = placemark
                //println(i)
            }
        }
    }

    fun plotPlacemark() {
        // 既存マーカークリア
        for (marker in markerList) {
            marker.remove()
        }
        markerList.clear()

        if (kmlParser.placemarks == null) return

        // マーカープロット
        for (placemark in kmlParser.placemarks) {
            plotImpl(placemark)
        }
    }

    // プロットする。実装部。
    private fun plotImpl(placemark: KmlParser.Placemark) {
        if (placemark.ll != null) {
            val kmlStyle = kmlParser.kmlStyleMap[placemark.styleUrl]
            val markerOptions =
                MarkerOptions()
                    .position(placemark.ll)
                    .title(placemark.name)
            //.snippet(placemark.description)

            if (kmlStyle?.href != null) {
                if (!kmlStyle!!.href!!.startsWith("http://") && !kmlStyle!!.href!!.startsWith("https://")) {
                    val bitmap = loadBitmap(kmlStyle)
                    val bitmap2 = Bitmap.createScaledBitmap(
                        bitmap,
                        (84 * kmlStyle.scale!!).toInt(), (84 * kmlStyle.scale!!).toInt(), true
                    )
                    val bitmap3 = BitmapDescriptorFactory.fromBitmap(bitmap2)
                    markerOptions.icon(bitmap3)
                    if (kmlStyle.x == -1 && kmlStyle.y == -1) {
                        markerOptions.anchor(0.5f, 0.5f)
                    }
                }
            }
            val marker: Marker = mMap.addMarker(markerOptions)
            markerList.add(marker)
        }
    }

    private fun loadBitmap(kmlStyle: KmlParser.KmlStyle): Bitmap {
        val iconPath = Paths.get(extDir.toString(), kmlStyle.href)
        val path = iconPath.toString()
        return BitmapFactory.decodeFile(path)
    }

    fun drawLine(latlng: LatLng, cellid: Int, mMap: GoogleMap): Boolean {

        if (locLine != null) {
            locLine!!.remove()
            locLine = null
        }

        if (eciMap.containsKey(cellid)) {
            val placemark = eciMap[cellid]!!
            myMapLL = placemark.ll
            val context = MapsActivity.getInstance()
            locLine = mMap.addPolyline(
                PolylineOptions()
                    .add(latlng, myMapLL)
                    .color(ContextCompat.getColor(context, R.color.colorCurrToCellLine))
            )
            return true
        }
        myMapLL = null
        return false
    }
}