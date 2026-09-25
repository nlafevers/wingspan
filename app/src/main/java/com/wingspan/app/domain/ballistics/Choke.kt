package com.wingspan.app.domain.ballistics

/**
 * Choke constrictions and the conventional distance, in yards, at which each still holds a
 * useful shot pattern ([patternRangeYards]).
 *
 * These figures are a **convention or estimate**, not a measurement: they are commonly cited
 * rules of thumb, and actual pattern performance at a given range depends heavily on the specific
 * load, the barrel, and the pattern-percentage criterion used to judge "useful" (e.g. 70% of
 * pellets in a 30-inch circle). A real pattern board with the shooter's own ammunition would give
 * a different number for the same choke. This table feeds only the pattern-limited half of the
 * effective-range calculation and never affects maximum range, which is governed purely by the
 * drag model.
 */
enum class Choke(val label: String, val patternRangeYards: Double) {
    CYLINDER("Cylinder", 25.0),
    SKEET("Skeet", 25.0),
    IMPROVED_CYLINDER("Improved Cylinder", 30.0),
    LIGHT_MODIFIED("Light Modified", 33.0),
    MODIFIED("Modified", 35.0),
    IMPROVED_MODIFIED("Improved Modified", 38.0),
    FULL("Full", 40.0),
    EXTRA_FULL("Extra Full", 45.0),
}
