package jp.tech4u.searchrktncell.setting

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputFilter
import android.text.InputFilter.LengthFilter
import android.text.InputType
import android.text.TextUtils
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import jp.tech4u.searchrktncell.*
import jp.tech4u.searchrktncell.util.Utils


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

        // マイマップのサマリーをカスタマイズする
        val myMapUrlSummaryProvider = Preference.SummaryProvider<EditTextPreference> { preference ->
            try {
                val mid = Utils.getMyMapMidFromUrl(preference.text)
                if (mid == "") {
                    "未設定"
                } else {
                    val app = MyApplication.getInstance()
                    val name = app.myMapManager.midAttributeMap[mid]?.name
                    if (TextUtils.isEmpty(name)) {
                        mid
                    } else {
                        name
                    }
                }
            } catch (e: Exception) {
                // URIの解析ができない場合はそのまま返す
                preference.text
            }
        }
        findPreference<EditTextPreference>("myMapUrl")?.summaryProvider = myMapUrlSummaryProvider
        findPreference<EditTextPreference>("myMapUrl2")?.summaryProvider = myMapUrlSummaryProvider
        findPreference<EditTextPreference>("myMapUrl3")?.summaryProvider = myMapUrlSummaryProvider
        findPreference<EditTextPreference>("myMapUrl4")?.summaryProvider = myMapUrlSummaryProvider
        findPreference<EditTextPreference>("myMapUrl5")?.summaryProvider = myMapUrlSummaryProvider

        // maxDistance の入力制限
        val numberPreference: EditTextPreference? = findPreference("maxDistance")
        numberPreference?.setOnBindEditTextListener { editText ->
            // 数字入力のみにする
            editText.inputType = InputType.TYPE_CLASS_NUMBER
            // 文字数制限
            editText.selectAll() // select all text
            editText.filters = arrayOf<InputFilter>(LengthFilter(6))
        }

        val button: Preference? = findPreference("deleteAllRadioInfoButton")
        button?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            AlertDialog.Builder(this.context)
                //.setTitle("確認")
                .setMessage("記録した最良電測ポイントを全て消去します。\nよろしいですか？")
                .setNegativeButton("キャンセル", null)
                .setPositiveButton("消去する") { dialog, which ->
                    MyApplication.getInstance().mlsManager.clearRadioInfo()
                }
                .show()
            true
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
