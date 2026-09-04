package com.privateagent.private_agent.service

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

data class InstalledApp(
    val label: String,
    val packageName: String
)

class AppLauncherService(private val context: Context) {

    fun getInstalledApps(): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        val result = mutableListOf<InstalledApp>()
        for (info in resolveInfos) {
            val label = info.loadLabel(pm).toString()
            val pkg = info.activityInfo.packageName
            result.add(InstalledApp(label = label, packageName = pkg))
        }
        return result.distinctBy { it.packageName }
    }

    fun openApp(nameOrPackage: String): Boolean {
        val pm = context.packageManager
        val query = nameOrPackage.trim().lowercase()

        // 1. Direct package check
        val directIntent = pm.getLaunchIntentForPackage(nameOrPackage.trim())
        if (directIntent != null) {
            directIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(directIntent)
            return true
        }

        // 2. Search installed apps by name
        val apps = getInstalledApps()
        val exact = apps.firstOrNull { it.label.lowercase() == query || it.packageName.lowercase() == query }
        if (exact != null) {
            val intent = pm.getLaunchIntentForPackage(exact.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            }
        }

        val fuzzy = apps.firstOrNull { it.label.lowercase().contains(query) || it.packageName.lowercase().contains(query) }
        if (fuzzy != null) {
            val intent = pm.getLaunchIntentForPackage(fuzzy.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            }
        }

        return false
    }
}
