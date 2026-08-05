package com.shottimer.app.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shottimer.app.util.formatElapsed

/**
 * Reusable "here's what happened in this run" view: usable both for a run still in progress
 * (partial results, keeps updating) and a finished/saved run (from history, in a later milestone).
 */
@Composable
fun RunSummaryView(
    totalElapsedMillis: Long,
    shotTimestampsMillis: List<Long>,
    modifier: Modifier = Modifier
) {
    val metrics = computeRunMetrics(totalElapsedMillis, shotTimestampsMillis)

    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            SummaryStat(label = "First Shot", value = metrics.firstShotMillis?.let(::formatElapsed) ?: "--")
            SummaryStat(label = "Total Time", value = formatElapsed(metrics.totalElapsedMillis))
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text(text = "Shots (${metrics.splits.size})", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        if (metrics.splits.isEmpty()) {
            Text(text = "No shots detected yet")
        } else {
            metrics.splits.forEach { split ->
                Text(
                    text = "Shot ${split.shotNumber}: ${formatElapsed(split.elapsedMillis)}" +
                        "  (+${formatElapsed(split.splitMillis)})"
                )
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Text(text = value, style = MaterialTheme.typography.headlineSmall)
    }
}
