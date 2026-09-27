package com.wingspan.app.domain.report

import com.wingspan.app.domain.ballistics.LoadSettings
import com.wingspan.app.domain.ballistics.UnitSystem
import com.wingspan.app.domain.geo.EnuProjection
import com.wingspan.app.domain.geo.Geometry2D
import com.wingspan.app.domain.geo.LatLon
import kotlinx.serialization.Serializable
import kotlin.math.atan2
import kotlin.math.max

/** [timestampMs] is 0 when the shot was stored before timestamps were recorded. */
@Serializable
data class ReportShot(
    val bearingTrueDeg: Double,
    val label: String = "",
    val timestampMs: Long = 0L,
)

@Serializable
data class ReportFan(
    val leftTrueDeg: Double,
    val rightTrueDeg: Double,
    val leftMagDeg: Double,
    val rightMagDeg: Double,
    val fullCircle: Boolean,
)

@Serializable
data class ReportPosition(
    val id: Long,
    val timestampMs: Long,
    val position: LatLon,
    val positionSource: String,
    val maxRangeM: Double,
    val effectiveRangeM: Double,
    val windBufferM: Double,
    val declinationDeg: Double,
    val fans: List<ReportFan> = emptyList(),
    val shots: List<ReportShot> = emptyList(),
    val accuracyM: Double? = null,
    val effectiveRangeLimiter: String = "",
    /** Load settings frozen with the position; null when recorded before settings were stored. */
    val settings: LoadSettings? = null,
) {
    val fanRangeM: Double get() = maxRangeM + windBufferM
}

@Serializable
data class RangeReport(
    val createdMs: Long = 0L,
    val positions: List<ReportPosition> = emptyList(),
    val version: Int = 1,
) {

    companion object {
        const val CURRENT_VERSION = 2
    }

    /**
     * Repairs reports written before version 2, whose positions stored the
     * wind-buffered fan radius in [ReportPosition.maxRangeM].
     */
    fun migrated(): RangeReport {
        if (version >= CURRENT_VERSION) return this
        return copy(
            positions = positions.map { it.copy(maxRangeM = it.maxRangeM - it.windBufferM) },
            version = CURRENT_VERSION,
        )
    }

    val totalShots: Int get() = positions.sumOf { it.shots.size }

    fun withPosition(position: ReportPosition): RangeReport =
        copy(positions = positions + position)

    fun removePosition(id: Long): RangeReport =
        copy(positions = positions.filterNot { it.id == id })

    fun withShot(positionId: Long, shot: ReportShot): RangeReport {
        val index = positions.indexOfFirst { it.id == positionId }
        if (index < 0) return this
        val updated = positions[index].let { it.copy(shots = it.shots + shot) }
        return copy(positions = positions.toMutableList().also { it[index] = updated })
    }

    fun updateShot(positionId: Long, index: Int, shot: ReportShot): RangeReport {
        val posIndex = positions.indexOfFirst { it.id == positionId }
        if (posIndex < 0) return this
        val position = positions[posIndex]
        if (index !in position.shots.indices) return this
        val updatedShots = position.shots.toMutableList().also { it[index] = shot }
        val updatedPosition = position.copy(shots = updatedShots)
        return copy(positions = positions.toMutableList().also { it[posIndex] = updatedPosition })
    }

    fun removeShot(positionId: Long, index: Int): RangeReport {
        val posIndex = positions.indexOfFirst { it.id == positionId }
        if (posIndex < 0) return this
        val position = positions[posIndex]
        if (index !in position.shots.indices) return this
        val updatedShots = position.shots.toMutableList().also { it.removeAt(index) }
        val updatedPosition = position.copy(shots = updatedShots)
        return copy(positions = positions.toMutableList().also { it[posIndex] = updatedPosition })
    }
}

/** Minimum station radius in metres; matches the fan recompute threshold. */
private const val STATION_RADIUS_M = 5.0

/**
 * True when [candidate] is close enough to this position (within
 * `max(5 m, candidate accuracy)`), has the same load settings ignoring the
 * display-only unit system, and has the same fans by true bearings and
 * full-circle flag.
 */
fun ReportPosition.isSameStationAs(candidate: ReportPosition): Boolean {
    val distance = EnuProjection(position).toEnu(candidate.position).length
    if (distance > max(STATION_RADIUS_M, candidate.accuracyM ?: 0.0)) return false
    val a = settings?.copy(unitSystem = UnitSystem.IMPERIAL)
    val b = candidate.settings?.copy(unitSystem = UnitSystem.IMPERIAL)
    if (a != b) return false
    fun fanKeys(p: ReportPosition) = p.fans.map { Triple(it.leftTrueDeg, it.rightTrueDeg, it.fullCircle) }
    return fanKeys(this) == fanKeys(candidate)
}

/**
 * Records a shot at [tap]. The shot joins the last position when it
 * [isSameStationAs] [candidate]; otherwise [candidate] is appended as a new
 * position holding only this shot. Returns the new report and the id of the
 * position that received the shot.
 */
fun RangeReport.recordShot(
    candidate: ReportPosition,
    tap: LatLon,
    timestampMs: Long,
    label: String = "",
): Pair<RangeReport, Long> {
    val last = positions.lastOrNull()
    val appendToLast = last != null && last.isSameStationAs(candidate)
    val receiver = if (appendToLast) last else candidate
    val enu = EnuProjection(receiver.position).toEnu(tap)
    val bearing = Geometry2D.normalizeBearing(Math.toDegrees(atan2(enu.x, enu.y)))
    val shot = ReportShot(bearingTrueDeg = bearing, label = label, timestampMs = timestampMs)
    val updated = if (appendToLast) {
        copy(positions = positions.dropLast(1) + receiver.copy(shots = receiver.shots + shot))
    } else {
        withPosition(candidate.copy(shots = listOf(shot)))
    }
    return updated to receiver.id
}
