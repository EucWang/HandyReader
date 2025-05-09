package com.wxn.reader.presentation.home

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.StarRate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.play.core.review.ReviewException
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.model.ReviewErrorCode
import com.wxn.reader.R
import com.wxn.reader.data.model.AppTheme
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.navigation.Screens
import com.wxn.reader.presentation.settings.SetListItem
import com.wxn.reader.util.Logger

@Composable
fun HomeMinePanel(innerPadding: PaddingValues, viewModel: HomeViewModel) {
    val appPreferences by viewModel.appPreferences.collectAsStateWithLifecycle()
    val navController = LocalNavController.current

    val context = LocalContext.current
    val reviewManager = remember { ReviewManagerFactory.create(context) }
    val isDarkTheme = when (appPreferences.appTheme) {
        AppTheme.SYSTEM -> isSystemInDarkTheme()
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
    }

    Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.TopStart) {
            LazyColumn(
                modifier = Modifier.fillMaxSize()//.padding(top = 36.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = (-12).dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (appPreferences.isPremium) {
                                Image(
                                    painter = painterResource(id = R.drawable.crown),
                                    contentDescription = "Crown",
                                    modifier = Modifier
                                        .size(24.dp)
                                        .offset(y = (36).dp)  // Adjust this value to control overlap
                                )
                            }
                            Image(
                                painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                                contentDescription = "App Logo",
                                modifier = Modifier.size(150.dp)
                            )

                        }
                    }
                }

                item {
                    SetListItem(isDarkTheme, stringResource(R.string.shelves),Icons.Outlined.FolderCopy,) {
                        navController.navigate(Screens.ShelvesScreen.route)
                    }
                }

                item {
                    SetListItem(isDarkTheme, stringResource(R.string.deleted_books),Icons.Outlined.DeleteOutline,) {
                        navController.navigate(Screens.DeletedBooksScreen.route)
                    }
                }

                item {
                    SetListItem(isDarkTheme,  stringResource(R.string.notes),Icons.AutoMirrored.Outlined.StickyNote2,) {
                        navController.navigate(Screens.NotesScreen.route)
                    }
                }
                item {
                    SetListItem(isDarkTheme, stringResource(R.string.highlights_underlines),Icons.Outlined.BorderColor,) {
                        navController.navigate(Screens.AnnotationsScreen.route)
                    }
                }

                item {
                    SetListItem(isDarkTheme, stringResource(R.string.statistics), Icons.Outlined.QueryStats,) {
                        navController.navigate(Screens.StatisticsScreen.route)
                    }
                }
                item {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                    Spacer(Modifier.height(16.dp))
                }
                item {
                    SetListItem(isDarkTheme,
                        text = stringResource(R.string.general),
                        icon = Icons.Outlined.Tune) {
                        navController.navigate(Screens.GeneralSettingsScreen.route)
                    }
                }

                item {
                    SetListItem(isDarkTheme, stringResource(R.string.theme), Icons.Outlined.Palette) {
                        navController.navigate(Screens.ThemeScreen.route)
                    }
                }


                item {
                    SetListItem(isDarkTheme,  stringResource(R.string.about),Icons.Outlined.Info,) {
                        navController.navigate(Screens.AboutAppScreen.route + "/${isDarkTheme}")
                    }
                }

                item {
                    SetListItem(isDarkTheme,  stringResource(R.string.rate_the_app), Icons.Outlined.StarRate,) {
                        val request = reviewManager.requestReviewFlow()
                        request.addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                // We got the ReviewInfo object
                                val reviewInfo = task.result
                                val flow = reviewManager.launchReviewFlow(
                                    context as ComponentActivity,
                                    reviewInfo
                                )
                                flow.addOnCompleteListener { _ ->
                                    Logger.d("Review:Review flow completed")
                                    // The flow has finished. The API does not indicate whether the user
                                    // reviewed or not, or even whether the review dialog was shown. Thus, no
                                    // matter the result, we continue our app flow.
                                }
                            } else {
                                // There was some problem, log or handle the error code.
                                @ReviewErrorCode val reviewErrorCode =
                                    (task.exception as ReviewException).errorCode
                                Logger.e("Review:Error code: $reviewErrorCode")
                            }
                        }
                    }
                }
            }
        }
}
