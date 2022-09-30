package jp.tech4u.searchrktncell.util

import android.telephony.CellInfo
import android.text.TextUtils
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.google.android.gms.maps.model.LatLng
import jp.tech4u.searchrktncell.MyApplication
import jp.tech4u.searchrktncell.R
import jp.tech4u.searchrktncell.data.LogVO
import java.io.*
import java.lang.Integer.max
import java.lang.Integer.min
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
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
                }
            } catch (e: java.lang.Exception) {
                Log.w(TAG, e.toString())
                // 握りつぶすｗ
            }
        }
    }

    fun getColorByMnc(mccString: String?, mncString: String?): Int {
        val context = MyApplication.getInstance()
        return when (mccString) {
            "440" -> {
                when (mncString) {
                    "11" -> ContextCompat.getColor(context, R.color.colorRakuten)
                    "50", "51", "52", "53" -> ContextCompat.getColor(context, R.color.colorAu)
                    "10" -> ContextCompat.getColor(context, R.color.colorDocomo)
                    else -> ContextCompat.getColor(context, R.color.colorSoftbank)
                }
            }
            "441" -> {
                when (mncString) {
                    "0" -> ContextCompat.getColor(context, R.color.colorSoftbank)
                    "10" -> ContextCompat.getColor(context, R.color.colorAu)
                    else -> ContextCompat.getColor(context, R.color.colorForeign)
                }
            }
            else -> ContextCompat.getColor(context, R.color.colorForeign)
        }
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

    // 設定からマイマップのMIDを取得する
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

    fun getMyMapMidFromUrl(myMapUrl:String?): String {
        if (TextUtils.isEmpty(myMapUrl)) {
            return ""
        }
        var mid = ""
        val myMapUrlTrimmed = myMapUrl!!.trim()
        if (myMapUrlTrimmed.length == 33) {
            // 33文字の場合はそのまま
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

    fun getPreferenceString(key: String, default: String?): String? {
        val context = MyApplication.getInstance()
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPreferences.getString(key, default)
    }

    fun getPreferenceInt(key: String, default: Int): Int {
        val context = MyApplication.getInstance()
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPreferences.getInt(key, default)
    }

    fun getPreferenceBoolean(key: String, default: Boolean): Boolean {
        val context = MyApplication.getInstance()
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPreferences.getBoolean(key, default)
    }
}