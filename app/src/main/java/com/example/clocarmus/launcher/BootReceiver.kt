package com.example.clocarmus.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.clocarmus.MainActivity

/** Best effort only: Android/OEM background-start restrictions still apply. No playback request. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED ||
            !context.getSharedPreferences("player-settings", Context.MODE_PRIVATE).getBoolean("openAfterBoot", false)) return
        @Suppress("DEPRECATION")
        val home = context.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        if (home?.activityInfo?.packageName == context.packageName) return // Android owns Home startup.
        try {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("boot_open", true))
        } catch (_: RuntimeException) { /* Manual entry remains available when boot UI opening is blocked. */ }
    }
}
