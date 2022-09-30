package jp.tech4u.searchrktncell.data

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown=true)
data class DebugVO(
    val accuracy: Float = 0f,
    val bearing: Float = 0f,
    val bearingAccuracyDegrees: Float = 0f,
    val speed: Float = 0f,
    val speedAccuracyMetersPerSecond: Float = 0f,
)
