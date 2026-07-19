package com.wxn.reader.presentation.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.wxn.reader.R
import com.wxn.reader.data.model.AppPreferences
import com.wxn.reader.domain.model.Statistics
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.navigation.Screens
import com.wxn.reader.presentation.statistics.components.ReadingGraph
import com.wxn.reader.presentation.statistics.components.ReadingHeatmap
import com.wxn.reader.presentation.statistics.components.StatColumn
import com.wxn.reader.presentation.statistics.components.parseReadingTime
import com.wxn.reader.presentation.sharedComponents.AppTopAppBar
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val statistics by viewModel.statistics.collectAsStateWithLifecycle()
    val appPreferences by viewModel.appPreferences.collectAsStateWithLifecycle()

    when (appPreferences) {
        null -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        is AppPreferences -> {
            StatisticsContent(appPreferences!!, statistics, viewModel.heatmapWindowStart)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsContent(
    appPreferences: AppPreferences,
    statistics: Statistics,
    windowStartMillis: Long,
) {
    val navController: NavHostController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val resources = LocalContext.current.resources

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                AppTopAppBar(
                    title = { Text(stringResource(R.string.statistics)) },
                    navigationIcon = {
                        IconButton(onClick = {
                            scope.launch {
                                navController.popBackStack()
                            }
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .then(
                        if (appPreferences.isPremium) Modifier.verticalScroll(
                            rememberScrollState()
                        ) else Modifier
                    )
                    .padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {


                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 4.dp
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.books),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            StatColumn(
                                title = stringResource(R.string.stat_total),
                                value = statistics.totalBooks.toString()
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatColumn(
                                title = stringResource(R.string.stat_read),
                                value = statistics.booksRead.toString()
                            )
                            StatColumn(
                                title = stringResource(R.string.in_progress),
                                value = statistics.booksInProgress.toString()
                            )
                            StatColumn(
                                title = stringResource(R.string.to_read),
                                value = statistics.booksToRead.toString()
                            )
                        }
                    }
                }


                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 4.dp
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.books_read),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            StatColumn(
                                title = stringResource(R.string.stat_total),
                                value = statistics.booksRead.toString()
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatColumn(
                                title = stringResource(R.string.this_year),
                                value = statistics.booksReadThisYear.toString()
                            )
                            StatColumn(
                                title = stringResource(R.string.this_month),
                                value = statistics.booksReadThisMonth.toString()
                            )
                        }
                    }
                }


                // Average Rating Card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 4.dp
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.ratings),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatColumn(
                                title = stringResource(R.string.rated_books),
                                value = statistics.ratedBooks.toString()
                            )
                            StatColumn(
                                titleStyle = MaterialTheme.typography.bodyMedium,
                                title = stringResource(R.string.average_rating),
                                value = String.format(
                                    Locale.getDefault(),
                                    "%.1f",
                                    statistics.averageRating
                                )
                            )
                        }
                    }
                }




                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 4.dp
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.annotations),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            StatColumn(
                                title = stringResource(R.string.stat_total),
                                value = (statistics.totalNotes + statistics.totalHighlights + statistics.totalUnderlines).toString()
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatColumn(
                                title = stringResource(R.string.notes),
                                value = statistics.totalNotes.toString()
                            )
                            StatColumn(
                                title = stringResource(R.string.highlights),
                                value = statistics.totalHighlights.toString()
                            )
                            StatColumn(
                                title = stringResource(R.string.underlines),
                                value = statistics.totalUnderlines.toString()
                            )
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 4.dp
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.authors),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.favorite_authors),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = stringResource(R.string.author))
                            Text(text = stringResource(R.string.books))
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            thickness = 2.dp
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                statistics.favoriteAuthors.take(5).forEach { author ->
                                    Text(
                                        text = author.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .padding(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                statistics.favoriteAuthors.take(5).forEach { author ->
                                    Text(
                                        text = author.books.size.toString(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                    }
                }


                //Reading habits
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 4.dp
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.reading_times),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.total_reading_time),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val totalTime = parseReadingTime(statistics.totalReadingTime)
                            StatColumn(
                                title = stringResource(R.string.hours),
                                titleStyle = MaterialTheme.typography.bodyMedium,
                                value = totalTime.hours.toString()
                            )
                            StatColumn(
                                title = stringResource(R.string.minutes),
                                titleStyle = MaterialTheme.typography.bodyMedium,
                                value = totalTime.minutes.toString()
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.average_reading_time_per_book),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val averageTime = parseReadingTime(statistics.averageReadingTimePerBook)
                            StatColumn(
                                title = stringResource(R.string.hours),
                                titleStyle = MaterialTheme.typography.bodyMedium,
                                value = averageTime.hours.toString()
                            )
                            StatColumn(
                                title = stringResource(R.string.minutes),
                                titleStyle = MaterialTheme.typography.bodyMedium,
                                value = averageTime.minutes.toString()
                            )
                        }


                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.daily_reading_time),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val dailyTime =
                                parseReadingTime(statistics.averageDailyReadingTime)
                            StatColumn(
                                title = stringResource(R.string.hours),
                                titleStyle = MaterialTheme.typography.bodyMedium,
                                value = dailyTime.hours.toString()
                            )
                            StatColumn(
                                title = stringResource(R.string.minutes),
                                titleStyle = MaterialTheme.typography.bodyMedium,
                                value = dailyTime.minutes.toString()
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.reading_graph),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        ReadingGraph(readingActivities = statistics.readingActivities)

                    }
                }


                //                //Reading heatmap
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 4.dp
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.reading_habits),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatColumn(
                                title = stringResource(R.string.longest_streak),
                                titleStyle = MaterialTheme.typography.bodyMedium,
                                value = resources.getQuantityString(
                                    R.plurals.days_count,
                                    statistics.longestReadingStreak,
                                    statistics.longestReadingStreak
                                )
                            )
                            StatColumn(
                                title = stringResource(R.string.current_streak),
                                titleStyle = MaterialTheme.typography.bodyMedium,
                                value = resources.getQuantityString(
                                    R.plurals.days_count,
                                    statistics.currentReadingStreak,
                                    statistics.currentReadingStreak
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        ReadingHeatmap(
                            readingActivities = statistics.readingActivities,
                            windowStartMillis = windowStartMillis,
                        )
                    }
                }
            }
        }

        if (!appPreferences.isPremium) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 1f)
                            )
                        )
                    )
                    .zIndex(10f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.unlock_premium),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            navController.navigate(Screens.PremiumScreen.route)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(text = stringResource(R.string.unlock_premium))
                    }
                }
            }
        }
    }
}