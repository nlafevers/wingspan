package com.wingspan.app.ui.report

import com.wingspan.app.domain.ballistics.LoadSettings
import com.wingspan.app.domain.ballistics.UnitSystem
import com.wingspan.app.domain.geo.LatLon
import com.wingspan.app.domain.report.RangeReport
import com.wingspan.app.domain.report.ReportFan
import com.wingspan.app.domain.report.ReportPosition
import com.wingspan.app.domain.report.ReportShot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun position(
    id: Long,
    settings: LoadSettings?,
    maxRangeM: Double = 100.0,
    windBufferM: Double = 10.0,
    effectiveRangeM: Double = 50.0,
    fans: List<ReportFan> = emptyList(),
    shots: List<ReportShot> = emptyList(),
    timestampMs: Long = 0L,
): ReportPosition = ReportPosition(
    id = id,
    timestampMs = timestampMs,
    position = LatLon(40.12345, -74.12345),
    positionSource = "GPS",
    maxRangeM = maxRangeM,
    effectiveRangeM = effectiveRangeM,
    windBufferM = windBufferM,
    declinationDeg = 0.0,
    fans = fans,
    shots = shots,
    accuracyM = null,
    settings = settings,
)

class ReportTablesTest {

    @Test
    fun `two positions with equal settings give one configuration A`() {
        val settings = LoadSettings()
        val report = RangeReport(
            positions = listOf(
                position(1, settings),
                position(2, settings),
            ),
        )

        val tables = ReportTables.build(report, UnitSystem.IMPERIAL)

        assertEquals(1, tables.configs.size)
        assertEquals("A", tables.configs[0].letter)
    }

    @Test
    fun `velocity change gives A and B with changed only in velocity max range fan radius and effective range`() {
        val settingsA = LoadSettings(muzzleVelocityFps = 1200.0)
        val settingsB = LoadSettings(muzzleVelocityFps = 1300.0)
        val report = RangeReport(
            positions = listOf(
                position(1, settingsA, maxRangeM = 100.0, windBufferM = 10.0, effectiveRangeM = 50.0),
                position(2, settingsB, maxRangeM = 120.0, windBufferM = 10.0, effectiveRangeM = 60.0),
            ),
        )

        val tables = ReportTables.build(report, UnitSystem.IMPERIAL)

        assertEquals(2, tables.configs.size)
        assertEquals("A", tables.configs[0].letter)
        assertEquals("B", tables.configs[1].letter)

        val changed = tables.configs[1].changed
        val expectedChangedIndices = setOf(2, 7, 8, 9) // Velocity, Max range, Fan radius, Effective range
        for (i in changed.indices) {
            assertEquals("column $i changed flag", i in expectedChangedIndices, changed[i])
        }
        // Sanity: the underlying values that should differ actually do, and those that shouldn't don't.
        assertNotEquals(tables.configs[0].cells[2], tables.configs[1].cells[2])
        assertNotEquals(tables.configs[0].cells[7], tables.configs[1].cells[7])
        assertNotEquals(tables.configs[0].cells[8], tables.configs[1].cells[8])
        assertNotEquals(tables.configs[0].cells[9], tables.configs[1].cells[9])
        assertEquals(tables.configs[0].cells[6], tables.configs[1].cells[6])
    }

    @Test
    fun `unitSystem-only change gives one configuration`() {
        val settingsImperial = LoadSettings(unitSystem = UnitSystem.IMPERIAL)
        val settingsMetric = LoadSettings(unitSystem = UnitSystem.METRIC)
        val report = RangeReport(
            positions = listOf(
                position(1, settingsImperial),
                position(2, settingsMetric),
            ),
        )

        val tables = ReportTables.build(report, UnitSystem.IMPERIAL)

        assertEquals(1, tables.configs.size)
    }

    @Test
    fun `legacy positions with equal ranges share one configuration and print em dash load cells`() {
        val report = RangeReport(
            positions = listOf(
                position(1, settings = null, maxRangeM = 100.0, windBufferM = 10.0, effectiveRangeM = 50.0),
                position(2, settings = null, maxRangeM = 100.0, windBufferM = 10.0, effectiveRangeM = 50.0),
            ),
        )

        val tables = ReportTables.build(report, UnitSystem.IMPERIAL)

        assertEquals(1, tables.configs.size)
        val cells = tables.configs[0].cells
        // Shot, Material, Velocity, Choke, Min. energy, Wind
        for (i in 0..5) {
            assertEquals("—", cells[i])
        }
    }

