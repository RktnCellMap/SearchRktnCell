package jp.tech4u.searchrktncell

import android.graphics.Bitmap
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import jp.tech4u.searchrktncell.data.LogVO
import jp.tech4u.searchrktncell.room.GoogleMyMapEntity
import jp.tech4u.searchrktncell.util.MyException
import jp.tech4u.searchrktncell.util.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream

class MyMapManager {
    companion object {
        private const val TAG = "MyMapManager"
    }

    // root_preferences.xml のマイマップ定義の個数と合わせてください
    val myMapUrlKeys = arrayOf("myMapUrl", "myMapUrl2", "myMapUrl3", "myMapUrl4", "myMapUrl5")

    private val kmlParserAry:Array<KmlParser?> = arrayOfNulls(myMapUrlKeys.size)
    private var locLine: Polyline? = null    // 基地局-現在地の線
    var myMapLL: LatLng? = null           // 掴んでいる基地局の位置

    //var midNameMap = mutableMapOf<String, String>()
    var midAttributeMap = mutableMapOf<String, GoogleMyMapEntity>()
    private var eciDefined = false  // eNB-LCIDの定義が1つ以上あるかどうか

    // midAttributeMap　をroomで保存してたデータからメモリに読み込む
    fun initMidAttributeMap() {
        val dao = MyApplication.database.googleMyMapDao()
        CoroutineScope(Dispatchers.Main).launch {
            withContext(Dispatchers.Default) {
                val gmmAry = dao.loadAllGoogleMyMapEntities()
                for (gmm in gmmAry) {
                    midAttributeMap[gmm.mid] = gmm
                }
                // このループはマルチスレッドで衝突しそうな気がする。が、とりあえず放置。
            }
        }
    }

    private fun upsertMidAttribute(mid: String, name: String, description: String) {
        var gmm = GoogleMyMapEntity(mid, name, description)
        midAttributeMap[mid] = gmm

        // 次回起動時のために、roomで保存しておく
        val dao = MyApplication.database.googleMyMapDao()
        CoroutineScope(Dispatchers.Main).launch {
            withContext(Dispatchers.Default) {
                dao.upsert(gmm)
            }
        }
    }

    fun readMyMap(index: Int, mid: String, cacheFile: File, bReload: Boolean) {
        var kmlParser = kmlParserAry[index]
        if (kmlParser == null) {
            kmlParser = KmlParser()
            kmlParserAry[index] = kmlParser
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
                kmlParser.clearMarkers()

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
            upsertMidAttribute(mid, kmlParser.name, kmlParser.description)
            if (kmlParser.eciDefined) {
                eciDefined = true
            }
        }
    }

    fun plotAllPlacemarks(index: Int, mMap: GoogleMap) {
        val kmlParser = kmlParserAry[index] ?: return

        // 既存マーカークリア
        kmlParser.clearMarkers()

        // マーカープロット
        val zIndex = kmlParserAry.size - index + 4000f
        for (placemark in kmlParser.placemarks) {
            plotImpl(kmlParser, placemark, mMap, zIndex)
        }

        // 重複している基地局を非表示にする
        hideDuplicatePlacemark()
    }

    // 重複している基地局を非表示にする
    fun hideDuplicatePlacemark() {
        for (index1 in kmlParserAry.size - 1 downTo 0) {
            val kmlParser1 = this.kmlParserAry[index1] ?: continue
            for ((ecis, marker) in kmlParser1.markerMapByEcis) {
                marker.isVisible = true // とりま見えるように戻しておく
                for (index2 in index1 - 1 downTo 0) {
                    val kmlParser2 = this.kmlParserAry[index2] ?: continue
                    if (kmlParser2.markerMapByEcis.containsKey(ecis)) {
                        marker.isVisible = false    // 自分より優先のマイマップに存在した → 非表示
                        break
                    }
                }
            }
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
        val ecis = placemark.eciAry.joinToString(",")
        kmlParser.markerList.add(marker)
        kmlParser.markerMapByEcis[ecis] = marker
    }

    fun removeLocLine() {
        locLine?.remove()
        locLine = null
        myMapLL = null
    }

    private fun getPlacemark(cellid: Long): KmlParser.Placemark? {
        var placemark: KmlParser.Placemark? = null

        for (kmlParser in kmlParserAry) {
            if (kmlParser==null) continue
            val eciMap = kmlParser.eciMap
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

    fun getName(cellid: Long): String? {
        return if (!eciDefined) {
            // そもそもマイマップにeNB-LCIDの定義がない
            null
        } else {
            val placemark = getPlacemark(cellid)
            placemark?.name ?: ""
        }
    }

    fun clearMyMap(index: Int) {
        val kmlParser = this.kmlParserAry[index] ?: return
        kmlParser.clear()
        hideDuplicatePlacemark()
    }
}