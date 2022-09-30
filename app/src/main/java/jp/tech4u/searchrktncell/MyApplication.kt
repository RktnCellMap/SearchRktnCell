package jp.tech4u.searchrktncell

import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.android.gms.maps.GoogleMap
import jp.tech4u.searchrktncell.room.AppDatabase

class MyApplication : Application() {
    companion object {
        private lateinit var instance: MyApplication
        lateinit var database: AppDatabase

        // あんまりやりたくないけど、みんなコレどうしてんだろう？
        fun getInstance(): MyApplication {
            return instance
        }
    }

    val mlsManager = MlsManager()
    val myMapManager = MyMapManager()
    val recordManager = RecordManager()
    lateinit var mMap: GoogleMap

    override fun onCreate() {
        super.onCreate()
        instance = this

        // AppDatabaseをビルドする
        database = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "app_database")
            .addMigrations(MIGRATION_1_2)
            .build()

        myMapManager.initMidAttributeMap()
    }

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `GoogleMyMapEntity` (`mid` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, PRIMARY KEY(`mid`))")
        }
    }
}