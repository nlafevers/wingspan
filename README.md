# Wingspan

## Overview

Wingspan is a native Android app for hunters and shooters that computes shotgun ballistic
range fans around a firing position and displays them on a map, automatically clipped so
they never cross a user-drawn no-fire zone. It reports each fan's left and right limits as
magnetic compass bearings, applies a wind safety buffer that grows the fans and the margin
kept from every zone, and supports offline use in the field: downloadable map areas, a
manual position override for planning away from the range, timestamped firing-position
snapshots, and a report mode that records shots during a session and exports a printable
two-page PDF range report.

## Features

- **Map and basemaps** — a zoomable map with a choice of USGS topographic or satellite
  imagery base layers, selectable at any time; the choice persists across sessions. Map
  areas can be downloaded for offline use (see below), so the app remains usable without a
  network connection in the field.
- **No-fire zones with GeoJSON import/export** — draw markers (point + radius), polygons,
  and buffered lines directly on the map by tapping to place points and dragging to refine
  them (drag existing vertices/markers, insert a vertex at a midpoint handle, delete a
  point, rename or delete a zone). Zones persist locally and can be exported to, or
  imported from, standard GeoJSON files, with a choice to replace or merge on import.
- **Settings and ballistics model** — configure shot size, pellet material, muzzle
  velocity, and choke constriction. These drive a point-mass trajectory model (RK4
  integration, ISA sea-level atmosphere, a Mach-dependent smooth-sphere drag curve) that
  computes maximum range and an effective (pattern- and energy-limited) range. The
  maximum-range figures are cross-checked against Journee's rule of thumb (maximum range
  in yards is approximately 2200 times shot diameter in inches) for lead shot, and this
  cross-check is an executable unit test, not just a design note.
- **Range fans and magnetic bearings** — fans are cast outward from the firing position in
  every direction the model allows, cut wherever a ray would cross a no-fire zone (with a
  safety margin on either side of a blocked run), and drawn as an outer arc at maximum
  range with an inner arc at effective range. Tapping a fan shows its left and right limits
  as magnetic bearings, corrected for local declination.
- **Wind safety buffer** — entering a wind speed adds a safety pad to every fan's radius and
  to the margin every no-fire zone keeps between itself and a fan; the buffer only ever
  makes fans more conservative. Wind direction is not modeled — the buffer is a
  worst-case, direction-independent pad. Clearing the wind speed restores the unpadded
  fans and margins exactly.
- **Manual position override** — for planning or testing away from the range, a long-press
  on the map places the firing position manually instead of using a live GPS fix; a banner
  indicates when manual mode is active.
- **Offline map areas** — download the visible map area at a chosen zoom range for use with
  no network connection, with a region list showing download progress and allowing
  deletion. Offline downloads are served through a small in-app local HTTP server that
  hands MapLibre's offline downloader the same bundled basemap style used by the live map,
  so no external hosting is required.
- **Firing-position snapshots** — capture a timestamped snapshot of the current firing
  position (map image, position, settings, ranges, fans, and notes), browse a list of past
  snapshots, view details, add notes, share a snapshot, show it back on the map, or export
  all snapshots as a zip file.
- **Report mode with PDF export** — a dedicated mode for recording shots by tapping the
  map; each tap becomes an editable magnetic bearing from the currently selected firing
  position, and a report can hold multiple firing positions, each keeping the fans and
  settings that applied at the moment it was recorded. A report survives an app restart
  and can be exported as a two-page PDF: a square map page showing every firing position,
  fan, and shot with bearing labels, a north arrow, a scale bar, and the load settings
  printed beneath it, followed by a "Model and assumptions" appendix that restates how the
  model works and records whether each firing position came from a live GPS fix or a
  manual placement.

## Building

### Android Studio

Open the project root in Android Studio and build/run the `app` module normally.

### Command line

Requires JDK 21.

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Acceptance Checklist

The following checks should be performed on a physical device before final sign-off, using
`app-debug.apk`:

1. Topo and imagery basemaps both render, and the selected basemap persists across app
   restarts.
2. The GPS position dot with its accuracy circle appears; switching to manual mode and
   long-pressing the map moves the firing position.
