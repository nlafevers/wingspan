package com.wingspan.app.ui.report

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wingspan.app.domain.geo.Geometry2D
import com.wingspan.app.domain.report.RangeReport
import com.wingspan.app.ui.Formatters

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportControls(
    report: RangeReport,
    activePositionId: Long?,
    canAddPosition: Boolean,
    onAddPosition: () -> Unit,
    onSelectPosition: (Long) -> Unit,
    onEditShot: (Long, Int) -> Unit,
    onClear: () -> Unit,
    onExport: () -> Unit,
    onExit: () -> Unit,
) {
    val activePosition = report.positions.find { it.id == activePositionId }

    Surface(tonalElevation = 4.dp) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text("${report.positions.size} positions - ${report.totalShots} shots")
            Text("Tap the map to record a shot direction from the selected position")

            Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp)) {
                report.positions.forEachIndexed { index, position ->
                    FilterChip(
                        selected = position.id == activePositionId,
                        onClick = { onSelectPosition(position.id) },
                        label = { Text("P${index + 1}") },
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
            }

            if (activePosition != null) {
                FlowRow(modifier = Modifier.padding(top = 4.dp)) {
                    activePosition.shots.forEachIndexed { shotIndex, shot ->
                        val magneticDeg = Geometry2D.normalizeBearing(
                            shot.bearingTrueDeg - activePosition.declinationDeg
                        )
                        AssistChip(
                            onClick = { onEditShot(activePosition.id, shotIndex) },
                            label = { Text(Formatters.bearing(magneticDeg)) },
                            modifier = Modifier.padding(end = 4.dp, top = 4.dp),
                        )
                    }
                }
            }

            Row(modifier = Modifier.padding(top = 4.dp)) {
                TextButton(onClick = onAddPosition, enabled = canAddPosition) {
                    Text("Add position")
                }
                TextButton(onClick = onClear) {
                    Text("Clear")
                }
                TextButton(onClick = onExport, enabled = report.positions.isNotEmpty()) {
                    Text("Export PDF")
                }
                TextButton(onClick = onExit) {
                    Text("Exit")
                }
            }
        }
    }
}
