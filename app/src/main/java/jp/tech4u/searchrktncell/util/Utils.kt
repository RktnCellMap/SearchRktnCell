package jp.tech4u.searchrktncell.util

import android.content.Context
import android.graphics.Color
import android.telephony.CellInfo
import android.text.TextUtils
import android.util.Log
import androidx.preference.PreferenceManager
import com.google.android.gms.maps.model.LatLng
import jp.tech4u.searchrktncell.MyApplication
import jp.tech4u.searchrktncell.R
import jp.tech4u.searchrktncell.data.DebugVO
import jp.tech4u.searchrktncell.data.LogVO
import java.io.*
import java.lang.Integer.max
import java.lang.Integer.min
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sqrt

object Utils {
    private const val TAG = "Utils"

    // 2点から、正三角形のもう1点を求める
    fun getThirdLL(ll1: LatLng, ll2: LatLng, direction: Boolean): LatLng {
        // 中点
        val cLat = (ll1.latitude + ll2.latitude) / 2
        val cLon = (ll1.longitude + ll2.longitude) / 2
        val dLat = ll1.latitude - ll2.latitude
        val dLon = ll1.longitude - ll2.longitude
        val dy = dLat / cos(cLat / 180 * Math.PI) * sqrt(3.0) / 2
        val dx = dLon * cos(cLat / 180 * Math.PI) * sqrt(3.0) / 2
        val nLat = cLat + if (direction) dx else -dx
        val nLon = cLon + if (direction) -dy else dy
        return LatLng(nLat, nLon)
    }

