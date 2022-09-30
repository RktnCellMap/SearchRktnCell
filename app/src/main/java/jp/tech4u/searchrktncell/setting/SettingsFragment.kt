package jp.tech4u.searchrktncell.setting

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import jp.tech4u.searchrktncell.DebugCellInfo
import jp.tech4u.searchrktncell.MapsActivity
import jp.tech4u.searchrktncell.R

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        // バージョン表記の設定
        val versionPreference: Preference? = findPreference("version")
        versionPreference?.summary = context?.let { getVersionName(it) }

        // Androidバージョンによる表示制御
        if (28 <= Build.VERSION.SDK_INT) {
            val bwFromEarfcn: SwitchPreference? = findPreference("bwFromEarfcn")
            bwFromEarfcn?.isVisible = true
        }
    }

    private fun getVersionName(context: Context): String {
        return try {
            // アプリケーションパッケージの情報を取得
            val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_META_DATA)
            val d =  if (DebugCellInfo.DEBUG_CELL_INFO_ENABLE) "-DEBUG" else ""

            // バージョン番号の文字列を返す
            info.versionName + d
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            "x.x.x"
        }
    }

    override fun onResume() {
        super.onResume()
        val instance = MapsActivity.getInstance()
        preferenceManager.sharedPreferences.registerOnSharedPreferenceChangeListener(instance)
    }

    override fun onPause() {
        super.onPause()
        val instance = MapsActivity.getInstance()
        preferenceManager.sharedPreferences.unregisterOnSharedPreferenceChangeListener(instance)
    }
}
