package com.wingspan.app.ui.snapshots

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnapshotsScreen(
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    viewModel: SnapshotsViewModel = viewModel(
        factory = SnapshotsViewModel.factory(LocalContext.current.appContainer())
    ),
) {
    val snapshots by viewModel.snapshots.collectAsState()
    val context = LocalContext.current
    val resolver = context.contentResolver
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let {
            scope.launch {
                viewModel.exportAll(resolver, it)
                Toast.makeText(context, "Snapshots exported", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Snapshots") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { exportLauncher.launch(SnapshotExport.suggestedName()) },
                        enabled = snapshots.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Export all")
                    }
                },
            )
        },
    ) { padding ->
        if (snapshots.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("No snapshots yet. Use the star button on the map.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(snapshots, key = { it.id }) { snapshot ->
                    SnapshotCard(
                        snapshot = snapshot,
                        onClick = { onOpen(snapshot.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SnapshotCard(
    snapshot: FiringSnapshot,
    onClick: () -> Unit,
) {
    val bitmap = remember(snapshot.imageFile) {
        BitmapFactory.decodeFile(snapshot.imageFile.path)?.asImageBitmap()
    }
    val loadSummary = "${snapshot.settings.shotSize.label} ${snapshot.settings.material.label}, " +
        "${Formatters.velocity(snapshot.settings.muzzleVelocityFps, snapshot.settings.unitSystem)}, " +
        snapshot.settings.choke.label

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Snapshot thumbnail",
                    modifier = Modifier.size(96.dp),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    DateFormat.getDateTimeInstance().format(Date(snapshot.timestampMs)),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(loadSummary, style = MaterialTheme.typography.bodySmall)
                val firstNoteLine = snapshot.notes.lineSequence().firstOrNull { it.isNotBlank() }
                if (firstNoteLine != null) {
                    Text(firstNoteLine, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
