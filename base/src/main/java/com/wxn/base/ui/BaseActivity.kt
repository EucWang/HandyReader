package com.wxn.base.ui

import androidx.activity.ComponentActivity

abstract class BaseActivity : ComponentActivity() {


    val isInMultiWindow: Boolean
        get() {
            return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                isInMultiWindowMode
            } else {
                false
            }
        }
}