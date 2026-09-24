package com.wingspan.app.ui.offline

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wingspan.app.data.map.Basemap
import com.wingspan.app.domain.geo.TileMath
import com.wingspan.app.ui.map.Bounds
import java.text.DateFormat
import java.util.Date
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

private const val TILE_WARNING_THRESHOLD = 20_000
private const val TILE_HARD_LIMIT = 60_000

@Composable
fun DownloadAreaDialog(
    basemap: Basemap,
    bounds: Bounds,
    currentZoom: Double,
    onConfirm: (name: String, minZoom: Int, maxZoom: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val minZoom = remember(currentZoom, basemap) {
        floor(currentZoom).toInt().coerceIn(0, basemap.maxZoom)
    }
    var name by remember {
        mutableStateOf("Area " + DateFormat.getDateInstance().format(Date()))
    }
    var maxZoom by remember(minZoom, basemap) {
        mutableStateOf(min(minZoom + 4, basemap.maxZoom))
    }
    val tileEstimate = remember(bounds, minZoom, maxZoom) {
        TileMath.tileCount(bounds.south, bounds.west, bounds.north, bounds.east, minZoom, maxZoom)
    }
    val overWarningThreshold = tileEstimate > TILE_WARNING_THRESHOLD
    val overHardLimit = tileEstimate > TILE_HARD_LIMIT

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download this area") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                Text(basemap.label, style = MaterialTheme.typography.bodySmall)
                Text("Min zoom: $minZoom", modifier = Modifier.padding(top = 8.dp))
                Text("Max zoom: $maxZoom")
                Slider(
                    value = maxZoom.toFloat(),
                    onValueChange = { maxZoom = it.roundToInt() },
                    valueRange = minZoom.toFloat()..basemap.maxZoom.toFloat(),
                    steps = (basemap.maxZoom - minZoom - 1).coerceAtLeast(0),
                )
                val tileColor = if (overWarningThreshold) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
                Text(
                    "≈ $tileEstimate tiles",
                    color = tileColor,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (overWarningThreshold) {
                    Text(
                        "This is a large download and may take a while.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.ifBlank { "Area" }, minZoom, maxZoom) },
                enabled = !overHardLimit,
            ) {
                Text("Download")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
