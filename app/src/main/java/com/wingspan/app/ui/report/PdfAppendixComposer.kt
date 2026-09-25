package com.wingspan.app.ui.report

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.wingspan.app.domain.ballistics.LoadSettings
import com.wingspan.app.domain.report.RangeReport
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders the "Model and assumptions" appendix as page 2 of the exported PDF. The caller owns the
 * [PdfDocument] (creation, [PdfDocument.writeTo] and [PdfDocument.close]); this object only starts
 * and finishes the one page it draws.
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

    fun writePage(document: PdfDocument, report: RangeReport, settings: LoadSettings, timestampMs: Long) {
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT, PAGE_HEIGHT_PT, 2).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val units = settings.unitSystem

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#212121")
            textSize = 14f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#616161")
            textSize = 9f
            typeface = Typeface.SANS_SERIF
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

        val timestampFormat = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.US)
        canvas.drawText("Page 2 of 2 · ${timestampFormat.format(Date(timestampMs))}", LEFT_X, 88f, metaPaint)

        val firstPosition = report.positions.firstOrNull()
        var y = 116f

        y = drawSection(canvas, "Maximum range", AppendixText.MAX_RANGE, headingPaint, bodyPaint, y)

        val limiter = firstPosition?.effectiveRangeLimiter ?: ""
        val effectiveRangeText = AppendixText.effectiveRange(limiter, settings.energyThresholdFtLbf, units)
        y = drawSection(canvas, "Effective range", effectiveRangeText, headingPaint, bodyPaint, y)

        y = drawSection(canvas, "Fan geometry", AppendixText.FAN_GEOMETRY, headingPaint, bodyPaint, y)

        if (AppendixText.windApplies(settings.windSpeedMph) && firstPosition != null) {
            val windText = AppendixText.wind(settings.windSpeedMph, firstPosition.windBufferM, units)
            y = drawSection(canvas, "Wind buffer", windText, headingPaint, bodyPaint, y)
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
                val line = AppendixText.positionLine(
                    "P${index + 1}",
                    position.positionSource,
                    position.accuracyM,
                    position.declinationDeg,
                    units,
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
    ): Float {
        var y = startY
        canvas.drawText(heading, LEFT_X, y, headingPaint)
        y += BODY_LEADING
        y = drawWrapped(canvas, body, bodyPaint, LEFT_X, y, CONTENT_WIDTH, BODY_LEADING)
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
