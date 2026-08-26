package com.shottimer.app.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.shottimer.app.R
import com.shottimer.app.data.RunEntity
import com.shottimer.app.results.computeRunMetrics
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val SHARE_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("MMM d yyyy, h:mm a")
private val CSV_TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME

/** Human-readable single-run summary for the system share sheet (text messages, notes, etc.). */
fun buildRunShareText(context: Context, run: RunEntity): String {
    val metrics = computeRunMetrics(run.totalElapsedMillis, run.shotTimestampsMillis)
    val timestamp = Instant.ofEpochMilli(run.timestampEpochMillis)
        .atZone(ZoneId.systemDefault()).format(SHARE_TIMESTAMP_FORMATTER)

    return buildString {
        appendLine(context.getString(R.string.app_name) + (run.drillName?.let { " - $it" } ?: ""))
        appendLine(timestamp)
        run.shooterName?.let { appendLine(context.getString(R.string.share_shooter, it)) }
        run.parTimeSeconds?.let { appendLine(context.getString(R.string.share_par, it)) }
        metrics.firstShotMillis?.let {
            appendLine(context.getString(R.string.share_first_shot, formatElapsed(it)))
        }
        appendLine(
            context.resources.getQuantityString(
                R.plurals.share_total,
                metrics.splits.size,
                formatElapsed(metrics.totalElapsedMillis),
                metrics.splits.size
            )
        )
        if (metrics.splits.isNotEmpty()) {
            appendLine(
                context.getString(
                    R.string.share_splits,
                    metrics.splits.joinToString(", ") { formatElapsed(it.splitMillis) }
                )
            )
        }
    }.trimEnd()
}

fun shareRunAsText(context: Context, run: RunEntity) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, buildRunShareText(context, run))
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_run)))
}

/**
 * Writes every run to a CSV in the app's cache (shot splits semicolon-joined inside the one
 * quoted cell) and hands it to the share sheet via FileProvider - the file never needs storage
 * permissions and the cache copy is fair game for the OS to clean up later.
 */
fun exportRunsAsCsv(context: Context, runs: List<RunEntity>) {
    val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(exportsDir, "shot-timer-runs.csv")

    file.bufferedWriter().use { writer ->
        writer.appendLine("timestamp,drill,shooter,par_seconds,total_millis,shot_count,shot_timestamps_millis")
        runs.forEach { run ->
            val timestamp = Instant.ofEpochMilli(run.timestampEpochMillis)
                .atZone(ZoneId.systemDefault()).format(CSV_TIMESTAMP_FORMATTER)
            writer.appendLine(
                listOf(
                    timestamp,
                    csvCell(run.drillName),
                    csvCell(run.shooterName),
                    run.parTimeSeconds?.toString() ?: "",
                    run.totalElapsedMillis.toString(),
                    run.shotTimestampsMillis.size.toString(),
                    run.shotTimestampsMillis.joinToString(";")
                ).joinToString(",")
            )
        }
    }

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.export_csv)))
}

/** Quote a free-text cell and escape embedded quotes, per RFC 4180. */
private fun csvCell(value: String?): String =
    value?.let { "\"" + it.replace("\"", "\"\"") + "\"" } ?: ""
