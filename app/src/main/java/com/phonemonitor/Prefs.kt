package com.phonemonitor

import android.content.Context

object Prefs {
    private const val NAME = "phone_monitor_prefs"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_DEVICE_ID = "device_id"
    private const val DEFAULT_SERVER_URL = "http://192.168.1.100:8765"

    private fun sp(context: Context) =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun getServerUrl(context: Context): String {
        return sp(context).getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
    }

    fun setServerUrl(context: Context, url: String) {
        sp(context).edit().putString(KEY_SERVER_URL, url).apply()
    }

    fun getDeviceId(context: Context): String {
        var id = sp(context).getString(KEY_DEVICE_ID, null)
        if (id == null) {
            id = "android_" + System.currentTimeMillis().toString().takeLast(8)
            sp(context).edit().putString(KEY_DEVICE_ID, id).apply()
        }
        return id
    }
}
