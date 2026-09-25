package com.wingspan.app.domain.ballistics

object DragModel {
    /**
     * Air density at sea level in the International Standard Atmosphere (ISA), at 15 degrees
     * Celsius, in kg/m^3. This is a **physical constant of a defined reference atmosphere**, not
     * of any particular day: real air density varies with altitude, temperature, humidity and
     * barometric pressure, and this model does not correct for any of those. Treat it as a
     * standard baseline rather than current conditions.
     */
    const val AIR_DENSITY_KG_M3 = 1.225

    /**
     * Speed of sound at sea level in the International Standard Atmosphere (ISA), at 15 degrees
     * Celsius, in m/s. Like [AIR_DENSITY_KG_M3], this is a **physical constant of a defined
     * reference atmosphere**; the true local speed of sound shifts with temperature and altitude,
     * and the model does not correct for that.
     */
    const val SPEED_OF_SOUND_MPS = 340.3

    /**
     * Standard gravity, in m/s^2: a **physical constant**, specifically an internationally
     * defined fixed value (not a locally measured one), used for the vertical acceleration term
     * in the trajectory model.
     */
    const val GRAVITY_MPS2 = 9.80665

    /**
     * Drag coefficient of a smooth sphere as a function of Mach number, linearly interpolated
     * between the tabulated points by [dragCoefficient]. This is a **convention or estimate**: a
     * widely used, textbook-style smooth-sphere drag curve rather than a measurement of any
     * particular pellet. It encodes the familiar shape of sphere drag - a roughly constant
     * subsonic coefficient near 0.47, a sharp transonic rise approaching Mach 1 up to about 1.0,
     * and a gradual supersonic decline beyond that. Because it models a smooth sphere, it does
     * not account for surface finish, spin, or the deformation that real shot pellets undergo
     * from setback and barrel travel.
     */
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
