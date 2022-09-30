package jp.tech4u.searchrktncell

import android.app.Application
import androidx.room.Room
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
        database = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "app_database").build()
    }
}