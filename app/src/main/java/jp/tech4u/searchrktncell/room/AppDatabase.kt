package jp.tech4u.searchrktncell.room

import androidx.room.Database
import androidx.room.RoomDatabase

/*
■テーブル追加方法
DaoとEntityクラスを作る
@Database(~) にEntityクラス追加
abstract fun ~　にDaoクラス追加
MyApplication.kt 内で Migration を検索して、見よう見まねでマイグレーションコードを書く
 */

@Database(entities = [RadioInfoEntity::class, GoogleMyMapEntity::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun radioInfoDao(): RadioInfoDao
    abstract fun googleMyMapDao(): GoogleMyMapDao
}
