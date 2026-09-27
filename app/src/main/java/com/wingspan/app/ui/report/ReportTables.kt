package com.wingspan.app.ui.report

import com.wingspan.app.domain.ballistics.LoadSettings
import com.wingspan.app.domain.ballistics.PelletMaterial
import com.wingspan.app.domain.ballistics.ShotSize
import com.wingspan.app.domain.ballistics.UnitSystem
import com.wingspan.app.domain.ballistics.Units
import com.wingspan.app.domain.geo.Geometry2D
import com.wingspan.app.domain.report.RangeReport
import com.wingspan.app.domain.report.ReportPosition
import com.wingspan.app.ui.Formatters
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * Pure Kotlin report tables and pagination, mirroring [AppendixText]'s arrangement: all layout
 * text and page counting run here with zero `android.*`/`androidx.*` imports so they can be unit
 * tested on the JVM without a device. Text is produced through [Formatters] in the export-time
 * [UnitSystem].
 */

/** One distinct load/range configuration, lettered "A", "B", ... in order of first appearance. */
data class ConfigRow(
    val letter: String,
    val cells: List<String>,
    val changed: List<Boolean>,
)

/** One firing position row. [fanLines] holds one entry per fan, or a single "None". */
data class PositionRow(
    val label: String,
    val config: String,
    val time: String,
    val coords: String,
    val source: String,
    val fanLines: List<String>,
    val shots: String,
)

/** One shot row, numbered continuously across the whole report. */
data class ShotRow(
    val number: String,
    val position: String,
    val time: String,
    val bearing: String,
    val label: String,
)

