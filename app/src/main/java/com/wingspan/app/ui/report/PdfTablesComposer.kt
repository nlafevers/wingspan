package com.wingspan.app.ui.report

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import kotlin.math.max

/**
 * Draws one table page (a run of [Block]s from [paginate]) using the exact row/heading/header
 * heights [paginate] used to compute the page split, so the drawn layout matches the computed
 * one. This object only starts and finishes the one page it is given; [PdfReportComposer] owns
 * the [PdfDocument] itself.
 */
object PdfTablesComposer {

    private const val PAGE_WIDTH_PT = 612
    private const val PAGE_HEIGHT_PT = 792

    private const val LEFT_X = 36f
    private const val TABLE_WIDTH = 540f

    // Mirrors the pagination metrics computed in ReportTables.kt (WS-11.6) exactly.
    private const val CONTENT_TOP = 72f
    private const val TITLE_HEIGHT = 30f
    private const val HEADING_HEIGHT = 20f
    private const val HEADER_HEIGHT = 14f
    private const val LINE_HEIGHT = 12f
    private const val SECTION_GAP = 12f

    private val CONFIG_COLUMNS = listOf(
        "Cfg" to 24f,
        "Shot" to 36f,
        "Material" to 60f,
        "Velocity" to 52f,
        "Choke" to 72f,
        "Min. energy" to 52f,
        "Wind" to 40f,
        "Wind buffer" to 50f,
        "Max range" to 52f,
        "Fan radius" to 50f,
        "Effective range" to 52f,
    )

    private val POSITION_COLUMNS = listOf(
        "Pos" to 30f,
        "Cfg" to 26f,
        "Time" to 50f,
        "Coordinates" to 120f,
        "Source" to 70f,
        "Fans (magnetic)" to 204f,
        "Shots" to 40f,
    )

    private val SHOT_COLUMNS = listOf(
        "#" to 30f,
        "Pos" to 36f,
        "Time" to 60f,
        "Bearing (mag)" to 70f,
        "Label" to 344f,
    )

    private fun columnsFor(section: Section): List<Pair<String, Float>> = when (section) {
        Section.CONFIGS -> CONFIG_COLUMNS
        Section.POSITIONS -> POSITION_COLUMNS
        Section.SHOTS -> SHOT_COLUMNS
    }

    private fun headingFor(section: Section): String = when (section) {
        Section.CONFIGS -> "Load configurations"
        Section.POSITIONS -> "Firing positions"
        Section.SHOTS -> "Shots"
    }

