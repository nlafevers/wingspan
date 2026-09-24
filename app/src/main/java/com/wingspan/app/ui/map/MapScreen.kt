package com.wingspan.app.ui.map

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt
import com.wingspan.app.appContainer
import com.wingspan.app.data.geojson.GeoJsonCodec
import com.wingspan.app.data.location.LocationProvider
import com.wingspan.app.data.map.Basemap
import com.wingspan.app.domain.geo.LatLon
import com.wingspan.app.ui.Formatters
import com.wingspan.app.ui.editor.EditorControls
import com.wingspan.app.ui.editor.EditorViewModel
import com.wingspan.app.ui.editor.ZoneFileIo
import com.wingspan.app.ui.editor.ZoneInfoSheet
import com.wingspan.app.ui.editor.ZoneNameDialog
import com.wingspan.app.ui.offline.DownloadAreaDialog
import com.wingspan.app.domain.geo.NoFireLine
import com.wingspan.app.domain.geo.NoFireMarker
import com.wingspan.app.domain.geo.NoFirePolygon
import com.wingspan.app.ui.snapshots.SnapshotCapture
import com.wingspan.app.ui.snapshots.SnapshotNotesDialog
import kotlinx.coroutines.launch

@Composable
fun MapScreen(
    snapshotId: Long,
    onOpenSettings: () -> Unit,
    onOpenOffline: () -> Unit,
    onOpenSnapshots: () -> Unit,
    viewModel: MapViewModel = viewModel(factory = MapViewModel.factory(LocalContext.current.appContainer()))
) {
    val context = LocalContext.current
    val resolver = context.contentResolver
    val scope = rememberCoroutineScope()
    val controller = remember { MapController(context.applicationContext) }
    val basemap by viewModel.basemap.collectAsStateWithLifecycle()
    val shooter by viewModel.shooter.collectAsStateWithLifecycle()
    val manualMode by viewModel.manualMode.collectAsStateWithLifecycle()
    val zones by viewModel.zones.collectAsStateWithLifecycle()
    val fanState by viewModel.fanState.collectAsStateWithLifecycle()
    val selectedFanIndex by viewModel.selectedFanIndex.collectAsStateWithLifecycle()
    val viewingSnapshot by viewModel.viewingSnapshot.collectAsStateWithLifecycle()
    var showFanDetail by remember { mutableStateOf(false) }
    LaunchedEffect(selectedFanIndex) { showFanDetail = false }

    val editorViewModel: EditorViewModel = viewModel(
        factory = EditorViewModel.factory(context.appContainer())
    )
    val editorMode by editorViewModel.mode.collectAsStateWithLifecycle()
    val editorRender by editorViewModel.render.collectAsStateWithLifecycle()
    val canFinishEditor by editorViewModel.canFinish.collectAsStateWithLifecycle()
    val showNameDialog by editorViewModel.showNameDialog.collectAsStateWithLifecycle()
    val selectedZone by editorViewModel.selectedZone.collectAsStateWithLifecycle()
    val showRenameDialog by editorViewModel.showRenameDialog.collectAsStateWithLifecycle()
    val pendingImport by editorViewModel.pendingImport.collectAsStateWithLifecycle()
    var addMenuExpanded by remember { mutableStateOf(false) }
    var downloadAreaRequest by remember { mutableStateOf<Pair<Bounds, Double>?>(null) }
    var pendingPng by remember { mutableStateOf<ByteArray?>(null) }
    var showNotesDialog by remember { mutableStateOf(false) }

    fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> viewModel.onLocationPermissionResult(result.values.any { it }) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/geo+json")
    ) { uri ->
        uri?.let {
            scope.launch {
                ZoneFileIo.writeText(resolver, it, editorViewModel.exportGeoJson())
                toast("Zones exported")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                editorViewModel.pendingImport.value = ZoneFileIo.readText(resolver, it)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (LocationProvider.hasPermission(context)) {
            viewModel.onLocationPermissionResult(true)
        } else {
            permissionLauncher.launch(arrayOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION))
        }
    }

    LaunchedEffect(Unit) {
        editorViewModel.message.collect { toast(it) }
    }

    LaunchedEffect(snapshotId) { if (snapshotId >= 0) viewModel.viewSnapshot(snapshotId) }
    LaunchedEffect(viewingSnapshot) { controller.setSnapshotFans(viewingSnapshot) }
    LaunchedEffect(basemap) { controller.setBasemap(basemap) }
    LaunchedEffect(shooter) { controller.setShooter(shooter) }
    LaunchedEffect(zones) { controller.setZones(zones) }
    LaunchedEffect(fanState) { controller.setFans(fanState) }
    LaunchedEffect(fanState, selectedFanIndex) { controller.setSelectedFan(fanState, selectedFanIndex) }
    var selectedFanLabels by remember { mutableStateOf<MapController.SelectedFanLabels?>(null) }
    LaunchedEffect(controller) { controller.setSelectedFanLabelsListener { selectedFanLabels = it } }
    LaunchedEffect(Unit) {
        viewModel.cameraRequests.collect { controller.animateCamera(it, zoom = maxOf(controller.currentZoom(), 15.0)) }
    }
    LaunchedEffect(controller) { controller.setOnLongPress { viewModel.setManualPosition(it) } }
    LaunchedEffect(editorRender) { controller.setEditorRender(editorRender) }
    LaunchedEffect(controller) {
        controller.setOnTap { hit ->
            when {
                editorMode !is EditorViewModel.EditorMode.Idle -> editorViewModel.onMapTap(hit.position)
                hit.zoneId != null -> editorViewModel.onTap(hit, zones)
                hit.fanIndex != null -> viewModel.selectFan(hit.fanIndex)
                else -> viewModel.selectFan(null)
            }
        }
        controller.setHandleDragListener(object : MapController.HandleDragListener {
            override fun onHandleMoved(index: Int, p: LatLon) {
                editorViewModel.moveVertex(index, p)
            }
            override fun onHandleTapped(index: Int) {
                editorViewModel.selectVertex(index)
            }
            override fun onMidpointPressed(insertAfter: Int, p: LatLon): Int {
                return editorViewModel.insertVertexAfter(insertAfter, p)
            }
        })
    }

    Box(Modifier.fillMaxSize()) {
        MapLibreView(controller, Modifier.fillMaxSize())
        selectedFanLabels?.let { labels ->
            BearingLabel(labels.left.x, labels.left.y, labels.leftText)
            BearingLabel(labels.right.x, labels.right.y, labels.rightText)
        }
        Box(Modifier.align(Alignment.TopStart).padding(8.dp)) {
            MapMenu(
                onSettings = onOpenSettings,
                onOffline = onOpenOffline,
                onDownloadArea = {
                    controller.visibleBounds()?.let { bounds ->
                        downloadAreaRequest = bounds to controller.currentZoom()
                    }
                },
                onSnapshots = onOpenSnapshots,
                onExportZones = { exportLauncher.launch(ZoneFileIo.suggestedExportName()) },
                onImportZones = { importLauncher.launch(arrayOf("*/*")) },
            )
        }
        val currentFanState = fanState
        Column(
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Row {
                Basemap.entries.forEach {
                    FilterChip(
                        selected = it == basemap,
                        onClick = { viewModel.setBasemap(it) },
                        label = { Text(it.label) },
                    )
                }
            }
            if (manualMode || currentFanState != null) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (manualMode && viewingSnapshot == null) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.padding(end = 4.dp),
                        ) {
                            Text("Long-press to move", modifier = Modifier.padding(4.dp))
                        }
                    }
                    if (currentFanState != null) {
                        val windPart = if (currentFanState.range.windBufferM > 0) {
                            " · Wind +${Formatters.distance(currentFanState.range.windBufferM, currentFanState.units)}"
                        } else {
                            ""
                        }
                        Surface {
                            Text(
                                "Max ${Formatters.distance(currentFanState.range.maxRangeM, currentFanState.units)} · " +
                                    "Eff ${Formatters.distance(currentFanState.range.effectiveRangeM, currentFanState.units)}" +
                                    windPart,
                                modifier = Modifier.padding(4.dp),
                            )
                        }
                    }
                }
            }
        }
        Column(
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding()
                .padding(top = 64.dp, start = 8.dp, end = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val snapshotBeingViewed = viewingSnapshot
            if (snapshotBeingViewed != null) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Viewing snapshot from " +
                                DateFormat.getDateTimeInstance().format(Date(snapshotBeingViewed.timestampMs))
                        )
                        IconButton(onClick = { viewModel.closeSnapshotView() }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }
            } else if (currentFanState != null && currentFanState.insideZone) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text("Inside a no-fire zone — no safe field of fire", modifier = Modifier.padding(8.dp))
                }
            } else if (currentFanState != null && currentFanState.fans.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text("No clear field of fire", modifier = Modifier.padding(8.dp))
                }
            }
        }
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            horizontalAlignment = Alignment.End,
        ) {
            if (editorMode is EditorViewModel.EditorMode.Idle) {
                Box {
                    FloatingActionButton(onClick = { addMenuExpanded = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add zone")
                    }
                    DropdownMenu(
                        expanded = addMenuExpanded,
                        onDismissRequest = { addMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("No-fire polygon") },
                            onClick = {
                                addMenuExpanded = false
                                editorViewModel.startPolygon()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("No-fire marker") },
                            onClick = {
                                addMenuExpanded = false
                                editorViewModel.startMarker()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("No-fire line") },
                            onClick = {
                                addMenuExpanded = false
                                editorViewModel.startLine()
                            },
                        )
                    }
                }
            }
            SmallFloatingActionButton(
                onClick = { viewModel.toggleManualMode() },
                containerColor = if (manualMode) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Manual position")
            }
            SmallFloatingActionButton(
                onClick = { viewModel.recenter() },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Icon(Icons.Default.Place, contentDescription = "Recenter")
            }
            val canSnapshot = currentFanState != null && editorMode is EditorViewModel.EditorMode.Idle
            SmallFloatingActionButton(
                onClick = {
                    if (canSnapshot) {
                        controller.captureBitmap { bitmap ->
                            pendingPng = SnapshotCapture.toPng(bitmap)
                            showNotesDialog = true
                        }
                    }
                },
                containerColor = if (canSnapshot) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Icon(Icons.Default.Star, contentDescription = "Snapshot")
            }
        }

        if (editorMode !is EditorViewModel.EditorMode.Idle) {
            Box(Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                EditorControls(
                    mode = editorMode,
                    canFinish = canFinishEditor,
                    onUndo = { editorViewModel.undoLastVertex() },
                    onDeletePoint = { editorViewModel.deleteSelectedVertex() },
                    onCancel = { editorViewModel.cancel() },
                    onFinish = { editorViewModel.requestFinish() },
                    onRadiusChange = { r ->
                        when (editorMode) {
                            is EditorViewModel.EditorMode.MarkerEdit -> editorViewModel.setMarkerRadius(r)
                            is EditorViewModel.EditorMode.LineEdit -> editorViewModel.setLineBuffer(r)
                            else -> Unit
                        }
                    },
                )
            }
        }

        if (showNameDialog) {
            val currentMode = editorMode
            val initialRadius = when (currentMode) {
                is EditorViewModel.EditorMode.MarkerEdit -> currentMode.radiusM
                is EditorViewModel.EditorMode.LineEdit -> currentMode.bufferM
                else -> null
            }
            val radiusLabel = when (currentMode) {
                is EditorViewModel.EditorMode.MarkerEdit -> "Radius (m)"
                is EditorViewModel.EditorMode.LineEdit -> "Buffer (m)"
                else -> "Radius (m)"
            }
            val initialName = when (currentMode) {
                is EditorViewModel.EditorMode.PolygonEdit -> currentMode.name
                is EditorViewModel.EditorMode.MarkerEdit -> currentMode.name
                is EditorViewModel.EditorMode.LineEdit -> currentMode.name
                else -> ""
            }
            ZoneNameDialog(
                initialName = initialName,
                initialRadiusM = initialRadius,
                radiusLabel = radiusLabel,
                onConfirm = { name, radius -> editorViewModel.confirmFinish(name, radius) },
                onDismiss = { editorViewModel.dismissNameDialog() },
            )
        }

        val selectedFanView = currentFanState?.fans?.getOrNull(selectedFanIndex ?: -1)
        if (currentFanState != null && selectedFanView != null) {
            if (showFanDetail) {
                FanDetailSheet(
                    state = currentFanState,
                    fan = selectedFanView,
                    onDismiss = { showFanDetail = false },
                )
            } else {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    tonalElevation = 4.dp,
                ) {
                    Row(modifier = Modifier.padding(8.dp)) {
                        TextButton(onClick = { viewModel.selectFan(null) }) {
                            Text("Clear")
                        }
                        Button(onClick = { showFanDetail = true }) {
                            Text("Info")
                        }
                    }
                }
            }
        }

        val currentSelectedZone = selectedZone
        if (currentSelectedZone != null) {
            ZoneInfoSheet(
                zone = currentSelectedZone,
                onEditShape = { editorViewModel.editSelectedShape() },
                onRename = { editorViewModel.showRenameDialog.value = true },
                onDelete = { editorViewModel.deleteSelected() },
                onDismiss = { editorViewModel.clearSelection() },
            )
        }

        if (showRenameDialog && currentSelectedZone != null) {
            val currentName = when (currentSelectedZone) {
                is NoFirePolygon -> currentSelectedZone.name
                is NoFireMarker -> currentSelectedZone.name
                is NoFireLine -> currentSelectedZone.name
            }
            ZoneNameDialog(
                initialName = currentName,
                initialRadiusM = null,
                onConfirm = { name, _ -> editorViewModel.renameSelected(name) },
                onDismiss = { editorViewModel.showRenameDialog.value = false },
            )
        }

        val importText = pendingImport
        if (importText != null) {
            val decodedCount = remember(importText) {
                runCatching { GeoJsonCodec.decode(importText).size }.getOrNull()
            }
            if (decodedCount == null) {
                LaunchedEffect(importText) {
                    toast("Not a valid GeoJSON file")
                    editorViewModel.pendingImport.value = null
                }
            } else {
                fun performImport(replace: Boolean) {
                    editorViewModel.pendingImport.value = null
                    scope.launch {
                        try {
                            val count = editorViewModel.importGeoJson(importText, replace)
                            toast("Imported $count zones")
                        } catch (e: IllegalArgumentException) {
                            toast("Not a valid GeoJSON file")
                        }
                    }
                }
                AlertDialog(
                    onDismissRequest = { editorViewModel.pendingImport.value = null },
                    title = { Text("Import $decodedCount zones?") },
                    confirmButton = {
                        Row {
                            TextButton(onClick = { performImport(replace = true) }) {
                                Text("Replace all")
                            }
                            TextButton(onClick = { performImport(replace = false) }) {
                                Text("Merge")
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { editorViewModel.pendingImport.value = null }) {
                            Text("Cancel")
                        }
                    },
                )
            }
        }

        val pendingDownload = downloadAreaRequest
        if (pendingDownload != null) {
            val (bounds, zoom) = pendingDownload
            DownloadAreaDialog(
                basemap = basemap,
                bounds = bounds,
                currentZoom = zoom,
                onConfirm = { name, minZoom, maxZoom ->
                    downloadAreaRequest = null
                    context.appContainer().offlineRepository.startDownload(
                        name,
                        basemap,
                        bounds.south,
                        bounds.west,
                        bounds.north,
                        bounds.east,
                        minZoom,
                        maxZoom,
                        context.resources.displayMetrics.density,
                    )
                    toast("Download started")
                    onOpenOffline()
                },
                onDismiss = { downloadAreaRequest = null },
            )
        }

        if (showNotesDialog) {
            SnapshotNotesDialog(
                onConfirm = { notes ->
                    showNotesDialog = false
                    val png = pendingPng
                    pendingPng = null
                    if (png != null) {
                        viewModel.buildSnapshot(notes)?.let { snapshot ->
                            scope.launch {
                                viewModel.saveSnapshot(snapshot, png)
                                toast("Snapshot saved")
                            }
                        }
                    }
                },
                onDismiss = {
                    showNotesDialog = false
                    pendingPng = null
                },
            )
        }
    }
}

@Composable
private fun BearingLabel(x: Float, y: Float, text: String) {
    val density = LocalDensity.current
    val halfWidthPx = with(density) { 22.dp.toPx() }
    val halfHeightPx = with(density) { 10.dp.toPx() }
    Surface(
        color = Color.White.copy(alpha = 0.25f),
        contentColor = Color.Black,
        modifier = Modifier.offset {
            IntOffset((x - halfWidthPx).roundToInt(), (y - halfHeightPx).roundToInt())
        },
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
    }
}
