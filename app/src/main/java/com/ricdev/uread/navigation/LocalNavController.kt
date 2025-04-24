package com.ricdev.uread.navigation

import androidx.compose.runtime.compositionLocalOf
import androidx.navigation.NavHostController
import com.ricdev.uread.util.PurchaseHelper

val LocalNavController = compositionLocalOf<NavHostController>{
    error("No NavController found!")
}