    fun writePage(
        document: PdfDocument,
        tables: ReportTables,
        blocks: List<Block>,
        pageNumber: Int,
        pageCount: Int,
        timestampMs: Long,
    ) {
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT, PAGE_HEIGHT_PT, pageNumber).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        drawPageFooter(canvas, pageNumber, pageCount, timestampMs)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#212121")
            textSize = 14f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#212121")
            textSize = 10f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#212121")
            textSize = 8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#212121")
            textSize = 8f
            typeface = Typeface.SANS_SERIF
        }
        val bodyBoldPaint = Paint(bodyPaint).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#BDBDBD")
            strokeWidth = 0.5f
        }
        val headerBandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#EEEEEE")
        }

        var y = CONTENT_TOP
        var isFirstBlockOnPage = true

        for (block in blocks) {
            when (block) {
                is Block.Title -> {
                    canvas.drawText("Firing record", LEFT_X, y + 14f, titlePaint)
                    y += TITLE_HEIGHT
                }

                is Block.SectionHeading -> {
                    val gap = if (block.section == Section.CONFIGS || isFirstBlockOnPage) 0f else SECTION_GAP
                    y += gap
                    val text = headingFor(block.section) + if (block.continued) " (continued)" else ""
                    canvas.drawText(text, LEFT_X, y + 10f, headingPaint)
                    y += HEADING_HEIGHT
                }

                is Block.HeaderRow -> {
                    val columns = columnsFor(block.section)
                    canvas.drawRect(RectF(LEFT_X, y, LEFT_X + TABLE_WIDTH, y + HEADER_HEIGHT), headerBandPaint)
                    var x = LEFT_X
                    for ((label, width) in columns) {
                        drawCell(canvas, label, x + 3f, y + 10f, width - 3f, headerPaint)
                        x += width
                    }
                    y += HEADER_HEIGHT
                }

                is Block.TableSlice -> {
                    y = drawTableSlice(canvas, tables, block, y, bodyPaint, bodyBoldPaint, rulePaint)
                }

                is Block.EmptyNotice -> {
                    canvas.drawText(block.text, LEFT_X, y + 9f, bodyPaint)
                    y += LINE_HEIGHT
                }
            }
            isFirstBlockOnPage = false
        }

        document.finishPage(page)
    }

    private fun drawTableSlice(
        canvas: Canvas,
        tables: ReportTables,
        slice: Block.TableSlice,
        startY: Float,
        bodyPaint: Paint,
        bodyBoldPaint: Paint,
        rulePaint: Paint,
    ): Float {
        var y = startY
        val columns = columnsFor(slice.section)
        for (row in slice.fromRow..slice.toRow) {
            val rowHeight = when (slice.section) {
                Section.CONFIGS -> LINE_HEIGHT
                Section.SHOTS -> LINE_HEIGHT
                Section.POSITIONS -> max(1, tables.positions[row].fanLines.size) * LINE_HEIGHT
            }

            when (slice.section) {
                Section.CONFIGS -> drawConfigRow(canvas, tables.configs[row], columns, y, bodyPaint, bodyBoldPaint)
                Section.POSITIONS -> drawPositionRow(canvas, tables.positions[row], columns, y, bodyPaint)
                Section.SHOTS -> drawShotRow(canvas, tables.shots[row], columns, y, bodyPaint)
            }

            canvas.drawLine(LEFT_X, y + rowHeight, LEFT_X + TABLE_WIDTH, y + rowHeight, rulePaint)
            y += rowHeight
        }
        return y
    }

    private fun drawConfigRow(
        canvas: Canvas,
        row: ConfigRow,
        columns: List<Pair<String, Float>>,
        y: Float,
        bodyPaint: Paint,
        bodyBoldPaint: Paint,
    ) {
        var x = LEFT_X
        val (letterLabel, letterWidth) = columns[0]
        drawCell(canvas, row.letter, x + 3f, y + 9f, letterWidth - 3f, bodyPaint)
        x += letterWidth

        for (i in row.cells.indices) {
            val (_, width) = columns[i + 1]
            val paint = if (row.changed.getOrNull(i) == true) bodyBoldPaint else bodyPaint
            drawCell(canvas, row.cells[i], x + 3f, y + 9f, width - 3f, paint)
            x += width
        }
    }

    private fun drawPositionRow(
        canvas: Canvas,
        row: PositionRow,
        columns: List<Pair<String, Float>>,
        y: Float,
        bodyPaint: Paint,
    ) {
        val values = listOf(row.label, row.config, row.time, row.coords, row.source)
        var x = LEFT_X
        for (i in values.indices) {
            val (_, width) = columns[i]
            drawCell(canvas, values[i], x + 3f, y + 9f, width - 3f, bodyPaint)
            x += width
        }

        val fansX = x
        val fansWidth = columns[5].second
        row.fanLines.forEachIndexed { index, line ->
            drawCell(canvas, line, fansX + 3f, y + 9f + index * LINE_HEIGHT, fansWidth - 3f, bodyPaint)
        }
        x += fansWidth

        val shotsWidth = columns[6].second
        drawCell(canvas, row.shots, x + 3f, y + 9f, shotsWidth - 3f, bodyPaint)
    }

    private fun drawShotRow(
        canvas: Canvas,
        row: ShotRow,
        columns: List<Pair<String, Float>>,
        y: Float,
        bodyPaint: Paint,
    ) {
        val values = listOf(row.number, row.position, row.time, row.bearing, row.label)
        var x = LEFT_X
        for (i in values.indices) {
            val (_, width) = columns[i]
            drawCell(canvas, values[i], x + 3f, y + 9f, width - 3f, bodyPaint)
            x += width
        }
    }

    /** Draws [text] at [x],[y], cutting it with [Paint.breakText] and an ellipsis if it overflows [maxWidth]. */
    private fun drawCell(canvas: Canvas, text: String, x: Float, y: Float, maxWidth: Float, paint: Paint) {
        if (text.isEmpty()) return
        if (paint.measureText(text) <= maxWidth) {
            canvas.drawText(text, x, y, paint)
            return
        }
        val ellipsis = "…"
        val ellipsisWidth = paint.measureText(ellipsis)
        val available = (maxWidth - ellipsisWidth).coerceAtLeast(0f)
        val count = paint.breakText(text, true, available, null)
        canvas.drawText(text.substring(0, count) + ellipsis, x, y, paint)
    }
}
