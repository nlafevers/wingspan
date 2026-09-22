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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wingspan.app.appContainer
import com.wingspan.app.data.location.LocationProvider
import com.wingspan.app.data.map.Basemap

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
            SmallFloatingActionButton(
                onClick = {
                    if (LocationProvider.hasPermission(context)) {
                        viewModel.recenter()
                    } else {
                        permissionLauncher.launch(arrayOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION))
                    }
                },
            ) {
                Icon(Icons.Default.Place, contentDescription = "My location")
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
        }
    }
}
