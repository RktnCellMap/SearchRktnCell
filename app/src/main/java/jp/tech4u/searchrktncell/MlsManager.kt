package jp.tech4u.searchrktncell

import android.util.Log
import androidx.core.content.ContextCompat
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import jp.tech4u.searchrktncell.data.CellId
import jp.tech4u.searchrktncell.data.LogVO
import jp.tech4u.searchrktncell.data.MlsCellInfo
import jp.tech4u.searchrktncell.room.RadioInfoEntity
import jp.tech4u.searchrktncell.util.MyException
import jp.tech4u.searchrktncell.util.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*

class MlsManager {
    companion object {
        private const val TAG = "MlsManager"

        private const val PATTERN_DASH_LENGTH_PX = 20f
        private const val PATTERN_GAP_LENGTH_PX = 20f
        private val DASH: PatternItem = Dash(PATTERN_DASH_LENGTH_PX)
        private val GAP: PatternItem = Gap(PATTERN_GAP_LENGTH_PX)
        private val LINE_PATTERN_1: List<PatternItem> = listOf(GAP, DASH)
    }

    private var cidMap: MutableMap<Long, MlsCellInfo> = mutableMapOf()
    private var radioInfoMap: MutableMap<Long, RadioInfoEntity> = mutableMapOf()

    private var mlsMarker: Marker? = null       // 基地局1個だけの場合のマーカー
    private var mlsPolygon: Polygon? = null     // 基地局3セクタの三角形
    private var mlsLine1: Polyline? = null      // 基地局3セクタの線（実線）
    private var mlsLine2: Polyline? = null      // 基地局3セクタの線（点線）
    private var mlsLocLine: Polyline? = null    // 基地局-現在地の線

    private var mlsLL: LatLng? = null           // 掴んでいる基地局の位置（別セクタ・仮セクタの場合もアリ）
    var foundMlsBounds: LatLngBounds? = null    // 基地局3セクタが収まる矩形
    private var lastCell = -1L                  // 前回掴んでいた基地局ID

    fun readRadioInfo() {
        val dao = MyApplication.database.radioInfoDao()
        CoroutineScope(Dispatchers.Main).launch {
            withContext(Dispatchers.Default) {
                val riAry = dao.loadAllRadioInfos()
                for(ri in riAry) {
                    radioInfoMap[ri.cid] = ri
                }
                // このループはマルチスレッドで衝突しそうな気がする。が、とりあえず放置。
            }
        }
    }

    fun readMls(urlString: String, cacheFile: File, bReload: Boolean) {
        val cutOff: Long = System.currentTimeMillis() - 60 * 60 * 1000
        if (bReload || !cacheFile.exists() || cacheFile.lastModified() < cutOff) {
            // 明示的なリロード、キャッシュが存在しない、古い場合　はサーバーから取得
            Log.i(TAG, "＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊MLSダウンロード開始します")
            val con = Utils.getHttpURLConnectionFromUrlString(urlString)
            var inputStream: InputStream? = null
            try {
                inputStream = con.inputStream
            } catch (e: IOException) {
                when (con.responseCode) {
                    403 -> throw MyException("MLSデータが非公開です")
                    404 -> throw MyException("MLSデータが見つかりません")
                    in 500..599 -> throw MyException("小平サーバーエラー(${con.responseCode})")
                    else -> throw MyException("MLS取得エラー(${con.responseCode})")
                }
            }
            if (inputStream != null) {
                Utils.saveFromInputStream(inputStream, cacheFile)
            }
        }
        parseMlsJson(cacheFile)
    }

    private fun parseMlsJson(cacheFile: File) {
        Log.i(TAG, "＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊MLS読み込み開始します")
        val contents = cacheFile.readText(Charsets.UTF_8)
        Log.i(TAG, "＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊MLS読み込みました")

        // TODO 高速化(現状でも十分かも)
        val mapper = jacksonObjectMapper()

        val cellAry: List<MlsCellInfo>
        try {
            cellAry = mapper.readValue(contents)
        } catch (e: Exception) {
            Log.i(TAG, "＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊MLSパースできませんでした")
            //Toast.makeText(instance, "MLSデータ読み込み失敗", Toast.LENGTH_LONG).show()
            return
        }
        Log.i(TAG, "＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊MLSパースしました")

        val lCidMap = mutableMapOf<Long, MlsCellInfo>()
        for (cellInfo in cellAry) {
            lCidMap[cellInfo.cell] = cellInfo
        }

        cidMap = lCidMap
        Log.i(TAG, "＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊＊格納しました")
        //Toast.makeText(instance, "MLSデータ読み込み成功", Toast.LENGTH_LONG).show()
    }

