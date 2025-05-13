package com.wxn.base.util

import android.app.Application
import androidx.annotation.StringRes
import com.hjq.toast.Toaster

object ToastUtil {

    fun init(app: Application) {
        Toaster.init(app)
    }

    fun show(text: CharSequence) {
        if (Toaster.isInit()) {
            Toaster.show(text)
        }
    }

    fun show(@StringRes stringId: Int) {
        if (Toaster.isInit()) {
            Toaster.show(stringId)
        }
    }

    fun showShort(text: CharSequence) {
        if (Toaster.isInit()) {
            Toaster.showShort(text)
        }
    }

    fun showLong(text: CharSequence) {
        if (Toaster.isInit()) {
            Toaster.showLong(text)
        }
    }

    fun delayShow(text: CharSequence, delayMillis: Long = 500) {
        if (Toaster.isInit()) {
            Toaster.delayedShow(text, delayMillis)
        }
    }

    fun setGravity(gravity: Int) {
        Toaster.setGravity(gravity)
    }

    fun debugShow(text: CharSequence) {
        if (Toaster.isInit()) {
            Toaster.debugShow(text)
        }
    }

}