package com.wingspan.app.domain.ballistics

/**
 * Nominal diameters of standard US commercial shot sizes, in inches.
 *
 * These are a **nominal industry standard**: catalogued values that shot manufacturers and
 * reloading references converge on, not measurements of any specific batch of pellets. Real
 * pellets vary around the nominal figure within normal manufacturing tolerance, so treat each
 * [diameterInches] as a representative "size on the box" rather than an exact measurement.
 *
 * [CUSTOM] is a sentinel, not a real size: its diameter is `0.0` and is never used directly in a
 * calculation. `LoadSettings.effectiveDiameterInches()` substitutes the user's own
 * `customDiameterInches` whenever [CUSTOM] is selected.
 */
enum class ShotSize(val label: String, val diameterInches: Double) {
    NO_9("9", 0.080),
    NO_8_5("8.5", 0.085),
    NO_8("8", 0.090),
    NO_7_5("7.5", 0.095),
    NO_7("7", 0.100),
    NO_6("6", 0.110),
    NO_5("5", 0.120),
    NO_4("4", 0.130),
    NO_3("3", 0.140),
    NO_2("2", 0.150),
    NO_1("1", 0.160),
    B("B", 0.170),
    BB("BB", 0.180),
    BBB("BBB", 0.190),
    T("T", 0.200),
    F("F", 0.220),

    /**
     * Sentinel entry, not a real shot size. The `0.0` diameter is a placeholder that is never
     * used in a calculation: `LoadSettings.effectiveDiameterInches()` substitutes the user's own
     * `customDiameterInches` whenever this entry is selected.
     */
    CUSTOM("Custom", 0.0),
}