data class ReportTables(
    val configs: List<ConfigRow>,
    val positions: List<PositionRow>,
    val shots: List<ShotRow>,
) {
    companion object {

        fun build(report: RangeReport, units: UnitSystem): ReportTables = ReportTables(
            configs = buildConfigs(report, units),
            positions = buildPositions(report, units),
            shots = buildShots(report, units),
        )

        /** Position id to configuration letter, for the map page's labels. */
        fun configLetterFor(report: RangeReport): Map<Long, String> {
            val letterByKey = groupConfigs(report).mapIndexed { index, (key, _) -> key to letterFor(index) }.toMap()
            return report.positions.associate { it.id to (letterByKey[keyOf(it)] ?: "") }
        }

        private fun buildConfigs(report: RangeReport, units: UnitSystem): List<ConfigRow> {
            var previousCells: List<String>? = null
            return groupConfigs(report).mapIndexed { index, (key, first) ->
                val cells = cellsFor(key, first, units)
                val previous = previousCells
                val changed = if (previous == null) {
                    List(cells.size) { false }
                } else {
                    cells.indices.map { i -> cells[i] != previous[i] }
                }
                previousCells = cells
                ConfigRow(letterFor(index), cells, changed)
            }
        }

        private fun buildPositions(report: RangeReport, units: UnitSystem): List<PositionRow> {
            val letters = configLetterFor(report)
            val timeFormat = SimpleDateFormat("h:mm a", Locale.US)
            return report.positions.mapIndexed { index, position ->
                val fanLines = if (position.fans.isEmpty()) {
                    listOf("None")
                } else {
                    position.fans.map { fan ->
                        if (fan.fullCircle) {
                            "All directions"
                        } else {
                            "${Formatters.bearing(fan.leftMagDeg)}–${Formatters.bearing(fan.rightMagDeg)}"
                        }
                    }
                }
                val source = when {
                    position.positionSource == "GPS" && position.accuracyM != null ->
                        "GPS ±${Formatters.distance(position.accuracyM, units)}"
                    position.positionSource == "GPS" -> "GPS"
                    position.positionSource == "MANUAL" -> "Manual"
                    else -> position.positionSource
                }
                PositionRow(
                    label = "P${index + 1}",
                    config = letters[position.id] ?: "",
                    time = timeFormat.format(Date(position.timestampMs)),
                    coords = "%.5f, %.5f".format(position.position.lat, position.position.lon),
                    source = source,
                    fanLines = fanLines,
                    shots = position.shots.size.toString(),
                )
            }
        }

        private fun buildShots(report: RangeReport, units: UnitSystem): List<ShotRow> {
            val timeFormat = SimpleDateFormat("h:mm a", Locale.US)
            val rows = mutableListOf<ShotRow>()
            var number = 1
            report.positions.forEachIndexed { index, position ->
                val positionLabel = "P${index + 1}"
                position.shots.forEach { shot ->
                    val time = if (shot.timestampMs == 0L) {
                        "—"
                    } else {
                        timeFormat.format(Date(shot.timestampMs))
                    }
                    val bearingDeg = Geometry2D.normalizeBearing(shot.bearingTrueDeg - position.declinationDeg)
                    rows.add(
                        ShotRow(
                            number = number.toString(),
                            position = positionLabel,
                            time = time,
                            bearing = Formatters.bearing(bearingDeg),
                            label = shot.label,
                        ),
                    )
                    number++
                }
            }
            return rows
        }

        /** The configuration grouping key: normalized settings, or ranges alone when legacy. */
        private data class ConfigKey(
            val settings: LoadSettings?,
            val legacyRanges: Triple<Double, Double, Double>?,
        )

        private fun keyOf(position: ReportPosition): ConfigKey =
            if (position.settings != null) {
                ConfigKey(position.settings.copy(unitSystem = UnitSystem.IMPERIAL), null)
            } else {
                ConfigKey(null, Triple(position.maxRangeM, position.windBufferM, position.effectiveRangeM))
            }

        /** Distinct configuration keys with their first-seen position, in order of first appearance. */
        private fun groupConfigs(report: RangeReport): List<Pair<ConfigKey, ReportPosition>> {
            val seen = LinkedHashMap<ConfigKey, ReportPosition>()
            for (position in report.positions) {
                val key = keyOf(position)
                if (!seen.containsKey(key)) seen[key] = position
            }
            return seen.entries.map { it.key to it.value }
        }

        /** Spreadsheet-style letters: A, B, ..., Z, AA, AB, ... */
        private fun letterFor(index: Int): String {
            var n = index
            val sb = StringBuilder()
            do {
                sb.insert(0, 'A' + (n % 26))
                n = n / 26 - 1
            } while (n >= 0)
            return sb.toString()
        }

        /** Shot, Material, Velocity, Choke, Min. energy, Wind, Wind buffer, Max range, Fan radius, Effective range. */
        private fun cellsFor(key: ConfigKey, first: ReportPosition, units: UnitSystem): List<String> {
            val settings = key.settings
            val loadCells = if (settings != null) {
                val shotCell = if (settings.shotSize == ShotSize.CUSTOM) {
                    "Custom (%.3f in)".format(settings.customDiameterInches)
                } else {
                    settings.shotSize.label
                }
                val materialCell = if (settings.material == PelletMaterial.CUSTOM) {
                    "Custom (%.2f g/cc)".format(settings.customDensityGcc)
                } else {
                    settings.material.label
                }
                listOf(
                    shotCell,
                    materialCell,
                    Formatters.velocity(settings.muzzleVelocityFps, units),
                    settings.choke.label,
                    Formatters.energy(Units.ftLbfToJoules(settings.energyThresholdFtLbf), units),
                    Formatters.windSpeed(settings.windSpeedMph, units),
                )
            } else {
                listOf("—", "—", "—", "—", "—", "—")
            }
            return loadCells + listOf(
                Formatters.distance(first.windBufferM, units),
                Formatters.distance(first.maxRangeM, units),
                Formatters.distance(first.fanRangeM, units),
                Formatters.distance(first.effectiveRangeM, units),
            )
        }
    }
}

/** Which table a pagination [Block] belongs to. */
enum class Section { CONFIGS, POSITIONS, SHOTS }

