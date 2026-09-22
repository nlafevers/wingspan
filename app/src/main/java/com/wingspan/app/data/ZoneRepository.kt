package com.wingspan.app.data

import com.wingspan.app.data.db.ZoneDao
import com.wingspan.app.data.db.ZoneEntity
import com.wingspan.app.domain.geo.LatLon
import com.wingspan.app.domain.geo.NoFireMarker
import com.wingspan.app.domain.geo.NoFirePolygon
import com.wingspan.app.domain.geo.NoFireZone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class ZoneRepository(private val dao: ZoneDao) {

    val zones: Flow<List<NoFireZone>> = dao.observeAll().map { entities -> entities.map(::toDomain) }

    suspend fun getAll(): List<NoFireZone> = dao.getAll().map(::toDomain)

    suspend fun addPolygon(name: String, vertices: List<LatLon>): Long =
        dao.insert(
            ZoneEntity(
                name = name,
                type = ZoneEntity.TYPE_POLYGON,
                verticesJson = encodeVertices(vertices),
                lat = null,
                lon = null,
                radiusM = null,
                createdAt = System.currentTimeMillis(),
            ),
        )

    suspend fun addMarker(name: String, center: LatLon, radiusM: Double): Long =
        dao.insert(
            ZoneEntity(
                name = name,
                type = ZoneEntity.TYPE_MARKER,
                verticesJson = null,
                lat = center.lat,
                lon = center.lon,
                radiusM = radiusM,
                createdAt = System.currentTimeMillis(),
            ),
        )

    suspend fun updatePolygon(id: Long, name: String, vertices: List<LatLon>) {
        val existing = dao.getById(id) ?: return
        dao.update(
            existing.copy(
                name = name,
                type = ZoneEntity.TYPE_POLYGON,
                verticesJson = encodeVertices(vertices),
                lat = null,
                lon = null,
                radiusM = null,
            ),
        )
    }

    suspend fun updateMarker(id: Long, name: String, center: LatLon, radiusM: Double) {
        val existing = dao.getById(id) ?: return
        dao.update(
            existing.copy(
                name = name,
                type = ZoneEntity.TYPE_MARKER,
                verticesJson = null,
                lat = center.lat,
                lon = center.lon,
                radiusM = radiusM,
            ),
        )
    }

    suspend fun rename(id: Long, name: String) {
        val existing = dao.getById(id) ?: return
        dao.update(existing.copy(name = name))
    }

    suspend fun delete(id: Long) {
        dao.deleteById(id)
    }

    suspend fun addAll(zones: List<NoFireZone>) {
        dao.insertAll(zones.map { zone -> toEntity(zone, createdAt = System.currentTimeMillis()) })
    }

    suspend fun replaceAll(zones: List<NoFireZone>) {
        dao.deleteAll()
        dao.insertAll(zones.map { zone -> toEntity(zone, createdAt = System.currentTimeMillis()) })
    }

    private fun encodeVertices(vertices: List<LatLon>): String =
        Json.encodeToString(vertices.map { listOf(it.lat, it.lon) })

    private fun decodeVertices(verticesJson: String?): List<LatLon> {
        if (verticesJson.isNullOrBlank()) return emptyList()
        val decoded: List<List<Double>> = Json.decodeFromString(verticesJson)
        return decoded.map { LatLon(lat = it[0], lon = it[1]) }
    }

    private fun toDomain(entity: ZoneEntity): NoFireZone = when (entity.type) {
        ZoneEntity.TYPE_POLYGON -> NoFirePolygon(
            id = entity.id,
            name = entity.name,
            vertices = decodeVertices(entity.verticesJson),
        )
        ZoneEntity.TYPE_MARKER -> NoFireMarker(
            id = entity.id,
            name = entity.name,
            center = LatLon(lat = entity.lat ?: 0.0, lon = entity.lon ?: 0.0),
            radiusM = entity.radiusM ?: 0.0,
        )
        else -> error("Unknown zone type: ${entity.type}")
    }

    private fun toEntity(zone: NoFireZone, createdAt: Long): ZoneEntity = when (zone) {
        is NoFirePolygon -> ZoneEntity(
            id = zone.id,
            name = zone.name,
            type = ZoneEntity.TYPE_POLYGON,
            verticesJson = encodeVertices(zone.vertices),
            lat = null,
            lon = null,
            radiusM = null,
            createdAt = createdAt,
        )
        is NoFireMarker -> ZoneEntity(
            id = zone.id,
            name = zone.name,
            type = ZoneEntity.TYPE_MARKER,
            verticesJson = null,
            lat = zone.center.lat,
            lon = zone.center.lon,
            radiusM = zone.radiusM,
            createdAt = createdAt,
        )
    }
}
