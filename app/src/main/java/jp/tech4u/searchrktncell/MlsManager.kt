package jp.tech4u.searchrktncell

import androidx.core.content.ContextCompat
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import jp.tech4u.searchrktncell.data.CellId
import jp.tech4u.searchrktncell.data.MlsCellInfo
import jp.tech4u.searchrktncell.util.EciUtil
import jp.tech4u.searchrktncell.util.Utils
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

class MlsManager {
    private var enbMap: MutableMap<String, MutableList<MlsCellInfo>>? = null
    private var mlsMarker: Marker? = null       // 基地局1個だけの場合のマーカー
    private var mlsPolygon: Polygon? = null     // 基地局3セクタの三角形
    private var mlsLine1: Polyline? = null      // 基地局3セクタの線（実線）
    private var mlsLine2: Polyline? = null      // 基地局3セクタの線（点線）
    private var mlsLocLine: Polyline? = null    // 基地局-現在地の線

    private var mlsLL: LatLng? = null           // 掴んでいる基地局の位置（別セクタ・仮セクタの場合もアリ）
    var foundMlsBounds: LatLngBounds? = null    // 基地局3セクタが収まる矩形
    private var lastCell = -1L                  // 前回掴んでいた基地局ID

    companion object {
        private const val PATTERN_DASH_LENGTH_PX = 20f
        private const val PATTERN_GAP_LENGTH_PX = 20f
        private val DASH: PatternItem = Dash(PATTERN_DASH_LENGTH_PX)
        private val GAP: PatternItem = Gap(PATTERN_GAP_LENGTH_PX)
        private val LINE_PATTERN_1: List<PatternItem> = listOf(GAP, DASH)
    }

    fun readMls(url: String, cacheFile: File, bReload: Boolean) {
        val cutOff: Long = System.currentTimeMillis() - 60 * 60 * 1000
        if (bReload || !cacheFile.exists() || cacheFile.lastModified() < cutOff) {
            // 明示的なリロード、キャッシュが存在しない、古い場合　はサーバーから取得
            println("＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊MLSダウンロード開始します")
            val inputStream =  Utils.getInputStreamFromUrlString(url)
            if(inputStream!=null) {
                Utils.saveFromInputStream(inputStream, cacheFile)
            }
        }
        parseMlsJson(cacheFile)
    }

    private fun parseMlsJson(cacheFile: File) {
        println("＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊MLS読み込み開始します")
        val contents = cacheFile.readText(Charsets.UTF_8)
        println("＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊MLS読み込みました")

        // TODO 高速化(現状でも十分かも)
        val mapper = jacksonObjectMapper()

        val cellAry: List<MlsCellInfo>
        try {
            cellAry = mapper.readValue(contents)
        } catch (e: Exception) {
            println("＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊MLSパースできませんでした")
            //Toast.makeText(instance, "MLSデータ読み込み失敗", Toast.LENGTH_LONG).show()
            return
        }
        println("＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊MLSパースしました")


        val lEnbMap = mutableMapOf<String, MutableList<MlsCellInfo>>()
        for (cellInfo in cellAry) {
            val ci = CellId(cellInfo.cell)
            if (!lEnbMap.containsKey(ci.eNB1)) {
                lEnbMap[ci.eNB1] = mutableListOf()
            }
            lEnbMap[ci.eNB1]?.add(cellInfo)
        }

        enbMap = lEnbMap
        println("＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊格納しました")
        //Toast.makeText(instance, "MLSデータ読み込み成功", Toast.LENGTH_LONG).show()
    }

    fun removeMls() {

        mlsMarker?.remove()
        mlsMarker = null
        mlsPolygon?.remove()
        mlsPolygon = null
        mlsLine1?.remove()
        mlsLine1 = null
        mlsLine2?.remove()
        mlsLine2 = null
        mlsLocLine?.remove()
        mlsLocLine = null

        foundMlsBounds = null
        lastCell = 0
        mlsLL = null
    }