    @Test
    fun `a position with three fans has lineCount 3 and one with none prints None`() {
        val threeFans = listOf(
            ReportFan(0.0, 10.0, 0.0, 10.0, fullCircle = false),
            ReportFan(90.0, 100.0, 90.0, 100.0, fullCircle = false),
            ReportFan(180.0, 190.0, 180.0, 190.0, fullCircle = false),
        )
        val report = RangeReport(
            positions = listOf(
                position(1, LoadSettings(), fans = threeFans),
                position(2, LoadSettings(), fans = emptyList()),
            ),
        )

        val tables = ReportTables.build(report, UnitSystem.IMPERIAL)

        val withFans = tables.positions[0]
        val withoutFans = tables.positions[1]
        assertEquals(3, withFans.fanLines.size)
        assertEquals(3, maxOf(1, withFans.fanLines.size))
        assertEquals(listOf("None"), withoutFans.fanLines)
        assertEquals(1, maxOf(1, withoutFans.fanLines.size))
    }

    @Test
    fun `shot numbering runs continuously across positions`() {
        val shots1 = listOf(ReportShot(0.0), ReportShot(10.0))
        val shots2 = listOf(ReportShot(20.0), ReportShot(30.0), ReportShot(40.0))
        val report = RangeReport(
            positions = listOf(
                position(1, LoadSettings(), shots = shots1),
                position(2, LoadSettings(), shots = shots2),
            ),
        )

        val tables = ReportTables.build(report, UnitSystem.IMPERIAL)

        assertEquals(listOf("1", "2", "3", "4", "5"), tables.shots.map { it.number })
        assertEquals(listOf("P1", "P1", "P2", "P2", "P2"), tables.shots.map { it.position })
    }

    @Test
    fun `time is em dash for zero timestamp`() {
        val shots = listOf(
            ReportShot(0.0, timestampMs = 0L),
            ReportShot(10.0, timestampMs = 1_700_000_000_000L),
        )
        val report = RangeReport(positions = listOf(position(1, LoadSettings(), shots = shots)))

        val tables = ReportTables.build(report, UnitSystem.IMPERIAL)

        assertEquals("—", tables.shots[0].time)
        assertNotEquals("—", tables.shots[1].time)
    }

    @Test
    fun `a 2-position 5-shot report paginates to one page`() {
        val settings = LoadSettings()
        val report = RangeReport(
            positions = listOf(
                position(1, settings, shots = listOf(ReportShot(0.0), ReportShot(10.0))),
                position(2, settings, shots = listOf(ReportShot(20.0), ReportShot(30.0), ReportShot(40.0))),
            ),
        )

        val tables = ReportTables.build(report, UnitSystem.IMPERIAL)
        val pages = paginate(tables)

        assertEquals(1, pages.size)
    }

    @Test
    fun `a 1-position 150-shot report paginates to more than one page with every shot covered once`() {
        val shots = (0 until 150).map { ReportShot(bearingTrueDeg = it.toDouble()) }
        val report = RangeReport(positions = listOf(position(1, LoadSettings(), shots = shots)))

        val tables = ReportTables.build(report, UnitSystem.IMPERIAL)
        val pages = paginate(tables)

        assertTrue("expected more than one page, got ${pages.size}", pages.size > 1)

        val page2ShotSlices = pages[1].filterIsInstance<Block.TableSlice>()
            .filter { it.section == Section.SHOTS }
        assertTrue(
            "expected a continued SHOTS slice on page 2",
            page2ShotSlices.any { it.continued },
        )

        val covered = BooleanArray(150)
        var totalRows = 0
        for (page in pages) {
            for (block in page.filterIsInstance<Block.TableSlice>()) {
                if (block.section != Section.SHOTS) continue
                for (row in block.fromRow..block.toRow) {
                    assertEquals("row $row covered more than once", false, covered[row])
                    covered[row] = true
                    totalRows++
                }
            }
        }
        assertEquals(150, totalRows)
        assertTrue(covered.all { it })
    }

    @Test
    fun `an empty report yields exactly one page`() {
        val tables = ReportTables.build(RangeReport(positions = emptyList()), UnitSystem.IMPERIAL)

        val pages = paginate(tables)

        assertEquals(1, pages.size)
        val emptyNotices = pages[0].filterIsInstance<Block.EmptyNotice>()
        assertEquals(3, emptyNotices.size)
        assertTrue(emptyNotices.all { it.text == "None recorded" })
    }
}
