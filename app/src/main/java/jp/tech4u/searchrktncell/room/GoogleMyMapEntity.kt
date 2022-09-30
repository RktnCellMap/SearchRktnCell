package jp.tech4u.searchrktncell.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class GoogleMyMapEntity(
    @PrimaryKey val mid: String,
    val name: String,
    val description: String,
)