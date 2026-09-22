package com.wingspan.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "snapshots")
data class SnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val lat: Double,
    val lon: Double,
    val positionSource: String,
    val settingsJson: String,
    val maxRangeM: Double,
    val effectiveRangeM: Double,
    val declinationDeg: Double,
    val fansJson: String,
    val notes: String,
    val imagePath: String,
)
