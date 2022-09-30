package jp.tech4u.searchrktncell

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process

// 参考 https://qiita.com/Shiozawa/items/85f078ed57aed46f6b69

class RestartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. メインプロセスを Kill する
        val intent = intent
        val mainPid = intent.getIntExtra(EXTRA_MAIN_PID, -1)
        Process.killProcess(mainPid)

        // 2. MainActivity を再起動する
        val context = applicationContext
        val restartIntent = Intent(Intent.ACTION_MAIN)
        restartIntent.setClassName(context.packageName, MapsActivity::class.java.name)
        restartIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(restartIntent)

        // 3. RestartActivity を終了する
        finish()
        Process.killProcess(Process.myPid())
    }

    companion object {
        const val EXTRA_MAIN_PID = "RestartActivity.main_pid"
        fun createIntent(context: Context): Intent {
            val intent = Intent()
            intent.setClassName(context.packageName, RestartActivity::class.java.name)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            // メインプロセスの PID を Intent に保存しておく
            intent.putExtra(EXTRA_MAIN_PID, Process.myPid())
            return intent
        }
    }
}