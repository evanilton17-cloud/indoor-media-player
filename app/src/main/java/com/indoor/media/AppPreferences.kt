package com.indoor.media

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import java.util.UUID

object AppPreferences {

    const val SERVER_URL = "http://10.0.2.2:3000"

    private const val PREFS_NAME = "indoor_cloud"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getDeviceId(context: Context): String {
        val current = prefs(context).getString("device_id", null)
        if (current != null) return current
        val newId = UUID.randomUUID().toString()
        prefs(context).edit().putString("device_id", newId).apply()
        return newId
    }

    fun isPaired(context: Context): Boolean = prefs(context).getBoolean("paired", false)

    fun setPaired(context: Context, paired: Boolean) {
        prefs(context).edit().putBoolean("paired", paired).apply()
    }

    fun setPairingCode(context: Context, code: String) {
        prefs(context).edit().putString("pairing_code", code).apply()
    }

    fun getPairingCode(context: Context): String =
        prefs(context).getString("pairing_code", "000000") ?: "000000"

    fun getModel(): String = Build.MODEL ?: "unknown"

    fun getAndroidVersion(): String = Build.VERSION.RELEASE ?: "unknown"
}
