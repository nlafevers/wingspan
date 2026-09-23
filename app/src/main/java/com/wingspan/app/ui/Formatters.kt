package com.wingspan.app.ui

import com.wingspan.app.domain.ballistics.UnitSystem
import com.wingspan.app.domain.ballistics.Units
import kotlin.math.roundToInt

/**
 * Pure display/parsing helpers shared by settings and map UI. All physical quantities are
 * stored internally in SI-ish canonical units (meters, meters/second, joules, mph for wind)
 * and converted here only for presentation or for parsing user input back into those units.
 */
object Formatters {

    private const val METERS_PER_YARD = 0.9144
    private const val METERS_PER_FOOT = 0.3048
    private const val JOULES_PER_FT_LBF = 1.3558179483314004

    fun distance(meters: Double, units: UnitSystem): String =
        when (units) {
            UnitSystem.IMPERIAL -> "${(meters / METERS_PER_YARD).roundToInt()} yd"
            UnitSystem.METRIC -> "${meters.roundToInt()} m"
        }

    fun velocity(fps: Double, units: UnitSystem): String =
        when (units) {
            UnitSystem.IMPERIAL -> "${fps.roundToInt()} fps"
            UnitSystem.METRIC -> "${(fps * METERS_PER_FOOT).roundToInt()} m/s"
        }

    fun energy(joules: Double, units: UnitSystem): String =
        when (units) {
            UnitSystem.IMPERIAL -> "%.1f ft·lbf".format(joules / JOULES_PER_FT_LBF)
            UnitSystem.METRIC -> "%.1f J".format(joules)
        }

    fun bearing(deg: Double, magnetic: Boolean = true): String {
        val rounded = deg.roundToInt()
        val wrapped = ((rounded % 360) + 360) % 360
        val suffix = if (magnetic) " M" else " T"
        return "%03d°%s".format(wrapped, suffix)
    }

    fun angle(deg: Double): String = "${deg.roundToInt()}°"

    fun velocityInputToFps(text: String, units: UnitSystem): Double? {
        val value = text.toDoubleOrNull() ?: return null
        return when (units) {
            UnitSystem.IMPERIAL -> value
            UnitSystem.METRIC -> value / METERS_PER_FOOT
        }
    }

    fun fpsToVelocityInput(fps: Double, units: UnitSystem): String =
        when (units) {
            UnitSystem.IMPERIAL -> fps.roundToInt().toString()
            UnitSystem.METRIC -> (fps * METERS_PER_FOOT).roundToInt().toString()
        }

    fun windSpeed(mph: Double, units: UnitSystem): String =
        when (units) {
            UnitSystem.IMPERIAL -> "${mph.roundToInt()} mph"
            UnitSystem.METRIC -> "${Units.mphToMps(mph).roundToInt()} m/s"
        }

    fun windInputToMph(text: String, units: UnitSystem): Double? {
        val value = text.toDoubleOrNull() ?: return null
        return when (units) {
            UnitSystem.IMPERIAL -> value
            UnitSystem.METRIC -> value / Units.mphToMps(1.0)
        }
    }

    fun mphToWindInput(mph: Double, units: UnitSystem): String =
        when (units) {
            UnitSystem.IMPERIAL -> mph.roundToInt().toString()
            UnitSystem.METRIC -> Units.mphToMps(mph).roundToInt().toString()
        }
}
