package jp.tech4u.searchrktncell.util

import androidx.preference.PreferenceManager
import com.google.android.gms.maps.model.LatLng
import jp.tech4u.searchrktncell.MapsActivity
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.cos
import kotlin.math.sqrt

object Utils {
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

    fun getInputStreamFromUrlString(urlString:String) :InputStream?{
        val url = URL(urlString)
        return (url.openConnection() as? HttpURLConnection)?.run {
            readTimeout = 10000
            connectTimeout = 15000
            requestMethod = "GET"
            doInput = true
            // Starts the query
            connect()
            inputStream
        }
    }

    fun getPreferenceString(key: String, default: String?): String? {
        val context = MapsActivity.getInstance()
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPreferences.getString(key, default)
    }

    fun getPreferenceInt(key: String, default: Int): Int {
        val context = MapsActivity.getInstance()
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPreferences.getInt(key, default)
    }

    fun getPreferenceBoolean(key: String, default: Boolean): Boolean {
        val context = MapsActivity.getInstance()
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPreferences.getBoolean(key, default)
    }
}