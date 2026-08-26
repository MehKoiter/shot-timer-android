package com.shottimer.app.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shottimer.app.R
import com.shottimer.app.data.RunEntity
import com.shottimer.app.results.RunSummaryView
import com.shottimer.app.ui.ScreenScaffold
import com.shottimer.app.util.exportRunsAsCsv
import com.shottimer.app.util.formatElapsed
import com.shottimer.app.util.shareRunAsText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("MMM d, h:mm a")
private const val PRACTICE_CATEGORY = "Practice"
private const val UNASSIGNED_SHOOTER = "Unassigned"

private fun formatTimestamp(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(TIMESTAMP_FORMATTER)

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    initialShooterFilter: String? = null,
    viewModel: HistoryViewModel = viewModel()
) {
    val runs by viewModel.runs.collectAsStateWithLifecycle()
    // The id, not the RunEntity, so the selection survives rotation via rememberSaveable (and
    // resolves against fresh data if the run list changes underneath us - a deleted run's id
    // simply stops resolving and we fall back to the list).
    var selectedRunId by rememberSaveable { mutableStateOf<Long?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Delete immediately and offer Undo via snackbar instead of a confirm dialog - one tap for
    // the common case, and a mistake is recoverable for the few seconds the snackbar shows.
    // Resolved here, not inside the coroutine - showSnackbar runs outside composition.
    val runDeletedMessage = stringResource(R.string.run_deleted)
    val undoLabel = stringResource(R.string.undo)
    val deleteWithUndo: (RunEntity) -> Unit = { run ->
        viewModel.deleteRun(run)
        selectedRunId = null
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = runDeletedMessage,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.restoreRun(run)
            }
        }
    }

    Box(modifier = modifier) {
        val current = selectedRunId?.let { id -> runs.find { it.id == id } }
        if (current != null) {
            RunDetail(
                run = current,
                onBack = { selectedRunId = null },
                onDelete = deleteWithUndo,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            RunList(
                runs = runs,
                onSelect = { selectedRunId = it.id },
                modifier = Modifier.fillMaxSize(),
                initialShooterFilter = initialShooterFilter
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun RunList(
    runs: List<RunEntity>,
    onSelect: (RunEntity) -> Unit,
    modifier: Modifier = Modifier,
    initialShooterFilter: String? = null
) {
    // null selection = "All". Categories reflect what's actually in the data, not a hardcoded
    // drill list, so this still makes sense if DrillLibrary changes later.
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    // Seeded from initialShooterFilter when arriving via a tap on the Shooters tab; the user can
    // still change or clear it like any other chip selection afterwards.
    var selectedShooter by rememberSaveable { mutableStateOf(initialShooterFilter) }
    val categories = remember(runs) {
        listOf(null) + runs.mapNotNull { it.drillName }.distinct().sorted() + PRACTICE_CATEGORY
    }
    val shooters = remember(runs) {
        listOf(null) + runs.mapNotNull { it.shooterName }.distinct().sorted() + UNASSIGNED_SHOOTER
    }
    // Both filters apply together (AND) - e.g. "John's El Presidente runs" is a real, expected query.
    val filteredRuns = remember(runs, selectedCategory, selectedShooter) {
        runs.filter { run ->
            val matchesCategory = when (selectedCategory) {
                null -> true
                PRACTICE_CATEGORY -> run.drillName == null
                else -> run.drillName == selectedCategory
            }
            val matchesShooter = when (selectedShooter) {
                null -> true
                UNASSIGNED_SHOOTER -> run.shooterName == null
                else -> run.shooterName == selectedShooter
            }
            matchesCategory && matchesShooter
        }
    }

    val context = LocalContext.current
    ScreenScaffold(
        title = stringResource(R.string.tab_history),
        modifier = modifier,
        actions = {
            if (runs.isNotEmpty()) {
                IconButton(onClick = { exportRunsAsCsv(context, runs) }) {
                    Icon(Icons.Default.FileDownload, contentDescription = stringResource(R.string.export_csv))
                }
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            if (runs.isEmpty()) {
                Text(text = stringResource(R.string.no_runs_yet))
            } else {
                Text(text = stringResource(R.string.filter_drill), style = MaterialTheme.typography.labelMedium)
                CategoryFilterRow(categories = categories, selected = selectedCategory, onSelect = { selectedCategory = it })
                Spacer(Modifier.height(8.dp))
                Text(text = stringResource(R.string.filter_shooter), style = MaterialTheme.typography.labelMedium)
                CategoryFilterRow(categories = shooters, selected = selectedShooter, onSelect = { selectedShooter = it })
                Spacer(Modifier.height(8.dp))

                LazyColumn {
                    items(filteredRuns, key = { it.id }) { run ->
                        RunRow(run = run, onClick = { onSelect(run) })
                        HorizontalDivider()
                    }
                }
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
                label = {
                    // The sentinel constants stay as internal match keys (they're compared
                    // against DB values); only their display goes through resources.
                    Text(
                        when (category) {
                            null -> stringResource(R.string.filter_all)
                            PRACTICE_CATEGORY -> stringResource(R.string.filter_practice)
                            UNASSIGNED_SHOOTER -> stringResource(R.string.filter_unassigned)
                            else -> category
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun RunRow(run: RunEntity, onClick: () -> Unit) {
    val detail = stringResource(R.string.run_shots, run.shotTimestampsMillis.size) +
        (run.drillName?.let { "  ·  $it" } ?: "") +
        (run.shooterName?.let { "  ·  $it" } ?: "") +
        (run.parTimeSeconds?.let { "  ·  " + stringResource(R.string.par_short, it) } ?: "")

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
    ScreenScaffold(
        title = formatTimestamp(run.timestampEpochMillis),
        modifier = modifier,
        onBack = onBack,
        actions = {
            val context = LocalContext.current
            IconButton(onClick = { shareRunAsText(context, run) }) {
                Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share_run))
            }
            // No confirm dialog: delete is immediate, and the caller shows an Undo snackbar.
            IconButton(onClick = { onDelete(run) }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete_run),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (run.drillName != null) {
                Text(text = run.drillName, style = MaterialTheme.typography.titleSmall)
            }
            if (run.shooterName != null) {
                Text(text = run.shooterName, style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(16.dp))
            RunSummaryView(
                totalElapsedMillis = run.totalElapsedMillis,
                shotTimestampsMillis = run.shotTimestampsMillis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
