package com.phonemonitor

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

object UsageStatsHelper {

    fun hasPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOp(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun queryUsageStats(context: Context): List<Map<String, Any>> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val startOfDay = getStartOfDay()
        val pm = context.packageManager

        val events = usm.queryEvents(startOfDay, now)
        val appTimeMap = mutableMapOf<String, Long>()
        var lastEvent: UsageEvents.Event? = null

        while (events.hasNextEvent()) {
            val event = UsageEvents.Event()
            events.getNextEvent(event)

            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND
            ) {
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    lastEvent = event
                } else if (event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND && lastEvent != null) {
                    val duration = event.timeStamp - lastEvent.timeStamp
                    val pkg = lastEvent.packageName
                    appTimeMap[pkg] = (appTimeMap[pkg] ?: 0) + duration
                    lastEvent = null
                }
            }
        }

        // 如果当前有 App 在 foreground，算到现在的时长
        if (lastEvent != null) {
            val duration = now - lastEvent.timeStamp
            val pkg = lastEvent.packageName
            appTimeMap[pkg] = (appTimeMap[pkg] ?: 0) + duration
        }

        // 转换为上报格式，按时长降序排列
        return appTimeMap.entries
            .filter { it.value > 5_000 } // 过滤掉 <5秒的
            .sortedByDescending { it.value }
            .map { (pkg, duration) ->
                val appName = try {
                    val ai = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(ai).toString()
                } catch (e: Exception) {
                    pkg
                }
                mapOf(
                    "app_name" to appName,
                    "package_name" to pkg,
                    "duration" to (duration / 1000).toInt(),
                    "category" to getAppCategory(pkg)
                )
            }
    }

    private fun getStartOfDay(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun getAppCategory(packageName: String): String {
        return when {
            packageName.contains("wechat") || packageName.contains("whatsapp") ||
            packageName.contains("telegram") || packageName.contains("messenger") ||
            packageName.contains("discord") || packageName.contains("dingtalk") ||
            packageName.contains("com.tencent.mm") ||
            packageName.contains("com.tencent.wework") ||
            packageName.contains("com.taobao") || packageName.contains("com.alibaba") -> "社交"
            packageName.contains("tiktok") || packageName.contains("douyin") ||
            packageName.contains("kuaishou") || packageName.contains("bilibili") ||
            packageName.contains("youtube") || packageName.contains("netflix") ||
            packageName.contains("tencent") || packageName.contains("iqiyi") ||
            packageName.contains("youku") -> "娱乐/视频"
            packageName.contains("chrome") || packageName.contains("browser") ||
            packageName.contains("firefox") || packageName.contains("edge") ||
            packageName.contains("uc") || packageName.contains("qqbrowser") ||
            packageName.contains("baidu") -> "浏览器"
            packageName.contains("zhihu") || packageName.contains("weibo") ||
            packageName.contains("tieba") || packageName.contains("reddit") ||
            packageName.contains("douban") || packageName.contains("xhs") ||
            packageName.contains("xiaohongshu") -> "社交/社区"
            packageName.contains("alipay") || packageName.contains("weixin") ||
            packageName.contains("unionpay") || packageName.contains("bank") ||
            packageName.contains("pay") -> "金融/支付"
            packageName.contains("meituan") || packageName.contains("dianping") ||
            packageName.contains("eleme") || packageName.contains("jd") ||
            packageName.contains("taobao") || packageName.contains("pinduoduo") ||
            packageName.contains("amazon") || packageName.contains("vip") ||
            packageName.contains("suning") || packageName.contains("walmart") -> "购物"
            packageName.contains("map") || packageName.contains("amap") ||
            packageName.contains("baidu") || packageName.contains("gaode") ||
            packageName.contains("didi") || packageName.contains("navigation") -> "出行/导航"
            packageName.contains("email") || packageName.contains("mail") ||
            packageName.contains("gmail") || packageName.contains("outlook") -> "邮件"
            packageName.contains("game") || packageName.contains("unity") ||
            packageName.contains("cocos") || packageName.contains("nintendo") ||
            packageName.contains("epic") || packageName.contains("miHoYo") ||
            packageName.contains("honor") || packageName.contains("king") -> "游戏"
            packageName.contains("launcher") || packageName.contains("systemui") ||
            packageName.contains("settings") || packageName.contains("android") &&
            !packageName.contains("theme") -> "系统"
            else -> "其他"
        }
    }
}
