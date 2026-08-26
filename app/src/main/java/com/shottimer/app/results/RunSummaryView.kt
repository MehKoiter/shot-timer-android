package com.shottimer.app.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shottimer.app.R
import com.shottimer.app.util.formatElapsed

/**
 * Reusable "here's what happened in this run" view: usable both for a run still in progress
 * (partial results, keeps updating) and a finished/saved run from history.
 */
@Composable
fun RunSummaryView(
    totalElapsedMillis: Long,
    shotTimestampsMillis: List<Long>,
    modifier: Modifier = Modifier,
    expectedRoundCount: Int? = null
) {
    val metrics = computeRunMetrics(totalElapsedMillis, shotTimestampsMillis)

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                SummaryStat(
                    label = stringResource(R.string.first_shot),
                    value = metrics.firstShotMillis?.let(::formatElapsed) ?: "--"
                )
                SummaryStat(
                    label = stringResource(R.string.total_time),
                    value = formatElapsed(metrics.totalElapsedMillis)
                )
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            val shotsTitle = if (expectedRoundCount != null) {
                stringResource(R.string.shots_count_expected, metrics.splits.size, expectedRoundCount)
            } else {
                stringResource(R.string.shots_count, metrics.splits.size)
            }
            Text(text = shotsTitle, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            if (metrics.splits.isEmpty()) {
                Text(text = stringResource(R.string.no_shots_yet), style = MaterialTheme.typography.bodyMedium)
            } else {
                metrics.splits.forEach { split ->
                    ShotRow(split)
                }
            }
        }
    }
}

@Composable
private fun ShotRow(split: ShotSplit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = stringResource(R.string.shot_n, split.shotNumber))
        Text(text = formatElapsed(split.elapsedMillis))
        Text(
            text = "+${formatElapsed(split.splitMillis)}",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun SummaryStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Text(text = value, style = MaterialTheme.typography.headlineSmall)
    }
}
