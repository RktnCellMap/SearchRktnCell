package jp.tech4u.searchrktncell.util

import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

// LatLngBounds.Builder のようなもの。
// 違いは、遠すぎる点はそこそこの距離で、というところです。
class LatLngArea(currentLL: LatLng) {
    private val llBuilder = LatLngBounds.Builder().include(currentLL)
    private val maxDistance = Utils.getPreferenceString("maxDistance", "2000")?.toInt()
    private val dLat = 180.0 / 20000000 * (maxDistance ?: 2000)
    private val dLng = dLat * cos(currentLL.latitude * Math.PI / 180)
    private val minLat = currentLL.latitude - dLat
    private val maxLat = currentLL.latitude + dLat
    private val minLng = currentLL.longitude - dLng
    private val maxLng = currentLL.longitude + dLng

    init {
        // 拡大しすぎないように最低限のエリアを含める(MLSの場合はより拡大傾向)
        llBuilder.include(LatLng(currentLL.latitude - 0.003, currentLL.longitude - 0.003 * 0.8))
        llBuilder.include(LatLng(currentLL.latitude + 0.003, currentLL.longitude + 0.003 * 0.8))
        // 参考：0.00001[度]≒1.11[m]  cos(36)≒0.8
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