package com.wingspan.app.ui.report

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.wingspan.app.domain.ballistics.UnitSystem
import com.wingspan.app.domain.report.RangeReport
import com.wingspan.app.domain.report.ReportPosition
import com.wingspan.app.ui.Formatters

/**
 * Renders the "Model and assumptions" appendix as the last page of the exported PDF. The caller
 * owns the [PdfDocument] (creation, [PdfDocument.writeTo] and [PdfDocument.close]); this object
 * only starts and finishes the one page it draws.
 */
object PdfAppendixComposer {

    private const val PAGE_WIDTH_PT = 612
    private const val PAGE_HEIGHT_PT = 792

    private const val LEFT_X = 36f
    private const val RIGHT_X = 576f
    private const val CONTENT_WIDTH = RIGHT_X - LEFT_X
    private const val TRUNCATE_Y = 744f
    private const val BODY_LEADING = 12f
    private const val SECTION_GAP = 10f

    fun writePage(
        document: PdfDocument,
        report: RangeReport,
        configLetters: Map<Long, String>,
        units: UnitSystem,
        timestampMs: Long,
        pageNumber: Int,
        pageCount: Int,
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
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#212121")
            textSize = 9f
            typeface = Typeface.SANS_SERIF
        }
        val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#9E9E9E")
            strokeWidth = 0.75f
        }

        canvas.drawText("Model and assumptions", LEFT_X, 72f, titlePaint)

        // One representative position per configuration letter, in order of first appearance.
        val configEntries = LinkedHashMap<String, ReportPosition>()
        for (position in report.positions) {
            val letter = configLetters[position.id] ?: continue
            if (!configEntries.containsKey(letter)) configEntries[letter] = position
        }
        val configCount = configEntries.size
        val firstPosition = report.positions.firstOrNull()

        var y = 96f

        y = drawSection(canvas, "Maximum range", AppendixText.MAX_RANGE, headingPaint, bodyPaint, y)

        val primaryConfig = configEntries.values.firstOrNull() ?: firstPosition
        val limiter = primaryConfig?.effectiveRangeLimiter ?: ""
        val threshold = primaryConfig?.settings?.energyThresholdFtLbf ?: 0.0
        val effectiveRangeText = AppendixText.effectiveRange(limiter, threshold, units)
        val effectiveRangeLines = if (configCount > 1) {
            configEntries.map { (letter, position) ->
                AppendixText.effectiveRangeLimiterLine(
                    letter,
                    position.effectiveRangeLimiter,
                    position.settings?.energyThresholdFtLbf,
                    units,
                )
            }
        } else {
            emptyList()
        }
        y = drawSection(canvas, "Effective range", effectiveRangeText, headingPaint, bodyPaint, y, effectiveRangeLines)

        y = drawSection(canvas, "Fan geometry", AppendixText.FAN_GEOMETRY, headingPaint, bodyPaint, y)

        fun windSpeedMph(position: ReportPosition): Double = position.settings?.windSpeedMph ?: 0.0

        val windyConfig = configEntries.values.firstOrNull { AppendixText.windApplies(windSpeedMph(it)) }
        if (windyConfig != null) {
            val windText = AppendixText.wind(windSpeedMph(windyConfig), windyConfig.windBufferM, units)
            val windLines = if (configCount > 1) {
                configEntries.map { (letter, position) ->
                    val speed = windSpeedMph(position)
                    if (AppendixText.windApplies(speed)) {
                        "Configuration $letter: wind ${Formatters.windSpeed(speed, units)}, " +
                            "buffer ${Formatters.distance(position.windBufferM, units)}"
                    } else {
                        "Configuration $letter: no wind, buffer ${Formatters.distance(position.windBufferM, units)}"
                    }
                }
            } else {
                emptyList()
            }
            y = drawSection(canvas, "Wind buffer", windText, headingPaint, bodyPaint, y, windLines)
        }

        y = drawSection(canvas, "Bearings", AppendixText.BEARINGS, headingPaint, bodyPaint, y)

        canvas.drawText("Firing positions", LEFT_X, y, headingPaint)
        y += BODY_LEADING
        val positions = report.positions
        if (positions.isEmpty()) {
            y = drawWrapped(canvas, "No firing positions recorded.", bodyPaint, LEFT_X, y, CONTENT_WIDTH, BODY_LEADING)
        } else {
            for ((index, position) in positions.withIndex()) {
                if (y > TRUNCATE_Y) {
                    canvas.drawText("... and ${positions.size - index} more", LEFT_X, y, bodyPaint)
                    y += BODY_LEADING
                    break
                }
                val configArg = if (configCount > 1) configLetters[position.id] else null
                val line = AppendixText.positionLine(
                    "P${index + 1}",
                    position.positionSource,
                    position.accuracyM,
                    position.declinationDeg,
                    units,
                    configArg,
                )
                y = drawWrapped(canvas, line, bodyPaint, LEFT_X, y, CONTENT_WIDTH, BODY_LEADING)
            }
        }
        y += SECTION_GAP

        canvas.drawLine(LEFT_X, y, RIGHT_X, y, rulePaint)
        y += SECTION_GAP
        y = drawSection(canvas, "Disclaimer", AppendixText.DISCLAIMER, headingPaint, bodyPaint, y)

        document.finishPage(page)
    }

    private fun drawSection(
        canvas: Canvas,
        heading: String,
        body: String,
        headingPaint: Paint,
        bodyPaint: Paint,
        startY: Float,
        extraLines: List<String> = emptyList(),
    ): Float {
        var y = startY
        canvas.drawText(heading, LEFT_X, y, headingPaint)
        y += BODY_LEADING
        y = drawWrapped(canvas, body, bodyPaint, LEFT_X, y, CONTENT_WIDTH, BODY_LEADING)
        for (line in extraLines) {
            y = drawWrapped(canvas, line, bodyPaint, LEFT_X, y, CONTENT_WIDTH, BODY_LEADING)
        }
        y += SECTION_GAP
        return y
    }

    private fun drawWrapped(
        canvas: Canvas,
        text: String,
        paint: Paint,
        x: Float,
        y: Float,
        maxWidth: Float,
        leading: Float,
    ): Float {
        val words = text.split(" ")
        var cursorY = y
        var line = StringBuilder()
        for (word in words) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (line.isNotEmpty() && paint.measureText(candidate) > maxWidth) {
                canvas.drawText(line.toString(), x, cursorY, paint)
                cursorY += leading
                line = StringBuilder(word)
            } else {
                line = StringBuilder(candidate)
            }
        }
        if (line.isNotEmpty()) {
            canvas.drawText(line.toString(), x, cursorY, paint)
            cursorY += leading
        }
        return cursorY
    }
}
