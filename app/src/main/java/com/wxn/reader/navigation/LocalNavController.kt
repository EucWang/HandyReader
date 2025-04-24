package com.wxn.reader.navigation

import androidx.compose.runtime.compositionLocalOf
import androidx.navigation.NavHostController
import com.wxn.reader.util.PurchaseHelper

val LocalNavController = compositionLocalOf<NavHostController>{
    error("No NavController found!")
}
