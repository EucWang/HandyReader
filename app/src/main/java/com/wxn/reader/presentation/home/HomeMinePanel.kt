package com.wxn.reader.presentation.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.rememberAsyncImagePainter
import com.wxn.reader.R
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.navigation.Screens
import com.wxn.reader.presentation.sharedComponents.NavigationItem

@Composable
fun HomeMinePanel(innerPadding: PaddingValues, viewModel: HomeViewModel) {
    val appPreferences by viewModel.appPreferences.collectAsStateWithLifecycle()
    val navController = LocalNavController.current

    Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.TopStart) {
//        if (appPreferences.homeBackgroundImage.isNotEmpty()) { //自定义背景
//            Image(
//                painter = rememberAsyncImagePainter(appPreferences.homeBackgroundImage),
//                contentDescription = "Book cover",
//                modifier = Modifier
//                    .fillMaxSize()
//                    .alpha(0.7f),
//                contentScale = ContentScale.Crop
//            )
//        }
//        // Gradient overlay
//        Box(                    //默认背景
//            modifier = Modifier.fillMaxSize()
//
//                .background(
//                    brush = Brush.verticalGradient(
//                        colors = listOf(
//                            Color.Transparent,
//                            MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
//                            MaterialTheme.colorScheme.background
//                        ),
//                        startY = 0f,
//                        endY = 2000f
//                    )
//                )
//        )

//        Column{
//        ModalDrawerSheet {
//            Spacer(Modifier.height(if (isPortrait) 24.dp else 0.dp))
//            Spacer(Modifier.height(24.dp))
//            Column(
//                modifier = Modifier.width(200.dp),
//                verticalArrangement = Arrangement.Center,
//                horizontalAlignment = Alignment.CenterHorizontally,
//            ) {
//                if (appPreferences.isPremium) {
//                    Image(
//                        painter = painterResource(id = R.drawable.crown),
//                        contentDescription = "Crown",
//                        modifier = Modifier
////                            .size(if (isPortrait) 24.dp else 16.dp)
//                            .size(24.dp)
//                            .offset(y = 36.dp)
////                            .offset(y = (if (isPortrait) 36 else 18).dp)
//                    )
////                        FilledTonalButton(
////                                contentPadding = PaddingValues(8.dp),
////                        onClick = {
////                        },
////                        modifier = Modifier
////                            .offset(y = (if (isPortrait) 36 else 18).dp)
////                        ) {
////                                Text("Connect to Google Drive", fontSize = 12.sp)
////                        }
//                } else {
//                    FilledTonalButton(
//                        contentPadding = PaddingValues(8.dp),
//                        onClick = {
//                            navController.navigate(Screens.PremiumScreen.route)
//                        },
//                        modifier = Modifier
////                            .offset(y = (if (isPortrait) 36 else 18).dp)
//                            .offset(y = 36.dp)
//                    ) {
//                        Row(
//                            verticalAlignment = Alignment.CenterVertically,
//                            horizontalArrangement = Arrangement.spacedBy(4.dp)
//                        ) {
//                            Image(
//                                painter = painterResource(id = R.drawable.crown),
//                                contentDescription = "Crown",
//                                modifier = Modifier.size(16.dp)
//                            )
//                            Text(stringResource(R.string.unlock_premium), fontSize = 12.sp)
//                            Image(
//                                painter = painterResource(id = R.drawable.crown),
//                                contentDescription = "Crown",
//                                modifier = Modifier.size(16.dp)
//                            )
//                        }
//                    }
//                }
//                Image(
//                    painter = painterResource(id = R.mipmap.ic_launcher_foreground),
//                    contentDescription = "App Logo",
//                    modifier = Modifier.size(150.dp)
//                )
//            }
//            HorizontalDivider()
//            Spacer(Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(top = 36.dp)
            ) {
                item {
                    NavigationItem(
                        icon = Icons.Outlined.QueryStats,
                        label = stringResource(R.string.statistics),
                        isSelected = false,
                        onClick = {
                            navController.navigate(Screens.StatisticsScreen.route)
                        }
                    )
                    Spacer(Modifier.height(16.dp))
                }
                item {
                    NavigationItem(
                        icon = Icons.Outlined.FolderCopy,
                        label = stringResource(R.string.shelves),
                        isSelected = false,
                        onClick = {
                            navController.navigate(Screens.ShelvesScreen.route)
                        }
                    )
                    Spacer(Modifier.height(16.dp))
                }
                item {
                    NavigationItem(
                        icon = Icons.AutoMirrored.Outlined.StickyNote2,
                        label = stringResource(R.string.notes),
                        isSelected = false,
                        onClick = {
                            navController.navigate(Screens.NotesScreen.route)
                        }
                    )
                    Spacer(Modifier.height(16.dp))
                }
                item {
                    NavigationItem(
                        icon = Icons.Outlined.BorderColor,
                        label = stringResource(R.string.highlights_underlines),
                        isSelected = false,
                        onClick = {
                            navController.navigate(Screens.AnnotationsScreen.route)
                        }
                    )
                    Spacer(Modifier.height(16.dp))
                }
                item {
                    NavigationItem(
                        icon = Icons.Outlined.Settings,
                        label = stringResource(R.string.settings),
                        isSelected = false,
                        onClick = {
                            navController.navigate(Screens.SettingsScreen.route)
                        }
                    )
                }
            }
        }
//    }
}
