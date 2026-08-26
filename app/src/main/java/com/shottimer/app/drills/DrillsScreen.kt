package com.shottimer.app.drills

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shottimer.app.R
import com.shottimer.app.data.CustomDrillEntity
import com.shottimer.app.ui.ScreenScaffold

@Composable
fun DrillsScreen(
    onStartDrill: (Drill) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DrillsViewModel = viewModel()
) {
    val customDrills by viewModel.customDrills.collectAsStateWithLifecycle()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    ScreenScaffold(
        title = stringResource(R.string.tab_drills),
        modifier = modifier,
        actions = {
            IconButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_drill))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.drills_desc),
                style = MaterialTheme.typography.bodySmall
            )

            DrillLibrary.ALL.forEach { drill ->
                DrillCard(drill = drill, onStart = { onStartDrill(drill) })
            }

            customDrills.forEach { entity ->
                CustomDrillCard(
                    entity = entity,
                    onStart = { onStartDrill(entity.toDrill()) },
                    onDelete = { viewModel.deleteDrill(entity) }
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showAddDialog) {
        AddDrillDialog(
            onAdd = { name, rounds, instructions ->
                viewModel.addDrill(name, rounds, instructions)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

/**
 * Collapsed by default - just the drill's name and round count, so the list scans like a menu.
 * Tapping the card expands it in place with the full explanation and the Start button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrillCard(drill: Drill, onStart: () -> Unit) {
    var expanded by rememberSaveable(drill.id) { mutableStateOf(false) }

    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .animateContentSize()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = drill.name, style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.drill_rounds, drill.roundCount),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            if (expanded) {
                Text(text = drill.summary, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Text(text = drill.instructions, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onStart) { Text(stringResource(R.string.start_this_drill)) }
            }
        }
    }
}

/** Same collapsed-by-default pattern as [DrillCard], plus the delete affordance. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomDrillCard(
    entity: CustomDrillEntity,
    onStart: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by rememberSaveable(entity.id) { mutableStateOf(false) }

    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .animateContentSize()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = entity.name, style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.drill_rounds, entity.roundCount),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            Text(text = stringResource(R.string.custom_drill_label), style = MaterialTheme.typography.bodySmall)
            if (expanded) {
                if (entity.instructions.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(text = entity.instructions, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = onStart) { Text(stringResource(R.string.start_this_drill)) }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete_drill),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddDrillDialog(
    onAdd: (name: String, rounds: Int, instructions: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var roundsText by remember { mutableStateOf("") }
    var instructions by remember { mutableStateOf("") }
    val rounds = roundsText.toIntOrNull()
    val valid = name.isNotBlank() && rounds != null && rounds in MIN_DRILL_ROUNDS..MAX_DRILL_ROUNDS

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_drill)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.drill_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = roundsText,
                    onValueChange = { roundsText = it },
                    label = { Text(stringResource(R.string.rounds_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text(stringResource(R.string.instructions_optional)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name, rounds ?: MIN_DRILL_ROUNDS, instructions) },
                enabled = valid
            ) { Text(stringResource(R.string.add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
