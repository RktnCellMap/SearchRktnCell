package jp.tech4u.searchrktncell

import android.location.Location
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import jp.tech4u.searchrktncell.data.LogVO
import jp.tech4u.searchrktncell.util.Utils
import java.io.File

class DebugCellInfo {
    companion object {
        private const val TAG = "DebugCellInfo"
        //const val DEBUG_CELL_INFO_ENABLE = true
        const val DEBUG_CELL_INFO_ENABLE = false    // リリースはコレ
        const val USE_GPS_LOG = false               // リリースはコレ

        //private const val DEBUG_LOAD_LOG_FILENAME = "log_20201219_higasifushimi.csv"
        //private const val DEBUG_LOAD_LOG_FILENAME = "log_20210105_higashiyamato_yamada_with_au.csv"
        //private const val DEBUG_LOAD_LOG_FILENAME = "log_20210105_higashiyamato_yamada.csv"
        //private const val DEBUG_LOAD_LOG_FILENAME = "log_20210201_sayama_iruma.csv"
        //private const val DEBUG_LOAD_LOG_FILENAME = "log_20210209_qurune.csv"
        //private const val DEBUG_LOAD_LOG_FILENAME = "log_20210209_qurune_tac.csv"
        //private const val DEBUG_LOAD_LOG_FILENAME = "log_20210907_kiyose.csv"
        //private const val DEBUG_LOAD_LOG_FILENAME = "log_gps_360.csv"
        //private const val DEBUG_LOAD_LOG_FILENAME = "log_20211206_Mi11_f.csv"
        private const val DEBUG_LOAD_LOG_FILENAME = ""    // リリースはコレ

        private const val DEBUG_TRACE_LOG_START_INDEX = 0

        //const val DISABLE_RADIO_INFO = true
        const val DISABLE_RADIO_INFO = false    // リリースはコレ

        var INTERVAL_MS = 1000L        // 描画間隔[ms]
        private const val DISTANCE = 20f    // プ0ロット間隔[m]
    }

    private var debugCellIndex = DEBUG_TRACE_LOG_START_INDEX
    private var logList = mutableListOf<LogVO>()
    private var lastll = LatLng(0.0, 0.0)
    private var lastCid = 0L

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

            /*
            logList.add(LogVO(0, 35.74722, 139.42638, "440", "11", 13315, 293596 * 256 + 1, -120, 20 * 1000, 3, 487)) // 既存
            logList.add(LogVO(0, 35.74722, 139.42838, "440", "11", 13315, 293596 * 256 + 2, -120, 20 * 1000, 3, 488)) // 既存
            logList.add(LogVO(0, 35.74822, 139.42738, "440", "11", 13315, 293596 * 256 + 3, -120, 20 * 1000, 3, 482)) // 既存
             */
            //小平第三中学校付近
            logList.add(LogVO(0, 35.72681, 139.50307, "440", "11", 13315, 293602 * 256 + 7, -90, 20 * 1000, 0, 388 )) // 既存
/*
            // 桜町病院
            logList.add(LogVO(0, 35.71511, 139.51274+0.03, "440", "11", 13315, 279999 * 256 + 7, -88, 20 * 1000, 24, 15))
            logList.add(LogVO(0, 35.71511, 139.51274+0.04, "440", "11", 13315, 279999 * 256 + 8, -97, 20 * 1000, 24, 15))
            logList.add(LogVO(0, 35.71511, 139.51274+0.04, "440", "11", 13315, 279999 * 256 + 9, -105, 20 * 1000, 24, 15))

            // 上水本町
            logList.add(LogVO(0, 35.71111, 139.48453, "440", "11", 13315, 280430 * 256 + 2, -88, 20 * 1000, 9, 1))
            logList.add(LogVO(0, 35.71111, 139.48453, "440", "11", 13315, 280430 * 256 + 3, -87, 20 * 1000, 10, 11))
            logList.add(LogVO(0, 35.71111, 139.48453, "440", "11", 13315, 280430 * 256 + 4, -86, 20 * 1000, 11, 111))

            // 東京病院
            logList.add(LogVO(0, 35.76838, 139.50751, "440", "11", 13315, 280330 * 256 + 7, -88, 20 * 1000, 9, 1))
            logList.add(LogVO(0, 35.76838, 139.50751, "440", "11", 13315, 280330 * 256 + 8, -87, 20 * 1000, 10, 11))
            logList.add(LogVO(0, 35.76838, 139.50751, "440", "11", 13315, 280330 * 256 + 9, -86, 20 * 1000, 11, 111))

            // リヴィンオズ大泉店
            logList.add(LogVO(0, 35.75308, 139.59540, "440", "11", 13315, 288892 * 256 + 9, -88, 20 * 1000, 0, 437))
            logList.add(LogVO(0, 35.75308, 139.59540, "440", "11", 13315, 288892 * 256 + 10, -87, 20 * 1000, 0, 478))
            logList.add(LogVO(0, 35.75308, 139.59540, "440", "11", 13315, 288892 * 256 + 11, -86, 20 * 1000, 0, 421))
            logList.add(LogVO(0, 35.75308, 139.59540, "440", "11", 13315, 288894 * 256 + 12, -88, 20 * 1000, 9, 1))
            logList.add(LogVO(0, 35.75308, 139.59540, "440", "11", 13315, 288894 * 256 + 13, -87, 20 * 1000, 10, 11))
            logList.add(LogVO(0, 35.75308, 139.59540, "440", "11", 13315, 288894 * 256 + 14, -86, 20 * 1000, 11, 111))
            logList.add(LogVO(0, 35.75308, 139.59540, "440", "11", 13315, 288896 * 256 + 15, -88, 20 * 1000, 9, 1))
            logList.add(LogVO(0, 35.75308, 139.59540, "440", "11", 13315, 288896 * 256 + 16, -87, 20 * 1000, 10, 11))
            logList.add(LogVO(0, 35.75308, 139.59540, "440", "11", 13315, 288896 * 256 + 17, -86, 20 * 1000, 11, 111))
*/
            INTERVAL_MS = 5000
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
            val distance =
                SphericalUtil.computeDistanceBetween(LatLng(logList[i].lat, logList[i].lng), lastll)
            if (DISTANCE < distance) {
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