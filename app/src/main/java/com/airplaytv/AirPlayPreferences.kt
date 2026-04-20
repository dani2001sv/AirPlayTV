package com.airplaytv

import android.content.Context
import android.os.Build

object AirPlayPreferences {

    private const val PREFS_NAME = "airplay_prefs"
    private const val KEY_DEVICE_NAME = "device_name"

    fun getDeviceName(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DEVICE_NAME, null) ?: getDefaultDeviceName()
    }

    fun setDeviceName(context: Context, name: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DEVICE_NAME, name.trim().take(32))
            .apply()
    }

    private fun getDefaultDeviceName(): String {
        val model = Build.MODEL.take(20)
        return "AirPlay TV ($model)"
    }
}
