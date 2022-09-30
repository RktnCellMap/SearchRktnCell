package jp.tech4u.searchrktncell.room

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [RadioInfoEntity::class, GoogleMyMapEntity::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun radioInfoDao(): RadioInfoDao
    abstract fun googleMyMapDao(): GoogleMyMapDao
}
