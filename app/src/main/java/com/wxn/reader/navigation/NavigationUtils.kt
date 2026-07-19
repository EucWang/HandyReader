package com.wxn.reader.navigation

import androidx.navigation.NavHostController

/**
 * 统一的首页导航辅助。
 *
 * 优先回到栈中已有的 HomeScreen（保持状态）；
 * 若 HomeScreen 不在返回栈中（深链接 / Intent 直达阅读页），
 * 则重建返回栈导航到首页。
 */
fun navigateToHome(navController: NavHostController) {
    if (!navController.popBackStack(Screens.HomeScreen.route, false)) {
        navController.navigate(Screens.HomeScreen.route) {
            popUpTo(0) { inclusive = true }
        }
    }
}
