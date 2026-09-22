package com.wingspan.app.domain.ballistics

import kotlin.math.min

object RangeCalculator {
    fun compute(input: BallisticInput): RangeResult {
        val pellet = Pellet.of(input.diameterInches, input.densityGcc)
        val v0 = Units.fpsToMps(input.muzzleVelocityFps)

        var maxRangeM = Double.NEGATIVE_INFINITY
        var optimalAngleDeg = 0.0
        var angleDeg = 10.0
        while (angleDeg <= 50.0) {
            val range = Trajectory.horizontalRangeM(pellet, v0, angleDeg)
            if (range > maxRangeM) {
                maxRangeM = range
                optimalAngleDeg = angleDeg
            }
            angleDeg += 1.0
        }

        val energyLimitedRangeM = Trajectory.flatFireDistanceToEnergyM(
            pellet,
            v0,
            Units.ftLbfToJoules(input.energyThresholdFtLbf),
        )
        val patternLimitedRangeM = Units.yardsToMeters(input.choke.patternRangeYards)
        val effectiveRangeM = min(energyLimitedRangeM, patternLimitedRangeM).coerceAtMost(maxRangeM)
        val muzzleEnergyJ = 0.5 * pellet.massKg * v0 * v0

        return RangeResult(
            maxRangeM = maxRangeM,
            optimalAngleDeg = optimalAngleDeg,
            effectiveRangeM = effectiveRangeM,
            energyLimitedRangeM = energyLimitedRangeM,
            patternLimitedRangeM = patternLimitedRangeM,
            muzzleEnergyJ = muzzleEnergyJ,
        )
    }
}
