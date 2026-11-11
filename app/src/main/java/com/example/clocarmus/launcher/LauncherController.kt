package com.example.clocarmus.launcher

import android.app.Activity
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Eligibility, user preference, pending disable and actual Home selection are independent. */
class LauncherController(private val activity: Activity, private val requestSelection: (Intent) -> Unit) {
    private val prefs = activity.getSharedPreferences("player-settings", Activity.MODE_PRIVATE)
    private val home = ComponentName(activity, "${activity.packageName}.HomeActivity")
    var enabled by mutableStateOf(prefs.getBoolean("launcherMode", false))
        private set
    var isDefault by mutableStateOf(false)
        private set
    var pendingDisable by mutableStateOf(prefs.getBoolean("launcherDisablePending", false))
        private set

    init { reconcile() }

    @Suppress("DEPRECATION")
    fun reconcile() {
        // A pending disable must never remove the selected Home component.
        isDefault = activity.packageManager.resolveActivity(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == activity.packageName
        if (pendingDisable && !isDefault) {
            pendingDisable = false
            prefs.edit().putBoolean("launcherDisablePending", false).apply()
            applyEnabled(false)
        } else {
            if (pendingDisable) {
                // Returning with the same default means the switch was cancelled.
                pendingDisable = false
                prefs.edit().putBoolean("launcherDisablePending", false).apply()
            }
            setCapability(enabled || isDefault)
            if (isDefault && !enabled) applyEnabled(true)
        }
    }

    fun toggle(value: Boolean) {
        if (value) {
            applyEnabled(true)
            chooseHome()
        } else {
            reconcile()
            if (!isDefault) applyEnabled(false)
            else {
                @Suppress("DEPRECATION")
                val alternatives = activity.packageManager.queryIntentActivities(homeIntent(), 0)
                    .any { it.activityInfo.packageName != activity.packageName && it.activityInfo.enabled && it.activityInfo.exported }
                if (!alternatives) {
                    message("Enable or install another launcher before disabling Launcher mode.")
                    return
                }
                pendingDisable = true
                prefs.edit().putBoolean("launcherDisablePending", true).apply()
                message("Choose another Home app to turn off Launcher mode. Cancellation keeps it enabled.")
                chooseHome()
            }
        }
    }

    fun chooseHome() {
        val request = if (Build.VERSION.SDK_INT >= 29 && !pendingDisable) {
            val roles = activity.getSystemService(RoleManager::class.java)
            if (roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME))
                roles.createRequestRoleIntent(RoleManager.ROLE_HOME) else Intent(Settings.ACTION_HOME_SETTINGS)
        } else Intent(Settings.ACTION_HOME_SETTINGS)
        try { requestSelection(request) }
        catch (_: RuntimeException) {
            try { requestSelection(Intent(Settings.ACTION_SETTINGS)) }
            catch (_: RuntimeException) { message("Home selection is unavailable on this device.") }
        }
    }

    private fun applyEnabled(value: Boolean) {
        setCapability(value)
        enabled = value
        if (!value) pendingDisable = false
        prefs.edit().putBoolean("launcherMode", value).putBoolean("launcherDisablePending", pendingDisable).apply()
    }

    private fun setCapability(value: Boolean) {
        val state = if (value) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        if (activity.packageManager.getComponentEnabledSetting(home) != state)
            activity.packageManager.setComponentEnabledSetting(home, state, PackageManager.DONT_KILL_APP)
    }

    private fun message(text: String) = Toast.makeText(activity, text, Toast.LENGTH_LONG).show()
    private fun homeIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
}
