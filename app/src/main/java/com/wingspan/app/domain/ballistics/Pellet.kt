package com.wingspan.app.domain.ballistics

import kotlin.math.PI
import kotlin.math.pow

data class Pellet(val diameterM: Double, val densityKgM3: Double) {
    init {
        require(diameterM > 0) { "diameterM must be > 0" }
        require(densityKgM3 > 0) { "densityKgM3 must be > 0" }
    }

    val massKg: Double = densityKgM3 * PI / 6.0 * diameterM.pow(3)

    val areaM2: Double = PI * diameterM * diameterM / 4.0

    val dragFactor: Double = 0.5 * DragModel.AIR_DENSITY_KG_M3 * areaM2 / massKg

    companion object {
        fun of(diameterInches: Double, densityGcc: Double): Pellet =
            Pellet(
                diameterM = diameterInches * Units.INCH_M,
                densityKgM3 = densityGcc * Units.GCC_TO_KGM3,
            )
    }
}
