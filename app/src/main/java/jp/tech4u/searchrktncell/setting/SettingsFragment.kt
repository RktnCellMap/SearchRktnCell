package jp.tech4u.searchrktncell.setting

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputFilter
import android.text.InputFilter.LengthFilter
import android.text.InputType
import android.text.TextUtils
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.google.gson.Gson
import jp.tech4u.searchrktncell.*
import jp.tech4u.searchrktncell.util.Utils
import org.xmlpull.v1.XmlPullParser.END_DOCUMENT
import org.xmlpull.v1.XmlPullParser.START_TAG
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


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

        val deleteAllRadioInfoButton: Preference? = findPreference("deleteAllRadioInfoButton")
        deleteAllRadioInfoButton?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
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

        val exportPreferenceButton: Preference? = findPreference("exportPreferenceButton")
        exportPreferenceButton?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            // 設定エクスポート
            exportPreference()
            true
        }

        val importPreferenceButton: Preference? = findPreference("importPreferenceButton")
        importPreferenceButton?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            // 設定インポート
            importPreference()
            true
        }
    }

    private fun getVersionName(context: Context): String {
        return try {
            // アプリケーションパッケージの情報を取得
            val info = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_META_DATA
            )
            val d = if (DebugCellInfo.DEBUG_CELL_INFO_ENABLE) "-DEBUG" else ""

            // バージョン番号の文字列を返す
            info.versionName + d
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            "x.x.x"
        }
    }

    private fun exportPreference() {
        // 初期ファイル名
        val now = LocalDateTime.now()
        val dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val yyyymmdd = now.format(dtf)
        val filename = "楽天電測設定" + yyyymmdd + ".json"

        // ファイル選択ダイアログ的なもの
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, filename)
        }
        exportPreferenceResultLauncher.launch(intent);
    }

    // 設定エクスポートのインテント送信して、ユーザーがファイルを選んだあとで実行されるところ
    private val exportPreferenceResultLauncher = registerForActivityResult(
        StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                exportPreferenceToUri(uri)
                activity?.finish()
            }
        }
    }

    // 設定エクスポートの実装
    private fun exportPreferenceToUri(uri: Uri) {
        try {
            context?.applicationContext?.contentResolver?.openOutputStream(uri)
                .use { outputStream ->
                    if (outputStream != null) {
                        val map = preferenceManager.sharedPreferences.all.toMutableMap()
                        // エクスポート対象外を削除
                        map.remove("deleteAllRadioInfoButton")
                        map.remove("version")
                        map.remove("exportPreferenceButton")
                        map.remove("importPreferenceButton")
                        map.remove("destination")

                        // jsonに変換して保存
                        val bw = outputStream.bufferedWriter()
                        bw.write(Gson().toJson(map))
                        bw.close()
                    }
                }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    private fun importPreference(){
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        importPreferenceResultLauncher.launch(intent)
    }

    // 設定インポートのインテント送信して、ユーザーがファイルを選んだあとで実行されるところ
    private val importPreferenceResultLauncher = registerForActivityResult(
        StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                importPreferenceFromUri(uri)

                // 再起動（設定を反映させるため）
                val app = MyApplication.getInstance()
                app.restartApp()
            }
        }
    }

    // 設定インポートの実装
    private fun importPreferenceFromUri(uri: Uri) {
        try {
            context?.applicationContext?.contentResolver?.openInputStream(uri)
                .use { inputStream ->
                    if (inputStream != null) {

                        val br = inputStream.bufferedReader()
                        val str: String = br.readText()
                        val preferenceMap = Gson().fromJson<MutableMap<String, Any>>(
                            str,
                            MutableMap::class.java
                        )
                        br.close()

                        val keyTypeMap = getKeyTypeMap()

                        for (kv in preferenceMap) {
                            if ( !keyTypeMap.containsKey(kv.key))
                                // アプリが把握してない設定項目→無視
                                continue
                            when {
                                keyTypeMap[kv.key] == "string" -> {
                                    try {
                                        val value = kv.value.toString()
                                        Utils.setPreferenceString(kv.key, value)
                                    } catch (e: Exception) {
                                    }
                                }
                                keyTypeMap[kv.key] == "int" -> {
                                    try {
                                        val value = kv.value.toString().toInt()
                                        Utils.setPreferenceInt(kv.key, value)
                                    } catch (e: Exception) {
                                    }
                                }
                                keyTypeMap[kv.key] == "bool" -> {
                                    try {
                                        val value = kv.value.toString().toBoolean()
                                        Utils.setPreferenceBoolean(kv.key, value)
                                    } catch (e: Exception) {
                                    }
                                }
                                else -> throw Exception("実装しなきゃ！")
                            }
                        }
                    }
                }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    // 設定のキーに対応する型名を入れたマップを作成する
    private fun getKeyTypeMap(): Map<String, String> {
        // 戻り値。キーは設定名、値は設定値の型名
        val keyTypeMap = mutableMapOf<String, String>()

        // root_preferences.xml を解析するパーサーを取得する
        val parser = context?.resources?.getXml(R.xml.root_preferences)

        if (parser != null) {
            // 解析が完了し、ドキュメントの終端に到達するまで処理を続ける
            while (parser.eventType != END_DOCUMENT) {
                // 開始タグなら
                if (parser.eventType == START_TAG) {
                    val type = when (parser.name) {
                        "EditTextPreference" -> "string"
                        //"ListPreference" -> "string"
                        //"MultiSelectListPreference" -> "string"
                        "DropDownPreference" -> "string"
                        "SeekBarPreference" -> "int"
                        "SwitchPreference" -> "bool"
                        //"CheckBoxPreference" -> "bool"
                        "Preference" -> {
                            parser.next()
                            continue
                        }
                        "PreferenceScreen" -> {
                            parser.next()
                            continue
                        }
                        else -> throw Exception("実装しなきゃ！")
                    }
                    val key =
                        parser.getAttributeValue("http://schemas.android.com/apk/res-auto", "key")
                    keyTypeMap[key] = type
                }
                parser.next()
            }
            parser.close()
        }
        return keyTypeMap
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
