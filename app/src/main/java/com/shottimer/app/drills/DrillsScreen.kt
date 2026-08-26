package com.shottimer.app.drills

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shottimer.app.R
import com.shottimer.app.ui.ScreenScaffold

@Composable
fun DrillsScreen(
    onStartDrill: (Drill) -> Unit,
    modifier: Modifier = Modifier
) {
    ScreenScaffold(title = stringResource(R.string.tab_drills), modifier = modifier) {
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
        }
    }
}

@Composable
private fun DrillCard(drill: Drill, onStart: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = drill.name, style = MaterialTheme.typography.titleSmall)
            Text(text = drill.summary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Text(text = drill.instructions, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text(text = stringResource(R.string.drill_rounds, drill.roundCount), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onStart) { Text(stringResource(R.string.start_this_drill)) }
        }
    }
}
