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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
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
private const val PRACTICE_CATEGORY = "Practice"

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
        RunDetail(
            run = current,
            onBack = { selectedRun = null },
            onDelete = viewModel::deleteRun,
            modifier = modifier
        )
    } else {
        RunList(runs = runs, onSelect = { selectedRun = it }, modifier = modifier)
    }
}

@Composable
private fun RunList(runs: List<RunEntity>, onSelect: (RunEntity) -> Unit, modifier: Modifier = Modifier) {
    // null selection = "All". Categories reflect what's actually in the data, not a hardcoded
    // drill list, so this still makes sense if DrillLibrary changes later.
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    val categories = remember(runs) {
        listOf(null) + runs.mapNotNull { it.drillName }.distinct().sorted() + PRACTICE_CATEGORY
    }
    val filteredRuns = remember(runs, selectedCategory) {
        when (selectedCategory) {
            null -> runs
            PRACTICE_CATEGORY -> runs.filter { it.drillName == null }
            else -> runs.filter { it.drillName == selectedCategory }
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "History", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        if (runs.isEmpty()) {
            Text(text = "No runs yet - completed runs from the Timer tab will show up here")
            return
        }

        CategoryFilterRow(categories = categories, selected = selectedCategory, onSelect = { selectedCategory = it })
        Spacer(Modifier.height(8.dp))

        LazyColumn {
            items(filteredRuns, key = { it.id }) { run ->
                RunRow(run = run, onClick = { onSelect(run) })
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFilterRow(categories: List<String?>, selected: String?, onSelect: (String?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(categories) { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelect(category) },
                label = { Text(category ?: "All") }
            )
        }
    }
}

@Composable
private fun RunRow(run: RunEntity, onClick: () -> Unit) {
    val detail = "${run.shotTimestampsMillis.size} shots" +
        (run.drillName?.let { "  ·  $it" } ?: "") +
        (run.parTimeSeconds?.let { "  ·  Par %.1fs".format(it) } ?: "")

    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        headlineContent = { Text(text = formatTimestamp(run.timestampEpochMillis)) },
        supportingContent = { Text(text = detail) },
        trailingContent = {
            Text(text = formatElapsed(run.totalElapsedMillis), style = MaterialTheme.typography.titleMedium)
        }
    )
}

@Composable
private fun RunDetail(
    run: RunEntity,
    onBack: () -> Unit,
    onDelete: (RunEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("< Back") }
            TextButton(onClick = { showDeleteConfirm = true }) {
                Text("Delete run", color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (run.drillName != null) {
            Text(text = run.drillName, style = MaterialTheme.typography.titleSmall)
        }
        Text(text = formatTimestamp(run.timestampEpochMillis), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        RunSummaryView(
            totalElapsedMillis = run.totalElapsedMillis,
            shotTimestampsMillis = run.shotTimestampsMillis,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this run?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete(run)
                    onBack()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
