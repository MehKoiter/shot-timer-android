package com.shottimer.app.shooters

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    val recentTimes by viewModel.recentTimes.collectAsStateWithLifecycle()
    var editingShooter by remember { mutableStateOf<String?>(null) }

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
                        ShooterCard(
                            stats = shooter,
                            recentTimes = recentTimes[shooter.shooterName].orEmpty(),
                            onClick = { onShooterClick(shooter.shooterName) },
                            onEdit = { editingShooter = shooter.shooterName }
                        )
                    }
                }
            }
        }
    }

    editingShooter?.let { name ->
        EditShooterDialog(
            shooterName = name,
            onRename = { newName ->
                viewModel.renameShooter(name, newName)
                editingShooter = null
            },
            onRemove = {
                viewModel.removeShooter(name)
                editingShooter = null
            },
            onDismiss = { editingShooter = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShooterCard(
    stats: ShooterStats,
    recentTimes: List<Long>,
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = stats.shooterName, style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_shooter))
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatColumn(label = stringResource(R.string.stat_runs), value = stats.runCount.toString())
                StatColumn(label = stringResource(R.string.stat_best), value = formatElapsed(stats.bestTimeMillis))
                StatColumn(label = stringResource(R.string.stat_average), value = formatElapsed(stats.avgTimeMillis.roundToLong()))
            }
            // A trend needs at least two points; single-run shooters just show their stats.
            if (recentTimes.size >= 2) {
                Spacer(Modifier.height(12.dp))
                TrendSparkline(
                    times = recentTimes,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                )
            }
        }
    }
}

/**
 * Total run time over the shooter's last few runs, oldest to newest - downward slope means
 * they're getting faster. Deliberately minimal (no axes, grid, or labels): the Best/Average
 * numbers directly above carry the exact values, the line carries only the direction.
 */
@Composable
private fun TrendSparkline(times: List<Long>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val endpointRing = MaterialTheme.colorScheme.surfaceContainerHigh
    val description = stringResource(R.string.trend_desc, times.size)

    Canvas(modifier = modifier.semantics { contentDescription = description }) {
        val min = times.min()
        val max = times.max()
        // Flat-line the chart mid-height when all runs are identical instead of dividing by zero.
        val range = (max - min).coerceAtLeast(1L).toFloat()
        // Inset so the 2dp stroke and the endpoint dot aren't clipped at the edges.
        val inset = 5.dp.toPx()
        val w = size.width - inset * 2
        val h = size.height - inset * 2

        fun pointAt(index: Int): Offset {
            val x = inset + w * index / (times.size - 1).toFloat()
            val y = inset + h * (times[index] - min) / range
            return Offset(x, y)
        }

        val path = Path()
        times.indices.forEach { i ->
            val p = pointAt(i)
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
        // Emphasized endpoint: the latest run, ringed with the surface color so it reads as a
        // marker sitting on the line rather than a blob in it.
        val last = pointAt(times.size - 1)
        drawCircle(color = endpointRing, radius = 5.dp.toPx(), center = last)
        drawCircle(color = lineColor, radius = 3.5f.dp.toPx(), center = last)
    }
}

@Composable
private fun EditShooterDialog(
    shooterName: String,
    onRename: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(shooterName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_shooter)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.shooter_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.edit_shooter_note),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onRemove) {
                    Text(stringResource(R.string.remove_shooter), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onRename(text) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        Text(text = value, style = MaterialTheme.typography.titleMedium)
    }
}
