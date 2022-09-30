package jp.tech4u.searchrktncell

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import jp.tech4u.searchrktncell.data.LogVO
import jp.tech4u.searchrktncell.util.MyException
import jp.tech4u.searchrktncell.util.Utils
import jp.tech4u.searchrktncell.util.ZipUtil
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class MyMapManager {
    companion object {
        private const val TAG = "MyMapManager"
    }

    private var kmlParser = KmlParser()

    private var markerList = mutableListOf<Marker>()
    private val eciMap = mutableMapOf<Long, KmlParser.Placemark>()

    private lateinit var extDir: Path
    private var locLine: Polyline? = null    // 基地局-現在地の線
    var myMapLL: LatLng? = null           // 掴んでいる基地局の位置

    fun readMyMap(mid: String, cacheFile: File, bReload: Boolean) {
        val cutOff: Long = System.currentTimeMillis() - 60 * 60 * 1000
        if (bReload || !cacheFile.exists() || cacheFile.lastModified() < cutOff) {
            // 明示的なリロード、キャッシュが存在しない、古い場合　はサーバーから取得
            Log.i(TAG, "ＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸMyMapダウンロード開始します")
            val urlString = "https://www.google.com/maps/d/kml?mid=$mid"
            val con = Utils.getHttpURLConnectionFromUrlString(urlString)
            var inputStream: InputStream? = null
            try {
                inputStream = con.inputStream
            } catch (e: IOException) {
                markerList.clear()
                eciMap.clear()
                when (con.responseCode) {
                    403 -> throw MyException("マイマップが非公開です")
                    404 -> throw MyException("マイマップが見つかりません")
                    in 500..599 -> throw MyException("マイマップサーバーエラー(${con.responseCode})")
                    else -> throw MyException("マイマップ取得エラー(${con.responseCode})")
                }
            }
            if (inputStream != null) {
                Utils.saveFromInputStream(inputStream, cacheFile)
                Log.i(TAG, "ＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸMyMapダウンロードし、キャッシュを保存しますた")
            }
        }

        if (cacheFile.exists()) {
            parseMyMap(cacheFile)
        }
    }

    // KML解析（裏スレッド）
    private fun parseMyMap(cacheFile: File) {
        Log.i(TAG, "ＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸMyMap読み込み開始します")

        eciMap.clear()

        val name = cacheFile.nameWithoutExtension

        @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        val parent: String = cacheFile.parent.toString()
        extDir = Paths.get(parent, name)
        val kmlFilePath = Paths.get(parent, name, "doc.kml")

        try {
            ZipUtil.unzipFile(cacheFile.toPath(), extDir)
            kmlParser.parse(Files.newInputStream(kmlFilePath))
            createEciMap()
        } catch (e: Exception) {
            Log.w(TAG, "ＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸMyMap読み込み失敗しました")
            Log.w(TAG, e.toString())
            return
        }
        Log.i(TAG, "ＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸＸMyMap読み込みました")
    }

    // kmlParser.placemarks から eciMap を作る
    private fun createEciMap() {
        for (placemark in kmlParser.placemarks) {
            for (eci in placemark.eciAry) {
                eciMap[eci] = placemark
            }
        }
    }

    fun plotPlacemark(mMap: GoogleMap) {
        // 既存マーカークリア
        for (marker in markerList) {
            marker.remove()
        }
        markerList.clear()

        // マーカープロット
        for (placemark in kmlParser.placemarks) {
            plotImpl(placemark, mMap)
        }
    }

    // プロットする。実装部。
    private fun plotImpl(placemark: KmlParser.Placemark, mMap: GoogleMap) {
        val kmlStyle = kmlParser.kmlStyleMap[placemark.styleUrl]
        val markerOptions =
            MarkerOptions()
                .position(placemark.ll)
                .title(placemark.name)
                .zIndex(4000f)
        if (placemark.snippet != "") markerOptions.snippet(placemark.snippet)

        if (kmlStyle?.href != null) {
            if (!kmlStyle.href!!.startsWith("http://") && !kmlStyle.href!!.startsWith("https://")) {
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

    private fun loadBitmap(kmlStyle: KmlParser.KmlStyle): Bitmap {
        val iconPath = Paths.get(extDir.toString(), kmlStyle.href)
        val path = iconPath.toString()
        return BitmapFactory.decodeFile(path)
    }

    // 線を描いたら true を返す
    fun drawLine(latlng: LatLng, logVO: LogVO, mMap: GoogleMap): Boolean {

        locLine?.remove()
        locLine = null

        if (logVO.mncString != "11") return false

        myMapLL = if (eciMap.containsKey(logVO.ci)) {
            val placemark = eciMap[logVO.ci]!!
            placemark.ll  // nullの場合もあり、かも。無いと思うけど。
        } else {
            null
        }

        if (myMapLL != null) {
            val context = MyApplication.getInstance()
            locLine = mMap.addPolyline(
                PolylineOptions()
                    .add(latlng, myMapLL)
                    .color(ContextCompat.getColor(context, R.color.colorCurrToCellLine))
            )
            return true
        }
        return false
    }

    fun getName(cellid: Long): String {
        return if (eciMap.containsKey(cellid)) {
            val placemark = eciMap[cellid]!!
            placemark.name
        } else {
            ""
        }
    }
}