    // 三角形などを消す
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

    // 電測最良データ計算（最良かどうか判断し、最良なら記憶しておく）
    private fun calcBestRadioPoint(logVO: LogVO) {

        // TAもRSRPもダメダメ→無視
        if (1 < logVO.ta && logVO.rsrp < -70) return

        val ri = RadioInfoEntity(logVO.ci, logVO.lat.toFloat(), logVO.lng.toFloat(), logVO.ta, logVO.rsrp)
        val dao = MyApplication.database.radioInfoDao()

        if (radioInfoMap.contains(logVO.ci)) {
            val oldRi = radioInfoMap[logVO.ci]
            if (ri.ta < oldRi!!.ta || (oldRi.ta == ri.ta && oldRi.rsrp < ri.rsrp)) {
                // 前の記録より条件が良ければ、記憶。
                radioInfoMap[logVO.ci] = ri
                upsert(ri)
            }
        } else {
            // 新規、記憶。
            radioInfoMap[logVO.ci] = ri
            upsert(ri)
        }
    }

    private fun upsert(ri: RadioInfoEntity){
        val dao = MyApplication.database.radioInfoDao()
        CoroutineScope(Dispatchers.Main).launch {
            withContext(Dispatchers.Default) {
                dao.upsert(ri)
            }
        }
    }

    // MLSのセルの三角形などと、それへの直線を描画する
    fun drawMlsCells(latlng: LatLng, logVO: LogVO, mMap: GoogleMap) {
        val app = MyApplication.getInstance()

        if (lastCell != logVO.ci) {
            // 三角形の描画はハンドオーバーしたときだけ
            lastCell = logVO.ci

            removeMls()

            if (logVO.mncString != "11") return

            // 電測最良データ計算
            calcBestRadioPoint(logVO)

            val cellId = CellId(logVO.ci)
            val cellidBase = logVO.ci - cellId.sector3


            val brpList = mutableListOf<MlsCellInfo>()
            val mlsList = mutableListOf<MlsCellInfo>()
            for (sector in 0..2) {
                val ri = radioInfoMap[cellidBase + sector]
                if (ri != null) {
                    // 電測最良データがあった
                    val mci = MlsCellInfo(ri.lat, ri.lon, 0, ri.cid)
                    brpList.add(mci)
                } else if (cidMap.containsKey(cellidBase + sector)) {
                    // MSLにあった
                    mlsList.add(cidMap[cellidBase + sector]!!)
                }
            }

            mlsList.addAll(brpList)

            // MLS三角形表示
            if (mlsList.size != 0) {
                mlsLL = null

                // 判明したセクタ数に応じた描画処理に振り分ける
                when (mlsList.count()) {
                    1 -> if (brpList.isEmpty()) {
                        // MLSから1点だけ見つかった→マーカーを描く
                        drawMlsCells1(cellId, mlsList)
                    } else {
                        // 電測最良データから1点だけ見つかった
                        // マーカーを描かず、セルセンターへの線だけ引くようにする
                        mlsLL = LatLng(brpList[0].lat.toDouble(), brpList[0].lon.toDouble())
                    }
                    2 -> drawMlsCells2(cellId, mlsList)  // もう一セクタの位置を推測して正三角形を描く
                    3 -> drawMlsCells3(cellId, mlsList)  // 三角形を描く
                }
                // （drawMlsCellsX の副作用で mlsLL, foundMlsBounds が設定されます）

                if (brpList.count() == 1 && mlsList.count() == 1) {
                    // 電測最良データが1個あるだけの場合は、範囲ズームをやらないようにする
                    foundMlsBounds = null
                }
            }
        } else {
            // セルが変わってない場合は、セルセンターへの線だけ消す
            mlsLocLine?.remove()
            mlsLocLine = null
        }

        // セルセンターへの線を引く
        if (mlsLL != null) {
            mlsLocLine = mMap.addPolyline(
                PolylineOptions()
                    .add(latlng, mlsLL)
                    .color(ContextCompat.getColor(app, R.color.colorCurrToCellLine))
            )
        }
    }

