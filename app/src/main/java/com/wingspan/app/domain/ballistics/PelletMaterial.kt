package com.wingspan.app.domain.ballistics

/**
 * Pellet material densities, in grams per cubic centimetre, used to convert a shot diameter into
 * a mass for drag and energy calculations. Each entry's provenance differs and is noted below.
 */
enum class PelletMaterial(val label: String, val densityGcc: Double) {
    /**
     * Bulk density of pure lead metal: a **physical constant** of the element. Lead shot is
     * typically alloyed with a small amount of antimony for hardness, so real shot density runs
     * slightly below this figure, but 11.34 is the standard reference value used throughout
     * ballistics literature.
     */
    LEAD("Lead", 11.34),

    /**
     * Bulk density of pure iron/steel: a **physical constant** of the metal. As with lead, real
     * steel shot (typically a low-carbon steel) sits close to but slightly below the pure-element
     * figure.
     */
    STEEL("Steel", 7.86),

    /**
     * Density of commercial bismuth shot alloy, not pure bismuth metal (pure bismuth is denser,
     * around 9.8 g/cc). This is a **nominal industry standard**: a representative figure for the
     * bismuth-tin alloy typically used in shotshells, widely tabulated but not a measurement of
     * any single manufacturer's product.
     */
    BISMUTH("Bismuth", 9.6),

    /**
     * Density of tungsten super shot (TSS), a tungsten alloy rather than pure tungsten. This is
     * the figure to treat most cautiously here: it is a **convention or estimate**, a
     * representative mid-range value, since actual TSS density varies by vendor and by the exact
     * alloy composition of a given loading. Treat 18.0 as a reasonable planning figure, not an
     * authoritative constant.
     */
    TUNGSTEN("Tungsten (TSS)", 18.0),

    /**
     * Sentinel entry, not a real material, mirroring [ShotSize.CUSTOM]. The `0.0` density is a
     * placeholder that is never used directly in a calculation; the user's own custom density is
     * substituted wherever this entry is selected.
     */
    CUSTOM("Custom", 0.0),
}
