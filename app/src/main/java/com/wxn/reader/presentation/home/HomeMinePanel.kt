package com.wxn.reader.presentation.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.StarRate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.play.core.review.ReviewException
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.model.ReviewErrorCode
import com.mikepenz.markdown.m3.Markdown
import com.wxn.reader.R
import com.wxn.reader.data.model.AppTheme
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.navigation.Screens
import com.wxn.reader.presentation.settings.SetListItem
import com.wxn.base.util.Logger
import com.wxn.reader.util.customMarkdownTypography
import com.wxn.reader.util.getAppVersion
import java.io.IOException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeMinePanel(innerPadding: PaddingValues, viewModel: HomeViewModel) {
    val appPreferences by viewModel.appPreferences.collectAsStateWithLifecycle()
    val navController = LocalNavController.current

    val context = LocalContext.current
    val reviewManager = remember { ReviewManagerFactory.create(context) }
    val isDarkTheme = when (appPreferences?.appTheme) {
        AppTheme.SYSTEM -> isSystemInDarkTheme()
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        else -> isSystemInDarkTheme()
    }
    val elevationOverlay = if (isDarkTheme == true) {
        Color.White.copy(alpha = 0.09f)
            .compositeOver(MaterialTheme.colorScheme.surface)
    } else {
        MaterialTheme.colorScheme.surface
    }

    fun readPrivacyPolicy(context: Context): String {
        return try {
            context.assets.open("documentation/PRIVACY_POLICY.md").bufferedReader()
                .use { it.readText() }
        } catch (e: IOException) {
            "Error loading privacy policy"
        }
    }

    val appVersion = getAppVersion()

    var showVersionDialog by remember { mutableStateOf(false) }
    var showPrivacyPolicyModal by remember { mutableStateOf(false) }
    val privacyPolicy = remember { readPrivacyPolicy(context) }

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
//                            if (appPreferences.isPremium) {
//                                Image(
//                                    painter = painterResource(id = R.drawable.crown),
//                                    contentDescription = "Crown",
//                                    modifier = Modifier
//                                        .size(24.dp)
//                                        .offset(y = (36).dp)  // Adjust this value to control overlap
//                                )
//                            }
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
                                showPrivacyPolicyModal = true
                            })
                            .fillMaxWidth(),
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.privacy_policy),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Outlined.PrivacyTip,
                                contentDescription = "Rating",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                        )
                    )
                }


                item {
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
                                showVersionDialog = true
                            })
                            .fillMaxWidth(),
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.version),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = "Rating",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                        )
                    )
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

    if (showPrivacyPolicyModal) {
        ModalBottomSheet(
            shape = BottomSheetDefaults.HiddenShape,
            dragHandle = null,
            onDismissRequest = { showPrivacyPolicyModal = false },
            sheetState = rememberModalBottomSheetState(
                skipPartiallyExpanded = true,
                confirmValueChange = { it != SheetValue.PartiallyExpanded }
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .padding(vertical = 16.dp)
                        .weight(1f) // Give the scrollable content as much space as possible
                        .verticalScroll(rememberScrollState())
                ) {
                    Markdown(
                        typography = customMarkdownTypography(),
                        content = privacyPolicy
                    )
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/EucWang/HandyReader/blob/main/PrivayPolicy.md")
                                )
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    "Unable to open Privacy Policy",
                                    Toast.LENGTH_SHORT
                                )
                                    .show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = "Navigate to")
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = {
                            showPrivacyPolicyModal = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = "Close")
                    }
                }
            }
        }
    }

    if (showVersionDialog) {
        AlertDialog(
            onDismissRequest = {
                showVersionDialog = false
            },
            title = {
                Text(text = stringResource(R.string.version))
            },
            text = {
                Column {
                    Text(text = "${stringResource(LocalContext.current.applicationInfo.labelRes)}v${appVersion?.versionName ?: "Unknown"} (${appVersion?.versionNumber ?: "Unknown"}) stable")
                    Text(text = "Release Date: ${appVersion?.releaseDate ?: "Unknown"}")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showVersionDialog = false
                    }
                ) {
                    Text(text = "OK")
                }
            }
        )
    }
}
