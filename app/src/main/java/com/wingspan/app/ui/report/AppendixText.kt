package com.wingspan.app.ui.report

import com.wingspan.app.domain.ballistics.UnitSystem
import com.wingspan.app.domain.ballistics.Units
import com.wingspan.app.ui.Formatters

/**
 * Pure Kotlin text for the "Model and assumptions" appendix page. Every paragraph here restates a
 * decision already recorded in the roadmap; this object holds no drawing logic so it can run in
 * plain JVM unit tests (see `ui/editor/EditorGeometry.kt` for the same arrangement).
 */
object AppendixText {

    const val MAX_RANGE: String =
        "Maximum range is integrated as a point-mass sphere trajectory with a fourth-order " +
            "Runge-Kutta solver at a 0.002 s step, using the ISA sea-level atmosphere (air density " +
            "1.225 kg/m3, speed of sound 340.3 m/s) and a Mach-dependent smooth-sphere drag curve, " +
            "reporting the best result over launch angles 10 to 50 degrees in 1 degree steps. Choke " +
            "never affects maximum range. The model is cross-checked in the unit test suite against " +
            "Journee's rule for lead shot at 1300 fps across sizes #9 to F."

    const val FAN_GEOMETRY: String =
        "Fans are found by casting a ray every 1 degree of true bearing out to the fan radius and " +
            "blocking any bearing whose ray reaches a no-fire zone. Blocked runs are then widened by " +
            "a 2 degree margin on each side, and any remaining clear fan narrower than 5 degrees is " +
            "suppressed. A suppressed fan means no fan is drawn there rather than that the direction " +
            "is safe."

    const val BEARINGS: String =
        "Bearings are shown as magnetic, derived as magnetic = true - declination from the " +
            "on-device World Magnetic Model via android.hardware.GeomagneticField, with the " +
            "declination that applied at each firing position listed alongside that position."

    const val DISCLAIMER: String =
        "These ranges are model estimates under ideal conditions. The reader must apply their own " +
            "safety margins and follow all range rules. This report does not replace a range safety " +
            "officer."

    fun effectiveRange(limiter: String, energyThresholdFtLbf: Double, units: UnitSystem): String {
        val thresholdText = Formatters.energy(Units.ftLbfToJoules(energyThresholdFtLbf), units)
        val limiterWord = if (limiter == "energy") "energy" else "pattern"
        return "Effective range is the lesser of the energy-limited distance, at which a single " +
            "pellet's kinetic energy falls below $thresholdText, and the choke's pattern-limited " +
            "distance. For this report, $limiterWord was the binding limit. Effective range is " +
            "deliberately not adjusted for wind because it measures lethality rather than safety."
    }

    fun windApplies(windSpeedMph: Double): Boolean = windSpeedMph > 0.0

    fun wind(windSpeedMph: Double, windBufferM: Double, units: UnitSystem): String {
        val speedText = Formatters.windSpeed(windSpeedMph, units)
        val bufferText = Formatters.distance(windBufferM, units)
        return "At a wind speed of $speedText, the resulting buffer is $bufferText. The buffer is " +
            "the greater of the downwind extension of maximum range and the crosswind drift at the " +
            "optimal launch angle. It is added both to the fan radius and to the blocking distance " +
            "of every no-fire zone. Wind direction is deliberately not modelled, so the buffer " +
            "applies in all directions."
    }

    fun positionLine(
        label: String,
        source: String,
        accuracyM: Double?,
        declinationDeg: Double,
        units: UnitSystem,
    ): String {
        val sourceText = when (source) {
            "GPS" -> {
                val accuracyText = if (accuracyM != null) {
                    Formatters.distance(accuracyM, units)
                } else {
                    "accuracy not recorded"
                }
                "came from a live GPS fix (accuracy: $accuracyText)"
            }

            "MANUAL" -> "was placed manually on the map and was not a live position fix"

            else -> source
        }
        val declinationText = "%+.1f".format(declinationDeg)
        return "$label: $sourceText. Declination: $declinationText°."
    }
}
