package com.wingspan.app.domain.report

import com.wingspan.app.domain.geo.LatLon
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
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
}
