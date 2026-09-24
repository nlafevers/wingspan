package com.wingspan.app.ui.snapshots

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wingspan.app.appContainer
import com.wingspan.app.data.FiringSnapshot
import com.wingspan.app.ui.Formatters
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnapshotDetailScreen(
    id: Long,
    onBack: () -> Unit,
    onShowOnMap: (Long) -> Unit,
    viewModel: SnapshotsViewModel = viewModel(
        factory = SnapshotsViewModel.factory(LocalContext.current.appContainer())
    ),
) {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf<FiringSnapshot?>(null) }
    var notesText by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        val loaded = viewModel.get(id)
        snapshot = loaded
        notesText = loaded?.notes.orEmpty()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Snapshot") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val current = snapshot
        if (current == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Loading…")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val bitmap = remember(current.imageFile) {
                    BitmapFactory.decodeFile(current.imageFile.path)?.asImageBitmap()
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Snapshot image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }

                val units = current.settings.unitSystem
                val sourceLabel = if (current.positionSource.equals("GPS", ignoreCase = true)) "GPS" else "manual"

                DetailRow("Date", DateFormat.getDateTimeInstance().format(Date(current.timestampMs)))
                DetailRow(
                    "Position",
                    "%.5f".format(current.position.lat) + ", " +
                        "%.5f".format(current.position.lon) + " ($sourceLabel)",
                )
                DetailRow(
                    "Load",
                    "${current.settings.shotSize.label} ${current.settings.material.label}, " +
                        "${Formatters.velocity(current.settings.muzzleVelocityFps, units)}, " +
                        current.settings.choke.label,
                )
                DetailRow(
                    "Range",
                    "Max ${Formatters.distance(current.maxRangeM, units)} · " +
                        "Effective ${Formatters.distance(current.effectiveRangeM, units)}",
                )
                DetailRow("Fans (magnetic)", viewModel.fansText(current.fans))
                DetailRow("Declination", Formatters.angle(current.declinationDeg))

                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = { viewModel.updateNotes(current.id, notesText) }) {
                    Text("Save notes")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = { onShowOnMap(current.id) }) {
                        Text("Show on map")
                    }
                    OutlinedButton(onClick = { SnapshotShare.share(context, current, viewModel.summaryText(current)) }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                        Text("Share")
                    }
                    OutlinedButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        Text("Delete")
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        val toDelete = snapshot
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete snapshot?") },
            text = { Text("This will remove the snapshot and its image from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    toDelete?.let { viewModel.delete(it.id) }
                    onBack()
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

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.fillMaxWidth(0.4f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
