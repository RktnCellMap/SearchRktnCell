package jp.tech4u.searchrktncell.data

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

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
)