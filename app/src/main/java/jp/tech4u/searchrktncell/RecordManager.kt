package jp.tech4u.searchrktncell

import android.graphics.*
import android.location.Location
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import jp.tech4u.searchrktncell.data.CellId
import jp.tech4u.searchrktncell.data.LogVO
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception
import kotlin.math.max
import kotlin.math.min

class RecordManager(mMap: GoogleMap, myMapManager:MyMapManager) {

    companion object {
        private const val PLOT_DISTANCE_EQ = 10f
        private const val PLOT_DISTANCE_NE = 50f
    }

    private var mMap = mMap
    private var myMapManager = myMapManager

    var logList = mutableListOf<LogVO>()
    private var markerList = mutableListOf<Marker>()
    private var lLogVo = LogVO()
    private var nowCi = CellId(0)

    private var csvList = mutableListOf<String>()

    private val distanceResult = FloatArray(3)

    fun readLog(file: File) {
        if (!file.exists()) {
            println("logfile not found: ${file.path}")
            return
        }
        file.bufferedReader().forEachLine {
            try {
                val rows = it.split(',')
                val logVO = LogVO(
                    rows[0].toInt(),
                    rows[1].toDouble(),
                    rows[2].toDouble(),
                    rows[3],
                    rows[4],
                    rows[5].toInt(),
                    rows[6].toLong(),
                    rows[7].toInt(),
                    rows[8].toInt(),
                    rows[9].toInt(),
                )
                logList.add(logVO)
            } catch (e: Exception) {
                println(e)
                // 握りつぶすｗ
            }
        }
    }

    fun clearMarkers() {
        for (marker in markerList) {
            marker.remove()
        }
        markerList.clear()
    }

    fun plotAll(ci: CellId, bPlotPartner: Boolean, bPlotMyMapCell: Boolean) {
        nowCi = ci

        for (logVO in logList) {
            if (!bPlotMyMapCell && myMapManager.contains(logVO.ci)){
                continue
            }
            if (bPlotPartner || logVO.mncString == "11") {
                val ci = CellId(logVO.ci)
                val bEqualBs = nowCi.equalBs(ci)
                plot(logVO, ci, bEqualBs)
            }
        }
    }

    fun plotAndLog(logVO: LogVO, bPlotPartner: Boolean, bPlotMyMapCell: Boolean) {
        val ci = CellId(logVO.ci)
        val bEqualBs = nowCi.equalBs(ci)

        if (bEqualBs) {
            if (bPlotPartner || logVO.mncString == "11") {
                if (bPlotMyMapCell || !myMapManager.contains(logVO.ci)){
                    plot(logVO, ci, bEqualBs)
                }
                logList.add(logVO)
            }
        } else {
            clearMarkers()
            logList.add(logVO)
            plotAll(ci, bPlotPartner, bPlotMyMapCell)
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
            println("csv保存しました")
        }

    }

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
    private fun plot(logVO: LogVO, ci: CellId, bEqualBs: Boolean) {
        val bPlot = needPlot(logVO, bEqualBs)
        if (bPlot) {
            lLogVo = logVO.copy()
            plotImpl(logVO, ci, bEqualBs)
        }
    }

    // プロットする。実装部。
    private fun plotImpl(logVO: LogVO, ci: CellId, bEqualBs: Boolean) {
        val latlng = LatLng(logVO.lat, logVO.lng)

        val icon = BitmapDescriptorFactory.fromBitmap(createBitmap(logVO.rsrp, ci.sector3, logVO.ta, bEqualBs))

        val title = "${ci.eNB}-${ci.sector}"
        val markerOptions = MarkerOptions()
            .position(latlng)
            .anchor(0.5f, 0.5f)
            .title(title)
            .snippet("RSRP:${logVO.rsrp}")
            .icon(icon)
            .zIndex((if (bEqualBs) 3999f else 1999f) - logVO.ta)
        val marker: Marker = mMap.addMarker(markerOptions)
        markerList.add(marker)
    }

    // マーカーに使うビットマップを生成
    private fun createBitmap(rsrp: Int, sector3: Int, ta: Int, bEqualBs: Boolean): Bitmap {
        val size = 48
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val h = ((sector3 + 0) % 3) * 120f // セクタ順に RGBになるようにしてます
        val s = max(0.02f, min(1f, 1 + (rsrp + 60) / 30f))
        val v = if (bEqualBs) 1f else 0.6f
        val color = Color.HSVToColor(floatArrayOf(h, s, v))
        val paint = Paint()
        paint.isAntiAlias = true
        paint.color = color

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