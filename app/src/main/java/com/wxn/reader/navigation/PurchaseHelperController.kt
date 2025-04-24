package com.wxn.reader.navigation

import androidx.compose.runtime.compositionLocalOf
import com.wxn.reader.util.PurchaseHelper

val PurchaseHelperController = compositionLocalOf<PurchaseHelper> {
    error("No PurchaseHelperController found!")
}