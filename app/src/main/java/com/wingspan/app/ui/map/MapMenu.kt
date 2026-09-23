package com.wingspan.app.ui.map

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun MapMenu(
    onSettings: () -> Unit,
    onOffline: () -> Unit,
    onSnapshots: () -> Unit,
    onExportZones: () -> Unit,
    onImportZones: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.statusBarsPadding()) {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Settings") },
                onClick = {
                    expanded = false
                    onSettings()
                },
            )
            DropdownMenuItem(
                text = { Text("Offline maps") },
                onClick = {
                    expanded = false
                    onOffline()
                },
            )
            DropdownMenuItem(
                text = { Text("Snapshots") },
                onClick = {
                    expanded = false
                    onSnapshots()
                },
            )
            DropdownMenuItem(
                text = { Text("Export zones (GeoJSON)") },
                onClick = {
                    expanded = false
                    onExportZones()
                },
            )
            DropdownMenuItem(
                text = { Text("Import zones (GeoJSON)") },
                onClick = {
                    expanded = false
                    onImportZones()
                },
            )
        }
    }
}