    // MLSのセルの三角形などと、それへの直線を描画する
    fun drawMlsCells(latlng: LatLng, cellid: Long, mMap: GoogleMap) {
        if (enbMap == null) return

        val context = MapsActivity.getInstance()

        if (lastCell != cellid) {
            lastCell = cellid

            removeMls()

            val conCi = CellId(cellid)

            // MLS基地局表示
            val enbList = enbMap!![conCi.eNB1]
            if (enbList != null) {
                mlsLL = null

                when (enbList.count()) {
                    1 -> {
                        // 1セクタ判明→マーカーを描く
                        val cellInfo = enbList[0]
                        val mlsCi = CellId(cellInfo.cell)
                        val color = if (conCi.sector == mlsCi.sector) {
                            BitmapDescriptorFactory.HUE_RED
                        } else {
                            BitmapDescriptorFactory.HUE_ORANGE
                        }
                        val ll = LatLng(cellInfo.lat.toDouble(), cellInfo.lon.toDouble())
                        val marker = mMap.addMarker(
                            MarkerOptions()
                                .position(ll)
                                .title("${mlsCi.eNB}-${mlsCi.sector}")
                                .icon(BitmapDescriptorFactory.defaultMarker(color))
                                .zIndex(4000f)
                        )
                        mlsMarker = marker
                        mlsLL = ll
                        foundMlsBounds = LatLngBounds(ll, ll)

                    }
                    3 -> {
                        // 3セクタ判明→三角形を描く
                        val llBuilder = LatLngBounds.Builder()
                        val polyOpt = PolygonOptions()
                            .strokeColor(ContextCompat.getColor(context, R.color.colorMlsStroke))
                            .fillColor(ContextCompat.getColor(context, R.color.colorMlsFill))
                        for (cellInfo in enbList) {
                            val ll = LatLng(cellInfo.lat.toDouble(), cellInfo.lon.toDouble())
                            llBuilder.include(ll)
                            polyOpt.add(ll)
                            val mlsCi = CellId(cellInfo.cell)
                            if (conCi.sector == mlsCi.sector) {
                                mlsLL = ll
                            }
                        }
                        foundMlsBounds = llBuilder.build()
                        mlsPolygon = mMap.addPolygon(polyOpt)

                    }
                    2 -> {
                        // 2セクタ判明→もう一セクタの位置を推測して正三角形を描く

                        val mlsLL0 = LatLng(enbList[0].lat.toDouble(), enbList[0].lon.toDouble())
                        val mlsLL1 = LatLng(enbList[1].lat.toDouble(), enbList[1].lon.toDouble())

                        // 左右どちらに伸ばすかの判定（謎ロジック）
                        val diffS3 = CellId(enbList[0].cell).sector3 - CellId(enbList[1].cell).sector3
                        val direction = (diffS3 == -1 || diffS3 == 2)
                        // 3点目算出
                        val nLL = Utils.getThirdLL(mlsLL0, mlsLL1, direction)

                        // 三角
                        mlsPolygon = mMap.addPolygon(
                            PolygonOptions()
                                .strokeWidth(0f)
                                .fillColor(ContextCompat.getColor(context, R.color.colorMlsFill))
                                .add(mlsLL0).add(mlsLL1).add(nLL)
                        )

                        // 実線
                        mlsLine1 = mMap.addPolyline(
                            PolylineOptions()
                                .color(ContextCompat.getColor(context, R.color.colorMlsStroke))
                                .add(mlsLL0).add(mlsLL1)
                        )
                        // 点線
                        mlsLine2 = mMap.addPolyline(
                            PolylineOptions()
                                .color(ContextCompat.getColor(context, R.color.colorMlsStroke))
                                .pattern(LINE_PATTERN_1)
                                .add(mlsLL0).add(nLL).add(mlsLL1)
                        )

                        val llBuilder = LatLngBounds.Builder()
                        llBuilder.include(mlsLL0).include(mlsLL1).include(nLL)
                        foundMlsBounds = llBuilder.build()

                        for (cellInfo in enbList) {
                            if (conCi.ci == cellInfo.cell) {
                                mlsLL = LatLng(cellInfo.lat.toDouble(), cellInfo.lon.toDouble())
                            }
                        }
                        if (mlsLL == null) {
                            mlsLL = nLL
                        }
                    }
                }
            }
        }

        // セルが変わってない場合は、上の処理で消されないので、ココでももう一回消す
        mlsLocLine?.remove()
        mlsLocLine = null

        if (mlsLL != null) {
            mlsLocLine = mMap.addPolyline(
                PolylineOptions()
                    .add(latlng, mlsLL)
                    .color(ContextCompat.getColor(context, R.color.colorCurrToCellLine))
            )
        }
    }
}