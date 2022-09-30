package jp.tech4u.searchrktncell.util

import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.SphericalUtil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// LatLngBounds.Builder のようなもの。
// 違いは、
// ・拡大しすぎないよう、範囲をそこそこに、
// ・縮小しすぎないよう、遠すぎる点はそこそこの距離で、
// というところです。
class LatLngArea(currentLL: LatLng) {
    private val llBuilder = LatLngBounds.Builder().include(currentLL)
    private val maxDistance = Utils.getPreferenceString("maxDistance", "2000")?.toInt()?: 2000
    private val dLat = 180.0 / 20000000 * maxDistance
    private val dLng = dLat * cos(currentLL.latitude * Math.PI / 180)
    private val minLat = currentLL.latitude - dLat
    private val maxLat = currentLL.latitude + dLat
    private val minLng = currentLL.longitude - dLng
    private val maxLng = currentLL.longitude + dLng

    init {
        // 拡大しすぎないように最低限のエリアを含める(MLSの場合はより拡大傾向)
        val minDistance: Double =
            Utils.getPreferenceString("minDistance", "300")?.toDouble() ?: 300.0
        val distance = minDistance * sqrt(2.0)
        val llNE = SphericalUtil.computeOffset(currentLL, distance, 45.0)   // 北東
        val llSW = SphericalUtil.computeOffset(currentLL, distance, 225.0)  // 南西
        llBuilder.include(llNE)
        llBuilder.include(llSW)
    }

    fun include(ll: LatLng) {
        val lat = max(minLat, min(maxLat, ll.latitude))
        val lng = max(minLng, min(maxLng, ll.longitude))
        llBuilder.include(LatLng(lat, lng))
    }

    fun build(): LatLngBounds {
        return llBuilder.build()
    }
}