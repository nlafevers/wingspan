package com.wingspan.app.ui.map

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wingspan.app.appContainer
import com.wingspan.app.data.location.LocationProvider
import com.wingspan.app.data.map.Basemap
import com.wingspan.app.domain.geo.LatLon
import com.wingspan.app.ui.editor.EditorControls
import com.wingspan.app.ui.editor.EditorViewModel
import com.wingspan.app.ui.editor.ZoneInfoSheet
import com.wingspan.app.ui.editor.ZoneNameDialog
import com.wingspan.app.domain.geo.NoFireLine
import com.wingspan.app.domain.geo.NoFireMarker
import com.wingspan.app.domain.geo.NoFirePolygon

@Composable
fun MapScreen(
    viewModel: MapViewModel = viewModel(factory = MapViewModel.factory(LocalContext.current.appContainer()))
) {
    val context = LocalContext.current
    val controller = remember { MapController(context.applicationContext) }
    val basemap by viewModel.basemap.collectAsStateWithLifecycle()
    val shooter by viewModel.shooter.collectAsStateWithLifecycle()
    val manualMode by viewModel.manualMode.collectAsStateWithLifecycle()
    val zones by viewModel.zones.collectAsStateWithLifecycle()

    val editorViewModel: EditorViewModel = viewModel(
        factory = EditorViewModel.factory(context.appContainer())
    )
    val editorMode by editorViewModel.mode.collectAsStateWithLifecycle()
    val editorRender by editorViewModel.render.collectAsStateWithLifecycle()
    val canFinishEditor by editorViewModel.canFinish.collectAsStateWithLifecycle()
    val showNameDialog by editorViewModel.showNameDialog.collectAsStateWithLifecycle()
    val selectedZone by editorViewModel.selectedZone.collectAsStateWithLifecycle()
    val showRenameDialog by editorViewModel.showRenameDialog.collectAsStateWithLifecycle()
    var addMenuExpanded by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> viewModel.onLocationPermissionResult(result.values.any { it }) }

    LaunchedEffect(Unit) {
        if (LocationProvider.hasPermission(context)) {
            viewModel.onLocationPermissionResult(true)
        } else {
            permissionLauncher.launch(arrayOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION))
        }
    }

    LaunchedEffect(basemap) { controller.setBasemap(basemap) }
    LaunchedEffect(shooter) { controller.setShooter(shooter) }
    LaunchedEffect(zones) { controller.setZones(zones) }
    LaunchedEffect(Unit) {
        viewModel.cameraRequests.collect { controller.animateCamera(it, zoom = maxOf(controller.currentZoom(), 15.0)) }
    }
    LaunchedEffect(controller) { controller.setOnLongPress { viewModel.setManualPosition(it) } }
    LaunchedEffect(editorRender) { controller.setEditorRender(editorRender) }
    LaunchedEffect(controller) {
        controller.setOnTap { hit -> editorViewModel.onTap(hit, zones) }
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
        Row(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)) {
            Basemap.entries.forEach {
                FilterChip(
                    selected = it == basemap,
                    onClick = { viewModel.setBasemap(it) },
                    label = { Text(it.label) },
                )
            }
        }
        if (manualMode) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(8.dp),
            ) {
                Text("MANUAL POSITION — long-press map to move", modifier = Modifier.padding(8.dp))
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
    }
}
