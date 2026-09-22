package com.wingspan.app.data

import com.wingspan.app.data.db.SnapshotDao
import com.wingspan.app.data.db.SnapshotEntity
import com.wingspan.app.domain.ballistics.LoadSettings
import com.wingspan.app.domain.geo.LatLon
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SnapshotFan(
    val leftTrueDeg: Double,
    val rightTrueDeg: Double,
    val leftMagDeg: Double,
    val rightMagDeg: Double,
    val fullCircle: Boolean,
)

data class FiringSnapshot(
    val id: Long,
    val timestampMs: Long,
    val position: LatLon,
    val positionSource: String,
    val settings: LoadSettings,
    val maxRangeM: Double,
    val effectiveRangeM: Double,
    val declinationDeg: Double,
    val fans: List<SnapshotFan>,
    val notes: String,
    val imageFile: File,
)

class SnapshotRepository(private val dao: SnapshotDao, private val imagesDir: File) {

    val snapshots: Flow<List<FiringSnapshot>> = dao.observeAll().map { entities -> entities.map(::toDomain) }

    suspend fun get(id: Long): FiringSnapshot? = withContext(Dispatchers.IO) {
        dao.getById(id)?.let(::toDomain)
    }

    suspend fun create(snapshot: FiringSnapshot, pngBytes: ByteArray): Long = withContext(Dispatchers.IO) {
        if (!imagesDir.exists()) {
            imagesDir.mkdirs()
        }
        val imageFile = File(imagesDir, "snap_${snapshot.timestampMs}.png")
        imageFile.writeBytes(pngBytes)
        dao.insert(toEntity(snapshot, imageFile.absolutePath))
    }

    suspend fun updateNotes(id: Long, notes: String) = withContext(Dispatchers.IO) {
        val existing = dao.getById(id) ?: return@withContext
        dao.update(existing.copy(notes = notes))
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        dao.getById(id)?.let { existing -> File(existing.imagePath).delete() }
        dao.deleteById(id)
    }

    suspend fun getAll(): List<FiringSnapshot> = withContext(Dispatchers.IO) {
        dao.getAll().map(::toDomain)
    }

    private fun toEntity(snapshot: FiringSnapshot, imagePath: String): SnapshotEntity =
        SnapshotEntity(
            id = snapshot.id,
            timestampMs = snapshot.timestampMs,
            lat = snapshot.position.lat,
            lon = snapshot.position.lon,
            positionSource = snapshot.positionSource,
            settingsJson = Json.encodeToString(snapshot.settings),
            maxRangeM = snapshot.maxRangeM,
            effectiveRangeM = snapshot.effectiveRangeM,
            declinationDeg = snapshot.declinationDeg,
            fansJson = Json.encodeToString(snapshot.fans),
            notes = snapshot.notes,
            imagePath = imagePath,
        )

    private fun toDomain(entity: SnapshotEntity): FiringSnapshot =
        FiringSnapshot(
            id = entity.id,
            timestampMs = entity.timestampMs,
            position = LatLon(lat = entity.lat, lon = entity.lon),
            positionSource = entity.positionSource,
            settings = Json.decodeFromString(entity.settingsJson),
            maxRangeM = entity.maxRangeM,
            effectiveRangeM = entity.effectiveRangeM,
            declinationDeg = entity.declinationDeg,
            fans = Json.decodeFromString(entity.fansJson),
            notes = entity.notes,
            imageFile = File(entity.imagePath),
        )
}
