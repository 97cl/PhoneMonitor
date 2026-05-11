package com.phonemonitor

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

class ForegroundAppTracker : AccessibilityService() {

    companion object {
        var currentApp: String = ""
            private set

        fun isEnabled(context: Context): Boolean {
            val enabled = try {
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
            } catch (e: Exception) {
                null
            }
            return enabled?.contains(context.packageName + "/" + ForegroundAppTracker::class.java.name) ?: false
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (event.packageName != null) {
                    val pm = packageManager
                    val appName = try {
                        val ai = pm.getApplicationInfo(event.packageName.toString(), 0)
                        pm.getApplicationLabel(ai).toString()
                    } catch (e: Exception) {
                        event.packageName.toString()
                    }
                    currentApp = appName
                }
            }
        }
    }

    override fun onInterrupt() {
        // 无障碍服务中断时不做特殊处理
    }
}
