package com.wxn.reader.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.wxn.reader.presentation.audioBookReader.AudiobookReaderScreen
import com.wxn.reader.presentation.bookDetails.BookDetailsScreen
import com.wxn.reader.presentation.gettingStarted.GettingStartedScreen
import com.wxn.reader.presentation.home.HomeScreen
import com.wxn.reader.presentation.mainReader.MainReadScreen
import com.wxn.reader.presentation.notes.NotesScreen
import com.wxn.reader.presentation.pdfReader.PdfReaderScreen
import com.wxn.reader.presentation.settings.components.DeletedBooksScreen
import com.wxn.reader.presentation.settings.components.GeneralSettings
import com.wxn.reader.presentation.settings.components.SpeakerScreen
import com.wxn.reader.presentation.shelves.ShelvesScreen
import com.wxn.reader.presentation.settings.components.ThemeScreen
import com.wxn.reader.presentation.sharedComponents.PremiumScreen
import com.wxn.reader.presentation.statistics.StatisticsScreen

@Composable
fun SetupNavGraph(startDestination: String) {
    val navController : NavHostController = LocalNavController.current
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(
            route = Screens.GettingStartedScreen.route
        ) {
            GettingStartedScreen()
        }
        composable(
            route = Screens.HomeScreen.route
        ) {
            HomeScreen()
        }
        composable(
            route = Screens.MainReaderScreen.route + "/{bookId}/{bookUri}",
        ) {
            MainReadScreen()
        }
        composable(
            route = Screens.PdfReaderScreen.route + "/{bookId}/{bookUri}",
        ) {
            PdfReaderScreen()
        }
        composable(
            route = Screens.AudiobookReaderScreen.route + "/{bookId}/{bookUri}",
        ) {
            AudiobookReaderScreen()
        }
        composable(
            route = Screens.BookDetailsScreen.route + "/{bookId}/{bookUri}",
        ) {
            BookDetailsScreen()
        }
        composable(
            route = Screens.GeneralSettingsScreen.route,
        ) {
            GeneralSettings()
        }
        composable(
            route = Screens.ThemeScreen.route,
        ) {
            ThemeScreen()
        }
        composable(
            route = Screens.ShelvesScreen.route,
        ) {
            ShelvesScreen()
        }
        composable(
            route = Screens.DeletedBooksScreen.route,
        ) {
            DeletedBooksScreen()
        }

        composable(
            route = Screens.NotesScreen.route,
        ) {
            NotesScreen()
        }
        composable(
            route = Screens.StatisticsScreen.route,
        ) {
            StatisticsScreen()
        }
        composable(
            route = Screens.PremiumScreen.route,
        ) {
            PremiumScreen()
        }
        composable(
            route = Screens.TtsSetScreen.route,
        ) {
            SpeakerScreen()
        }
        //composable(
        //    route = Screens.OnlineBooksScreen.route,
        //) {
        //    OnlineBooksScreen(purchaseHelper)
        //}
        //composable(
        //    route = "webview/{url}",
        //    arguments = listOf(navArgument("url") { type = NavType.StringType })
        //) { backStackEntry ->
        //    val encodedUrl = backStackEntry.arguments?.getString("url") ?: ""
        //    val decodedUrl = URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8.toString())
        //    WebViewScreen(url = decodedUrl)
        //}
    }
}

fun NavHostController.navigateToScreen(route: String) {
    this.navigate(route) {
        // Pop up to the start destination of the graph to
        // avoid building up a large stack of destinations
        // on the back stack as users select items
        popUpTo(this@navigateToScreen.graph.startDestinationId) {
            saveState = true
        }
        // Avoid multiple copies of the same destination when
        // reelecting the same item
        launchSingleTop = true
        // Restore state when reelecting a previously selected item
        restoreState = true
    }
}