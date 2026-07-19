package com.wxn.reader.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager

object BatteryOptimizationHelper {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (isIgnoringBatteryOptimizations(context)) return
        try {
            val intent = Intent(
                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}")
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: SecurityException) {
            com.wxn.base.util.Logger.e("BatteryOptimizationHelper::requestIgnoreBatteryOptimizations SecurityException")
        }
    }

    fun openBatteryOptimizationSettings(context: Context) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: SecurityException) {
            com.wxn.base.util.Logger.e("BatteryOptimizationHelper::openBatteryOptimizationSettings SecurityException")
        }
    }

    enum class Manufacturer(val displayName: String) {
        XIAOMI("Xiaomi/Redmi"),
        HUAWEI("Huawei/Honor"),
        OPPO("OPPO/Realme"),
        VIVO("vivo"),
        SAMSUNG("Samsung"),
        OTHER("Other")
    }

    fun getManufacturer(): Manufacturer {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> Manufacturer.XIAOMI
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> Manufacturer.HUAWEI
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> Manufacturer.OPPO
            manufacturer.contains("vivo") -> Manufacturer.VIVO
            manufacturer.contains("samsung") -> Manufacturer.SAMSUNG
            else -> Manufacturer.OTHER
        }
    }

    fun getManufacturerSettingsIntent(context: Context): Intent? {
        val packageName = context.packageName
        val intent = when (getManufacturer()) {
            Manufacturer.XIAOMI -> {
                Intent().apply {
                    component = ComponentName(
                        "com.miui.powerkeeper",
                        "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                    )
                    putExtra("package_name", packageName)
                    putExtra("package_label", context.applicationInfo.loadLabel(context.packageManager))
                }
            }
            Manufacturer.HUAWEI -> {
                Intent().apply {
                    component = ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.optimize.process.ProtectActivity"
                    )
                }
            }
            Manufacturer.OPPO -> {
                Intent().apply {
                    component = ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )
                }
            }
            Manufacturer.VIVO -> {
                Intent().apply {
                    component = ComponentName(
                        "com.vivo.abe",
                        "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity"
                    )
                }
            }
            Manufacturer.SAMSUNG -> {
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
            }
            Manufacturer.OTHER -> {
                Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return if (intent.resolveActivity(context.packageManager) != null) {
            intent
        } else {
            Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }
}