    // Arduino の　map()関数と同じヤツ
    fun arduinoMap(x: Int, in_min: Int, in_max: Int, out_min: Int, out_max: Int): Int {
        return (x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min
    }

    // 基本は arduinoMap() と同じ。outに値域チェックが入る。
    fun arduinoMapLimit(x: Int, in_min: Int, in_max: Int, out_min: Int, out_max: Int): Int {
        return max(out_min, min(out_max, arduinoMap(x, in_min, in_max, out_min, out_max)))
    }

    fun saveFromInputStream(inputStream: InputStream, cacheFile: File) {
        val dataInput = DataInputStream(inputStream)
        val fileOutput = FileOutputStream(cacheFile)
        val dataOut = DataOutputStream(fileOutput)
        val buffer = ByteArray(4096)
        var readByte: Int
        while (dataInput.read(buffer).also { readByte = it } != -1) {
            dataOut.write(buffer, 0, readByte)
        }
        dataInput.close()
        fileOutput.close()
        dataInput.close()
        inputStream.close()
    }

    fun getHttpURLConnectionFromUrlString(urlString:String) :HttpURLConnection {
        val url = URL(urlString)
        val con = url.openConnection() as HttpURLConnection
        con.readTimeout = 10000
        con.connectTimeout = 15000
        con.requestMethod = "GET"
        con.doInput = true
        // Starts the query
        con.connect()
        return con
    }

    /*
    fun getInputStreamFromUrlString(urlString:String) :InputStream?{
        val con = getHttpURLConnectionFromUrlString(urlString)
        return con.inputStream
    }
     */

    fun createLogFileObj(context: Context, filename :String? = null): File {
        val filename2 = if ( filename==null) {
            val df = SimpleDateFormat("yyyyMMdd")
            val yyyyMMdd = df.format(Date())
            "log_$yyyyMMdd.csv"
        } else {
            filename
        }
        val logDir = File(context.externalCacheDir, "logs")
        //val logDir = File(context.getExternalFilesDir(null), "logs")
        logDir.mkdir()
        return File(logDir, filename2)
    }

    fun readLog(file:File, logList :MutableList<LogVO>) {
        if (!file.exists()) {
            Log.i(TAG, "logfile not found: ${file.path}")
            return
        }
        file.bufferedReader().forEachLine {
            try {
                val rows = it.split(',')
                if (rows.count() == 10) {
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
                }else if (rows.count() == 11) {
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
                        rows[10].toInt(),
                    )
                    logList.add(logVO)
                }else if (rows.count() == 16) {
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
                        rows[10].toInt(),
                    )
                    logVO.debug = DebugVO(
                        rows[11].toFloat(),
                        rows[12].toFloat(),
                        rows[13].toFloat(),
                        rows[14].toFloat(),
                        rows[15].toFloat()
                    )
                    logList.add(logVO)
                }
            } catch (e: java.lang.Exception) {
                Log.w(TAG, e.toString())
                // 握りつぶすｗ
            }
        }
    }

    fun calcColor(rsrp: Int, sector3: Int, bEqualBs: Boolean): Int {
        val h = (sector3 % 3) * 120f // 色相。セクタ順に RGBになるようにしてます
        val s = Utils.arduinoMapLimit(rsrp, -98, -50, 16, 255) / 255f // 彩度。RSRP比例
        val v = if (bEqualBs) 1f else 0.6f  // 明度。別基地局は暗く。
        return Color.HSVToColor(floatArrayOf(h, s, v))
    }


    // 輝度(いわゆるYUVのY)を返す
    fun getLuminanceColor(color: Int): Int {
        return (Color.red(color) * 0.299f + Color.green(color) * 0.587f + Color.blue(color) * 0.114f).toInt()
    }

    // 背景色に負けない前景色(モノクロ)を返す
    fun getRevMonoColor(color: Int): Int {
        val y = getLuminanceColor(color)
        return if (y < 128) R.color.colorThemeIndependentLight else R.color.colorThemeIndependentDark
    }

    fun getActiveMnc(cellInfoAry: List<CellInfo>): String? {
        val listMnc = mutableListOf<String?>()
        for (cellInfo in cellInfoAry) {
            if (cellInfo.isRegistered) {
                val mncString = cellInfo.mncString
                if (mncString != null && mncString != "") {
                    listMnc.add(mncString)
                }
            }
        }

        // 採用するMNCを決める
        return when (listMnc.size) {
            0 -> null // 圏外
            1 -> listMnc[0] // 1個の場合はそれ
            else -> {
                // 複数ある場合は、下記優先順位順で
                when {
                    listMnc.contains("11") -> "11"
                    listMnc.contains("53") -> "53"
                    listMnc.contains("50") -> "50"
                    else -> listMnc[0]  // 決められない場合は最初に見つかったモノ
                }
            }
        }
    }

    // マイマップURLが有効か確認する
    fun checkMyMapMid(key:String): Boolean {
        if (! getPreferenceBoolean(key+"Enable", true)) return false

        var midOrKmz = getMyMapKmzUrl(key)
        return midOrKmz != ""
    }

    // 設定からマイマップURLを引き、midを返す
    fun getMyMapMid(key:String): String {
        var mid = ""
        try {
                val myMapUrl = getPreferenceString(key, null)
                if (myMapUrl != null) {
                    mid = getMyMapMidFromUrl(myMapUrl)
                }
        } catch (e: Exception) {
            // マイマップURI解析失敗
            // ユーザーへの通知は、、、まあいいや・・・
        }
        return mid
            }

    // 設定からマイマップURLを引き、KMZアクセス用URLに変換して返す
    // （最後が.kmzならそのまま、マイマップっぽかったらマイマップ用URL）
    fun getMyMapKmzUrl(key:String): String {
        var url = ""
        try {
            val myMapUrl = getPreferenceString(key, null)
            if (myMapUrl != null) {
                url = if (myMapUrl.toLowerCase().endsWith(".kmz")) {
                    myMapUrl;
                } else {
                    var mid = getMyMapMidFromUrl(myMapUrl)
                    "https://www.google.com/maps/d/kml?mid=$mid"
                }
            }
        } catch (e: Exception) {
            // マイマップURI解析失敗
            // ユーザーへの通知は、、、まあいいや・・・
        }
        return url
    }

    // 設定からマイマップURLを引き、内部で使うキャッシュファイル名にして返す
    fun getMyMapCacheName(key:String): String {
        var myMapCacheName = ""
        try {
            val myMapUrl = getPreferenceString(key, null)
            if (myMapUrl != null) {
                myMapCacheName = if (myMapUrl.toLowerCase().endsWith(".kmz")) {
                    key + Paths.get(URL(myMapUrl).path).fileName.toString()
                } else {
                    var mid = getMyMapMidFromUrl(myMapUrl)
                    "$mid.kmz"
                }
            }
        } catch (e: Exception) {
            // マイマップURI解析失敗
            // ユーザーへの通知は、、、まあいいや・・・
        }
        return myMapCacheName
    }

    fun getMyMapMidFromUrl(myMapUrl:String?): String {
        if (TextUtils.isEmpty(myMapUrl)) {
            return ""
        }
        var mid = ""
        val myMapUrlTrimmed = myMapUrl!!.trim()
        if (myMapUrlTrimmed.length in 32..33) {
            // 32~33文字の場合はそのまま
            mid = myMapUrlTrimmed
        } else {
            // それ以外の場合は、URIを解析して引数midを取得
            val uri = URI(myMapUrlTrimmed)
            val query = uri.query
            val queryList = query.split("&")
            for (q in queryList) {
                val kv = q.split("=")
                if (kv[0] == "mid") {
                    mid = kv[1]
                }
            }
        }
        return mid
    }

    // °単位の角度を0～360の範囲に収める
    fun normalizeDegree(deg: Float): Float {
        // 360で割ったあまりを求める
        var deg = deg
        deg %= 360f

        // マイナスだったら360を足す
        if (0f > deg) deg += 360f
        return deg
    }

    fun getPreferenceString(key: String, default: String?): String? {
        val context = MyApplication.getInstance()
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPreferences.getString(key, default)
    }

    fun setPreferenceString(key: String, value: String) {
        val context = MyApplication.getInstance()
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        sharedPreferences.edit()
            .putString(key, value)
            .commit()
    }

    fun getPreferenceInt(key: String, default: Int): Int {
        val context = MyApplication.getInstance()
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPreferences.getInt(key, default)
    }

    fun setPreferenceInt(key: String, value: Int) {
        val context = MyApplication.getInstance()
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        sharedPreferences.edit()
            .putInt(key, value)
            .commit()
    }

    fun getPreferenceBoolean(key: String, default: Boolean): Boolean {
        val context = MyApplication.getInstance()
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPreferences.getBoolean(key, default)
    }

    fun setPreferenceBoolean(key: String, value: Boolean) {
        val context = MyApplication.getInstance()
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        sharedPreferences.edit()
            .putBoolean(key, value)
            .commit()
    }
}