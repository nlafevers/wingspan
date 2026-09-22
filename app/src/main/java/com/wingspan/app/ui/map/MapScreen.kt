package com.wingspan.app.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.FilterChip
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
import com.wingspan.app.data.map.Basemap

@Composable
fun MapScreen(
    viewModel: MapViewModel = viewModel(factory = MapViewModel.factory(LocalContext.current.appContainer()))
) {
    val context = LocalContext.current
    val controller = remember { MapController(context.applicationContext) }
    val basemap by viewModel.basemap.collectAsStateWithLifecycle()

    LaunchedEffect(basemap) { controller.setBasemap(basemap) }

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
    }
}
