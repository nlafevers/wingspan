package com.wingspan.app

import android.content.Context
import com.wingspan.app.data.SettingsRepository
import com.wingspan.app.data.SnapshotRepository
import com.wingspan.app.data.ZoneRepository
import com.wingspan.app.data.db.AppDatabase
import com.wingspan.app.data.settingsDataStore
import java.io.File

class AppContainer(context: Context) {
    val database = AppDatabase.build(context)
    val zoneRepository = ZoneRepository(database.zoneDao())
    val settingsRepository = SettingsRepository(context.settingsDataStore)
    val snapshotRepository = SnapshotRepository(database.snapshotDao(), File(context.filesDir, "snapshots"))
}
