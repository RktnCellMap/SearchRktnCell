package jp.tech4u.searchrktncell

import android.graphics.*
import android.location.Location
import android.util.Log
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import jp.tech4u.searchrktncell.data.CellId
import jp.tech4u.searchrktncell.data.LogVO
import jp.tech4u.searchrktncell.util.Utils
import java.io.File
import java.io.FileOutputStream

class RecordManager {

    companion object {
        private const val TAG = "RecordManager"
        private const val PLOT_DISTANCE_EQ = 30f //10f
        private const val PLOT_DISTANCE_NE = 30f //50f
    }

    private var logList = mutableListOf<LogVO>()
    private var markerList = mutableListOf<Marker>()
    private var lLogVo = LogVO()
    private var nowCi = CellId(0)

    private var csvList = mutableListOf<String>()

    private val distanceResult = FloatArray(3)

    fun readLog(file: File) {
        Utils.readLog(file, logList)
    }

    fun clearMarkers() {
        for (marker in markerList) {
            marker.remove()
        }
        markerList.clear()
    }

    fun rePlot(mMap: GoogleMap) {
        if (logList.count() == 0) {
            return
        }
        clearMarkers()
        val bPlotPartner = Utils.getPreferenceBoolean("plotPartner", false)
        val bPlotMyMapCell = Utils.getPreferenceBoolean("plotMyMapCell", true)
        plotAll(mMap, CellId(logList.last().ci), bPlotPartner, bPlotMyMapCell)
    }

    // プロットするかの判定
    // MyMap登録済みかの判定。パートナーエリアは無関係
    private fun needPlotM(logVO: LogVO, bPlotMyMapCell: Boolean): Boolean {
        if (bPlotMyMapCell) return true    // 設定で全プロットすることになってる
        val app = MyApplication.getInstance()
        val name = app.myMapManager.getName(logVO.ci)
        if (name == null || name == "") return true // マイマップになし→プロットする。
        return name.endsWith('？')  // マイマップ上の名前の最後が？ならプロットする。そうでなければしない。
    }

    fun plotAll(mMap: GoogleMap, ci: CellId, bPlotPartner: Boolean, bPlotMyMapCell: Boolean) {
        nowCi = ci

        for (logVO in logList) {
            if (bPlotPartner || logVO.mncString == "11") {
                if (needPlotM(logVO, bPlotMyMapCell)) {
                    val lci = CellId(logVO.ci)
                    val bEqualBs = nowCi.equalBs(lci)
                    plot(mMap, logVO, lci, bEqualBs)
                }
            }
        }
    }

    fun plotAndLog(mMap: GoogleMap, logVO: LogVO, bPlotPartner: Boolean, bPlotMyMapCell: Boolean) {
        val ci = CellId(logVO.ci)
        val bEqualBs = nowCi.equalBs(ci)

        if (bEqualBs) {
            if (bPlotPartner || logVO.mncString == "11") {
                if (needPlotM(logVO, bPlotMyMapCell)) {
                    plot(mMap, logVO, ci, bEqualBs)
                }
                logList.add(logVO)
            }
        } else {
            clearMarkers()
            logList.add(logVO)
            plotAll(mMap, ci, bPlotPartner, bPlotMyMapCell)
        }
    }

    fun writeCsvLog(logVO: LogVO, logFile: File) {
        val csvLine =
            "${logVO.epoch},${logVO.lat},${logVO.lng},${logVO.mccString},${logVO.mncString},${logVO.tac},${logVO.ci},${logVO.rsrp},${logVO.bw},${logVO.ta}\n"
        csvList.add(csvLine)

        val csvCacheCount = 100
        if (csvCacheCount < csvList.count()) {
            // 書き出し
            FileOutputStream(logFile, true).bufferedWriter().use { bw ->
                csvList.forEach {
                    bw.write(it)
                }
            }
            // 書き出した分をクリア
            csvList.clear()
            Log.i(TAG, "csv保存しました")
        }

    }

    // 間引きするかどうかの判定
    private fun needPlot(logVO: LogVO, bEqualBs: Boolean): Boolean {
        var bPlot = false
        if (lLogVo.ci != logVO.ci) {
            // 違うセルならプロット
            bPlot = true
        } else {
            // 同じセルなら、一定距離離れたときにプロット
            Location.distanceBetween(logVO.lat, logVO.lng, lLogVo.lat, lLogVo.lng, distanceResult)
            val diffDist = if (bEqualBs) PLOT_DISTANCE_EQ else PLOT_DISTANCE_NE
            if (diffDist < distanceResult[0]) {
                bPlot = true
            }
        }
        return bPlot
    }

    // プロットする。但し、前回と近い場合はプロットしない。
    private fun plot(mMap: GoogleMap, logVO: LogVO, ci: CellId, bEqualBs: Boolean) {
        val bPlot = needPlot(logVO, bEqualBs)
        if (bPlot) {
            lLogVo = logVO.copy()
            plotImpl(mMap, logVO, ci, bEqualBs)
        }
    }

    // プロットする。実装部。
    private fun plotImpl(mMap: GoogleMap, logVO: LogVO, ci: CellId, bEqualBs: Boolean) {
        val latlng = LatLng(logVO.lat, logVO.lng)

        val icon = BitmapDescriptorFactory.fromBitmap(createBitmap(logVO.rsrp, ci.sector3, logVO.ta, bEqualBs))

        val title = "${ci.eNB}-${ci.sector}"
            val snippet = "RSRP:${logVO.rsrp} TA:${logVO.taStr()}"
            val taForZ = if (logVO.ta < 2000) logVO.ta else 1999
        val markerOptions = MarkerOptions()
            .position(latlng)
            .anchor(0.5f, 0.5f)
            .title(title)
                .snippet(snippet)
            .icon(icon)
            .alpha(0.8f)
                .zIndex((if (bEqualBs) 3999f else 1999f) - taForZ)
        val marker: Marker = mMap.addMarker(markerOptions)
        markerList.add(marker)
    }

    // マーカーに使うビットマップを生成
    private fun createBitmap(rsrp: Int, sector3: Int, ta: Int, bEqualBs: Boolean): Bitmap {
        val size = 48
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        paint.isAntiAlias = true
        paint.color = Utils.calcColor(rsrp, sector3, bEqualBs)

        // taに応じた点を描画
        when (ta) {
            0 -> {
                // ■を描画
                canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

            }
            1 -> {
                // ◆を描画
                val half = size / 2f
                val path = Path()
                path.moveTo(half, 0f)
                path.lineTo(size.toFloat(), half)
                path.lineTo(half, size.toFloat())
                path.lineTo(0f, half)
                canvas.drawPath(path, paint)
            }
            else -> {
                // 円を描画
                val half = size / 2f
                val radius = half / 1.4f
                canvas.drawCircle(half, half, radius, paint)
            }
        }

        // TAのテキストを描画
        if (ta in 0..1282) {
            val textPaint = Paint()
            textPaint.isAntiAlias = true
            textPaint.color = Color.BLACK
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = size * 0.7f
            val xPos = canvas.width / 2f
            val yPos = (canvas.height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2)
            canvas.drawText(ta.toString(), xPos, yPos, textPaint)
        }
        return bitmap
    }
}