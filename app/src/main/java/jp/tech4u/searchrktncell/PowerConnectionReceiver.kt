package jp.tech4u.searchrktncell

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.view.Window
import android.view.WindowManager
import jp.tech4u.searchrktncell.util.Utils

class PowerConnectionReceiver(private var context: Context, private var window: Window) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        // 設定読み込み
        val keepScreenOn = Utils.getPreferenceString("keepScreenOn", "OFF")

        if (keepScreenOn == "POWER") {
            // 設定が電源連動だった場合のみ、画面OK設定へ反映
            setScreenOn(intent)
        }
    }

    // 常時画面ONの設定をする（設定とAC電源の状態を見て適切に設定する）
    fun setScreenOn() {
        when (Utils.getPreferenceString("keepScreenOn", "OFF")) {
            "ON" -> {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            "OFF" -> {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            "POWER" -> {
                val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { iFilter ->
                    context.registerReceiver(null, iFilter)
                }
                setScreenOn(batteryStatus)
            }
        }
    }

    // 常時画面ONの設定をする（AC電源の状態を見て適切に設定する）
    private fun setScreenOn(batteryStatus: Intent?) {
        if (batteryStatus == null) return

        when (batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            0 -> {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            else -> {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
}