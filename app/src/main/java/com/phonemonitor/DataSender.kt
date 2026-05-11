package com.phonemonitor

import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object DataSender {
    private const val TAG = "DataSender"

    suspend fun sendReport(serverUrl: String, data: Map<String, Any>): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$serverUrl/api/report")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.doOutput = true
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000

                val json = buildJson(data)
                OutputStreamWriter(conn.outputStream).use { it.write(json) }
                val code = conn.responseCode
                conn.disconnect()

                Log.d(TAG, "Report sent, response: $code")
                code == 200
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send report: ${e.message}")
                false
            }
        }
    }

    suspend fun sendNow(
        serverUrl: String,
        deviceId: String,
        appName: String,
        since: String
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$serverUrl/api/now")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setProperty("Content-Type", "application/json; charset=utf-8")
                conn.doOutput = true
                conn.connectTimeout = 5_000
                conn.readTimeout = 5_000

                val json = """{"device_id":"$deviceId","app_name":"${escape(appName)}","since":"$since"}"""
                OutputStreamWriter(conn.outputStream).use { it.write(json) }
                val code = conn.responseCode
                conn.disconnect()
                code == 200
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send now: ${e.message}")
                false
            }
        }
    }

    private fun escape(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
    }

    private fun buildJson(data: Map<String, Any>): String {
        val sb = StringBuilder("{")
        data.entries.forEachIndexed { i, (key, value) ->
            if (i > 0) sb.append(",")
            sb.append("\"${escape(key)}\":")
            when (value) {
                is String -> sb.append("\"${escape(value)}\"")
                is Number -> sb.append(value)
                is Boolean -> sb.append(value)
                is List<*> -> sb.append(buildListJson(value))
                else -> sb.append("\"${escape(value.toString())}\"")
            }
        }
        sb.append("}")
        return sb.toString()
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildListJson(list: List<*>): String {
        val sb = StringBuilder("[")
        list.forEachIndexed { i, item ->
            if (i > 0) sb.append(",")
            when (item) {
                is Map<*, *> -> sb.append(buildJson(item as Map<String, Any>))
                is String -> sb.append("\"${escape(item)}\"")
                is Number -> sb.append(item)
                else -> sb.append("\"${escape(item.toString())}\"")
            }
        }
        sb.append("]")
        return sb.toString()
    }
}
