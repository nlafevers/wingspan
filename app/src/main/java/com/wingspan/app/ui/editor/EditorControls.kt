package com.wingspan.app.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.wingspan.app.ui.editor.EditorViewModel.EditorMode

@Composable
fun EditorControls(
    mode: EditorMode,
    canFinish: Boolean,
    onUndo: () -> Unit,
    onDeletePoint: () -> Unit,
    onCancel: () -> Unit,
    onFinish: () -> Unit,
    onRadiusChange: (Double) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            val guidance = when (mode) {
                is EditorMode.PolygonEdit -> "Tap to add points, drag handles to adjust"
                is EditorMode.MarkerEdit -> "Tap to place the marker, drag to adjust"
                is EditorMode.LineEdit -> "Tap to add points along the line, drag handles to adjust"
                is EditorMode.Idle -> ""
            }
            Text(guidance, style = MaterialTheme.typography.bodySmall)

            Row(
                modifier = Modifier.padding(top = 8.dp),
            ) {
                val verticesNonEmpty = when (mode) {
                    is EditorMode.PolygonEdit -> mode.vertices.isNotEmpty()
                    is EditorMode.LineEdit -> mode.vertices.isNotEmpty()
                    else -> false
                }
                val hasSelectedPoint = when (mode) {
                    is EditorMode.PolygonEdit -> mode.selectedIndex != null && mode.vertices.isNotEmpty()
                    is EditorMode.LineEdit -> mode.selectedIndex != null && mode.vertices.isNotEmpty()
                    else -> false
                }

                if (mode is EditorMode.PolygonEdit || mode is EditorMode.LineEdit) {
                    TextButton(onClick = onUndo, enabled = verticesNonEmpty) {
                        Text("Undo")
                    }
                    TextButton(onClick = onDeletePoint, enabled = hasSelectedPoint) {
                        Text("Delete point")
                    }
                }

                when (mode) {
                    is EditorMode.MarkerEdit -> {
                        var radiusText by remember { mutableStateOf(mode.radiusM.toString()) }
                        OutlinedTextField(
                            value = radiusText,
                            onValueChange = { text ->
                                radiusText = text
                                text.toDoubleOrNull()?.let { onRadiusChange(it) }
                            },
                            label = { Text("Radius") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(100.dp),
                        )
                    }
                    is EditorMode.LineEdit -> {
                        var bufferText by remember { mutableStateOf(mode.bufferM.toString()) }
                        OutlinedTextField(
                            value = bufferText,
                            onValueChange = { text ->
                                bufferText = text
                                text.toDoubleOrNull()?.let { onRadiusChange(it) }
                            },
                            label = { Text("Buffer") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(100.dp),
                        )
                    }
                    else -> Unit
                }

                TextButton(onClick = onCancel) {
                    Text("Cancel")
                }
                Button(onClick = onFinish, enabled = canFinish) {
                    Text("Finish")
                }
            }
        }
    }
}
