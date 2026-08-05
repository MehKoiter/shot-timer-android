package com.shottimer.app.history

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shottimer.app.data.RunEntity
import com.shottimer.app.results.RunSummaryView
import com.shottimer.app.util.formatElapsed
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("MMM d, h:mm a")

private fun formatTimestamp(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(TIMESTAMP_FORMATTER)

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = viewModel()
) {
    val runs by viewModel.runs.collectAsStateWithLifecycle()
    var selectedRun by remember { mutableStateOf<RunEntity?>(null) }

    val current = selectedRun
    if (current != null) {
        RunDetail(run = current, onBack = { selectedRun = null }, modifier = modifier)
    } else {
        RunList(runs = runs, onSelect = { selectedRun = it }, modifier = modifier)
    }
}

@Composable
private fun RunList(runs: List<RunEntity>, onSelect: (RunEntity) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "History", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        if (runs.isEmpty()) {
            Text(text = "No runs yet - completed runs from the Timer tab will show up here")
        } else {
            LazyColumn {
                items(runs, key = { it.id }) { run ->
                    RunRow(run = run, onClick = { onSelect(run) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun RunRow(run: RunEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(text = formatTimestamp(run.timestampEpochMillis))
            val detail = "${run.shotTimestampsMillis.size} shots" +
                (run.parTimeSeconds?.let { "  ·  Par %.1fs".format(it) } ?: "")
            Text(text = detail, style = MaterialTheme.typography.bodySmall)
        }
        Text(text = formatElapsed(run.totalElapsedMillis), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun RunDetail(run: RunEntity, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        TextButton(onClick = onBack) { Text("< Back") }
        Spacer(Modifier.height(8.dp))
        Text(text = formatTimestamp(run.timestampEpochMillis), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        RunSummaryView(
            totalElapsedMillis = run.totalElapsedMillis,
            shotTimestampsMillis = run.shotTimestampsMillis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
