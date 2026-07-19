package com.wxn.reader.util

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.widget.Toast
import com.wxn.base.util.Logger
import com.wxn.reader.R
import com.wxn.reader.data.model.TranslatorItem

object TextProcessAppHelper {

    fun getTextProcessApps(context: Context): MutableList<ResolveInfo?> {
        val intent = Intent(Intent.ACTION_PROCESS_TEXT)
        intent.type = "text/plain"
        val pm = context.packageManager
        return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
    }

    fun getInstalledItems(
        context: Context,
        filter: (String) -> Boolean
    ): List<TranslatorItem> {
        return getTextProcessApps(context)
            .filterNotNull()
            .filter { filter(it.activityInfo.packageName) }
            .map { resolveInfo ->
                val activityInfo = resolveInfo.activityInfo
                TranslatorItem(
                    id = "${activityInfo.packageName}/${activityInfo.name}",
                    name = resolveInfo.loadLabel(context.packageManager).toString(),
                    packageName = activityInfo.packageName,
                    activityName = activityInfo.name,
                    isBuiltIn = false
                )
            }
    }

    fun isAppAvailable(context: Context, id: String, builtInId: String): Boolean {
        if (id.isEmpty()) return false
        if (id == builtInId) return true
        val parts = id.split("/", limit = 2)
        if (parts.size != 2) return false
        val intent = Intent(Intent.ACTION_PROCESS_TEXT).apply {
            type = "text/plain"
            component = ComponentName(parts[0], parts[1])
        }
        return intent.resolveActivity(context.packageManager) != null
    }

    fun sendTextToAppById(context: Context, id: String, text: String, errorResId: Int = R.string.translator_app_not_found): Boolean {
        val parts = id.split("/", limit = 2)
        if (parts.size != 2) {
            Logger.e("TextProcessAppHelper::sendTextToAppById invalid id: $id")
            return false
        }
        return try {
            val intent = Intent(Intent.ACTION_PROCESS_TEXT).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_PROCESS_TEXT, text)
                component = ComponentName(parts[0], parts[1])
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Logger.e("TextProcessAppHelper::sendTextToAppById ActivityNotFound: ${e.message}")
            Toast.makeText(context, context.getString(errorResId), Toast.LENGTH_SHORT).show()
            false
        } catch (e: Exception) {
            Logger.e("TextProcessAppHelper::sendTextToAppById error: ${e.message}")
            false
        }
    }

    fun sendTextToApp(context: Context, text: String?, resolveInfo: ResolveInfo?): Boolean {
        if (resolveInfo == null || resolveInfo.activityInfo == null) {
            return false
        }

        return try {
            val intent = Intent(Intent.ACTION_PROCESS_TEXT).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_PROCESS_TEXT, text)
                component = ComponentName(
                    resolveInfo.activityInfo.packageName,
                    resolveInfo.activityInfo.name
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: SecurityException) {
            Logger.e("TextProcessAppHelper::sendTextToApp SecurityException: ${e.message}")
            Toast.makeText(context, context.getString(R.string.action_launch_failed), Toast.LENGTH_SHORT).show()
            false
        } catch (e: ActivityNotFoundException) {
            Logger.e("TextProcessAppHelper::sendTextToApp ActivityNotFound: ${e.message}")
            Toast.makeText(context, context.getString(R.string.translator_app_unavailable), Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun shareText(context: Context, text: String?) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            val chooser = Intent.createChooser(shareIntent, context.getString(R.string.select_translator)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: SecurityException) {
            Logger.e("TextProcessAppHelper::shareText SecurityException: ${e.message}")
            Toast.makeText(context, context.getString(R.string.action_launch_failed), Toast.LENGTH_SHORT).show()
        }
    }
}
