package com.ricdev.uread.navigation

import androidx.compose.runtime.compositionLocalOf
import com.ricdev.uread.util.PurchaseHelper

val PurchaseHelperController = compositionLocalOf<PurchaseHelper> {
    error("No PurchaseHelperController found!")
}