    // 1セクタ判明→マーカーを描く
    private fun drawMlsCells1(cellId: CellId, mlsList: List<MlsCellInfo>) {
        val app = MyApplication.getInstance()

        val cellInfo = mlsList[0]
        val mlsCi = CellId(cellInfo.cell)
        val color = if (cellId.sector == mlsCi.sector) {
            BitmapDescriptorFactory.HUE_RED
        } else {
            BitmapDescriptorFactory.HUE_ORANGE
        }
        val ll = LatLng(cellInfo.lat.toDouble(), cellInfo.lon.toDouble())
        val marker = app.mMap.addMarker(
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

    // 2セクタ判明→もう一セクタの位置を推測して正三角形を描く
    private fun drawMlsCells2(cellId: CellId, mlsList: List<MlsCellInfo>) {
        val app = MyApplication.getInstance()

        val mlsLL0 = LatLng(mlsList[0].lat.toDouble(), mlsList[0].lon.toDouble())
        val mlsLL1 = LatLng(mlsList[1].lat.toDouble(), mlsList[1].lon.toDouble())

        // 左右どちらに伸ばすかの判定（謎ロジック）
        val diffS3 = CellId(mlsList[0].cell).sector3 - CellId(mlsList[1].cell).sector3
        val direction = (diffS3 == -1 || diffS3 == 2)
        // 3点目算出
        val nLL = Utils.getThirdLL(mlsLL0, mlsLL1, direction)

        // 三角
        mlsPolygon = app.mMap.addPolygon(
            PolygonOptions()
                .strokeWidth(0f)
                .fillColor(ContextCompat.getColor(app, R.color.colorMlsFill))
                .add(mlsLL0).add(mlsLL1).add(nLL)
        )

        // 実線
        mlsLine1 = app.mMap.addPolyline(
            PolylineOptions()
                .color(ContextCompat.getColor(app, R.color.colorMlsStroke))
                .add(mlsLL0).add(mlsLL1)
        )
        // 点線
        mlsLine2 = app.mMap.addPolyline(
            PolylineOptions()
                .color(ContextCompat.getColor(app, R.color.colorMlsStroke))
                .pattern(LINE_PATTERN_1)
                .add(mlsLL0).add(nLL).add(mlsLL1)
        )

        val llBuilder = LatLngBounds.Builder()
        llBuilder.include(mlsLL0).include(mlsLL1).include(nLL)
        foundMlsBounds = llBuilder.build()

        for (cellInfo in mlsList) {
            if (cellId.ci == cellInfo.cell) {
                mlsLL = LatLng(cellInfo.lat.toDouble(), cellInfo.lon.toDouble())
            }
        }
        if (mlsLL == null) {
            mlsLL = nLL
        }
    }

    // 3セクタ判明→三角形を描く
    private fun drawMlsCells3(cellId: CellId, mlsList: List<MlsCellInfo>) {
        val app = MyApplication.getInstance()

        val llBuilder = LatLngBounds.Builder()
        val polyOpt = PolygonOptions()
            .strokeColor(ContextCompat.getColor(app, R.color.colorMlsStroke))
            .fillColor(ContextCompat.getColor(app, R.color.colorMlsFill))
        for (cellInfo in mlsList) {
            val ll = LatLng(cellInfo.lat.toDouble(), cellInfo.lon.toDouble())
            llBuilder.include(ll)
            polyOpt.add(ll)
            val mlsCi = CellId(cellInfo.cell)
            if (cellId.sector == mlsCi.sector) {
                mlsLL = ll
            }
        }
        foundMlsBounds = llBuilder.build()
        mlsPolygon = app.mMap.addPolygon(polyOpt)
    }
}