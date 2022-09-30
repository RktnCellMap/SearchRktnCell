package jp.tech4u.searchrktncell.room

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [RadioInfoEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun radioInfoDao(): RadioInfoDao
}