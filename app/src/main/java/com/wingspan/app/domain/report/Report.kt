package com.wingspan.app.domain.report

import com.wingspan.app.domain.geo.LatLon
import kotlinx.serialization.Serializable

@Serializable
data class ReportShot(val bearingTrueDeg: Double, val label: String = "")

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
) {
    val fanRangeM: Double get() = maxRangeM + windBufferM
}

@Serializable
data class RangeReport(val createdMs: Long = 0L, val positions: List<ReportPosition> = emptyList()) {

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
