package com.wingspan.app.domain.report

import com.wingspan.app.domain.geo.EnuProjection
import com.wingspan.app.domain.geo.LatLon
import com.wingspan.app.domain.geo.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReportLayoutTest {

    private val origin = LatLon(39.0, -105.0)

    private fun positionAt(
        latLon: LatLon,
        maxRangeM: Double,
        windBufferM: Double = 0.0,
    ): ReportPosition = ReportPosition(
        id = 1L,
        timestampMs = 0L,
        position = latLon,
        positionSource = "gps",
        maxRangeM = maxRangeM,
        effectiveRangeM = maxRangeM,
        windBufferM = windBufferM,
        declinationDeg = 0.0,
    )

    @Test
    fun frameForEmptyReportIsNull() {
        assertNull(ReportLayout.frameFor(RangeReport()))
    }

    @Test
    fun frameForSinglePositionComputesHalfExtentAndScale() {
        val report = RangeReport(positions = listOf(positionAt(origin, maxRangeM = 300.0)))

        val frame = ReportLayout.frameFor(report)!!

        assertEquals(324.0, frame.halfExtentM, 1e-9)
        assertEquals(540.0 / 648.0, frame.pointsPerMeter, 1e-9)
    }

    @Test
    fun toPagePointMapsCenterToMapSquareCenter() {
        val report = RangeReport(positions = listOf(positionAt(origin, maxRangeM = 300.0)))
        val frame = ReportLayout.frameFor(report)!!

        val (x, y) = ReportLayout.toPagePoint(frame, origin)

        assertEquals(306.0, x, 1e-6)
        assertEquals(306.0, y, 1e-6)
    }

    @Test
    fun toPagePointMovesNorthByPointsPerMeter() {
        val report = RangeReport(positions = listOf(positionAt(origin, maxRangeM = 300.0)))
        val frame = ReportLayout.frameFor(report)!!
        val (centerX, centerY) = ReportLayout.toPagePoint(frame, origin)

        val north = EnuProjection(origin).fromEnu(Vec2(0.0, 100.0))
        val (x, y) = ReportLayout.toPagePoint(frame, north)

        assertEquals(centerX, x, 1e-6)
        assertEquals(centerY - 100.0 * frame.pointsPerMeter, y, 0.5)
    }

    @Test
    fun scaleBarMetersPicksLargestNiceValueUnder40Percent() {
        val report = RangeReport(positions = listOf(positionAt(origin, maxRangeM = 300.0)))
        val frame = ReportLayout.frameFor(report)!!

        assertEquals(200.0, ReportLayout.scaleBarMeters(frame), 1e-9)
    }

    @Test
    fun frameForSmallRangeCoercesToMinimumHalfExtent() {
        val report = RangeReport(positions = listOf(positionAt(origin, maxRangeM = 10.0)))

        val frame = ReportLayout.frameFor(report)!!

        assertEquals(50.0, frame.halfExtentM, 1e-9)
    }
}