3. Zone editing works end-to-end: drawing a polygon and a marker, dragging handles,
   inserting a vertex at a midpoint, deleting a point, renaming a zone, and deleting all
   zones.
4. Exporting zones to GeoJSON produces a file that opens correctly in geojson.io, and that
   file re-imports into the app.
5. Changing settings changes the computed ranges and fan sizes; fans are visibly cut by
   no-fire zones with a margin; tapping a fan shows magnetic bearings that agree with a
   separate compass app to within a few degrees.
6. An offline map area can be downloaded and still renders correctly with the device in
   airplane mode.
7. Firing-position snapshots can be captured, listed, opened in detail, annotated with
   notes, shared, shown back on the map, and exported together as a zip file.
8. Entering a wind speed enlarges the fan radius and widens the gap every fan keeps from
   each no-fire zone; clearing the wind speed restores the previous (unpadded) fans
   exactly.
9. Report mode records shots by tapping the map, keeps them across an app restart, and
   exports a two-page PDF: page one's square map shows every firing position, fan, and
   shot with readable bearing labels and the load settings printed below it; page two is
   the "Model and assumptions" appendix, and it correctly reports for each firing position
   whether it came from a live GPS fix or a manual placement.

## Basemap Attributions

- Topographic and imagery basemap tiles courtesy of the **USGS National Map**
  (basemap.nationalmap.gov).
- Additional satellite imagery courtesy of **Esri World Imagery**
  (server.arcgisonline.com).

## Data Sources and Validation

Wingspan's ballistics model relies on a handful of reference tables and constants, each of
a different kind of provenance:

- **Shot diameters** (`ShotSize`) are the nominal diameters of standard US commercial shot
  sizes, in inches. This is a nominal industry standard — a catalogued value that
  manufacturers converge on — rather than a physical measurement, and real pellets vary
  within manufacturing tolerance.
- **Pellet material densities** (`PelletMaterial`) are given in grams per cubic centimeter.
  Lead and steel densities are physical constants of the bulk metals (though real shot is
  usually alloyed and runs slightly below the pure figure). Bismuth is a commercial alloy
  rather than pure bismuth. Tungsten "TSS" is flagged separately: it is a **vendor-dependent
  alloy figure**, not a fixed standard — its density varies by manufacturer and loading, and
  the value used here is a representative mid-range figure.
- **Choke pattern ranges** (`Choke`) are conventional rules of thumb for the distance at
  which each choke constriction still holds a useful pattern. These are conventions, not
  measurements: real pattern performance depends on the specific load, the barrel, and the
  pattern-percentage criterion chosen. This table only affects the pattern-limited half of
  effective range and never affects maximum range.
- **Atmosphere and drag model** (`DragModel`) uses the ISA (International Standard
  Atmosphere) sea-level values for air density and speed of sound — physical constants of a
  defined reference atmosphere, not of any particular day's weather — together with a
  smooth-sphere drag curve tabulated against Mach number. The model does not correct for
  altitude, temperature, or humidity, and the drag curve reflects an idealized smooth
  sphere rather than the surface finish or deformation of real shot.

The provenance of every individual number above is recorded in place as KDoc comments on
the relevant declarations in `app/src/main/java/com/wingspan/app/domain/ballistics/`
(`ShotSize.kt`, `PelletMaterial.kt`, `Choke.kt`, `DragModel.kt`), so the reasoning behind
each figure travels with the code.

The maximum-range calculation is cross-checked against Journee's rule of thumb for lead
shot as an executable JUnit test (`RangeCalculatorTest`), not just a design-time claim.
More generally, all of `domain/` (including `domain/ballistics` and `domain/geo`) is pure
Kotlin with zero Android dependencies, and is covered entirely by JVM unit tests that run
without a device or emulator.

## Safety Disclaimer

The ranges and fans Wingspan computes are model estimates under idealized conditions —
they are not a guarantee of where shot will or will not land. Users must apply their own
safety margins on top of the app's output and must follow all applicable range rules and
local regulations. This app does not replace a range safety officer, and it should never be
the sole basis for a safety decision.
