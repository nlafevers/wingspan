package com.wingspan.app.domain.report

import com.wingspan.app.domain.ballistics.LoadSettings
import com.wingspan.app.domain.ballistics.UnitSystem
import com.wingspan.app.domain.geo.EnuProjection
import com.wingspan.app.domain.geo.LatLon
import com.wingspan.app.domain.geo.Vec2
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReportTest {

    private fun position(maxRangeM: Double, windBufferM: Double) = ReportPosition(
        id = 1L,
        timestampMs = 1L,
        position = LatLon(45.0, -120.0),
        positionSource = "GPS",
        maxRangeM = maxRangeM,
        effectiveRangeM = 40.0,
        windBufferM = windBufferM,
        declinationDeg = 0.0,
    )

    @Test
    fun `version 1 report subtracts the wind buffer from max range`() {
        val legacy = RangeReport(positions = listOf(position(300.0, 40.0)), version = 1)

        val migrated = legacy.migrated()

        val p = migrated.positions.single()
        assertEquals(260.0, p.maxRangeM, 1e-9)
        assertEquals(300.0, p.fanRangeM, 1e-9)
        assertEquals(2, migrated.version)
    }

    @Test
    fun `migrating twice equals migrating once`() {
        val legacy = RangeReport(positions = listOf(position(300.0, 40.0)), version = 1)

        val once = legacy.migrated()

        assertEquals(once, once.migrated())
    }

    @Test
    fun `version 1 position without wind keeps its max range`() {
        val legacy = RangeReport(positions = listOf(position(300.0, 0.0)), version = 1)

        val migrated = legacy.migrated()

        assertEquals(300.0, migrated.positions.single().maxRangeM, 1e-9)
    }

    @Test
    fun `migrated report round trips with version 2`() {
        val json = Json { ignoreUnknownKeys = true }
        val migrated = RangeReport(positions = listOf(position(300.0, 40.0))).migrated()

        val decoded = json.decodeFromString<RangeReport>(json.encodeToString(migrated))

        assertEquals(RangeReport.CURRENT_VERSION, decoded.version)
        assertEquals(260.0, decoded.positions.single().maxRangeM, 1e-9)
    }

    private val origin = LatLon(45.0, -120.0)
    private val fan = ReportFan(10.0, 80.0, 12.0, 82.0, false)

    private fun at(eastM: Double, northM: Double = 0.0): LatLon =
        EnuProjection(origin).fromEnu(Vec2(eastM, northM))

    private fun candidate(
        id: Long,
        where: LatLon = origin,
        settings: LoadSettings? = LoadSettings(),
        fans: List<ReportFan> = listOf(fan),
        accuracyM: Double? = null,
    ) = ReportPosition(
        id = id,
        timestampMs = id,
        position = where,
        positionSource = "GPS",
        maxRangeM = 250.0,
        effectiveRangeM = 40.0,
        windBufferM = 0.0,
        declinationDeg = 0.0,
        fans = fans,
        accuracyM = accuracyM,
        settings = settings,
    )

    private val tap = at(0.0, 50.0)

    private fun firstShot(): RangeReport =
        RangeReport().recordShot(candidate(1L), tap, 100L).first

    @Test
    fun `first shot on an empty report creates a position`() {
        val (report, id) = RangeReport().recordShot(candidate(1L), tap, 100L, "a")

        val p = report.positions.single()
        assertEquals(1L, id)
        assertEquals(1L, p.id)
        assertEquals(1, p.shots.size)
        assertEquals(100L, p.shots.single().timestampMs)
        assertEquals("a", p.shots.single().label)
    }

    @Test
    fun `shot from 3 m away with same settings and fans appends`() {
        val (report, id) = firstShot().recordShot(candidate(2L, at(3.0)), tap, 200L)

        assertEquals(1, report.positions.size)
        assertEquals(1L, id)
        assertEquals(2, report.positions.single().shots.size)
    }

    @Test
    fun `shot from 20 m away creates a second position`() {
        val (report, id) = firstShot().recordShot(candidate(2L, at(20.0)), tap, 200L)

        assertEquals(2, report.positions.size)
        assertEquals(2L, id)
        assertEquals(1, report.positions[1].shots.size)
        assertEquals(1, report.positions[0].shots.size)
    }

    @Test
    fun `shot from 8 m away with 12 m accuracy appends`() {
        val (report, id) = firstShot()
            .recordShot(candidate(2L, at(8.0), accuracyM = 12.0), tap, 200L)

        assertEquals(1, report.positions.size)
        assertEquals(1L, id)
    }

    @Test
    fun `different muzzle velocity creates a new position`() {
        val changed = LoadSettings(muzzleVelocityFps = 1300.0)
        val (report, id) = firstShot().recordShot(candidate(2L, settings = changed), tap, 200L)

        assertEquals(2, report.positions.size)
        assertEquals(2L, id)
        assertEquals(changed, report.positions[1].settings)
    }

    @Test
    fun `only unit system change appends`() {
        val metric = LoadSettings(unitSystem = UnitSystem.METRIC)
        val (report, id) = firstShot().recordShot(candidate(2L, settings = metric), tap, 200L)

        assertEquals(1, report.positions.size)
        assertEquals(1L, id)
    }

    @Test
    fun `different fan list creates a new position`() {
        val fans = listOf(fan, ReportFan(200.0, 250.0, 202.0, 252.0, false))
        val (report, id) = firstShot().recordShot(candidate(2L, fans = fans), tap, 200L)

        assertEquals(2, report.positions.size)
        assertEquals(2L, id)
    }

    @Test
    fun `returned id is the receiving position id`() {
        val (twoPositions, _) = firstShot().recordShot(candidate(2L, at(20.0)), tap, 200L)
        val (report, id) = twoPositions.recordShot(candidate(3L, at(21.0)), tap, 300L)

        assertEquals(2, report.positions.size)
        assertEquals(2L, id)
        assertEquals(2, report.positions.single { it.id == id }.shots.size)
    }

    @Test
    fun `tap due east of the receiver yields bearing 90`() {
        val receiverPos = at(20.0)
        val eastTap = EnuProjection(receiverPos).fromEnu(Vec2(100.0, 0.0))

        val (report, id) = RangeReport().recordShot(candidate(1L, receiverPos), eastTap, 100L)

        val shot = report.positions.single { it.id == id }.shots.single()
        assertEquals(90.0, shot.bearingTrueDeg, 0.1)
    }

    @Test
    fun `stored json without settings or shot timestamp decodes with defaults`() {
        val json = Json { ignoreUnknownKeys = true }
        val stored = """
            {"createdMs":1,"version":2,"positions":[{"id":1,"timestampMs":1,
            "position":{"lat":45.0,"lon":-120.0},"positionSource":"GPS",
            "maxRangeM":250.0,"effectiveRangeM":40.0,"windBufferM":0.0,
            "declinationDeg":0.0,"shots":[{"bearingTrueDeg":45.0,"label":"x"}]}]}
        """.trimIndent()

        val decoded = json.decodeFromString<RangeReport>(stored)

        val p = decoded.positions.single()
        assertNull(p.settings)
        assertEquals(0L, p.shots.single().timestampMs)
    }
}
