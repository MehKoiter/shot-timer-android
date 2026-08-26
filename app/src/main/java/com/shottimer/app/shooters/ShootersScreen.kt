package com.shottimer.app.shooters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shottimer.app.R
import com.shottimer.app.data.ShooterStats
import com.shottimer.app.ui.ScreenScaffold
import com.shottimer.app.util.formatElapsed
import kotlin.math.roundToLong

/** Aggregate stats per shooter - there's no separate "add a shooter" step, a shooter's profile
 * simply comes into existence the first time you tag a run with their name on the Timer tab
 * (see [com.shottimer.app.data.RunDao.observeShooterStats]). */
@Composable
fun ShootersScreen(
    onShooterClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ShootersViewModel = viewModel()
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()

    ScreenScaffold(title = stringResource(R.string.tab_shooters), modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Text(
                text = stringResource(R.string.shooters_desc),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(16.dp))

            if (stats.isEmpty()) {
                Text(text = stringResource(R.string.no_shooters_yet))
            } else {
                LazyColumn {
                    items(stats, key = { it.shooterName }) { shooter ->
                        ShooterCard(stats = shooter, onClick = { onShooterClick(shooter.shooterName) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShooterCard(stats: ShooterStats, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = stats.shooterName, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatColumn(label = stringResource(R.string.stat_runs), value = stats.runCount.toString())
                StatColumn(label = stringResource(R.string.stat_best), value = formatElapsed(stats.bestTimeMillis))
                StatColumn(label = stringResource(R.string.stat_average), value = formatElapsed(stats.avgTimeMillis.roundToLong()))
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        Text(text = value, style = MaterialTheme.typography.titleMedium)
    }
}
