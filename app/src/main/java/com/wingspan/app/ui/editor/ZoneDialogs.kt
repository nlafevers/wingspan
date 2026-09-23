package com.wingspan.app.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wingspan.app.domain.geo.NoFireLine
import com.wingspan.app.domain.geo.NoFireMarker
import com.wingspan.app.domain.geo.NoFirePolygon
import com.wingspan.app.domain.geo.NoFireZone

@Composable
fun ZoneNameDialog(
    initialName: String,
    initialRadiusM: Double?,
    radiusLabel: String = "Radius (m)",
    onConfirm: (name: String, radiusM: Double?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var radiusText by remember { mutableStateOf(initialRadiusM?.toString().orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Name zone") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                if (initialRadiusM != null) {
                    OutlinedTextField(
                        value = radiusText,
                        onValueChange = { radiusText = it },
                        label = { Text(radiusLabel) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val resolvedName = name.ifBlank { "Zone" }
                val resolvedRadius = if (initialRadiusM != null) {
                    radiusText.toDoubleOrNull() ?: initialRadiusM
                } else {
                    null
                }
                onConfirm(resolvedName, resolvedRadius)
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneInfoSheet(
    zone: NoFireZone,
    onEditShape: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val name = when (zone) {
        is NoFirePolygon -> zone.name
        is NoFireMarker -> zone.name
        is NoFireLine -> zone.name
    }
    val typeLabel = when (zone) {
        is NoFirePolygon -> "Polygon, ${zone.vertices.size} vertices"
        is NoFireMarker -> "Marker, radius ${formatMeters(zone.radiusM)} m"
        is NoFireLine -> "Line, ${zone.vertices.size} vertices, buffer ${formatMeters(zone.bufferM)} m"
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(typeLabel, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
            Button(onClick = onEditShape, modifier = Modifier.fillMaxWidth()) {
                Text("Edit shape")
            }
            OutlinedButton(
                onClick = onRename,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text("Rename")
            }
            OutlinedButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text("Delete")
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete zone?") },
            text = { Text("This will permanently delete \"$name\".") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

private fun formatMeters(value: Double): String {
    return if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
}
