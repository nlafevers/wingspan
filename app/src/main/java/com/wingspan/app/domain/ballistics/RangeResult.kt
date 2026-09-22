package com.wingspan.app.domain.ballistics

data class RangeResult(
    val maxRangeM: Double,
    val optimalAngleDeg: Double,
    val effectiveRangeM: Double,
    val energyLimitedRangeM: Double,
    val patternLimitedRangeM: Double,
    val muzzleEnergyJ: Double,
) {
    val effectiveRangeLimiter: String
        get() = if (energyLimitedRangeM <= patternLimitedRangeM) "energy" else "pattern"
}
