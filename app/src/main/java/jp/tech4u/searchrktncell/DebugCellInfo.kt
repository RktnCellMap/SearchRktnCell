package jp.tech4u.searchrktncell

import android.location.Location
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import jp.tech4u.searchrktncell.data.LogVO
import jp.tech4u.searchrktncell.util.Utils
import java.io.File

class DebugCellInfo {
    companion object {
        private const val TAG = "DebugCellInfo"
        //const val DEBUG_CELL_INFO_ENABLE = true
        const val DEBUG_CELL_INFO_ENABLE = false    // リリースはコレ

        //private const val DEBUG_LOAD_LOG_FILENAME = "log_20201219_higasifushimi.csv"
        //private const val DEBUG_LOAD_LOG_FILENAME = "log_20210105_higashiyamato_yamada_with_au.csv"
        //private const val DEBUG_LOAD_LOG_FILENAME = "log_20210105_higashiyamato_yamada.csv"
        //private const val DEBUG_LOAD_LOG_FILENAME = "log_20210201_sayama_iruma.csv"
        //private const val DEBUG_LOAD_LOG_FILENAME = "log_20210209_qurune.csv"
        //private const val DEBUG_LOAD_LOG_FILENAME = "log_20210209_qurune_tac.csv"
        private const val DEBUG_LOAD_LOG_FILENAME = ""    // リリースはコレ

        private const val DEBUG_TRACE_LOG_START_INDEX = 0

        //const val DISABLE_RADIO_INFO = true
        const val DISABLE_RADIO_INFO = false    // リリースはコレ

        var INTERVAL_MS = 100L        // 描画間隔[ms]
        private const val DISTANCE = 20f    // プ0ロット間隔[m]
    }

    private var debugCellIndex = DEBUG_TRACE_LOG_START_INDEX
    private var logList = mutableListOf<LogVO>()
    private var lastll = LatLng(0.0, 0.0)
    private var lastCid = 0L
    private val distanceResult = FloatArray(3)

    fun init() {
        if (DEBUG_LOAD_LOG_FILENAME != "") {
            // ログファイルから取得版
            val file = File(MyApplication.getInstance().externalCacheDir, DEBUG_LOAD_LOG_FILENAME)
            Utils.readLog(file, logList)
        } else {
            // ハードコーディングでデータ1個版
            logList.clear()
            val logVO =
                LogVO(
                    0,
                    35.715108863745385,
                    139.51274338768738,
                    "440",
                    "11",
                    123,
                    279949 * 256 + 8,
                    -88,
                    20 * 1000,
                    2,
                    123
                )
            logList.add(logVO)
            INTERVAL_MS = 3000
        }
    }

    fun isAvailable() : Boolean {
        return logList.size != 0
    }

    fun getLog(outListLogVO: MutableList<LogVO>) {
        if (logList.size == 0) return

        // メモ：このメソッドの最後でやるのが普通と思うが、
        // そうすると DEBUG_TRACE_LOG_START_INDEX が大きい場合に落ちるので、ここでやってます。
        if (logList.size <= debugCellIndex) {
            debugCellIndex = 0
        }

        for (i in debugCellIndex until logList.size) {
            if (lastCid != logList[i].ci) {
                // 別セルなら描画
                debugCellIndex = i
                break
            }

            Location.distanceBetween(logList[i].lat, logList[i].lng, lastll.latitude, lastll.longitude, distanceResult)
            if (DISTANCE < distanceResult[0]) {
                // 一定距離以上進んだら描画
                debugCellIndex = i
                break
            }
        }

        val logVO = logList[debugCellIndex]
        lastll = LatLng(logVO.lat, logVO.lng)
        lastCid = logVO.ci

        outListLogVO.add(logVO)
        Log.i(TAG, "debugCellIndex:$debugCellIndex")
        debugCellIndex++
    }
}