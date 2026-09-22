package com.wingspan.app.domain.ballistics

object DragModel {
    const val AIR_DENSITY_KG_M3 = 1.225
    const val SPEED_OF_SOUND_MPS = 340.3
    const val GRAVITY_MPS2 = 9.80665

    private val SPHERE_DRAG_TABLE = listOf(
        0.0 to 0.47,
        0.6 to 0.47,
        0.8 to 0.52,
        0.9 to 0.62,
        1.0 to 0.80,
        1.1 to 0.95,
        1.2 to 1.00,
        1.5 to 0.98,
        2.0 to 0.94,
        3.0 to 0.92,
    )

    fun dragCoefficient(mach: Double): Double {
        val table = SPHERE_DRAG_TABLE
        if (mach <= table.first().first) return table.first().second
        if (mach >= table.last().first) return table.last().second

        for (i in 0 until table.size - 1) {
            val (m0, cd0) = table[i]
            val (m1, cd1) = table[i + 1]
            if (mach >= m0 && mach <= m1) {
                val t = (mach - m0) / (m1 - m0)
                return cd0 + t * (cd1 - cd0)
            }
        }
        // Unreachable given the bounds checks above.
        return table.last().second
    }
}
