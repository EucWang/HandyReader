package com.wxn.reader.presentation.settings


import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.StarRate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.google.android.play.core.review.ReviewException
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.model.ReviewErrorCode
import com.wxn.reader.R
import com.wxn.reader.data.model.AppTheme
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.navigation.PurchaseHelperController
import com.wxn.reader.presentation.sharedComponents.CustomNavigationDrawer
import com.wxn.reader.util.PurchaseHelper
import com.wxn.reader.navigation.Screens
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val purchaseHelper: PurchaseHelper = PurchaseHelperController.current
    val navController: NavHostController = LocalNavController.current
    val context = LocalContext.current
    val reviewManager = remember { ReviewManagerFactory.create(context) }

//    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val appPreferences by viewModel.appPreferences.collectAsStateWithLifecycle()

    val isDarkTheme = when (appPreferences.appTheme) {
        AppTheme.SYSTEM -> isSystemInDarkTheme()
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
    }


    val elevationOverlay = if (isDarkTheme) {
        Color.White.copy(alpha = 0.09f)
            .compositeOver(MaterialTheme.colorScheme.surface)
    } else {
        MaterialTheme.colorScheme.surface
    }



//    CustomNavigationDrawer(
//        drawerState = drawerState,
//    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = {
                            scope.launch {
//                                drawerState.open()
                                navController.popBackStack()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    title = { Text(text = stringResource(R.string.settings)) }
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
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
                    SetListItem(isDarkTheme, stringResource(R.string.deleted_books),Icons.Outlined.DeleteOutline,) {
                        navController.navigate(Screens.DeletedBooksScreen.route)
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
                                            Log.d("Review", "Review flow completed")
                                            // The flow has finished. The API does not indicate whether the user
                                            // reviewed or not, or even whether the review dialog was shown. Thus, no
                                            // matter the result, we continue our app flow.
                                        }
                                    } else {
                                        // There was some problem, log or handle the error code.
                                        @ReviewErrorCode val reviewErrorCode =
                                            (task.exception as ReviewException).errorCode
                                        Log.e("Review", "Error code: $reviewErrorCode")
                                    }
                                }
                    }
                }

//                if (!appPreferences.isPremium) {
//                    item {
//                        ListItem(
//                            modifier = Modifier
//                                .padding(horizontal = 16.dp, vertical = 8.dp)
//                                .shadow(
//                                    elevation = 4.dp,
//                                    shape = RoundedCornerShape(16.dp),
//                                    spotColor = if (!isDarkTheme) {
//                                        Color.Black.copy(alpha = 0.8f)
//                                    } else {
//                                        Color.Black.copy(alpha = 0.5f)
//                                    }
//                                )
//                                .clip(RoundedCornerShape(16.dp))
//                                .background(elevationOverlay)
//                                .clickable(onClick = {
//                                    viewModel.purchasePremium(purchaseHelper)
//                                })
//                                .fillMaxWidth(),
//                            headlineContent = {
//                                Text(
//                                    text = stringResource(R.string.remove_ads),
//                                    color = MaterialTheme.colorScheme.onSurface
//                                )
//                            },
//                            leadingContent = {
//                                Icon(
//                                    Icons.Default.Block,
//                                    contentDescription = stringResource(R.string.remove_ads),
//                                    tint = MaterialTheme.colorScheme.onSurface
//                                )
//                            },
//                            colors = ListItemDefaults.colors(
//                                containerColor = Color.Transparent,
//                            )
//                        )
//                    }
//                }
            }
        }
//    }
}

@Composable
fun SetListItem(
    isDarkTheme: Boolean,
    text: String,
    icon: ImageVector,
    elevationOverlay: Color = if (isDarkTheme) {
        Color.White.copy(alpha = 0.09f)
            .compositeOver(MaterialTheme.colorScheme.surface)
    } else {
        MaterialTheme.colorScheme.surface
    },
    itemClick: (() -> Unit)? = null
) {
    ListItem(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = if (!isDarkTheme) {
                    Color.Black.copy(alpha = 0.8f)
                } else {
                    Color.Black.copy(alpha = 0.5f)
                }
            )
            .clip(RoundedCornerShape(16.dp))
            .background(elevationOverlay)
            .clickable(onClick = {
                itemClick?.invoke()
            })
            .fillMaxWidth(),
        headlineContent = {
            Text(
                text = text, //stringResource(R.string.general),
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        leadingContent = {
            Icon(
                imageVector = icon, //Icons.Outlined.Tune,
                contentDescription = "General",
                tint = MaterialTheme.colorScheme.onSurface
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
        )
    )
}