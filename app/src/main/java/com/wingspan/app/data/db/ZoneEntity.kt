package com.wingspan.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * For [TYPE_LINE] rows, [verticesJson] holds the (unclosed) polyline points exactly like
 * [TYPE_POLYGON], and [radiusM] is reused to hold the line's buffer half-width in meters
 * (`lat`/`lon` stay null, matching [TYPE_POLYGON]).
 */
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
        const val TYPE_LINE = "LINE"
    }
}
