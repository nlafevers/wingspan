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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    NavHost(navController, startDestination = "map") {
        composable("map") {
            MapScreen(
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
                onShowOnMap = { /* wired in a later step, WS-8.4 */ },
            )
        }
    }
}
