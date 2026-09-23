package com.wingspan.app.domain.ballistics

import kotlinx.serialization.Serializable

enum class UnitSystem { IMPERIAL, METRIC }

@Serializable
data class LoadSettings(
    val shotSize: ShotSize = ShotSize.NO_6,
    val material: PelletMaterial = PelletMaterial.LEAD,
    val customDiameterInches: Double = 0.130,
    val customDensityGcc: Double = 11.34,
    val muzzleVelocityFps: Double = 1250.0,
    val choke: Choke = Choke.MODIFIED,
    val energyThresholdFtLbf: Double = 1.5,
    val unitSystem: UnitSystem = UnitSystem.IMPERIAL,
    val windSpeedMph: Double = 0.0,
) {
    fun effectiveDiameterInches(): Double =
        if (shotSize == ShotSize.CUSTOM) customDiameterInches else shotSize.diameterInches

    fun effectiveDensityGcc(): Double =
        if (material == PelletMaterial.CUSTOM) customDensityGcc else material.densityGcc

    fun toBallisticInput(): BallisticInput =
        BallisticInput(
            diameterInches = effectiveDiameterInches(),
            densityGcc = effectiveDensityGcc(),
            muzzleVelocityFps = muzzleVelocityFps,
            choke = choke,
            energyThresholdFtLbf = energyThresholdFtLbf,
            windSpeedMps = Units.mphToMps(windSpeedMph),
        )
}
