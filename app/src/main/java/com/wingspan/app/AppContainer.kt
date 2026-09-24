package com.wingspan.app

import android.content.Context
import com.wingspan.app.data.AndroidDeclination
import com.wingspan.app.data.SettingsRepository
import com.wingspan.app.data.SnapshotRepository
import com.wingspan.app.data.ZoneRepository
import com.wingspan.app.data.db.AppDatabase
import com.wingspan.app.data.location.LocationProvider
import com.wingspan.app.data.map.OfflineRepository
import com.wingspan.app.data.settingsDataStore
import com.wingspan.app.domain.geo.DeclinationProvider
import java.io.File

class AppContainer(context: Context) {
    val database = AppDatabase.build(context)
    val zoneRepository = ZoneRepository(database.zoneDao())
    val settingsRepository = SettingsRepository(context.settingsDataStore)
    val snapshotRepository = SnapshotRepository(database.snapshotDao(), File(context.filesDir, "snapshots"))
    val locationProvider = LocationProvider(context)
    val declinationProvider: DeclinationProvider = AndroidDeclination()
    val offlineRepository = OfflineRepository(context)
}
