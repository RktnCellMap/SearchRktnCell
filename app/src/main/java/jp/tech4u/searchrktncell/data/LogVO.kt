package jp.tech4u.searchrktncell.data

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import jp.tech4u.searchrktncell.MapsActivity

@JsonIgnoreProperties(ignoreUnknown=true)
data class LogVO (
    val epoch: Int = 0,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val mccString: String? = null,
    val mncString: String? = null,
    val tac: Int = 0,
    val ci: Long = 0,
    val rsrp: Int = 0,
    val bw: Int = 0,
    val ta: Int = 0,
    val pci: Int = 0,
    val gen: Int = 4,
    var debug: DebugVO? = null,
) {
    fun bwStr() = if (this.bw == MapsActivity.UNAVAILABLE) "-" else "${this.bw / 1000}MHz"
    fun taStr() = if (this.ta == MapsActivity.UNAVAILABLE) "-" else this.ta.toString()
}