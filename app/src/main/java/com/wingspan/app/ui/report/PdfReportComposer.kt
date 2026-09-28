package com.wingspan.app.ui.report

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.wingspan.app.domain.ballistics.LoadSettings
import com.wingspan.app.domain.ballistics.UnitSystem
import com.wingspan.app.domain.geo.EnuProjection
import com.wingspan.app.domain.geo.Geometry2D
import com.wingspan.app.domain.geo.LatLon
import com.wingspan.app.domain.geo.NoFireLine
import com.wingspan.app.domain.geo.NoFireMarker
import com.wingspan.app.domain.geo.NoFirePolygon
import com.wingspan.app.domain.geo.NoFireZone
import com.wingspan.app.domain.geo.Sector
import com.wingspan.app.domain.geo.trueToMagnetic
import com.wingspan.app.domain.report.RangeReport
import com.wingspan.app.domain.report.ReportLayout
import com.wingspan.app.domain.report.ReportLayout.MapFrame
import com.wingspan.app.domain.report.ReportPosition
import com.wingspan.app.ui.Formatters
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.min

/**
 * Renders a [RangeReport] to a multi-page PDF via the platform [PdfDocument] API: a map page,
 * then a run of table pages built from [ReportTables], then the appendix. This object is the sole
 * owner of the [PdfDocument] (create, [PdfDocument.writeTo], [PdfDocument.close]); the table and
 * appendix pages are drawn by [PdfTablesComposer] and [PdfAppendixComposer], each of which only
 * starts and finishes the one page it is given.
 */
object PdfReportComposer {

    private const val PAGE_WIDTH_PT = 612
    private const val PAGE_HEIGHT_PT = 792

    private val SQUARE = RectF(36f, 36f, 576f, 576f)

    fun write(
        output: OutputStream,
        report: RangeReport,
        zones: List<NoFireZone>,
        settings: LoadSettings,
        background: Bitmap?,
        timestampMs: Long,
    ) {
        val units = settings.unitSystem
        val tables = ReportTables.build(report, units)
        val tablePages = paginate(tables)
        val pageCount = 1 + tablePages.size + 1
        val configLetters = ReportTables.configLetterFor(report)

        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT, PAGE_HEIGHT_PT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        drawPageFooter(canvas, 1, pageCount, timestampMs)

        val frame = ReportLayout.frameFor(report)
        if (frame == null) {
            drawEmptyMapMessage(canvas)
        } else {
            canvas.save()
            canvas.clipRect(SQUARE)
            drawMapBackground(canvas, background)
            drawSquareBorder(canvas)
            drawZones(canvas, frame, zones)
            drawFansAndArcs(canvas, frame, report)
            drawShots(canvas, frame, report)
            drawPositionDots(canvas, frame, report, configLetters, tables.configs.size)
            drawFanLabels(canvas, frame, report, units)
            drawShotLabels(canvas, frame, report)
            canvas.restore()
            drawNorthArrow(canvas)
            drawScaleBar(canvas, frame, units)
        }

        drawSummaryBlock(canvas, report, tables, timestampMs)

        document.finishPage(page)

        tablePages.forEachIndexed { index, blocks ->
            PdfTablesComposer.writePage(document, tables, blocks, pageNumber = 2 + index, pageCount = pageCount, timestampMs = timestampMs)
        }

        PdfAppendixComposer.writePage(
            document,
            report,
            configLetters,
            units,
            timestampMs,
            pageNumber = pageCount,
            pageCount = pageCount,
        )

        document.writeTo(output)
        document.close()
    }

    // ---------------------------------------------------------------------
    // Map square
    // ---------------------------------------------------------------------

    private fun drawMapBackground(canvas: Canvas, background: Bitmap?) {
        if (background != null) {
            canvas.drawBitmap(background, null, SQUARE, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        } else {
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.parseColor("#F5F5F5")
            }
            canvas.drawRect(SQUARE, fill)
        }
    }

