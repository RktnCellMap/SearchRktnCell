package jp.tech4u.searchrktncell.data

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown=true)
data class LogVO (
    val epoch: Int = 0,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val mcc: Int = 0,
    val mnc: Int = 0,
    val tac: Int = 0,
    val ci: Int = 0,
    val rsrp: Int = 0,
    val bw: Int = 0,
    val ta: Int = 0,
)