/** One layout element on a page, in the order they should be drawn. */
sealed interface Block {
    /** The 30pt page title, drawn once at the top of the first page only. */
    data object Title : Block

    /** A section's heading, repeated with [continued] true whenever the section resumes on a new page. */
    data class SectionHeading(val section: Section, val continued: Boolean) : Block

    /** A table's header row, repeated on every page a section's table spans. */
    data class HeaderRow(val section: Section) : Block

    /** A contiguous run of a section's rows, `[fromRow, toRow]` inclusive, 0-based. */
    data class TableSlice(val section: Section, val fromRow: Int, val toRow: Int, val continued: Boolean) : Block

    /** Printed instead of a header/table when a section has no rows at all. */
    data class EmptyNotice(val section: Section, val text: String = "None recorded") : Block
}

private const val CONTENT_TOP = 72.0
private const val CONTENT_BOTTOM = 756.0
private const val PAGE_HEIGHT = CONTENT_BOTTOM - CONTENT_TOP
private const val TITLE_HEIGHT = 30.0
private const val HEADING_HEIGHT = 20.0
private const val HEADER_HEIGHT = 14.0
private const val LINE_HEIGHT = 12.0
private const val SECTION_GAP = 12.0

/**
 * Lays [tables] out across pages using fixed row/heading heights (see the constants above), never
 * splitting a single position row across a page break. This lets the PDF's page count be computed
 * without ever measuring text.
 */
fun paginate(tables: ReportTables): List<List<Block>> {
    val configRowHeights = tables.configs.map { LINE_HEIGHT }
    val positionRowHeights = tables.positions.map { max(1, it.fanLines.size) * LINE_HEIGHT }
    val shotRowHeights = tables.shots.map { LINE_HEIGHT }

    val sections = listOf(
        Section.CONFIGS to configRowHeights,
        Section.POSITIONS to positionRowHeights,
        Section.SHOTS to shotRowHeights,
    )

    val pages = mutableListOf(mutableListOf<Block>(Block.Title))
    var remaining = PAGE_HEIGHT - TITLE_HEIGHT

    fun startNewPage() {
        pages.add(mutableListOf())
        remaining = PAGE_HEIGHT
    }

    var previousSectionEndedFreshPage = true

    sections.forEachIndexed { sectionIndex, (section, rowHeights) ->
        val gap = if (sectionIndex == 0 || previousSectionEndedFreshPage) 0.0 else SECTION_GAP

        if (rowHeights.isEmpty()) {
            val needed = gap + HEADING_HEIGHT + LINE_HEIGHT
            if (remaining < needed) {
                startNewPage()
            } else {
                remaining -= gap
            }
            pages.last().add(Block.SectionHeading(section, continued = false))
            pages.last().add(Block.EmptyNotice(section))
            remaining -= (HEADING_HEIGHT + LINE_HEIGHT)
            previousSectionEndedFreshPage = false
            return@forEachIndexed
        }

        var fromIndex = 0
        var segmentStart = true
        while (fromIndex < rowHeights.size) {
            val leadingGap = if (segmentStart) gap else 0.0
            val needed = leadingGap + HEADING_HEIGHT + HEADER_HEIGHT + rowHeights[fromIndex]
            if (remaining < needed) {
                startNewPage()
            } else {
                remaining -= leadingGap
            }
            pages.last().add(Block.SectionHeading(section, continued = !segmentStart))
            pages.last().add(Block.HeaderRow(section))
            remaining -= (HEADING_HEIGHT + HEADER_HEIGHT)

            var toIndex = fromIndex
            var used = 0.0
            while (toIndex < rowHeights.size && (toIndex == fromIndex || used + rowHeights[toIndex] <= remaining)) {
                used += rowHeights[toIndex]
                toIndex++
            }
            pages.last().add(Block.TableSlice(section, fromIndex, toIndex - 1, continued = !segmentStart))
            remaining -= used
            fromIndex = toIndex
            segmentStart = false
        }
        previousSectionEndedFreshPage = false
    }

    return pages
}