    private fun drawSquareBorder(canvas: Canvas) {
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#9E9E9E")
            strokeWidth = 0.75f
        }
        canvas.drawRect(SQUARE, border)
    }

    private fun drawEmptyMapMessage(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#212121")
            textSize = 14f
            typeface = Typeface.SANS_SERIF
            textAlign = Paint.Align.CENTER
        }
        val cx = (SQUARE.left + SQUARE.right) / 2f
        val cy = (SQUARE.top + SQUARE.bottom) / 2f
        canvas.drawText("No firing positions recorded", cx, cy, paint)
    }

    // ---------------------------------------------------------------------
    // Zones
    // ---------------------------------------------------------------------

    private fun drawZones(canvas: Canvas, frame: MapFrame, zones: List<NoFireZone>) {
        val fill = fillPaint("#33D32F2F")
        val stroke = strokePaint("#B71C1C", 1f)
        val lineStroke = strokePaint("#B71C1C", 1.5f)

        for (zone in zones) {
            when (zone) {
                is NoFirePolygon -> {
                    val path = pathFor(frame, zone.vertices, closed = true)
                    canvas.drawPath(path, fill)
                    canvas.drawPath(path, stroke)
                }

                is NoFireMarker -> {
                    val outline = Sector.circleOutline(zone.center, zone.radiusM)
                    val path = pathFor(frame, outline, closed = true)
                    canvas.drawPath(path, fill)
                    canvas.drawPath(path, stroke)
                }

                is NoFireLine -> {
                    val path = pathFor(frame, zone.vertices, closed = false)
                    if (zone.bufferM > 0) {
                        val bufferPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.STROKE
                            color = Color.parseColor("#26D32F2F")
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                            strokeWidth = (zone.bufferM * 2.0 * frame.pointsPerMeter).toFloat()
                        }
                        canvas.drawPath(path, bufferPaint)
                    }
                    canvas.drawPath(path, lineStroke)
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // Fans, arcs, shots, position dots
    // ---------------------------------------------------------------------

    private fun drawFansAndArcs(canvas: Canvas, frame: MapFrame, report: RangeReport) {
        val fill = fillPaint("#2643A047")
        val stroke = strokePaint("#2E7D32", 1.5f)
        val arcStroke = strokePaint("#2E7D32", 1f).apply {
            pathEffect = DashPathEffect(floatArrayOf(3f, 3f), 0f)
        }

        for (position in report.positions) {
            for (fan in position.fans) {
                val outline = Sector.sectorOutline(
                    position.position,
                    fan.leftTrueDeg,
                    fan.rightTrueDeg,
                    position.fanRangeM,
                )
                val path = pathFor(frame, outline, closed = true)
                canvas.drawPath(path, fill)
                canvas.drawPath(path, stroke)

                val arc = Sector.arcPoints(
                    position.position,
                    fan.leftTrueDeg,
                    fan.rightTrueDeg,
                    position.effectiveRangeM,
                )
                canvas.drawPath(pathFor(frame, arc, closed = false), arcStroke)
            }
        }
    }

    private fun drawShots(canvas: Canvas, frame: MapFrame, report: RangeReport) {
        val linePaint = strokePaint("#6A1B9A", 2f)
        val headPaint = fillPaint("#6A1B9A")

        for (position in report.positions) {
            for (shot in position.shots) {
                val (ox, oy) = pagePoint(frame, position.position)
                val tipLatLon = projectPoint(position.position, shot.bearingTrueDeg, position.fanRangeM)
                val (tx, ty) = pagePoint(frame, tipLatLon)

                canvas.drawLine(ox, oy, tx, ty, linePaint)
                drawArrowhead(canvas, ox, oy, tx, ty, headPaint)
            }
        }
    }

    private fun drawArrowhead(canvas: Canvas, ox: Float, oy: Float, tx: Float, ty: Float, paint: Paint) {
        val dx = tx - ox
        val dy = ty - oy
        val len = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (len <= 0f) return
        val ux = dx / len
        val uy = dy / len
        val px = -uy
        val py = ux

        val headLength = 6f
        val headWidth = 4f
        val backX = tx - ux * headLength
        val backY = ty - uy * headLength

        val path = Path().apply {
            moveTo(tx, ty)
            lineTo(backX + px * headWidth / 2f, backY + py * headWidth / 2f)
            lineTo(backX - px * headWidth / 2f, backY - py * headWidth / 2f)
            close()
        }
        canvas.drawPath(path, paint)
    }

    private fun drawPositionDots(
        canvas: Canvas,
        frame: MapFrame,
        report: RangeReport,
        configLetters: Map<Long, String>,
        configCount: Int,
    ) {
        val dotPaint = fillPaint("#6A1B9A")

        report.positions.forEachIndexed { index, position ->
            val (x, y) = pagePoint(frame, position.position)
            canvas.drawCircle(x, y, 4f, dotPaint)
            val label = if (configCount > 1) {
                "P${index + 1} · ${configLetters[position.id] ?: ""}"
            } else {
                "P${index + 1}"
            }
            drawLabel(canvas, x + 6f, y - 6f, label, Paint.Align.LEFT)
            if (position.fans.any { it.fullCircle }) {
                drawLabel(canvas, x + 6f, y + 4f, "Clear in all directions", Paint.Align.LEFT)
            }
        }
    }

    // ---------------------------------------------------------------------
    // Labels
    // ---------------------------------------------------------------------

    private fun drawFanLabels(canvas: Canvas, frame: MapFrame, report: RangeReport, units: UnitSystem) {
        for (position in report.positions) {
            for (fan in position.fans) {
                if (fan.fullCircle) continue

                val width = Geometry2D.normalizeBearing(fan.rightTrueDeg - fan.leftTrueDeg)
                val inset = min(4.0, width / 4.0)
                val leftLabelBearing = Geometry2D.normalizeBearing(fan.leftTrueDeg + inset)
                val rightLabelBearing = Geometry2D.normalizeBearing(fan.rightTrueDeg - inset)
                val midBearing = Geometry2D.normalizeBearing(fan.leftTrueDeg + width / 2.0)

                val leftPoint = projectPoint(position.position, leftLabelBearing, position.fanRangeM * 0.92)
                val rightPoint = projectPoint(position.position, rightLabelBearing, position.fanRangeM * 0.92)
                val midPoint = projectPoint(position.position, midBearing, position.fanRangeM * 0.55)

                val (lx, ly) = pagePoint(frame, leftPoint)
                val (rx, ry) = pagePoint(frame, rightPoint)
                val (mx, my) = pagePoint(frame, midPoint)

                drawLabel(canvas, lx, ly, Formatters.bearing(fan.leftMagDeg))
                drawLabel(canvas, rx, ry, Formatters.bearing(fan.rightMagDeg))
                drawLabel(canvas, mx, my, Formatters.distance(position.fanRangeM, units))
            }
        }
    }

    private fun drawShotLabels(canvas: Canvas, frame: MapFrame, report: RangeReport) {
        var shotNumber = 1
        for (position in report.positions) {
            for (shot in position.shots) {
                val (ox, oy) = pagePoint(frame, position.position)
                val tipLatLon = projectPoint(position.position, shot.bearingTrueDeg, position.fanRangeM)
                val (tx, ty) = pagePoint(frame, tipLatLon)

                val lx = ox + (tx - ox) * 0.7f
                val ly = oy + (ty - oy) * 0.7f

                val magBearing = trueToMagnetic(shot.bearingTrueDeg, position.declinationDeg)
                val text = if (shot.label.isNotEmpty()) {
                    "$shotNumber: ${Formatters.bearing(magBearing)} ${shot.label}"
                } else {
                    "$shotNumber: ${Formatters.bearing(magBearing)}"
                }
                drawLabel(canvas, lx, ly, text)
                shotNumber++
            }
        }
    }

    private fun drawLabel(canvas: Canvas, x: Float, y: Float, text: String, align: Paint.Align = Paint.Align.CENTER) {
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            color = Color.WHITE
            textSize = 8f
            typeface = Typeface.SANS_SERIF
            textAlign = align
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#212121")
            textSize = 8f
            typeface = Typeface.SANS_SERIF
            textAlign = align
        }
        canvas.drawText(text, x, y, strokePaint)
        canvas.drawText(text, x, y, fillPaint)
    }

    // ---------------------------------------------------------------------
    // North arrow & scale bar
    // ---------------------------------------------------------------------

    private fun drawNorthArrow(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#212121")
            strokeWidth = 1.5f
        }
        val fill = fillPaint("#212121")

        val x = SQUARE.right - 24f
        val baseY = SQUARE.top + 56f
        val tipY = baseY - 24f

        canvas.drawLine(x, baseY, x, tipY, paint)
        val head = Path().apply {
            moveTo(x, tipY)
            lineTo(x - 4f, tipY + 6f)
            lineTo(x + 4f, tipY + 6f)
            close()
        }
        canvas.drawPath(head, fill)

        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#212121")
            textSize = 10f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("N", x, tipY - 6f, label)
    }

    private fun drawScaleBar(canvas: Canvas, frame: MapFrame, units: UnitSystem) {
        val scaleBarMeters = ReportLayout.scaleBarMeters(frame)
        if (scaleBarMeters <= 0.0) return
        val lengthPt = (scaleBarMeters * frame.pointsPerMeter).toFloat()

        val startX = SQUARE.left + 16f
        val endX = startX + lengthPt
        val barY = SQUARE.bottom - 24f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#212121")
            strokeWidth = 1.5f
        }
        canvas.drawLine(startX, barY, endX, barY, paint)
        canvas.drawLine(startX, barY - 4f, startX, barY + 4f, paint)
        canvas.drawLine(endX, barY - 4f, endX, barY + 4f, paint)

        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#212121")
            textSize = 9f
            typeface = Typeface.SANS_SERIF
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(Formatters.distance(scaleBarMeters, units), (startX + endX) / 2f, barY - 8f, label)
    }

    // ---------------------------------------------------------------------
    // Text block
    // ---------------------------------------------------------------------

    private fun drawSummaryBlock(
        canvas: Canvas,
        report: RangeReport,
        tables: ReportTables,
        timestampMs: Long,
    ) {
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#212121")
            textSize = 14f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#212121")
            textSize = 9f
            typeface = Typeface.SANS_SERIF
        }

        val leftX = 36f
        val titleY = 596f
        canvas.drawText("Wingspan range report", leftX, titleY, titlePaint)

        val timestampY = titleY + 16f
        val timestampFormat = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.US)
        canvas.drawText(timestampFormat.format(Date(timestampMs)), leftX, timestampY, bodyPaint)

        val summaryY = timestampY + 16f
        val configCount = tables.configs.size
        val summaryLine =
            "${report.positions.size} positions · ${report.totalShots} shots · $configCount load configuration(s)"
        canvas.drawText(summaryLine, leftX, summaryY, bodyPaint)

        var y = summaryY
        if (configCount == 1) {
            y += 14f
            val cells = tables.configs[0].cells
            // Shot, material, velocity, choke, wind (indices per ReportTables.cellsFor).
            val loadLine = "${cells[0]} · ${cells[1]} · ${cells[2]} · ${cells[3]} · ${cells[5]}"
            canvas.drawText(loadLine, leftX, y, bodyPaint)
        }

        y += 18f
        val note = "Bearings are magnetic. Positions, load configurations and shots are tabulated on the " +
            "following pages; the model and its assumptions are on the last page."
        drawWrapped(canvas, note, bodyPaint, leftX, y, 540f, 12f)
    }

    private fun drawWrapped(
        canvas: Canvas,
        text: String,
        paint: Paint,
        x: Float,
        y: Float,
        maxWidth: Float,
        leading: Float,
    ) {
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
        }
    }

    // ---------------------------------------------------------------------
    // Geometry helpers
    // ---------------------------------------------------------------------

    private fun projectPoint(origin: LatLon, bearingDeg: Double, radiusM: Double): LatLon =
        EnuProjection(origin).fromEnu(Geometry2D.bearingToUnitVector(bearingDeg) * radiusM)

    private fun pagePoint(frame: MapFrame, point: LatLon): Pair<Float, Float> {
        val (x, y) = ReportLayout.toPagePoint(frame, point)
        return x.toFloat() to y.toFloat()
    }

    private fun pathFor(frame: MapFrame, points: List<LatLon>, closed: Boolean): Path {
        val path = Path()
        points.forEachIndexed { index, latLon ->
            val (x, y) = pagePoint(frame, latLon)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        if (closed) path.close()
        return path
    }

    private fun fillPaint(colorHex: String): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor(colorHex)
    }

    private fun strokePaint(colorHex: String, widthPt: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor(colorHex)
        strokeWidth = widthPt
    }
}

/**
 * Draws the "Page k of N · <timestamp>" line every page in the export carries in its top margin.
 * Shared by [PdfReportComposer], [PdfTablesComposer] and [PdfAppendixComposer], which each own a
 * different page of the same document.
 */
internal fun drawPageFooter(canvas: Canvas, pageNumber: Int, pageCount: Int, timestampMs: Long) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#616161")
        textSize = 9f
        typeface = Typeface.SANS_SERIF
        textAlign = Paint.Align.RIGHT
    }
    val timestampFormat = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.US)
    val text = "Page $pageNumber of $pageCount · ${timestampFormat.format(Date(timestampMs))}"
    canvas.drawText(text, 576f, 24f, paint)
}
