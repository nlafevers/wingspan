package com.wingspan.app.domain.ballistics

data class BallisticInput(
    val diameterInches: Double,
    val densityGcc: Double,
    val muzzleVelocityFps: Double,
    val choke: Choke,
    val energyThresholdFtLbf: Double,
)
