package com.wingspan.app.domain.ballistics

data class RangeResult(
    val maxRangeM: Double,
    val optimalAngleDeg: Double,
    val effectiveRangeM: Double,
    val energyLimitedRangeM: Double,
    val patternLimitedRangeM: Double,
    val muzzleEnergyJ: Double,
    val windBufferM: Double = 0.0,
) {
    val effectiveRangeLimiter: String
        get() = if (energyLimitedRangeM <= patternLimitedRangeM) "energy" else "pattern"

    val fanRangeM: Double
        get() = maxRangeM + windBufferM
}
