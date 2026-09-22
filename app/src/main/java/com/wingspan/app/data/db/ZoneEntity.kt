package com.wingspan.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "zones")
data class ZoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val verticesJson: String?,
    val lat: Double?,
    val lon: Double?,
    val radiusM: Double?,
    val createdAt: Long,
) {
    companion object {
        const val TYPE_POLYGON = "POLYGON"
        const val TYPE_MARKER = "MARKER"
    }
}
