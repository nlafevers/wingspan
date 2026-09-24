package com.wingspan.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wingspan.app.ui.map.MapScreen
import com.wingspan.app.ui.offline.OfflineScreen
import com.wingspan.app.ui.settings.SettingsScreen
import com.wingspan.app.ui.snapshots.SnapshotDetailScreen
import com.wingspan.app.ui.snapshots.SnapshotsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComingSoonScreen(onBack: () -> Unit, title: String) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Text("Coming soon")
        }
    }
}

@Composable
fun WingspanApp() {
    val navController = rememberNavController()
    NavHost(navController, startDestination = "map?snapshotId=-1") {
        composable(
            "map?snapshotId={snapshotId}",
            arguments = listOf(navArgument("snapshotId") { type = NavType.LongType; defaultValue = -1L }),
        ) { backStackEntry ->
            val initialSnapshotId = backStackEntry.arguments?.getLong("snapshotId") ?: -1L
            // "Show on map" (below) updates this handle on this SAME entry and pops back to it,
            // rather than navigating to a fresh "map?snapshotId=..." route: a new route string
            // would create a new NavBackStackEntry and therefore a brand new MapViewModel,
            // silently discarding in-memory session state (manual position mode, the manually
            // placed position, etc.) every time a snapshot was viewed.
            val snapshotId by backStackEntry.savedStateHandle
                .getStateFlow("snapshotId", initialSnapshotId)
                .collectAsStateWithLifecycle()
            MapScreen(
                snapshotId = snapshotId,
                onOpenSettings = { navController.navigate("settings") },
                onOpenOffline = { navController.navigate("offline") },
                onOpenSnapshots = { navController.navigate("snapshots") },
            )
        }
        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable("offline") {
            OfflineScreen(onBack = { navController.popBackStack() })
        }
        composable("snapshots") {
            SnapshotsScreen(
                onBack = { navController.popBackStack() },
                onOpen = { id -> navController.navigate("snapshot/$id") },
            )
        }
        composable(
            "snapshot/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("id") ?: 0L
            SnapshotDetailScreen(
                id = id,
                onBack = { navController.popBackStack() },
                onShowOnMap = { snapshotId ->
                    navController.getBackStackEntry("map?snapshotId={snapshotId}")
                        .savedStateHandle["snapshotId"] = snapshotId
                    navController.popBackStack("map?snapshotId={snapshotId}", inclusive = false)
                },
            )
        }
    }
}
