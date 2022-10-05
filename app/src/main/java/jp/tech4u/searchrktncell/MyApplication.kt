package jp.tech4u.searchrktncell

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.android.gms.maps.GoogleMap
import jp.tech4u.searchrktncell.room.AppDatabase
import jp.tech4u.searchrktncell.util.Utils

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
    lateinit var lcidView: LcidView

    override fun onCreate() {
        super.onCreate()
        instance = this

        // AppDatabaseをビルドする
        database = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "app_database")
            .addMigrations(MIGRATION_1_2)
            .build()

        myMapManager.initMidAttributeMap()

        setDefaultNightModeByPreference()
    }

    fun setDefaultNightModeByPreference() {
        val theme = Utils.getPreferenceString("theme", "MODE_NIGHT_FOLLOW_SYSTEM")
        Log.e("MyApplication", "setDefaultNightModeByPreference() $theme")
        when (theme) {
            "MODE_NIGHT_YES" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "MODE_NIGHT_NO" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    fun restartApp() {
        val intent = RestartActivity.createIntent(applicationContext)
        // RestartActivity を起動（AndroidManifest.xml での宣言により別プロセスで起動する
        applicationContext.startActivity(intent)
    }

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `GoogleMyMapEntity` (`mid` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, PRIMARY KEY(`mid`))")
        }
    }
}