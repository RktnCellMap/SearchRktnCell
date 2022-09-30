package jp.tech4u.searchrktncell

import android.graphics.Bitmap
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import jp.tech4u.searchrktncell.data.LogVO
import jp.tech4u.searchrktncell.util.MyException
import jp.tech4u.searchrktncell.util.Utils
import java.io.File
import java.io.IOException
import java.io.InputStream

class MyMapManager {
    companion object {
        private const val TAG = "MyMapManager"
    }

    private var kmlParserMap = mutableMapOf<Int, KmlParser>()
    private var locLine: Polyline? = null    // 基地局-現在地の線
    var myMapLL: LatLng? = null           // 掴んでいる基地局の位置
    var midNameMap = mutableMapOf<String, String>()

    fun readMyMap(index: Int, mid: String, cacheFile: File, bReload: Boolean) {
        var kmlParser = kmlParserMap[index]
        if (kmlParser == null) {
            kmlParser = KmlParser()
            kmlParserMap[index] = kmlParser
        }
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
                // 既存マーカークリア
                markerRemove(kmlParser)

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
            kmlParser.parseMyMap(cacheFile)
            midNameMap[mid] = kmlParser.name
        }
    }


    // 既存マーカークリア
    private fun markerRemove(kmlParser: KmlParser) {
        for (marker in kmlParser.markerList) {
            marker.remove()
        }
        kmlParser.markerList.clear()
    }

    fun plotPlacemark(index: Int, mMap: GoogleMap) {
        val zIndex = kmlParserMap.count() - index + 4000f
        val kmlParser = this.kmlParserMap[index] ?: return

        // 既存マーカークリア
        markerRemove(kmlParser)

        // マーカープロット
        for (placemark in kmlParser.placemarks) {
            plotImpl(kmlParser, placemark, mMap, zIndex)
        }
    }

    // プロットする。実装部。
    private fun plotImpl(kmlParser: KmlParser, placemark: KmlParser.Placemark, mMap: GoogleMap, zIndex: Float) {
        val kmlStyle = kmlParser.kmlStyleMap[placemark.styleUrl]
        val markerOptions =
            MarkerOptions()
                .position(placemark.ll)
                .title(placemark.name)
                .zIndex(zIndex)
        if (placemark.snippet != "") markerOptions.snippet(placemark.snippet)

        if (kmlStyle?.href != null) {
            if (!kmlStyle.href!!.startsWith("http://") && !kmlStyle.href!!.startsWith("https://")) {
                val bitmap = kmlParser.loadBitmap(kmlStyle)
                if (bitmap != null) {
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
        }
        val marker: Marker = mMap.addMarker(markerOptions)
        kmlParser.markerList.add(marker)
    }

    fun removeLocLine() {
        locLine?.remove()
        locLine = null
        myMapLL = null
    }

    private fun getPlacemark(cellid: Long): KmlParser.Placemark? {
        var placemark: KmlParser.Placemark? = null

        for (v in kmlParserMap.toSortedMap().values) {
            val eciMap = v.eciMap
            placemark = eciMap[cellid]
            if (placemark != null) break
        }
        return placemark
    }

    // 線を描いたら true を返す
    fun drawLine(latlng: LatLng, logVO: LogVO, mMap: GoogleMap): Boolean {

        removeLocLine()

        if (logVO.mncString != "11") {
            return false
        }

        val placemark = getPlacemark(logVO.ci)
        myMapLL = placemark?.ll

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
        val placemark = getPlacemark(cellid)
        return placemark?.name ?: ""
    }
}