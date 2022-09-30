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
        //private const val DEBUG_LOAD_LOG_FILENAME = "log_20210907_kiyose.csv"
        private const val DEBUG_LOAD_LOG_FILENAME = ""    // リリースはコレ

        private const val DEBUG_TRACE_LOG_START_INDEX = 500

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
            val file = Utils.createLogFileObj(MyApplication.getInstance(), DEBUG_LOAD_LOG_FILENAME)
            Utils.readLog(file, logList)
        } else {
            // ハードコーディング版(DEBUG_CELL_INFO_ENABLEがtrueの場合に有効)
            logList.clear()
            /*
            logList.add(LogVO(0, 35.71511, 139.51274, "440", "11", 13315, 279999 * 256 + 7, -88, 20 * 1000, 24, 15)) // 既存
            logList.add(LogVO(0, 35.71511, 139.51274, "440", "11", 212, (17*0x4000+0) * 256 + 1, -88, 20 * 1000, 0, 1)) // マクロ
            logList.add(LogVO(0, 35.71511, 139.51274, "440", "11", 65408, (17*0x4000+5000) * 256 + 2, -88, 20 * 1000, 1, 492))  // Casa
            logList.add(LogVO(0, 35.71511, 139.51274, "440", "11", 13315, (17*0x4000+10000) * 256 + 3, -88, 20 * 1000, 2, 404)) // 屋内
            logList.add(LogVO(0, 35.71511, 139.51274, "440", "11", 114, (17*0x4000+15000) * 256 + 3, -88, 20 * 1000, 2, 480)) // ピコ
            logList.add(LogVO(0, 35.71511, 139.51274, "440", "11", 212, (17*0x4000+0) * 256 + 1, -88, 20 * 1000, 0, 390))   // 恵比寿・マクロorピコ
            logList.add(LogVO(0, 35.71511, 139.51274, "440", "11", 212, (17*0x4000+0) * 256 + 1, -88, 20 * 1000, 0, 333))   // 恵比寿・マクロ
            logList.add(LogVO(0, 35.71511, 139.51274, "440", "11", 63488, 0xf8000 * 256 + 1, -88, 20 * 1000, 0, 481))   // 移動
             */
            /*
            logList.add(LogVO(0, 35.71511, 139.51274, "440", "11", 63488, 0xf8000 * 256 + 238, -88, 20 * 1000, 999, 333))   // 移動
            logList.add(LogVO(0, 35.71511, 139.51274, "440", "11", 63488, 0xf8000 * 256 + 123, -88, 20 * 1000, 99, 333))   // 移動
            logList.add(LogVO(0, 35.71511, 139.51274, "440", "11", 63488, 0xf8000 * 256 + 1, -88, 20 * 1000, 9, 333))   // 移動
             */

            logList.add(LogVO(0, 35.74722, 139.42638, "440", "11", 13315, 293596 * 256 + 1, -120, 20 * 1000, 3, 487)) // 既存
            logList.add(LogVO(0, 35.74722, 139.42838, "440", "11", 13315, 293596 * 256 + 2, -120, 20 * 1000, 3, 488)) // 既存
            logList.add(LogVO(0, 35.74822, 139.42738, "440", "11", 13315, 293596 * 256 + 3, -120, 20 * 1000, 3, 482)) // 既存
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