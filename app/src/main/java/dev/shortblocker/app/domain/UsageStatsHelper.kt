package dev.shortblocker.app.domain

import android.app.usage.UsageStatsManager
import android.content.Context
import dev.shortblocker.app.data.ServiceTarget

data class ForegroundAppSnapshot(
    val appName: String,
    val packageName: String,
)

class UsageStatsHelper(private val context: Context) {
    fun queryForegroundApp(): ForegroundAppSnapshot? {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val begin = end - 10 * 60 * 1000L
        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            begin,
            end,
        )
        val top = usageStats.maxByOrNull { it.lastTimeUsed } ?: return null
        val target = ServiceTarget.fromPackage(top.packageName)
        val appName = target?.appName ?: top.packageName.substringAfterLast('.').replaceFirstChar { char ->
            char.uppercase()
        }
        return ForegroundAppSnapshot(
            appName = appName,
            packageName = top.packageName,
        )
    }
}
