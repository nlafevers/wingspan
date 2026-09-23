package com.wingspan.app.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
