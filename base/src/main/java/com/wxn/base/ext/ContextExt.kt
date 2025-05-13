package com.wxn.base.ext

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.BatteryManager
import android.provider.Settings
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.wxn.base.R
import com.wxn.base.util.ToastUtil
import java.io.File

fun Context.getCompatColor(@ColorRes id: Int): Int = ContextCompat.getColor(this, id)

fun Context.getCompatDrawable(@DrawableRes id: Int): Drawable? = ContextCompat.getDrawable(this, id)

fun Context.getCompatColorStateList(@ColorRes id: Int): ColorStateList? =
    ContextCompat.getColorStateList(this, id)

/**
 * 系统息屏时间
 */
val Context.sysScreenOffTime: Int
    get() {
        var screenOffTime = 0
        try {
            screenOffTime =
                Settings.System.getInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return screenOffTime
    }

val Context.statusBarHeight: Int
    get() {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return resources.getDimensionPixelSize(resourceId)
    }

val Context.navigationBarHeight: Int
    get() {
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return resources.getDimensionPixelSize(resourceId)
    }


fun Context.sendToClip(text: String) {
    val clipboard =
        getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clipData = ClipData.newPlainText(null, text)
    clipboard?.let {
        clipboard.setPrimaryClip(clipData)
        ToastUtil.show(R.string.copy_complete)
    }
}

fun Context.getClipText(): String? {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
    clipboard?.primaryClip?.let {
        if (it.itemCount > 0) {
            return it.getItemAt(0).text.toString().trim()
        }
    }
    return null
}

fun Context.sendMail(mail: String) {
    try {
        val intent = Intent(Intent.ACTION_SENDTO)
        intent.data = Uri.parse("mailto:$mail")
        startActivity(intent)
    } catch (e: Exception) {
        ToastUtil.show(e.localizedMessage ?: "Error")
    }
}

/**
 * 系统是否暗色主题
 */
fun Context.sysIsDarkMode(): Boolean {
    val mode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return mode == Configuration.UI_MODE_NIGHT_YES
}

/**
 * 获取电量
 */
val Context.sysBattery: Int
    get() {
        val iFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = registerReceiver(null, iFilter)
        return batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    }

val Context.externalFilesDir: File
    get() = this.getExternalFilesDir(null) ?: this.filesDir

val Context.eCacheDir: File
    get() = this.externalCacheDir ?: this.cacheDir

fun Context.openUrl(url: String) {
    openUrl(Uri.parse(url))
}

fun Context.openUrl(uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW)
    intent.data = uri
    if (intent.resolveActivity(packageManager) != null) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            ToastUtil.show(e.localizedMessage ?: "open url error")
        }
    } else {
        try {
            startActivity(Intent.createChooser(intent, "请选择浏览器"))
        } catch (e: Exception) {
            ToastUtil.show(e.localizedMessage ?: "open url error")
        }
    }
}

val Context.channel: String
    get() {
        try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            return appInfo.metaData.getString("channel") ?: ""
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }

fun Context.goShop() {
    val uri = Uri.parse("market://details?id=$packageName")
    val goToMarket = Intent(Intent.ACTION_VIEW, uri)
    val googlePlay = "com.android.vending"//这里对应的是谷歌商店，跳转别的商店改成对应的即可

    goToMarket.setPackage(googlePlay)//这里对应的是谷歌商店，跳转别的商店改成对应的即可

    try {
        this.startActivity(goToMarket)
    } catch (e: ActivityNotFoundException) {
        if (goToMarket.resolveActivity(this.packageManager) != null) { //有浏览器
            this.startActivity(goToMarket)
        } else {
            ToastUtil.show(getString(R.string.no_google))
        }
        e.printStackTrace()
    }
}