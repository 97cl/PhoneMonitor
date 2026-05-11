package com.phonemonitor

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*

class UsageMonitorService : Service() {

    companion object {
        var isRunning = false
            private set
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastForegroundApp = ""
    private var lastAppSince = 0L

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
        isRunning = false
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationHelper.buildNotification(this).build()
        startForeground(NotificationHelper.NOTIFICATION_ID, notification)
        isRunning = true

        // 周期性收集数据并上报
        scope.launch {
            while (isActive) {
                try {
                    collectAndReport()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in collection cycle", e)
                }
                delay(30_000) // 每30秒上报一次
            }
        }

        Log.d(TAG, "Monitoring started")
        return START_STICKY
    }

    private suspend fun collectAndReport() {
        val deviceId = Prefs.getDeviceId(this)
        val serverUrl = Prefs.getServerUrl(this)

        // 1. 获取自上次上报以来的应用使用数据
        val apps = UsageStatsHelper.queryUsageStats(this)

        // 2. 获取当前前台 App（从 AccessibilityService）
        val foreground = ForegroundAppTracker.currentApp
        if (foreground != lastForegroundApp) {
            lastForegroundApp = foreground
            lastAppSince = System.currentTimeMillis()
        }

        // 3. 上报数据
        val payload = mapOf(
            "device_id" to deviceId,
            "apps" to apps,
            "screen_events" to emptyList<Any>()
        )

        // 4. 同时上报当前活跃应用
        DataSender.sendNow(
            serverUrl = serverUrl,
            deviceId = deviceId,
            appName = foreground,
            since = if (lastAppSince > 0) formatTime(lastAppSince) else ""
        )

        // 5. 发送批量数据
        DataSender.sendReport(serverUrl, payload)
    }

    private fun formatTime(ms: Long): String {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = ms
        return String.format(
            "%04d-%02d-%02dT%02d:%02d:%02d",
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH),
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE),
            cal.get(java.util.Calendar.SECOND)
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        scope.cancel()
        Log.d(TAG, "Service stopped")
        super.onDestroy()
    }

    private companion object {
        const val TAG = "PhoneMonitor"
    }
}
