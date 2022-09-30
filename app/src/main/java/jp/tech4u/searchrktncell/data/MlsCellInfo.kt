package jp.tech4u.searchrktncell.data

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown=true)
data class MlsCellInfo(val lat: Float, val lon: Float, val area: Int, val cell: Int)
