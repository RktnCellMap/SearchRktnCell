package jp.tech4u.searchrktncell.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class RadioInfoEntity(
    @PrimaryKey val cid: Long,
    val lat: Float,
    val lon: Float,
    val ta: Int,
    val rsrp: Int
)