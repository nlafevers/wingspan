# Wingspan Implementation Roadmap (Phase 1)

This roadmap builds **wingspan**, a native Android app that draws shotgun range fans around the shooter's position, clipped so no fan crosses a user-drawn no-fire zone, and reports each fan's left/right limits as magnetic bearings. It also covers settings-driven ballistics, zone editing with import/export, offline basemap downloads, and timestamped firing-position snapshots. It exists so that lightweight implementer agents can build the app one self-contained step at a time without the planning conversation. Every design decision is recorded in this document.

## Product Description

Wingspan is a native Android app (working title "Shotgun Range Fan App") that:

1. Offers a map display mode with a zoomable map with options for either imagery or topo bases.
2. Offers a GUI editing mode allowing markers or polygons to be added by tapping points; the resulting markers and polygons are saved and persist across sessions (these are no-fire zones).
3. Allows import and export of saved markers and polygons (no-fire zones).
4. Displays the markers and polygons as an overlay on the map.
5. Offers settings to change shot size (#2, BB, etc.), pellet material (lead, steel, etc.), muzzle velocity, and choke.
6. Uses the settings to calculate effective and maximum ranges for shotgun pellets to travel under optimal conditions and firing angles.
7. Locates the live/current user position via GPS and displays it on the map.
8. Projects range fans in all directions from the user's current location that don't intersect with a no-fire marker or polygon.
9. In map mode, tapping a range fan displays the left and right field-of-fire limits as magnetic compass bearings.

Additions agreed during planning: a manual position override for planning/testing away from the range, vertex-drag editing (tap to place, drag to refine), explicit offline map-area downloads, and timestamped firing-position snapshots (map image + data record + notes) that can be shared and exported.

**Phase 2 (out of scope for this roadmap):** an AR overlay mode projecting fan/arc edges onto the live camera view via ARCore.

## Workspace And Git Rules

- Single Git repository at `/home/nathan/wingspan`, branch `main`, remote `https://github.com/nlafevers/wingspan` (public). There is no `AGENTS.md`; this section is the git policy.
- Every step ends with one commit made with the `conventional-committer` skill (`.agents/skills/conventional-committer/SKILL.md`). Suggested scopes: `build`, `ballistics`, `geo`, `data`, `map`, `editor`, `settings`, `offline`, `snapshots`.
- Never commit `local.properties`, `build/`, `.gradle/`, `*.apk`, `.env`, or anything under `$HOME`. Step WS-0.2 adds these to `.gitignore`.
- Files under `.agents/`, `.claude/`, and `CLAUDE.md` are git-ignored on purpose; do not add them.
- Do not push unless the user asks.

## How To Use This Roadmap

Implement with: `Use roadmap-implementer on @roadmap.md — implement the next incomplete step only.`

Toolchain conventions used by every step (WS-0.1 installs them):

```bash
# All Gradle commands run from the repo root. If `./gradlew` or `java` is not found,
# the persistent env was not loaded — prefix with a login shell:
bash -l -c 'cd /home/nathan/wingspan && ./gradlew assembleDebug testDebugUnitTest'

# Expected environment after WS-0.1 (defined in /etc/sandbox-persistent.sh):
#   JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
#   ANDROID_HOME=$HOME/android-sdk
#   PATH includes $JAVA_HOME/bin and $ANDROID_HOME/cmdline-tools/latest/bin
```

- The standard per-step verification is `./gradlew assembleDebug testDebugUnitTest`. It compiles the app, runs KSP/Room codegen, and runs all JVM unit tests. `lint` runs only in the final phase.
- There is no emulator or device in the sandbox. Items marked **(manual, on device)** are for the user to check later and are **not** blocking for marking a step done.
- The sandbox reaches the internet through an HTTP proxy. `JAVA_TOOL_OPTIONS` already carries the proxy settings and Gradle/sdkmanager inherit it; WS-0.1 also writes them to `~/.gradle/gradle.properties`. `maven.google.com` is blocked, but Gradle's `google()` repository resolves to `https://dl.google.com/dl/android/maven2/`, which is allowed.
- Tile servers (`basemap.nationalmap.gov`, `server.arcgisonline.com`) are **not** reachable from the sandbox. Never add a test that fetches tiles.
- Kotlin source root is `app/src/main/java/com/wingspan/app/`; unit tests live in `app/src/test/java/com/wingspan/app/`. Package names mirror directories.
- Code under `com.wingspan.app.domain` must have **zero** `android.*` / `androidx.*` imports so it runs in plain JVM unit tests.
- Use JUnit 4 (`org.junit.Test`, `org.junit.Assert.*`) for tests.
- Only `material-icons-core` is available (it ships with material3). Use only these icons: `Icons.Default.Add`, `Icons.AutoMirrored.Filled.ArrowBack`, `Check`, `Clear`, `Close`, `Delete`, `Done`, `Edit`, `Info`, `List`, `Menu`, `MoreVert`, `Place`, `Refresh`, `Search`, `Settings`, `Share`, `Star`, `Warning`. Do not add `material-icons-extended`.
- When a step says "write file X with the following content", the content is prescriptive; small naming/formatting deviations are fine, API shape is not.

## Design Decisions (recorded from the planning pass, 2026-09-18)

| Area | Decision |
| --- | --- |
| Toolchain | JDK 21 (apt), Android cmdline-tools `16111833`, `platforms;android-36`, `build-tools;36.0.0`, Gradle **8.14.5**, AGP **8.13.2**, Kotlin **2.3.21**, KSP **2.3.12**. `compileSdk 36`, `targetSdk 36`, `minSdk 26`. Java/Kotlin target 17. AGP 9.x was deliberately avoided (built-in Kotlin changes the build DSL; poorly documented). |
| Libraries | Compose BOM `2026.06.00`, material3 (via BOM), core-ktx `1.18.0`, activity-compose `1.13.0`, lifecycle `2.10.0`, navigation-compose `2.9.8`, Room `2.8.5` (KSP), DataStore preferences `1.2.1`, MapLibre `org.maplibre.gl:android-sdk-opengl:13.6.1` (OpenGL variant for broad device support; package `org.maplibre.android`), play-services-location `21.4.0`, kotlinx-serialization-json `1.10.0`, kotlinx-coroutines `1.10.2`, JUnit `4.13.2`. Newer androidx releases require compileSdk 37 + AGP 9.1 — do not bump them. |
| Architecture | Single Gradle module `app`, package `com.wingspan.app`. Kotlin + Jetpack Compose + Material 3, single `MainActivity`, MVVM (`ViewModel` + `StateFlow`), coroutines, **manual DI** through `AppContainer` held by `WingspanApplication` (no Hilt). Packages: `domain/ballistics`, `domain/geo`, `data/...`, `ui/...`. MapLibre `MapView` hosted in Compose via `AndroidView`; all MapLibre calls funnel through `ui/map/MapController.kt`. |
| Basemaps | Raster styles built as MapLibre style JSON in `app/src/main/assets/styles/`: USGS Topo (`https://basemap.nationalmap.gov/arcgis/rest/services/USGSTopo/MapServer/tile/{z}/{y}/{x}`, maxzoom 16), USGS Imagery (`.../USGSImageryOnly/MapServer/tile/{z}/{y}/{x}`, maxzoom 16), Esri World Imagery (`https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}`, maxzoom 19). The live map loads the style JSON from assets (`Style.Builder().fromJson`). Offline downloads must use an `https://` style URL (MapLibre's offline downloader only uses the network file source), so the same asset files are referenced at `https://raw.githubusercontent.com/nlafevers/wingspan/main/app/src/main/assets/styles/<file>.json`. Tile URLs are identical in both, so offline tiles serve the live map. |
| Ballistics | Point-mass sphere trajectory, RK4, dt 0.002 s, ISA sea level (air density 1.225 kg/m³, speed of sound 340.3 m/s), Mach-dependent sphere drag table, no wind. Max range = best of launch angles 10°–50° in 1° steps. Validated: results are within ±10% of Journee's rule (max yards ≈ 2200 × diameter in inches) for lead at 1300 fps across #9–F. Choke never affects max range. |
| Effective range | `min(energy-limited distance, choke pattern-limited distance)`. Energy-limited = distance along a flat trajectory where a single pellet's kinetic energy drops below a threshold (default 1.5 ft·lbf, editable). Pattern-limited from a table: Cylinder 25 yd, Skeet 25, Improved Cylinder 30, Light Modified 33, Modified 35, Improved Modified 38, Full 40, Extra Full 45. Fans are **computed** against max range; effective range is drawn as an inner arc. |
| Shot table | Birdshot #9 (0.080"), #8½ (0.085), #8 (0.090), #7½ (0.095), #7 (0.100), #6 (0.110), #5 (0.120), #4 (0.130), #3 (0.140), #2 (0.150), #1 (0.160), B (0.170), BB (0.180), BBB (0.190), T (0.200), F (0.220), plus Custom diameter. Materials: lead 11.34 g/cc, steel 7.86, bismuth 9.6, tungsten (TSS) 18.0, plus Custom density. |
| Fan algorithm | Local ENU projection around the shooter (equirectangular, R = 6371008.8 m). Cast a ray every **1°** (true bearing) out to max range. A bearing is blocked if the ray segment intersects any polygon edge, or passes within a marker's radius. If the shooter is inside a polygon or marker circle → no fans + warning. Blocked runs are dilated by **±2°** safety margin. Contiguous clear bearings form fans; fans narrower than **5°** are dropped. No zones → one full-circle fan. Recompute when position moves > 5 m, zones change, or settings change. |
| Bearings | Displayed as **magnetic**: `magnetic = (true − declination + 360) mod 360`, declination from `android.hardware.GeomagneticField` (WMM, offline). |
| Location | Play Services `FusedLocationProviderClient` (2 s interval, high accuracy) plus a **manual position** mode (long-press the map to place the shooter; banner shown while active). GPS accuracy circle drawn when live. |
| Markers | Point + per-marker radius (default 25 m, editable). Radius circle rendered. |
| Editor | Tap to place vertices/markers, then drag to refine. Existing vertices and markers are draggable; midpoint handles insert vertices; a selected vertex can be deleted; undo last vertex while drawing; name/rename; delete zone. Drag is implemented with `MapView.setOnTouchListener` + `queryRenderedFeatures` on handle layers, disabling map gestures during a drag. |
| Persistence | Room DB `wingspan.db` (tables `zones`, `snapshots`), DataStore preferences for settings. Zone polygons store vertices as a JSON string column. |
| Interchange | GeoJSON `FeatureCollection` via Storage Access Framework. Polygons → `Polygon` with `name`; markers → `Point` with `name`, `radius_m`. Import offers Replace or Merge. |
| Offline maps | MapLibre `OfflineManager` + `OfflineTilePyramidRegionDefinition` for the visible bounds, zoom range chosen by user (capped at basemap maxzoom), region list with progress and delete. Ambient cache raised to 200 MB. |
| Snapshots | `MapLibreMap.snapshot()` bitmap saved as PNG in `filesDir/snapshots/`, plus a Room record: timestamp, position, position source, settings JSON, max/effective range, fans (true + magnetic bearings), declination, notes. Browsable list, detail view, "show on map" overlay mode, share via Android share sheet (`FileProvider`), zip export of all snapshots. |
| Units | Yards / fps by default with a metric toggle (m / m/s). Internal storage is always metric meters and fps for velocity input. |
| Defaults | Shot #6, lead, 1250 fps, Modified choke, 1.5 ft·lbf, imperial, USGS Topo basemap. Default camera (before a fix): lat 39.5, lon −98.35, zoom 3. |

## Phase 0: Toolchain And Project Skeleton

Goal: the sandbox can build an empty Compose app with `./gradlew assembleDebug` and run JVM unit tests.

- [ ] **WS-0.1** Install JDK 21 and the Android SDK in the sandbox and persist the environment
  - **Repos:** none (environment only; nothing to commit)
  - **Read:** /etc/sandbox-persistent.sh
  - **Edit:** /etc/sandbox-persistent.sh, /home/agent/.gradle/gradle.properties
  - **Instructions:**
    1. Install packages: `sudo apt-get update && sudo apt-get install -y openjdk-21-jdk-headless unzip zip`.
    2. Install Android command-line tools:
       ```bash
       mkdir -p /home/agent/android-sdk/cmdline-tools
       curl -fsSL -o /tmp/cmdline-tools.zip https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip
       rm -rf /tmp/cmdline-tools && unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools
       mv /tmp/cmdline-tools/cmdline-tools /home/agent/android-sdk/cmdline-tools/latest
       ```
    3. Accept licenses and install SDK packages (the `JAVA_TOOL_OPTIONS` proxy settings already in the environment are honored by `sdkmanager`):
       ```bash
       export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/home/agent/android-sdk
       yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses > /dev/null
       $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
       ```
    4. Append the following block to `/etc/sandbox-persistent.sh` (guarded so PATH does not grow on every command; the file is sourced before every shell command):
       ```bash
       if [ -z "${WINGSPAN_ENV_DONE:-}" ]; then
         export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
         export ANDROID_HOME=/home/agent/android-sdk
         export ANDROID_SDK_ROOT=$ANDROID_HOME
         export PATH=$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH
         export WINGSPAN_ENV_DONE=1
       fi
       ```
    5. Create `/home/agent/.gradle/gradle.properties` with proxy settings copied from the current value of `echo "$JAVA_TOOL_OPTIONS"` (expected host `gateway.docker.internal`, port `3128`):
       ```properties
       systemProp.http.proxyHost=gateway.docker.internal
       systemProp.http.proxyPort=3128
       systemProp.https.proxyHost=gateway.docker.internal
       systemProp.https.proxyPort=3128
       systemProp.http.nonProxyHosts=localhost|127.*|[::1]|gateway.docker.internal
       org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
       ```
  - **Verify:** `bash -l -c 'java -version 2>&1 | head -1; echo JAVA_HOME=$JAVA_HOME; sdkmanager --list_installed 2>/dev/null | grep -E "platforms;android-36|build-tools;36.0.0|platform-tools"'`
  - **Done when:** `java -version` reports 21.x; `JAVA_HOME` points at the JDK 21 directory; the three SDK packages are listed as installed.

- [ ] **WS-0.2** Create the Gradle wrapper, root build files, version catalog, and gitignore
  - **Repos:** wingspan
  - **Read:** .gitignore
  - **Edit:** .gitignore, settings.gradle.kts, build.gradle.kts, gradle.properties, gradle/libs.versions.toml
  - **Instructions:**
    1. Obtain Gradle 8.14.5 once and generate the wrapper in the repo root (this creates `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`):
       ```bash
       curl -fsSL -o /tmp/gradle.zip https://services.gradle.org/distributions/gradle-8.14.5-bin.zip
       rm -rf /home/agent/gradle-8.14.5 && unzip -q /tmp/gradle.zip -d /home/agent
       cd /home/nathan/wingspan && /home/agent/gradle-8.14.5/bin/gradle wrapper --gradle-version 8.14.5 --distribution-type bin --no-daemon
       chmod +x gradlew
       ```
    2. Append to `.gitignore` (keep existing content):
       ```
       # Android / Gradle
       .gradle/
       build/
       local.properties
       *.apk
       *.aab
       captures/
       .kotlin/
       ```
    3. Write `settings.gradle.kts`:
       ```kotlin
       pluginManagement {
           repositories {
               google()
               mavenCentral()
               gradlePluginPortal()
           }
       }
       dependencyResolutionManagement {
           repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
           repositories {
               google()
               mavenCentral()
           }
       }
       rootProject.name = "wingspan"
       ```
       (Do **not** add `include(":app")` yet; the app directory does not exist.)
    4. Write `build.gradle.kts`:
       ```kotlin
       plugins {
           alias(libs.plugins.android.application) apply false
           alias(libs.plugins.kotlin.android) apply false
           alias(libs.plugins.kotlin.compose) apply false
           alias(libs.plugins.kotlin.serialization) apply false
           alias(libs.plugins.ksp) apply false
       }
       ```
    5. Write `gradle.properties`:
       ```properties
       org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
       org.gradle.caching=true
       android.useAndroidX=true
       android.nonTransitiveRClass=true
       kotlin.code.style=official
       ```
    6. Write `gradle/libs.versions.toml` exactly:
       ```toml
       [versions]
       agp = "8.13.2"
       kotlin = "2.3.21"
       ksp = "2.3.12"
       coreKtx = "1.18.0"
       activityCompose = "1.13.0"
       composeBom = "2026.06.00"
       lifecycle = "2.10.0"
       navigationCompose = "2.9.8"
       room = "2.8.5"
       datastore = "1.2.1"
       maplibre = "13.6.1"
       playServicesLocation = "21.4.0"
       kotlinxSerialization = "1.10.0"
       kotlinxCoroutines = "1.10.2"
       junit = "4.13.2"

       [libraries]
       androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
       androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
       androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
       androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
       androidx-compose-uiTooling = { group = "androidx.compose.ui", name = "ui-tooling" }
       androidx-compose-uiToolingPreview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
       androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
       androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
       androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
       androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
       androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
       androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
       androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
       maplibre-android = { group = "org.maplibre.gl", name = "android-sdk-opengl", version.ref = "maplibre" }
       play-services-location = { group = "com.google.android.gms", name = "play-services-location", version.ref = "playServicesLocation" }
       kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
       kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "kotlinxCoroutines" }
       kotlinx-coroutines-play-services = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-play-services", version.ref = "kotlinxCoroutines" }
       junit = { group = "junit", name = "junit", version.ref = "junit" }
       kotlin-test = { group = "org.jetbrains.kotlin", name = "kotlin-test-junit", version.ref = "kotlin" }

       [plugins]
       android-application = { id = "com.android.application", version.ref = "agp" }
       kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
       kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
       kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
       ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
       ```
    7. Commit the wrapper files (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`, including the jar) together with the build files.
  - **Verify:** `cd /home/nathan/wingspan && ./gradlew --version && ./gradlew help --no-daemon` (the second command downloads AGP and Kotlin plugins; allow up to 10 minutes)
  - **Done when:** `./gradlew --version` prints `Gradle 8.14.5` and a JVM of 21.x; `./gradlew help` finishes with `BUILD SUCCESSFUL`.

- [ ] **WS-0.3** Create the `app` module with an empty Compose activity and all Phase 1 dependencies
  - **Repos:** wingspan
  - **Read:** settings.gradle.kts, gradle/libs.versions.toml
  - **Edit:** settings.gradle.kts, app/build.gradle.kts, app/src/main/AndroidManifest.xml, app/src/main/java/com/wingspan/app/MainActivity.kt, app/src/main/java/com/wingspan/app/ui/theme/Theme.kt
  - **Instructions:**
    1. Append `include(":app")` as the last line of `settings.gradle.kts`.
    2. Write `app/build.gradle.kts`:
       ```kotlin
       plugins {
           alias(libs.plugins.android.application)
           alias(libs.plugins.kotlin.android)
           alias(libs.plugins.kotlin.compose)
           alias(libs.plugins.kotlin.serialization)
           alias(libs.plugins.ksp)
       }

       android {
           namespace = "com.wingspan.app"
           compileSdk = 36

           defaultConfig {
               applicationId = "com.wingspan.app"
               minSdk = 26
               targetSdk = 36
               versionCode = 1
               versionName = "0.1.0"
           }

           buildTypes {
               release {
                   isMinifyEnabled = false
                   proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
               }
           }

           compileOptions {
               sourceCompatibility = JavaVersion.VERSION_17
               targetCompatibility = JavaVersion.VERSION_17
           }

           buildFeatures {
               compose = true
           }

           lint {
               abortOnError = false
           }

           packaging {
               resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
           }
       }

       kotlin {
           compilerOptions {
               jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
           }
       }

       dependencies {
           implementation(libs.androidx.core.ktx)
           implementation(libs.androidx.activity.compose)
           implementation(platform(libs.androidx.compose.bom))
           implementation(libs.androidx.compose.ui)
           implementation(libs.androidx.compose.uiToolingPreview)
           debugImplementation(libs.androidx.compose.uiTooling)
           implementation(libs.androidx.compose.material3)
           implementation(libs.androidx.lifecycle.runtime.compose)
           implementation(libs.androidx.lifecycle.viewmodel.compose)
           implementation(libs.androidx.navigation.compose)
           implementation(libs.androidx.room.runtime)
           ksp(libs.androidx.room.compiler)
           implementation(libs.androidx.datastore.preferences)
           implementation(libs.maplibre.android)
           implementation(libs.play.services.location)
           implementation(libs.kotlinx.serialization.json)
           implementation(libs.kotlinx.coroutines.android)
           implementation(libs.kotlinx.coroutines.play.services)
           testImplementation(libs.junit)
           testImplementation(libs.kotlin.test)
       }
       ```
    3. Write `app/src/main/AndroidManifest.xml`:
       ```xml
       <?xml version="1.0" encoding="utf-8"?>
       <manifest xmlns:android="http://schemas.android.com/apk/res/android">

           <uses-permission android:name="android.permission.INTERNET" />
           <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
           <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

           <application
               android:label="Wingspan"
               android:supportsRtl="true"
               android:theme="@android:style/Theme.Material.Light.NoActionBar">
               <activity
                   android:name=".MainActivity"
                   android:exported="true"
                   android:configChanges="orientation|screenSize|keyboardHidden">
                   <intent-filter>
                       <action android:name="android.intent.action.MAIN" />
                       <category android:name="android.intent.category.LAUNCHER" />
                   </intent-filter>
               </activity>
           </application>
       </manifest>
       ```
    4. Write `app/src/main/java/com/wingspan/app/ui/theme/Theme.kt` containing a `@Composable fun WingspanTheme(content: @Composable () -> Unit)` that picks `darkColorScheme()` when `isSystemInDarkTheme()` else `lightColorScheme()` (Material 3 defaults, no dynamic color) and wraps `content` in `MaterialTheme(colorScheme = ...)`.
    5. Write `app/src/main/java/com/wingspan/app/MainActivity.kt`: a `ComponentActivity` whose `onCreate` calls `enableEdgeToEdge()` then `setContent { WingspanTheme { Surface(modifier = Modifier.fillMaxSize()) { Text("Wingspan") } } }`.
  - **Verify:** `cd /home/nathan/wingspan && ./gradlew assembleDebug && ls -la app/build/outputs/apk/debug/app-debug.apk` (first run downloads all dependencies; allow up to 10 minutes and run in the background if needed)
  - **Done when:** `BUILD SUCCESSFUL` and `app-debug.apk` exists.

- [ ] **WS-0.4** Add the launcher icon and a smoke unit test
  - **Repos:** wingspan
  - **Read:** app/src/main/AndroidManifest.xml
  - **Edit:** app/src/main/AndroidManifest.xml, app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml, app/src/main/res/drawable/ic_launcher_foreground.xml, app/src/main/res/values/colors.xml, app/src/test/java/com/wingspan/app/SmokeTest.kt
  - **Instructions:**
    1. Write `app/src/main/res/values/colors.xml` with one color: `<color name="ic_launcher_background">#1B5E20</color>`.
    2. Write `app/src/main/res/drawable/ic_launcher_foreground.xml`, a 108×108 `<vector>` (viewport 108×108) drawing a white range-fan glyph: a wedge `M54,58 L54,20 A38,38 0 0,1 86.9,39 Z` (fill `#FFFFFF`), a second wedge `M54,58 L21.1,39 A38,38 0 0,1 54,20 Z` (fill `#FFFFFF`, `android:fillAlpha="0.55"`), and a small circle at the apex `M54,58 m-5,0 a5,5 0 1,0 10,0 a5,5 0 1,0 -10,0` (fill `#FFFFFF`).
    3. Write `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`:
       ```xml
       <?xml version="1.0" encoding="utf-8"?>
       <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
           <background android:drawable="@color/ic_launcher_background" />
           <foreground android:drawable="@drawable/ic_launcher_foreground" />
       </adaptive-icon>
       ```
    4. In `AndroidManifest.xml`, add `android:icon="@mipmap/ic_launcher"` to the `<application>` element.
    5. Write `app/src/test/java/com/wingspan/app/SmokeTest.kt` with one JUnit 4 test asserting `2 + 2 == 4` (this proves the unit-test toolchain works).
  - **Verify:** `cd /home/nathan/wingspan && ./gradlew assembleDebug testDebugUnitTest`
  - **Done when:** build succeeds and the test report `app/build/reports/tests/testDebugUnitTest/index.html` shows 1 test passed.

Acceptance criteria for Phase 0:

- `./gradlew assembleDebug testDebugUnitTest` succeeds in the sandbox.
- `gradle/libs.versions.toml` pins every library version listed in the Design Decisions table.
- The repo ignores `build/`, `.gradle/`, `local.properties`.

## Phase 1: Ballistics Domain

Goal: a pure-Kotlin, unit-tested ballistics model that turns load settings into maximum and effective pellet ranges.

- [ ] **WS-1.1** Add shot, material, choke tables and unit constants
  - **Repos:** wingspan
  - **Read:** app/src/test/java/com/wingspan/app/SmokeTest.kt
  - **Edit:** app/src/main/java/com/wingspan/app/domain/ballistics/ShotSize.kt, app/src/main/java/com/wingspan/app/domain/ballistics/PelletMaterial.kt, app/src/main/java/com/wingspan/app/domain/ballistics/Choke.kt, app/src/main/java/com/wingspan/app/domain/ballistics/Units.kt, app/src/test/java/com/wingspan/app/domain/ballistics/TablesTest.kt
  - **Instructions:**
    1. `ShotSize.kt`: `enum class ShotSize(val label: String, val diameterInches: Double)` with entries in this order: `NO_9("#9", 0.080)`, `NO_8_5("#8½", 0.085)`, `NO_8("#8", 0.090)`, `NO_7_5("#7½", 0.095)`, `NO_7("#7", 0.100)`, `NO_6("#6", 0.110)`, `NO_5("#5", 0.120)`, `NO_4("#4", 0.130)`, `NO_3("#3", 0.140)`, `NO_2("#2", 0.150)`, `NO_1("#1", 0.160)`, `B("B", 0.170)`, `BB("BB", 0.180)`, `BBB("BBB", 0.190)`, `T("T", 0.200)`, `F("F", 0.220)`, `CUSTOM("Custom", 0.0)`.
    2. `PelletMaterial.kt`: `enum class PelletMaterial(val label: String, val densityGcc: Double)`: `LEAD("Lead", 11.34)`, `STEEL("Steel", 7.86)`, `BISMUTH("Bismuth", 9.6)`, `TUNGSTEN("Tungsten (TSS)", 18.0)`, `CUSTOM("Custom", 0.0)`.
    3. `Choke.kt`: `enum class Choke(val label: String, val patternRangeYards: Double)`: `CYLINDER("Cylinder", 25.0)`, `SKEET("Skeet", 25.0)`, `IMPROVED_CYLINDER("Improved Cylinder", 30.0)`, `LIGHT_MODIFIED("Light Modified", 33.0)`, `MODIFIED("Modified", 35.0)`, `IMPROVED_MODIFIED("Improved Modified", 38.0)`, `FULL("Full", 40.0)`, `EXTRA_FULL("Extra Full", 45.0)`.
    4. `Units.kt`: `object Units` with `const val YARD_M = 0.9144`, `const val FOOT_M = 0.3048`, `const val INCH_M = 0.0254`, `const val FTLBF_J = 1.3558179483`, `const val GCC_TO_KGM3 = 1000.0`, and functions `fpsToMps(fps: Double)`, `mpsToFps(mps: Double)`, `metersToYards(m: Double)`, `yardsToMeters(yd: Double)`, `joulesToFtLbf(j: Double)`, `ftLbfToJoules(ftlbf: Double)`.
    5. `TablesTest.kt` (JUnit 4): assert `ShotSize.NO_2.diameterInches == 0.150` (delta 1e-9), `ShotSize.entries.size == 17`, `PelletMaterial.LEAD.densityGcc == 11.34`, `Choke.FULL.patternRangeYards == 40.0`, `Units.fpsToMps(1000.0)` ≈ 304.8, `Units.metersToYards(Units.yardsToMeters(100.0))` ≈ 100.0.
  - **Verify:** `cd /home/nathan/wingspan && ./gradlew testDebugUnitTest`
  - **Done when:** `TablesTest` passes; no `android.*` imports in `domain/ballistics`.

- [ ] **WS-1.2** Implement the atmosphere/drag model and the trajectory integrator
  - **Repos:** wingspan
  - **Read:** app/src/main/java/com/wingspan/app/domain/ballistics/Units.kt
  - **Edit:** app/src/main/java/com/wingspan/app/domain/ballistics/DragModel.kt, app/src/main/java/com/wingspan/app/domain/ballistics/Pellet.kt, app/src/main/java/com/wingspan/app/domain/ballistics/Trajectory.kt, app/src/test/java/com/wingspan/app/domain/ballistics/TrajectoryTest.kt
  - **Instructions:**
    1. `DragModel.kt`: `object DragModel` with `const val AIR_DENSITY_KG_M3 = 1.225`, `const val SPEED_OF_SOUND_MPS = 340.3`, `const val GRAVITY_MPS2 = 9.80665`, and a private sphere drag table of `(mach, cd)` pairs: `(0.0, 0.47), (0.6, 0.47), (0.8, 0.52), (0.9, 0.62), (1.0, 0.80), (1.1, 0.95), (1.2, 1.00), (1.5, 0.98), (2.0, 0.94), (3.0, 0.92)`. Function `fun dragCoefficient(mach: Double): Double` linearly interpolates between table points, clamping below the first and above the last entry.
    2. `Pellet.kt`: `data class Pellet(val diameterM: Double, val densityKgM3: Double)` with computed properties `massKg = densityKgM3 * PI / 6.0 * diameterM.pow(3)`, `areaM2 = PI * diameterM * diameterM / 4.0`, and `dragFactor = 0.5 * DragModel.AIR_DENSITY_KG_M3 * areaM2 / massKg` (so deceleration magnitude = `dragFactor * cd * v²`). Add `companion object { fun of(diameterInches: Double, densityGcc: Double): Pellet }` using `Units.INCH_M` and `Units.GCC_TO_KGM3`. Require `diameterM > 0` and `densityKgM3 > 0`.
    3. `Trajectory.kt`: `object Trajectory` with:
       - `fun horizontalRangeM(pellet: Pellet, muzzleVelocityMps: Double, launchAngleDeg: Double, dtSeconds: Double = 0.002): Double` — integrate state `(x, y, vx, vy)` from `(0, 0, v0·cos θ, v0·sin θ)` with RK4 using acceleration `ax = -k·cd(M)·v·vx`, `ay = -k·cd(M)·v·vy − g` where `v = hypot(vx, vy)`, `M = v / SPEED_OF_SOUND_MPS`, `k = pellet.dragFactor`. Stop when the new `y < 0` and return `x` linearly interpolated to the ground crossing. Guard with a maximum of 200000 steps.
       - `fun flatFireDistanceToEnergyM(pellet: Pellet, muzzleVelocityMps: Double, energyThresholdJ: Double, dtSeconds: Double = 0.0005): Double` — one-dimensional: while `0.5·m·v² > threshold`, apply `v -= k·cd(M)·v²·dt; x += v·dt`; return `x`. Return `0.0` if muzzle energy is already below threshold; cap `x` at 3000 m.
    4. `TrajectoryTest.kt`: (a) `dragCoefficient(0.3) == 0.47`, `dragCoefficient(1.05)` ≈ 0.875, `dragCoefficient(5.0) == 0.92`; (b) for `Pellet.of(0.150, 11.34)` at 396.24 m/s (1300 fps), range at 25° is greater than range at 5° and greater than range at 60°; (c) `flatFireDistanceToEnergyM` with a threshold above muzzle energy returns 0.0; (d) `flatFireDistanceToEnergyM` for #2 lead at 396.24 m/s with threshold 2.03 J (1.5 ft·lbf) is between 90 m and 130 m (prototype value ≈ 113 m).
  - **Verify:** `cd /home/nathan/wingspan && ./gradlew testDebugUnitTest`
  - **Done when:** `TrajectoryTest` passes.

- [ ] **WS-1.3** Implement `RangeCalculator` and validate against Journee's rule
  - **Repos:** wingspan
  - **Read:** app/src/main/java/com/wingspan/app/domain/ballistics/Trajectory.kt, app/src/main/java/com/wingspan/app/domain/ballistics/Pellet.kt, app/src/main/java/com/wingspan/app/domain/ballistics/Choke.kt, app/src/main/java/com/wingspan/app/domain/ballistics/Units.kt
  - **Edit:** app/src/main/java/com/wingspan/app/domain/ballistics/BallisticInput.kt, app/src/main/java/com/wingspan/app/domain/ballistics/RangeResult.kt, app/src/main/java/com/wingspan/app/domain/ballistics/RangeCalculator.kt, app/src/test/java/com/wingspan/app/domain/ballistics/RangeCalculatorTest.kt
  - **Instructions:**
    1. `BallisticInput.kt`: `data class BallisticInput(val diameterInches: Double, val densityGcc: Double, val muzzleVelocityFps: Double, val choke: Choke, val energyThresholdFtLbf: Double)`.
    2. `RangeResult.kt`: `data class RangeResult(val maxRangeM: Double, val optimalAngleDeg: Double, val effectiveRangeM: Double, val energyLimitedRangeM: Double, val patternLimitedRangeM: Double, val muzzleEnergyJ: Double)` with a computed `val effectiveRangeLimiter: String` returning `"energy"` if `energyLimitedRangeM <= patternLimitedRangeM` else `"pattern"`.
    3. `RangeCalculator.kt`: `object RangeCalculator { fun compute(input: BallisticInput): RangeResult }`:
       - Build `Pellet.of(input.diameterInches, input.densityGcc)` and `v0 = Units.fpsToMps(input.muzzleVelocityFps)`.
       - Sweep launch angles 10.0 to 50.0 inclusive in 1.0° steps with `Trajectory.horizontalRangeM`; keep the largest range and its angle.
       - `energyLimitedRangeM = Trajectory.flatFireDistanceToEnergyM(pellet, v0, Units.ftLbfToJoules(input.energyThresholdFtLbf))`.
       - `patternLimitedRangeM = Units.yardsToMeters(input.choke.patternRangeYards)`.
       - `effectiveRangeM = min(energyLimitedRangeM, patternLimitedRangeM)`; also clamp `effectiveRangeM` to at most `maxRangeM`.
       - `muzzleEnergyJ = 0.5 * pellet.massKg * v0²`.
    4. `RangeCalculatorTest.kt`: for lead (11.34) at 1300 fps, Modified choke, 1.5 ft·lbf, assert for each of `#9 (0.080)`, `#6 (0.110)`, `#2 (0.150)`, `BB (0.180)`, `F (0.220)` that `Units.metersToYards(maxRangeM)` is within **12%** of `2200 × diameterInches` (prototype values: 194, 254, 328, 381, 449 yd). Assert BB steel (7.86) at 1450 fps has a smaller max range than BB lead at 1300 fps. Assert `optimalAngleDeg` is between 20 and 30 for #2 lead. Assert #8 lead at 1200 fps with `FULL` choke has `effectiveRangeM <= Units.yardsToMeters(40.0)` and `effectiveRangeM <= maxRangeM`. Assert BB lead at 1300 fps with `CYLINDER` has `effectiveRangeLimiter == "pattern"`.
  - **Verify:** `cd /home/nathan/wingspan && ./gradlew testDebugUnitTest`
  - **Done when:** `RangeCalculatorTest` passes.

Acceptance criteria for Phase 1:

- `RangeCalculator.compute` returns max range within ±12% of Journee's rule for lead at 1300 fps for all tested sizes.
- Effective range is never greater than max range and is capped by the choke table.
- No file under `domain/ballistics` imports `android.*` or `androidx.*`.

## Phase 2: Geometry Domain (Fans)

Goal: pure-Kotlin geometry that projects lat/lon to local meters, tests rays against zones, and produces range fans with bearings.

- [ ] **WS-2.1** Add lat/lon types, ENU projection, and 2-D primitives
  - **Repos:** wingspan
  - **Read:** app/src/main/java/com/wingspan/app/domain/ballistics/Units.kt
  - **Edit:** app/src/main/java/com/wingspan/app/domain/geo/LatLon.kt, app/src/main/java/com/wingspan/app/domain/geo/EnuProjection.kt, app/src/main/java/com/wingspan/app/domain/geo/Geometry2D.kt, app/src/test/java/com/wingspan/app/domain/geo/Geometry2DTest.kt
  - **Instructions:**
    1. `LatLon.kt`: `data class LatLon(val lat: Double, val lon: Double)`.
    2. `EnuProjection.kt`: `class EnuProjection(val origin: LatLon)` with `const EARTH_RADIUS_M = 6371008.8` (in a companion), `fun toEnu(p: LatLon): Vec2` where `x = toRadians(p.lon − origin.lon) · cos(toRadians(origin.lat)) · R` and `y = toRadians(p.lat − origin.lat) · R`, and `fun fromEnu(v: Vec2): LatLon` (inverse). Add `fun distanceM(a: LatLon, b: LatLon): Double` in a top-level function using the haversine formula.
    3. `Geometry2D.kt`: `data class Vec2(val x: Double, val y: Double)` with `plus`, `minus`, `times(scalar)`, `length`, and `object Geometry2D` with:
       - `fun segmentsIntersect(p1: Vec2, p2: Vec2, q1: Vec2, q2: Vec2): Boolean` (orientation test including collinear-overlap cases).
       - `fun pointInPolygon(p: Vec2, polygon: List<Vec2>): Boolean` (ray casting; polygon is not required to repeat its first vertex).
       - `fun distancePointToSegment(p: Vec2, a: Vec2, b: Vec2): Double`.
       - `fun bearingToUnitVector(bearingDeg: Double): Vec2` = `Vec2(sin(θ), cos(θ))` (bearing clockwise from north; +x = east, +y = north).
       - `fun normalizeBearing(deg: Double): Double` returning a value in `[0, 360)`.
    4. `Geometry2DTest.kt`: crossing segments intersect; parallel disjoint segments do not; touching-at-endpoint counts as intersecting; point-in-square true/false cases; distance from `(0,5)` to segment `(-10,0)-(10,0)` is 5; `bearingToUnitVector(90.0)` ≈ `(1, 0)`; `normalizeBearing(-10.0) == 350.0`; `EnuProjection(LatLon(39.0, -105.0))` round-trips a point 500 m north-east within 0.01 m, and `toEnu` of a point 0.001° north gives `y ≈ 111.2 m` (±0.5 m).
  - **Verify:** `cd /home/nathan/wingspan && ./gradlew testDebugUnitTest`
  - **Done when:** `Geometry2DTest` passes.

- [ ] **WS-2.2** Implement the fan calculator, sector outlines, bearing conversion, and tile math
  - **Repos:** wingspan
  - **Read:** app/src/main/java/com/wingspan/app/domain/geo/Geometry2D.kt, app/src/main/java/com/wingspan/app/domain/geo/EnuProjection.kt, app/src/main/java/com/wingspan/app/domain/geo/LatLon.kt
  - **Edit:** app/src/main/java/com/wingspan/app/domain/geo/Zones.kt, app/src/main/java/com/wingspan/app/domain/geo/FanCalculator.kt, app/src/main/java/com/wingspan/app/domain/geo/Sector.kt, app/src/main/java/com/wingspan/app/domain/geo/TileMath.kt, app/src/test/java/com/wingspan/app/domain/geo/FanCalculatorTest.kt
  - **Instructions:**
    1. `Zones.kt`: `sealed interface NoFireZone { val id: Long; val name: String }` with `data class NoFirePolygon(override val id: Long, override val name: String, val vertices: List<LatLon>) : NoFireZone` and `data class NoFireMarker(override val id: Long, override val name: String, val center: LatLon, val radiusM: Double) : NoFireZone`. Also `interface DeclinationProvider { fun declinationDeg(position: LatLon): Double }` and `fun trueToMagnetic(trueDeg: Double, declinationDeg: Double): Double = normalizeBearing(trueDeg − declinationDeg)`.
    2. `FanCalculator.kt`:
       - `data class Fan(val leftTrueDeg: Double, val rightTrueDeg: Double, val fullCircle: Boolean = false)` with `val widthDeg: Double` = 360.0 if `fullCircle` else `normalizeBearing(rightTrueDeg − leftTrueDeg)`.
       - `data class FanResult(val fans: List<Fan>, val insideZone: Boolean, val blockedBearings: Int)`.
       - `object FanCalculator { fun compute(origin: LatLon, maxRangeM: Double, zones: List<NoFireZone>, stepDeg: Double = 1.0, marginDeg: Double = 2.0, minFanDeg: Double = 5.0): FanResult }` implemented as:
         a. `proj = EnuProjection(origin)`; project polygons to `List<Vec2>` and markers to `(Vec2, radiusM)`.
         b. If the origin `(0,0)` is inside any polygon (`pointInPolygon`) or within any marker radius → return `FanResult(emptyList(), insideZone = true, 360)`.
         c. `n = (360 / stepDeg).toInt()`; `blocked = BooleanArray(n)`. For each `i`, bearing `b = i · stepDeg`, ray end `e = bearingToUnitVector(b) · maxRangeM`. Mark blocked if any polygon edge (including the closing edge last→first) intersects segment `(0,0)–e`, or any marker satisfies `distancePointToSegment(center, (0,0), e) < radiusM`.
         d. Dilate: `m = ceil(marginDeg / stepDeg)`; produce `dilated` where index `i` is blocked if any of `i−m..i+m` (wrapping) is blocked.
         e. If no index is blocked → `FanResult(listOf(Fan(0.0, 360.0, fullCircle = true)), false, 0)`.
         f. Find clear runs: start scanning at the first index `s` such that `dilated[(s−1+n)%n]` is true and `dilated[s]` is false; walk `n` indices from `s`, collecting maximal runs of clear indices (wrapping). For a run of clear indices `a..b` (wrapped), fan `left = a · stepDeg`, `right = b · stepDeg`. Keep fans whose `widthDeg >= minFanDeg`.
         g. `blockedBearings` = count of true in `dilated`.
    3. `Sector.kt`: `object Sector` with `fun arcPoints(origin: LatLon, leftDeg: Double, rightDeg: Double, radiusM: Double, stepDeg: Double = 1.0): List<LatLon>` (points from left to right clockwise at `radiusM`, inclusive of both ends; if `leftDeg == rightDeg` treat as full circle 0→360) and `fun sectorOutline(origin, leftDeg, rightDeg, radiusM, stepDeg = 1.0): List<LatLon>` = `origin + arcPoints + origin` for partial fans, or a closed circle (first point repeated at end) for full circles. Also `fun circleOutline(center: LatLon, radiusM: Double, stepDeg: Double = 5.0): List<LatLon>`.
    4. `TileMath.kt`: `object TileMath` with `fun tileX(lon: Double, zoom: Int): Int`, `fun tileY(lat: Double, zoom: Int): Int` (Web Mercator, clamp lat to ±85.0511, and clamp the resulting index to `[0, 2^zoom − 1]`), and `fun tileCount(south: Double, west: Double, north: Double, east: Double, minZoom: Int, maxZoom: Int): Long` summing `(xMax−xMin+1)·(yMax−yMin+1)` per zoom.
    5. `FanCalculatorTest.kt` with origin `O = LatLon(39.0, -105.0)`, `proj = EnuProjection(O)`, helper `p(x, y) = proj.fromEnu(Vec2(x, y))`:
       - No zones, 300 m → one fan with `fullCircle == true`, `insideZone == false`.
       - Square polygon with corners `p(-50,150), p(50,150), p(50,250), p(-50,250)`, max range 300 m → exactly one fan; `leftTrueDeg` in `[20, 22]`, `rightTrueDeg` in `[338, 340]`; bearing 0 blocked, i.e. the fan's `widthDeg` < 330.
       - Same square with max range 100 m → one full-circle fan (ray never reaches it).
       - Marker at `p(200, 0)` radius 25 m, range 300 m → one fan; `leftTrueDeg` in `[98, 101]` and `rightTrueDeg` in `[79, 82]` (blocked around 90°).
       - Origin inside a square `p(-10,-10), p(10,-10), p(10,10), p(-10,10)` → `insideZone == true`, no fans.
       - Two markers at bearings 0° and 6° (`p(0,200)` and `p(21,199)`) with radius 1 m → the gap between them (< 5° after margin) produces no fan; total fans == 1.
       - `trueToMagnetic(10.0, 8.0) == 2.0`; `trueToMagnetic(5.0, 8.0) == 357.0`.
       - `TileMath.tileCount(-85.0, -180.0, 85.0, 180.0, 0, 1) == 5`; `Sector.sectorOutline(O, 30.0, 60.0, 100.0)` has 33 points, first and last equal to `O`.
  - **Verify:** `cd /home/nathan/wingspan && ./gradlew testDebugUnitTest`
  - **Done when:** `FanCalculatorTest` passes.

Acceptance criteria for Phase 2:

- Fans never include a bearing whose ray to max range passes through a polygon or within a marker radius (plus the ±2° margin).
- A shooter inside a zone yields `insideZone == true` and no fans.
- All of `domain/geo` is Android-free and covered by JVM tests.

## Phase 3: Data Layer

Goal: Room persistence for zones and snapshots, DataStore-backed settings, GeoJSON interchange, and the app-wide `AppContainer`.

- [ ] **WS-3.1** Add Room entities, DAOs, and the database
  - **Repos:** wingspan
  - **Read:** app/build.gradle.kts
  - **Edit:** app/src/main/java/com/wingspan/app/data/db/ZoneEntity.kt, app/src/main/java/com/wingspan/app/data/db/ZoneDao.kt, app/src/main/java/com/wingspan/app/data/db/SnapshotEntity.kt, app/src/main/java/com/wingspan/app/data/db/SnapshotDao.kt, app/src/main/java/com/wingspan/app/data/db/AppDatabase.kt
  - **Instructions:**
    1. `ZoneEntity.kt`: `@Entity(tableName = "zones") data class ZoneEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val type: String, val verticesJson: String?, val lat: Double?, val lon: Double?, val radiusM: Double?, val createdAt: Long)`. Add `companion object { const val TYPE_POLYGON = "POLYGON"; const val TYPE_MARKER = "MARKER" }`. `verticesJson` holds a JSON array of `[lat, lon]` pairs for polygons and is null for markers.
    2. `ZoneDao.kt`: `@Dao interface ZoneDao` with `@Query("SELECT * FROM zones ORDER BY createdAt") fun observeAll(): Flow<List<ZoneEntity>>`, `@Query("SELECT * FROM zones ORDER BY createdAt") suspend fun getAll(): List<ZoneEntity>`, `@Query("SELECT * FROM zones WHERE id = :id") suspend fun getById(id: Long): ZoneEntity?`, `@Insert suspend fun insert(zone: ZoneEntity): Long`, `@Insert suspend fun insertAll(zones: List<ZoneEntity>)`, `@Update suspend fun update(zone: ZoneEntity)`, `@Query("DELETE FROM zones WHERE id = :id") suspend fun deleteById(id: Long)`, `@Query("DELETE FROM zones") suspend fun deleteAll()`.
    3. `SnapshotEntity.kt`: `@Entity(tableName = "snapshots") data class SnapshotEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val timestampMs: Long, val lat: Double, val lon: Double, val positionSource: String, val settingsJson: String, val maxRangeM: Double, val effectiveRangeM: Double, val declinationDeg: Double, val fansJson: String, val notes: String, val imagePath: String)`.
    4. `SnapshotDao.kt`: `observeAll(): Flow<List<SnapshotEntity>>` ordered by `timestampMs DESC`, `suspend fun getAll()`, `suspend fun getById(id: Long): SnapshotEntity?`, `@Insert suspend fun insert(s): Long`, `@Update suspend fun update(s)`, `@Query("DELETE FROM snapshots WHERE id = :id") suspend fun deleteById(id: Long)`.
    5. `AppDatabase.kt`: `@Database(entities = [ZoneEntity::class, SnapshotEntity::class], version = 1, exportSchema = false) abstract class AppDatabase : RoomDatabase()` exposing `abstract fun zoneDao(): ZoneDao` and `abstract fun snapshotDao(): SnapshotDao`, with `companion object { fun build(context: Context): AppDatabase = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "wingspan.db").build() }`.
  - **Verify:** `cd /home/nathan/wingspan && ./gradlew assembleDebug testDebugUnitTest`
  - **Done when:** build succeeds (KSP generates `AppDatabase_Impl` without errors).

- [ ] **WS-3.2** Add `LoadSettings`, `SettingsRepository` (DataStore), and `ZoneRepository`
  - **Repos:** wingspan
  - **Read:** app/src/main/java/com/wingspan/app/domain/ballistics/BallisticInput.kt, app/src/main/java/com/wingspan/app/domain/geo/Zones.kt, app/src/main/java/com/wingspan/app/data/db/ZoneDao.kt, app/src/main/java/com/wingspan/app/data/db/ZoneEntity.kt
  - **Edit:** app/src/main/java/com/wingspan/app/domain/ballistics/LoadSettings.kt, app/src/main/java/com/wingspan/app/data/SettingsRepository.kt, app/src/main/java/com/wingspan/app/data/ZoneRepository.kt, app/src/test/java/com/wingspan/app/domain/ballistics/LoadSettingsTest.kt
  - **Instructions:**
    1. `LoadSettings.kt` (domain, no Android imports): `enum class UnitSystem { IMPERIAL, METRIC }` and `@Serializable data class LoadSettings(val shotSize: ShotSize = ShotSize.NO_6, val material: PelletMaterial = PelletMaterial.LEAD, val customDiameterInches: Double = 0.130, val customDensityGcc: Double = 11.34, val muzzleVelocityFps: Double = 1250.0, val choke: Choke = Choke.MODIFIED, val energyThresholdFtLbf: Double = 1.5, val unitSystem: UnitSystem = UnitSystem.IMPERIAL)` with `fun effectiveDiameterInches()` (custom value when `shotSize == CUSTOM`), `fun effectiveDensityGcc()` (custom when `material == CUSTOM`), and `fun toBallisticInput(): BallisticInput`.
    2. `SettingsRepository.kt`: top-level `val Context.settingsDataStore by preferencesDataStore(name = "settings")`. `class SettingsRepository(private val dataStore: DataStore<Preferences>)` with private keys `shot_size`, `material`, `custom_diameter_in`, `custom_density_gcc`, `muzzle_velocity_fps`, `choke`, `energy_threshold_ftlbf`, `unit_system` (enum names stored as strings; doubles as `doublePreferencesKey`), and `basemap` (string, default `"USGS_TOPO"`). Expose `val settings: Flow<LoadSettings>` (missing/invalid values fall back to `LoadSettings()` defaults; wrap `enumValueOf` in `runCatching`), `suspend fun update(transform: (LoadSettings) -> LoadSettings)`, `val basemapKey: Flow<String>`, `suspend fun setBasemapKey(key: String)`.
    3. `ZoneRepository.kt`: `class ZoneRepository(private val dao: ZoneDao)` with `val zones: Flow<List<NoFireZone>> = dao.observeAll().map { it.map(::toDomain) }`, `suspend fun getAll(): List<NoFireZone>`, `suspend fun addPolygon(name: String, vertices: List<LatLon>): Long`, `suspend fun addMarker(name: String, center: LatLon, radiusM: Double): Long`, `suspend fun updatePolygon(id: Long, name: String, vertices: List<LatLon>)`, `suspend fun updateMarker(id: Long, name: String, center: LatLon, radiusM: Double)`, `suspend fun rename(id: Long, name: String)`, `suspend fun delete(id: Long)`, `suspend fun addAll(zones: List<NoFireZone>)`, `suspend fun replaceAll(zones: List<NoFireZone>)` (deleteAll then insertAll). Vertices are encoded with `kotlinx.serialization.json.Json` as `List<List<Double>>` of `[lat, lon]`. Mapping: `TYPE_POLYGON` → `NoFirePolygon`, `TYPE_MARKER` → `NoFireMarker`; `createdAt = System.currentTimeMillis()` on insert, preserved on update.
    4. `LoadSettingsTest.kt`: defaults map to `BallisticInput(0.110, 11.34, 1250.0, Choke.MODIFIED, 1.5)`; `LoadSettings(shotSize = ShotSize.CUSTOM, customDiameterInches = 0.2)` maps to diameter 0.2; `Json.encodeToString` then `decodeFromString` round-trips `LoadSettings(material = PelletMaterial.STEEL)`.
  - **Verify:** `cd /home/nathan/wingspan && ./gradlew assembleDebug testDebugUnitTest`
  - **Done when:** build and `LoadSettingsTest` pass.

- [ ] **WS-3.3** Implement the GeoJSON codec for zones
  - **Repos:** wingspan
  - **Read:** app/src/main/java/com/wingspan/app/domain/geo/Zones.kt, app/src/main/java/com/wingspan/app/domain/geo/LatLon.kt
  - **Edit:** app/src/main/java/com/wingspan/app/data/geojson/GeoJsonCodec.kt, app/src/test/java/com/wingspan/app/data/geojson/GeoJsonCodecTest.kt
  - **Instructions:**
    1. `GeoJsonCodec.kt`: `object GeoJsonCodec` using `kotlinx.serialization.json` (`buildJsonObject`, `buildJsonArray`, `Json.parseToJsonElement`; no `@Serializable` classes needed):
       - `fun encode(zones: List<NoFireZone>): String` → a `FeatureCollection`. Each `NoFirePolygon` becomes a `Feature` with `geometry.type = "Polygon"`, `coordinates = [[ [lon, lat], ..., [lon, lat] ]]` (ring closed by repeating the first vertex), `properties = { "name": name, "type": "no_fire_polygon" }`. Each `NoFireMarker` becomes a `Feature` with `geometry.type = "Point"`, `coordinates = [lon, lat]`, `properties = { "name": name, "type": "no_fire_marker", "radius_m": radiusM }`. Output is pretty-printed (`Json { prettyPrint = true }`).
       - `fun decode(text: String): List<NoFireZone>` → parses a `FeatureCollection` (or a single `Feature`), returning zones with `id = 0`. `Polygon` → outer ring only, dropping the closing vertex when it equals the first; `MultiPolygon` → one `NoFirePolygon` per polygon; `Point` → `NoFireMarker` with `radius_m` (default `25.0`). `name` defaults to `"Imported polygon"` / `"Imported marker"`. Geometry types other than these are skipped. Throw `IllegalArgumentException` if the root is not a `FeatureCollection`/`Feature` JSON object.
    2. `GeoJsonCodecTest.kt`: round-trip a list with one polygon (3 vertices) and one marker preserves names, vertex count, coordinates (±1e-9), and radius; decoding a hand-written FeatureCollection containing a `Polygon` with a closed ring and no `type` property yields one polygon with 4 vertices → 3 after dropping the closing vertex; a `Point` without `radius_m` yields radius 25.0; invalid root (`{"foo": 1}`) throws.
  - **Verify:** `cd /home/nathan/wingspan && ./gradlew testDebugUnitTest`
  - **Done when:** `GeoJsonCodecTest` passes.

- [ ] **WS-3.4** Add `SnapshotRepository`, `AppContainer`, and `WingspanApplication`
  - **Repos:** wingspan
  - **Read:** app/src/main/java/com/wingspan/app/data/db/SnapshotDao.kt, app/src/main/java/com/wingspan/app/data/db/SnapshotEntity.kt, app/src/main/java/com/wingspan/app/data/SettingsRepository.kt, app/src/main/java/com/wingspan/app/data/ZoneRepository.kt, app/src/main/AndroidManifest.xml
  - **Edit:** app/src/main/java/com/wingspan/app/data/SnapshotRepository.kt, app/src/main/java/com/wingspan/app/AppContainer.kt, app/src/main/java/com/wingspan/app/WingspanApplication.kt, app/src/main/AndroidManifest.xml
  - **Instructions:**
    1. `SnapshotRepository.kt`: define `@Serializable data class SnapshotFan(val leftTrueDeg: Double, val rightTrueDeg: Double, val leftMagDeg: Double, val rightMagDeg: Double, val fullCircle: Boolean)` and `data class FiringSnapshot(val id: Long, val timestampMs: Long, val position: LatLon, val positionSource: String, val settings: LoadSettings, val maxRangeM: Double, val effectiveRangeM: Double, val declinationDeg: Double, val fans: List<SnapshotFan>, val notes: String, val imageFile: File)`. `class SnapshotRepository(private val dao: SnapshotDao, private val imagesDir: File)` with `val snapshots: Flow<List<FiringSnapshot>>`, `suspend fun get(id: Long): FiringSnapshot?`, `suspend fun create(snapshot: FiringSnapshot, pngBytes: ByteArray): Long` (creates `imagesDir` if needed, writes `snap_<timestampMs>.png`, stores its absolute path), `suspend fun updateNotes(id: Long, notes: String)`, `suspend fun delete(id: Long)` (deletes the image file too), `suspend fun getAll(): List<FiringSnapshot>`. Settings and fans are stored as JSON via `kotlinx.serialization`. All file I/O on `Dispatchers.IO`.
    2. `AppContainer.kt`: `class AppContainer(context: Context)` creating, in order: `val database = AppDatabase.build(context)`, `val zoneRepository = ZoneRepository(database.zoneDao())`, `val settingsRepository = SettingsRepository(context.settingsDataStore)`, `val snapshotRepository = SnapshotRepository(database.snapshotDao(), File(context.filesDir, "snapshots"))`. (Later steps add more members here.)
    3. `WingspanApplication.kt`: `class WingspanApplication : Application()` with `lateinit var container: AppContainer`; `onCreate` calls `super.onCreate()`, `org.maplibre.android.MapLibre.getInstance(this)`, then `container = AppContainer(this)`. Add a top-level helper `fun Context.appContainer(): AppContainer = (applicationContext as WingspanApplication).container`.
    4. In `AndroidManifest.xml` add `android:name=".WingspanApplication"` to `<application>`.
  - **Verify:** `cd /home/nathan/wingspan && ./gradlew assembleDebug testDebugUnitTest`
  - **Done when:** build succeeds; manifest references the application class.

Acceptance criteria for Phase 3:

- Zones and snapshots persist in Room; settings persist in DataStore with the documented defaults.
- `GeoJsonCodec` round-trips zones and tolerates foreign GeoJSON (missing custom properties).
- `WingspanApplication` initializes MapLibre before any `MapView` is created.

## Phase 4: Map Screen

Goal: a MapLibre map with switchable USGS/Esri basemaps, live GPS or manual shooter position, and persisted zones drawn as overlays.

- [ ] **WS-4.1** Add basemap style assets and the `Basemap` enum
  - **Repos:** wingspan
  - **Read:** app/src/main/java/com/wingspan/app/data/SettingsRepository.kt
  - **Edit:** app/src/main/assets/styles/usgs_topo.json, app/src/main/assets/styles/usgs_imagery.json, app/src/main/assets/styles/esri_imagery.json, app/src/main/java/com/wingspan/app/data/map/Basemap.kt
  - **Instructions:**
    1. Write `usgs_topo.json`:
       ```json
       {
         "version": 8,
         "name": "USGS Topo",
         "sources": {
           "basemap": {
             "type": "raster",
             "tiles": ["https://basemap.nationalmap.gov/arcgis/rest/services/USGSTopo/MapServer/tile/{z}/{y}/{x}"],
             "tileSize": 256,
             "minzoom": 0,
             "maxzoom": 16,
             "attribution": "USGS The National Map"
           }
         },
         "layers": [
           { "id": "background", "type": "background", "paint": { "background-color": "#e0e0e0" } },
           { "id": "basemap", "type": "raster", "source": "basemap" }
         ]
       }
       ```
    2. Write `usgs_imagery.json` identically but `name` `"USGS Imagery"`, tiles `https://basemap.nationalmap.gov/arcgis/rest/services/USGSImageryOnly/MapServer/tile/{z}/{y}/{x}`, `maxzoom` 16, background `#000000`.
    3. Write `esri_imagery.json` identically but `name` `"Esri World Imagery"`, tiles `https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}`, `maxzoom` 19, attribution `"Esri, Maxar, Earthstar Geographics, and the GIS User Community"`, background `#000000`.
    4. `Basemap.kt`: `enum class Basemap(val key: String, val label: String, val assetFile: String, val maxZoom: Int)`: `USGS_TOPO("USGS_TOPO", "Topo", "usgs_topo.json", 16)`, `USGS_IMAGERY("USGS_IMAGERY", "USGS Imagery", "usgs_imagery.json", 16)`, `ESRI_IMAGERY("ESRI_IMAGERY", "Esri Imagery", "esri_imagery.json", 19)`. Properties: `val assetPath get() = "styles/$assetFile"`, `val remoteStyleUrl get() = "https://raw.githubusercontent.com/nlafevers/wingspan/main/app/src/main/assets/styles/$assetFile"` (used only by offline downloads; MapLibre's offline downloader requires an `https://` style URL). `companion object { fun fromKey(key: String?): Basemap = entries.firstOrNull { it.key == key } ?: USGS_TOPO }`.
  - **Verify:** `cd /home/nathan/wingspan && for f in app/src/main/assets/styles/*.json; do python3 -m json.tool "$f" > /dev/null && echo "ok $f"; done && ./gradlew assembleDebug`
  - **Done when:** all three JSON files parse; build succeeds.

- [ ] **WS-4.2** Host MapLibre in Compose with basemap switching
  - **Repos:** wingspan
  - **Read:** app/src/main/java/com/wingspan/app/data/map/Basemap.kt, app/src/main/java/com/wingspan/app/data/SettingsRepository.kt, app/src/main/java/com/wingspan/app/WingspanApplication.kt, app/src/main/java/com/wingspan/app/MainActivity.kt
  - **Edit:** app/src/main/java/com/wingspan/app/ui/map/MapLibreView.kt, app/src/main/java/com/wingspan/app/ui/map/MapController.kt, app/src/main/java/com/wingspan/app/ui/map/MapViewModel.kt, app/src/main/java/com/wingspan/app/ui/map/MapScreen.kt, app/src/main/java/com/wingspan/app/MainActivity.kt
  - **Instructions:**
    1. `MapController.kt` — the **only** place that talks to MapLibre. `class MapController(private val context: Context)`:
       - Fields: `private var map: MapLibreMap? = null`, `private var style: Style? = null`, `private var basemap: Basemap? = null`.
       - `fun attach(mapView: MapView, map: MapLibreMap)`: store both; `map.uiSettings.isRotateGesturesEnabled = false`, `isTiltGesturesEnabled = false`, `isCompassEnabled = false`, `isLogoEnabled = false`, `isAttributionEnabled = true`; if `basemap != null` call `loadStyle()`.
       - `fun setBasemap(b: Basemap)`: if `b == basemap` return; set and `loadStyle()`.
       - `private fun loadStyle()`: read `context.assets.open(basemap.assetPath)` as text; `map?.setStyle(Style.Builder().fromJson(json)) { loaded -> style = loaded; installOverlays(loaded) }`.
       - `private fun installOverlays(style: Style)`: currently empty except a comment listing the canonical overlay order that later steps must follow when adding sources/layers: `position-accuracy-fill`, `zones-polygons-fill`, `zones-polygons-outline`, `zones-marker-circles-fill`, `zones-marker-circles-outline`, `zones-marker-points`, `fans-fill`, `fans-outline`, `fans-effective`, `fans-selected`, `snapshot-fans-fill`, `snapshot-fans-outline`, `position-dot`, `editor-fill`, `editor-outline`, `editor-midpoints`, `editor-handles`. Every later step must (a) add its sources/layers by inserting code at the matching position inside `installOverlays` so that plain `style.addLayer(...)` calls produce this order (never append out of order; if unavoidable use `style.addLayerBelow(layer, "<next id in the list>")`), and (b) re-apply its last known data after a style reload (keep the last data in fields).
       - `fun moveCamera(target: LatLon, zoom: Double? = null)` and `fun animateCamera(target: LatLon, zoom: Double? = null)` using `CameraUpdateFactory.newLatLngZoom` / `newLatLng`.
       - `fun currentZoom(): Double` (`map?.cameraPosition?.zoom ?: 3.0`).
    2. `MapLibreView.kt`: `@Composable fun MapLibreView(controller: MapController, modifier: Modifier = Modifier)`:
       - `val mapView = remember { MapView(context, MapLibreMapOptions.createFromAttributes(context).camera(CameraPosition.Builder().target(LatLng(39.5, -98.35)).zoom(3.0).build())) }`.
       - `DisposableEffect(lifecycleOwner)`: add a `LifecycleEventObserver` that forwards `ON_CREATE → mapView.onCreate(null)`, `ON_START → onStart()`, `ON_RESUME → onResume()`, `ON_PAUSE → onPause()`, `ON_STOP → onStop()`, `ON_DESTROY → onDestroy()`; `onDispose` removes the observer and calls `mapView.onDestroy()`.
       - `AndroidView(modifier = modifier, factory = { mapView })` and a `LaunchedEffect(mapView) { mapView.getMapAsync { controller.attach(mapView, it) } }`.
    3. `MapViewModel.kt`: `class MapViewModel(private val settingsRepository: SettingsRepository) : ViewModel()` exposing `val basemap: StateFlow<Basemap>` (from `settingsRepository.basemapKey.map(Basemap::fromKey)`, `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Basemap.USGS_TOPO)`) and `fun setBasemap(b: Basemap)` (launches `settingsRepository.setBasemapKey(b.key)`). Provide `companion object { fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory { initializer { MapViewModel(container.settingsRepository) } } }`.
    4. `MapScreen.kt`: `@Composable fun MapScreen(viewModel: MapViewModel = viewModel(factory = MapViewModel.factory(LocalContext.current.appContainer())))`. Body: `val controller = remember { MapController(context.applicationContext) }`; `val basemap by viewModel.basemap.collectAsStateWithLifecycle()`; `LaunchedEffect(basemap) { controller.setBasemap(basemap) }`; `Box(Modifier.fillMaxSize()) { MapLibreView(controller, Modifier.fillMaxSize()); Row(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)) { Basemap.entries.forEach { FilterChip(selected = it == basemap, onClick = { viewModel.setBasemap(it) }, label = { Text(it.label) }) } } }`.
    5. `MainActivity.kt`: replace the placeholder content with `WingspanTheme { MapScreen() }`.
  - **Verify:** `cd /home/nathan/wingspan && ./gradlew assembleDebug testDebugUnitTest`
  - **Done when:** build succeeds. **(manual, on device)**: the map renders USGS Topo; the chips switch basemaps and the choice survives an app restart.

- [ ] **WS-4.3** Add live GPS position, manual position mode, and camera controls
  - **Repos:** wingspan
  - **Read:** app/src/main/java/com/wingspan/app/ui/map/MapController.kt, app/src/main/java/com/wingspan/app/ui/map/MapViewModel.kt, app/src/main/java/com/wingspan/app/ui/map/MapScreen.kt, app/src/main/java/com/wingspan/app/domain/geo/Sector.kt, app/src/main/java/com/wingspan/app/AppContainer.kt
  - **Edit:** app/src/main/java/com/wingspan/app/data/location/LocationProvider.kt, app/src/main/java/com/wingspan/app/AppContainer.kt, app/src/main/java/com/wingspan/app/ui/map/MapController.kt, app/src/main/java/com/wingspan/app/ui/map/MapViewModel.kt, app/src/main/java/com/wingspan/app/ui/map/MapScreen.kt
  - **Instructions:**
    1. `LocationProvider.kt`: `data class LocationFix(val position: LatLon, val accuracyM: Double, val timeMs: Long)`. `class LocationProvider(context: Context)` wrapping `LocationServices.getFusedLocationProviderClient(context)`; `@SuppressLint("MissingPermission") fun updates(): Flow<LocationFix> = callbackFlow { ... }` using `LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L).setMinUpdateIntervalMillis(1000L).build()`, a `LocationCallback` that `trySend`s `result.lastLocation`, `requestLocationUpdates(request, callback, Looper.getMainLooper())`, and `awaitClose { removeLocationUpdates(callback) }`. Add `fun hasPermission(context): Boolean` checking `ACCESS_FINE_LOCATION` or `ACCESS_COARSE_LOCATION` via `ContextCompat.checkSelfPermission`.
    2. `AppContainer.kt`: add `val locationProvider = LocationProvider(context)`.
    3. `MapViewModel.kt` (constructor now takes `settingsRepository` and `locationProvider`; update the factory):
       - Types: `enum class PositionSource { GPS, MANUAL }`, `data class ShooterPosition(val position: LatLon, val source: PositionSource, val accuracyM: Double?)`.
       - State: `private val gpsFix = MutableStateFlow<LocationFix?>(null)`, `private val manualPosition = MutableStateFlow<LatLon?>(null)`, `val manualMode = MutableStateFlow(false)`, `val hasLocationPermission = MutableStateFlow(false)`, and `val shooter: StateFlow<ShooterPosition?>` = combine of the three: manual mode with a manual position → `MANUAL`; otherwise the GPS fix → `GPS` with accuracy; else null.
       - `fun onLocationPermissionResult(granted: Boolean)`: set the flag; if granted and not already collecting, `viewModelScope.launch { locationProvider.updates().collect { gpsFix.value = it } }`.
       - `fun toggleManualMode()`: flips `manualMode`; when turning on and `manualPosition` is null, seed it with the current GPS position (if any).
       - `fun setManualPosition(p: LatLon)` (only applied when `manualMode.value` is true).
       - `val cameraRequests = MutableSharedFlow<LatLon>(extraBufferCapacity = 1)`; `fun recenter()` emits the current shooter position; the first GPS fix after permission also emits once (flag `centeredOnce`).
    4. `MapController.kt`:
       - In `installOverlays`, add `GeoJsonSource("position-accuracy")` + `FillLayer("position-accuracy-fill", "position-accuracy")` with `fillColor("#1E88E5")`, `fillOpacity(0.15f)`; and `GeoJsonSource("position")` + `CircleLayer("position-dot", "position")` with `circleRadius(7f)`, `circleColor(Expression.get("color"))`, `circleStrokeColor("#FFFFFF")`, `circleStrokeWidth(2f)` — added at the positions given by the canonical order (accuracy fill first, dot after the fan/snapshot layers, which do not exist yet).
       - `fun setShooter(s: ShooterPosition?)`: remember it in a field; build a `Feature` from `org.maplibre.geojson.Point.fromLngLat(lon, lat)` with string property `color` = `"#1E88E5"` for GPS or `"#FB8C00"` for manual; accuracy polygon from `Sector.circleOutline(position, accuracyM)` when `source == GPS && accuracyM != null`, else an empty `FeatureCollection`. Apply via `style.getSourceAs<GeoJsonSource>(id)?.setGeoJson(...)`. Re-apply inside `installOverlays` after a style reload.
       - `fun setOnLongPress(listener: (LatLon) -> Unit)` wiring `map.addOnMapLongClickListener { listener(LatLon(it.latitude, it.longitude)); true }` (register once in `attach`, delegating to a mutable field).
    5. `MapScreen.kt`:
       - `val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result -> viewModel.onLocationPermissionResult(result.values.any { it }) }`; `LaunchedEffect(Unit) { if (LocationProvider.hasPermission(context)) viewModel.onLocationPermissionResult(true) else permissionLauncher.launch(arrayOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)) }`.
       - Collect `shooter` and `manualMode`; `LaunchedEffect(shooter) { controller.setShooter(shooter) }`; `LaunchedEffect(Unit) { viewModel.cameraRequests.collect { controller.animateCamera(it, zoom = maxOf(controller.currentZoom(), 15.0)) } }`; `LaunchedEffect(controller) { controller.setOnLongPress { viewModel.setManualPosition(it) } }`.
       - Bottom-end `Column` of `SmallFloatingActionButton`s: "My location" (`Icons.Default.Place` → `viewModel.recenter()`; if permission missing, launch the permission request) and "Manual position" toggle (`Icons.Default.Edit`, tinted when active → `viewModel.toggleManualMode()`).
       - When `manualMode` is true show a top-center banner `Surface(color = MaterialTheme.colorScheme.tertiaryContainer) { Text("MANUAL POSITION — long-press map to move") }` below the status bar.
  - **Verify:** `cd /home/nathan/wingspan && ./gradlew assembleDebug testDebugUnitTest`
  - **Done when:** build succeeds. **(manual, on device)**: blue dot with accuracy circle follows GPS; toggling manual mode and long-pressing moves an orange dot; "My location" recenters.

- [ ] **WS-4.4** Render persisted no-fire zones on the map
  - **Repos:** wingspan
  - **Read:** app/src/main/java/com/wingspan/app/ui/map/MapController.kt, app/src/main/java/com/wingspan/app/ui/map/MapViewModel.kt, app/src/main/java/com/wingspan/app/ui/map/MapScreen.kt, app/src/main/java/com/wingspan/app/data/ZoneRepository.kt, app/src/main/java/com/wingspan/app/domain/geo/Sector.kt
  - **Edit:** app/src/main/java/com/wingspan/app/ui/map/MapController.kt, app/src/main/java/com/wingspan/app/ui/map/MapViewModel.kt, app/src/main/java/com/wingspan/app/ui/map/MapScreen.kt
  - **Instructions:**
    1. `MapViewModel.kt`: add `zoneRepository: ZoneRepository` to the constructor/factory and expose `val zones: StateFlow<List<NoFireZone>>` (`stateIn(..., emptyList())`).
    2. `MapController.kt`: in `installOverlays` add sources `zones-polygons`, `zones-marker-circles`, `zones-marker-points` and layers, in canonical order: `FillLayer("zones-polygons-fill")` (`fillColor("#D32F2F")`, `fillOpacity(0.30f)`), `LineLayer("zones-polygons-outline")` (`lineColor("#B71C1C")`, `lineWidth(2f)`), `FillLayer("zones-marker-circles-fill")` (same red, opacity 0.25), `LineLayer("zones-marker-circles-outline")` (`lineColor("#B71C1C")`, `lineWidth(1.5f)`), `CircleLayer("zones-marker-points")` (`circleRadius(6f)`, `circleColor("#B71C1C")`, `circleStrokeColor("#FFFFFF")`, `circleStrokeWidth(1.5f)`). Add `fun setZones(zones: List<NoFireZone>)`: polygons → `org.maplibre.geojson.Polygon.fromLngLats(listOf(ring))` with the ring closed, feature property `zoneId` (number) and `name`; markers → circle polygon from `Sector.circleOutline(center, radiusM)` into `zones-marker-circles` and a `Point` into `zones-marker-points`, both with `zoneId`/`name`. Keep the last list in a field and re-apply after style reload.
    3. `MapScreen.kt`: collect `zones` and `LaunchedEffect(zones) { controller.setZones(zones) }`.
  - **Verify:** `cd /home/nathan/wingspan && ./gradlew assembleDebug testDebugUnitTest`
  - **Done when:** build succeeds. **(manual, on device)**: zones inserted into the database appear as red polygons/circles and survive basemap switches.

Acceptance criteria for Phase 4:

- The map renders any of the three basemaps and remembers the last choice.
- The shooter position comes from GPS (blue) or manual placement (orange) with a visible mode banner.
- Zones from the repository render as overlays and are re-applied after every style change.

## Phase 5: Zone Editor

Goal: users can draw no-fire polygons and markers by tapping, refine them by dragging handles, edit or delete existing zones, and import/export zones as GeoJSON.

- [ ] **WS-5.1** Add editor state, pure editor geometry helpers, and the draw-by-tapping flow
  - **Repos:** wingspan
  - **Read:** app/src/main/java/com/wingspan/app/data/ZoneRepository.kt, app/src/main/java/com/wingspan/app/domain/geo/Zones.kt, app/src/main/java/com/wingspan/app/domain/geo/Sector.kt, app/src/main/java/com/wingspan/app/ui/map/MapViewModel.kt
  - **Edit:** app/src/main/java/com/wingspan/app/ui/editor/EditorGeometry.kt, app/src/main/java/com/wingspan/app/ui/editor/EditorViewModel.kt, app/src/main/java/com/wingspan/app/ui/editor/ZoneDialogs.kt, app/src/test/java/com/wingspan/app/ui/editor/EditorGeometryTest.kt
  - **Instructions:**
    1. `EditorGeometry.kt` (pure Kotlin, no Android imports): `data class EditorRender(val ring: List<LatLon>, val handles: List<LatLon>, val selectedIndex: Int?, val midpoints: List<LatLon>)` and `object EditorGeometry` with:
       - `fun polygonRender(vertices: List<LatLon>, selectedIndex: Int?): EditorRender` — `ring` = vertices closed by repeating the first when `vertices.size >= 3`, else empty; `handles` = vertices; `midpoints` = midpoint of each consecutive pair (arithmetic mean of lat/lon) including the closing edge when `size >= 3`, empty when `size < 2`. Midpoint `i` sits between vertex `i` and vertex `(i+1) % size`.
       - `fun markerRender(center: LatLon?, radiusM: Double): EditorRender` — `ring` = `Sector.circleOutline(center, radiusM)` (empty if center null), `handles` = `listOf(center)` or empty, `selectedIndex = 0` when present, no midpoints.
    2. `EditorViewModel.kt`: `class EditorViewModel(private val zoneRepository: ZoneRepository) : ViewModel()` with:
       - `sealed interface EditorMode { data object Idle; data class PolygonEdit(val zoneId: Long?, val name: String, val vertices: List<LatLon>, val selectedIndex: Int?); data class MarkerEdit(val zoneId: Long?, val name: String, val center: LatLon?, val radiusM: Double) }` (`zoneId == null` means a new zone).
       - `val mode = MutableStateFlow<EditorMode>(Idle)`, `val showNameDialog = MutableStateFlow(false)`, `val render: StateFlow<EditorRender?>` derived from `mode` with `EditorGeometry`.
       - `fun startPolygon()`, `fun startMarker()` (default radius 25.0, name ""), `fun editZone(zone: NoFireZone)` (loads vertices/center into the matching mode), `fun onMapTap(p: LatLon)` (PolygonEdit → append vertex and select it; MarkerEdit → set center; Idle → ignore), `fun undoLastVertex()`, `fun selectVertex(i: Int)`, `fun moveVertex(i: Int, p: LatLon)` (PolygonEdit vertex `i`, or MarkerEdit center when `i == 0`), `fun insertVertexAfter(i: Int, p: LatLon): Int` (inserts at `i+1`, selects it, returns the new index), `fun deleteSelectedVertex()`, `fun setMarkerRadius(r: Double)`, `fun cancel()` (→ Idle), `val canFinish: StateFlow<Boolean>` (polygon ≥ 3 vertices or marker has a center), `fun requestFinish()` (sets `showNameDialog = true`), `fun confirmFinish(name: String, radiusM: Double?)` (calls `addPolygon`/`updatePolygon` or `addMarker`/`updateMarker`, then Idle), `fun dismissNameDialog()`.
       - `companion object { fun factory(container: AppContainer) }` like `MapViewModel`.
    3. `ZoneDialogs.kt`: `@Composable fun ZoneNameDialog(initialName: String, initialRadiusM: Double?, onConfirm: (name: String, radiusM: Double?) -> Unit, onDismiss: () -> Unit)` — an `AlertDialog` with an `OutlinedTextField` for the name (default `"Zone"` if blank) and, when `initialRadiusM != null`, a numeric field for radius in meters (min 1).
    4. `EditorGeometryTest.kt`: 2 vertices → empty ring, 1 midpoint; 3 vertices → ring of 4 points, 3 midpoints, midpoint 2 lies between vertex 2 and vertex 0; `markerRender(null, 25.0)` has no handles; `markerRender(LatLon(39.0, -105.0), 25.0)` ring has ≥ 72 points.
  - **Verify:** `cd /home/nathan/wingspan && ./gradlew assembleDebug testDebugUnitTest`
  - **Done when:** build and `EditorGeometryTest` pass.

- [ ] **WS-5.2** Render the editor on the map and implement handle dragging
  - **Repos:** wingspan
  - **Read:** app/src/main/java/com/wingspan/app/ui/map/MapController.kt, app/src/main/java/com/wingspan/app/ui/editor/EditorViewModel.kt, app/src/main/java/com/wingspan/app/ui/editor/EditorGeometry.kt, app/src/main/java/com/wingspan/app/ui/map/MapScreen.kt
  - **Edit:** app/src/main/java/com/wingspan/app/ui/map/MapController.kt, app/src/main/java/com/wingspan/app/ui/map/MapScreen.kt, app/src/main/java/com/wingspan/app/ui/editor/EditorControls.kt
  - **Instructions:**
    1. `MapController.kt` — editor layers, added last in `installOverlays` (canonical order `editor-fill`, `editor-outline`, `editor-midpoints`, `editor-handles`): sources `editor-polygon`, `editor-midpoints`, `editor-handles`; `FillLayer("editor-fill")` (`fillColor("#1E88E5")`, `fillOpacity(0.20f)`), `LineLayer("editor-outline")` (`lineColor("#1E88E5")`, `lineWidth(2f)`), `CircleLayer("editor-midpoints")` (`circleRadius(6f)`, `circleColor("#9E9E9E")`, `circleStrokeColor("#FFFFFF")`, `circleStrokeWidth(1f)`), `CircleLayer("editor-handles")` (`circleRadius(10f)`, `circleColor("#FFFFFF")`, `circleStrokeWidth(3f)`, `circleStrokeColor(Expression.switchCase(Expression.eq(Expression.get("selected"), Expression.literal(true)), Expression.literal("#E53935"), Expression.literal("#1E88E5")))`). `fun setEditorRender(r: EditorRender?)`: handles get number property `index` and boolean `selected`; midpoints get number property `insertAfter`; ring → polygon or empty collection. Cache and re-apply after style reload.
    2. `MapController.kt` — dragging. Define `interface HandleDragListener { fun onHandleMoved(index: Int, p: LatLon); fun onHandleTapped(index: Int); fun onMidpointPressed(insertAfter: Int, p: LatLon): Int }` and `fun setHandleDragListener(l: HandleDragListener?)`. In `attach`, call `mapView.setOnTouchListener { _, ev -> handleTouch(ev) }`. `handleTouch`:
       - `ACTION_DOWN`: if no listener return `false`. Query `map.queryRenderedFeatures(RectF(x−24, y−24, x+24, y+24), "editor-handles")`; if non-empty, `dragIndex = feature.getNumberProperty("index").toInt()`, record `downX/downY`, `moved = false`, set `map.uiSettings.isScrollGesturesEnabled = false` and `isZoomGesturesEnabled = false`, return `true`. Else query `"editor-midpoints"`; if hit, `dragIndex = listener.onMidpointPressed(insertAfter, latLonAt(ev))`, same gesture lock, return `true`. Else return `false`.
       - `ACTION_MOVE` with `dragIndex != null`: if distance from down point > 8 px set `moved = true`; `listener.onHandleMoved(dragIndex, latLonAt(ev))`; return `true`.
       - `ACTION_UP` / `ACTION_CANCEL` with `dragIndex != null`: if `!moved` call `listener.onHandleTapped(dragIndex)`; re-enable scroll and zoom gestures; `dragIndex = null`; return `true`.
       - `latLonAt(ev)` = `map.projection.fromScreenLocation(PointF(ev.x, ev.y))` converted to `LatLon`.
       - Also add `fun setOnTap(listener: (LatLon) -> Unit)` backed by `map.addOnMapClickListener` (registered once in `attach`; return `true`).
    3. `EditorControls.kt`: `@Composable fun EditorControls(mode: EditorMode, canFinish: Boolean, onUndo, onDeletePoint, onCancel, onFinish, onRadiusChange: (Double) -> Unit)` — a bottom-center `Surface` with a `Row` of `TextButton`s: "Undo" (PolygonEdit only, enabled when vertices non-empty), "Delete point" (PolygonEdit with `selectedIndex != null` and size > 0), a radius `OutlinedTextField` (MarkerEdit only, meters), "Cancel", and a `Button` "Finish" enabled by `canFinish`. Above the row show one line of guidance: "Tap to add points, drag handles to adjust" / "Tap to place the marker, drag to adjust".
    4. `MapScreen.kt`: obtain `editorViewModel = viewModel(factory = EditorViewModel.factory(container))`; collect `mode`, `render`, `canFinish`, `showNameDialog`. `LaunchedEffect(render) { controller.setEditorRender(render) }`. `LaunchedEffect(controller) { controller.setOnTap { editorViewModel.onMapTap(it) }; controller.setHandleDragListener(object : HandleDragListener { onHandleMoved → editorViewModel.moveVertex; onHandleTapped → editorViewModel.selectVertex; onMidpointPressed → editorViewModel.insertVertexAfter }) }`. Replace the bottom-end FAB column's first item with an "Add zone" `FloatingActionButton` (`Icons.Default.Add`) that opens a `DropdownMenu` with "No-fire polygon" → `startPolygon()` and "No-fire marker" → `startMarker()`; hide the add button while `mode != Idle`. Show `EditorControls` when `mode != Idle`, and `ZoneNameDialog` when `showNameDialog` is true (initial radius from `MarkerEdit.radiusM`, or null for polygons).
  - **Verify:** `cd /home/nathan/wingspan && ./gradlew assembleDebug testDebugUnitTest`
  - **Done when:** build succeeds. **(manual, on device)**: tapping adds vertices with handles; dragging a handle moves it without panning the map; pressing a midpoint inserts a vertex; Finish saves and the zone renders in red.

- [ ] **WS-5.3** Select, edit, rename, and delete existing zones
  - **Repos:** wingspan
  - **Read:** app/src/main/java/com/wingspan/app/ui/map/MapController.kt, app/src/main/java/com/wingspan/app/ui/editor/EditorViewModel.kt, app/src/main/java/com/wingspan/app/ui/map/MapScreen.kt, app/src/main/java/com/wingspan/app/ui/editor/ZoneDialogs.kt
  - **Edit:** app/src/main/java/com/wingspan/app/ui/map/MapController.kt, app/src/main/java/com/wingspan/app/ui/editor/EditorViewModel.kt, app/src/main/java/com/wingspan/app/ui/editor/ZoneDialogs.kt, app/src/main/java/com/wingspan/app/ui/map/MapScreen.kt
  - **Instructions:**
    1. `MapController.kt`: change the tap callback to `data class TapHit(val position: LatLon, val zoneId: Long?)` and `fun setOnTap(listener: (TapHit) -> Unit)`. In the click listener, convert the `LatLng` to a screen `PointF` via `map.projection.toScreenLocation`, query `map.queryRenderedFeatures(pointF, "zones-marker-points", "zones-marker-circles-fill", "zones-polygons-fill")`, and take the first feature's `zoneId` number property (or null).
    2. `EditorViewModel.kt`: add `val selectedZone = MutableStateFlow<NoFireZone?>(null)`, `fun onTap(hit: TapHit, zones: List<NoFireZone>)`: when `mode` is `Idle` and `hit.zoneId != null` set `selectedZone` to the matching zone, else when editing call the existing `onMapTap(hit.position)`. Add `fun clearSelection()`, `fun editSelectedShape()` (calls `editZone(selectedZone)` and clears selection), `fun renameSelected(name: String)`, `fun deleteSelected()`, and `val showRenameDialog = MutableStateFlow(false)`.
    3. `ZoneDialogs.kt`: add `@Composable fun ZoneInfoSheet(zone: NoFireZone, onEditShape: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit)` using `ModalBottomSheet`: shows the name, the type ("Polygon, N vertices" or "Marker, radius R m"), and three buttons; Delete asks for confirmation with an `AlertDialog`.
    4. `MapScreen.kt`: route taps through `editorViewModel.onTap(hit, zones)`; show `ZoneInfoSheet` when `selectedZone != null`; show `ZoneNameDialog` (name only) for rename when `showRenameDialog` is true.
  - **Verify:** `cd /home/nathan/wingspan && ./gradlew assembleDebug testDebugUnitTest`
  - **Done when:** build succeeds. **(manual, on device)**: tapping a zone opens the sheet; Edit shape shows draggable handles and Finish updates the zone; Rename and Delete work.

- [ ] **WS-5.4** Import and export zones as GeoJSON through the system file picker
  - **Repos:** wingspan
  - **Read:** app/src/main/java/com/wingspan/app/data/geojson/GeoJsonCodec.kt, app/src/main/java/com/wingspan/app/data/ZoneRepository.kt, app/src/main/java/com/wingspan/app/ui/editor/EditorViewModel.kt, app/src/main/java/com/wingspan/app/ui/map/MapScreen.kt
  - **Edit:** app/src/main/java/com/wingspan/app/ui/editor/ZoneFileIo.kt, app/src/main/java/com/wingspan/app/ui/editor/EditorViewModel.kt, app/src/main/java/com/wingspan/app/ui/map/MapScreen.kt, app/src/main/java/com/wingspan/app/ui/map/MapMenu.kt
  - **Instructions:**
    1. `ZoneFileIo.kt`: `object ZoneFileIo` with `suspend fun writeText(resolver: ContentResolver, uri: Uri, text: String)` and `suspend fun readText(resolver: ContentResolver, uri: Uri): String` on `Dispatchers.IO` using `openOutputStream`/`openInputStream`, and `fun suggestedExportName(): String = "wingspan-zones-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".geojson"`.
    2. `EditorViewModel.kt`: add `suspend fun exportGeoJson(): String = GeoJsonCodec.encode(zoneRepository.getAll())`, `suspend fun importGeoJson(text: String, replace: Boolean): Int` (decode, then `replaceAll` or `addAll`; returns count; rethrows `IllegalArgumentException` for bad input), `val pendingImport = MutableStateFlow<String?>(null)` (holds text until the user chooses Replace/Merge), and `val message = MutableSharedFlow<String>(extraBufferCapacity = 1)` for toasts.
    3. `MapMenu.kt`: `@Composable fun MapMenu(onSettings: () -> Unit, onOffline: () -> Unit, onSnapshots: () -> Unit, onExportZones: () -> Unit, onImportZones: () -> Unit)` — an `IconButton` (`Icons.Default.MoreVert`) inside a small `Surface` at the top-start (status-bar padded) with a `DropdownMenu` containing: Settings, Offline maps, Snapshots, Export zones (GeoJSON), Import zones (GeoJSON). Settings/Offline/Snapshots callbacks are no-ops for now (later phases wire them).
    4. `MapScreen.kt`: add `exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/geo+json")) { uri -> uri?.let { scope.launch { ZoneFileIo.writeText(resolver, it, editorViewModel.exportGeoJson()); toast("Zones exported") } } }` (launch with `ZoneFileIo.suggestedExportName()`), `importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { scope.launch { editorViewModel.pendingImport.value = ZoneFileIo.readText(resolver, it) } } }` (launch with `arrayOf("*/*")`). When `pendingImport != null` show an `AlertDialog` "Import N zones?" with buttons **Replace all**, **Merge**, **Cancel**; on choice call `importGeoJson(text, replace)` and toast the count; on decode failure toast "Not a valid GeoJSON file". Collect `message` into `Toast.makeText(context, it, Toast.LENGTH_SHORT).show()`.
  - **Verify:** `cd /home/nathan/wingspan && ./gradlew assembleDebug testDebugUnitTest`
  - **Done when:** build succeeds. **(manual, on device)**: Export writes a `.geojson` file that opens in QGIS/geojson.io; Import of that file with Replace recreates the same zones.

Acceptance criteria for Phase 5:

- Polygons and markers can be created by tapping, refined by dragging, edited, renamed, and deleted; all changes persist.
- Dragging a handle never pans the map; tapping empty map while idle does nothing.
- GeoJSON export/import round-trips zones.

## Phase 6: Settings And Range Fans

Goal: a settings screen drives the ballistics model, and the map shows range fans around the shooter with tap-to-read magnetic bearings.

- [ ] **WS-6.1** Add display formatters, the settings view model, and the settings screen
  - **Repos:** wingspan
  - **Read:** app/src/main/java/com/wingspan/app/domain/ballistics/LoadSettings.kt, app/src/main/java/com/wingspan/app/domain/ballistics/RangeCalculator.kt, app/src/main/java/com/wingspan/app/domain/ballistics/RangeResult.kt, app/src/main/java/com/wingspan/app/data/SettingsRepository.kt, app/src/main/java/com/wingspan/app/ui/map/MapViewModel.kt
  - **Edit:** app/src/main/java/com/wingspan/app/ui/Formatters.kt, app/src/test/java/com/wingspan/app/ui/FormattersTest.kt, app/src/main/java/com/wingspan/app/ui/settings/SettingsViewModel.kt, app/src/main/java/com/wingspan/app/ui/settings/SettingsScreen.kt
  - **Instructions:**
    1. `Formatters.kt` (pure Kotlin, `object Formatters`): `fun distance(meters: Double, units: UnitSystem): String` → `"328 yd"` (rounded to whole yards) or `"300 m"`; `fun velocity(fps: Double, units: UnitSystem): String` → `"1250 fps"` or `"381 m/s"`; `fun energy(joules: Double, units: UnitSystem): String` → `"19.0 ft·lbf"` (one decimal) or `"25.8 J"`; `fun bearing(deg: Double, magnetic: Boolean = true): String` → round to the nearest whole degree, wrap into `0..359` (`((round % 360) + 360) % 360`), zero-pad to three digits, append `°`, then `" M"` when `magnetic` else `" T"`, e.g. `"057° M"`; `fun angle(deg: Double): String` → `"25°"`; `fun velocityInputToFps(text: String, units: UnitSystem): Double?` and `fun fpsToVelocityInput(fps: Double, units: UnitSystem): String` (whole numbers).
    2. `FormattersTest.kt`: `distance(300.0, IMPERIAL) == "328 yd"`, `distance(300.0, METRIC) == "300 m"`, `bearing(57.4) == "057° M"`, `bearing(359.6)` == `"000° M"` (wraps), `velocityInputToFps("400", METRIC)` ≈ 1312.3, `velocityInputToFps("abc", IMPERIAL) == null`.
    3. `SettingsViewModel.kt`: `class SettingsViewModel(private val settingsRepository: SettingsRepository) : ViewModel()` with `val settings: StateFlow<LoadSettings>` (`stateIn(..., LoadSettings())`), `val preview: StateFlow<RangeResult?>` = `settings.mapLatest { withContext(Dispatchers.Default) { RangeCalculator.compute(it.toBallisticInput()) } }.stateIn(..., null)`, and update functions each calling `settingsRepository.update { it.copy(...) }`: `setShotSize`, `setMaterial`, `setCustomDiameterInches`, `setCustomDensityGcc`, `setMuzzleVelocityFps`, `setChoke`, `setEnergyThresholdFtLbf`, `setUnitSystem`. `companion object { fun factory(container: AppContainer) }`.
    4. `SettingsScreen.kt`: `@Composable fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = viewModel(factory = ...))`. `Scaffold` with `TopAppBar(title = "Load settings", navigationIcon = back arrow)`; scrollable `Column` containing: a private `@Composable fun <T> EnumDropdown(label, options: List<T>, selected: T, labelOf: (T) -> String, onSelect: (T) -> Unit)` built on `ExposedDropdownMenuBox`; dropdowns for shot size, pellet material, choke; when `shotSize == CUSTOM` a numeric field "Pellet diameter (in)"; when `material == CUSTOM` a numeric field "Pellet density (g/cc)"; "Muzzle velocity" numeric field whose suffix is `fps` or `m/s` per `unitSystem` (parse with `Formatters.velocityInputToFps`; commit on value change only when parse succeeds); "Minimum pellet energy (ft·lbf)" numeric field; a `SingleChoiceSegmentedButtonRow` for Imperial / Metric. Below, a `Card` "Computed ranges" showing from `preview`: "Maximum range: {distance} at {angle} launch angle", "Effective range: {distance} ({energy|pattern}-limited)", "Muzzle energy per pellet: {energy}". Numeric fields keep local `remember` text state and push parsed doubles to the view model.
  - **Verify:** `cd /home/nathan/wingspan && ./gradlew assembleDebug testDebugUnitTest`
  - **Done when:** build and `FormattersTest` pass.

- [ ] **WS-6.2** Add app navigation and wire the map menu to Settings
  - **Repos:** wingspan
  - **Read:** app/src/main/java/com/wingspan/app/MainActivity.kt, app/src/main/java/com/wingspan/app/ui/map/MapScreen.kt, app/src/main/java/com/wingspan/app/ui/map/MapMenu.kt, app/src/main/java/com/wingspan/app/ui/settings/SettingsScreen.kt
  - **Edit:** app/src/main/java/com/wingspan/app/ui/WingspanApp.kt, app/src/main/java/com/wingspan/app/MainActivity.kt, app/src/main/java/com/wingspan/app/ui/map/MapScreen.kt
  - **Instructions:**
    1. `WingspanApp.kt`: `@Composable fun WingspanApp()` with `val navController = rememberNavController()` and `NavHost(navController, startDestination = "map")` with routes: `"map"` → `MapScreen(onOpenSettings = { navController.navigate("settings") }, onOpenOffline = { navController.navigate("offline") }, onOpenSnapshots = { navController.navigate("snapshots") })`; `"settings"` → `SettingsScreen(onBack = { navController.popBackStack() })`; `"offline"` and `"snapshots"` → temporary placeholder `Scaffold`s with a back arrow and the text "Coming soon" (replaced in Phases 7 and 8).
    2. `MapScreen.kt`: add parameters `onOpenSettings`, `onOpenOffline`, `onOpenSnapshots` (all `() -> Unit`) and pass them into `MapMenu`.
    3. `MainActivity.kt`: `setContent { WingspanTheme { WingspanApp() } }`.
  - **Verify:** `cd /home/nathan/wingspan && ./gradlew assembleDebug testDebugUnitTest`
  - **Done when:** build succeeds. **(manual, on device)**: menu → Settings opens the settings screen; changing shot size updates the computed ranges card; back returns to the map with state intact.

- [ ] **WS-6.3** Compute range fans from position, zones, and settings
  - **Repos:** wingspan
  - **Read:** app/src/main/java/com/wingspan/app/ui/map/MapViewModel.kt, app/src/main/java/com/wingspan/app/domain/geo/FanCalculator.kt, app/src/main/java/com/wingspan/app/domain/geo/Zones.kt, app/src/main/java/com/wingspan/app/domain/ballistics/RangeCalculator.kt, app/src/main/java/com/wingspan/app/AppContainer.kt
  - **Edit:** app/src/main/java/com/wingspan/app/data/AndroidDeclination.kt, app/src/main/java/com/wingspan/app/AppContainer.kt, app/src/main/java/com/wingspan/app/ui/map/MapViewModel.kt
  - **Instructions:**
    1. `AndroidDeclination.kt`: `class AndroidDeclination : DeclinationProvider` returning `GeomagneticField(lat.toFloat(), lon.toFloat(), 0f, System.currentTimeMillis()).declination.toDouble()`.
    2. `AppContainer.kt`: add `val declinationProvider: DeclinationProvider = AndroidDeclination()`.
    3. `MapViewModel.kt` (constructor gains `declinationProvider`; update the factory):
       - Types: `data class FanView(val index: Int, val fan: Fan, val leftMagDeg: Double, val rightMagDeg: Double)` and `data class FanUiState(val origin: LatLon, val source: PositionSource, val accuracyM: Double?, val range: RangeResult, val fans: List<FanView>, val insideZone: Boolean, val declinationDeg: Double, val units: UnitSystem)`.
       - `private val rangeResult: Flow<RangeResult> = settingsRepository.settings.map { RangeCalculator.compute(it.toBallisticInput()) }.flowOn(Dispatchers.Default)`.
       - `private val throttledShooter = shooter.distinctUntilChanged { old, new -> old != null && new != null && old.source == new.source && distanceM(old.position, new.position) < 5.0 }`.
       - `val fanState: StateFlow<FanUiState?> = combine(throttledShooter, zones, rangeResult, settingsRepository.settings) { s, z, r, cfg -> ... }.mapLatest { input -> input?.let { withContext(Dispatchers.Default) { compute(it) } } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)` where `compute` runs `FanCalculator.compute(origin, r.maxRangeM, zones)`, gets `declination = declinationProvider.declinationDeg(origin)`, and maps each `Fan` to `FanView` with `trueToMagnetic` for both limits (full-circle fans get 0/360).
       - `val selectedFanIndex = MutableStateFlow<Int?>(null)`, `fun selectFan(i: Int?)`; reset selection to null whenever `fanState` emits a new value with a different fan count.
  - **Verify:** `cd /home/nathan/wingspan && ./gradlew assembleDebug testDebugUnitTest`
  - **Done when:** build succeeds and `MapViewModel` compiles with `fanState` exposed.

- [ ] **WS-6.4** Draw the fans and show fan limits on tap
  - **Repos:** wingspan
  - **Read:** app/src/main/java/com/wingspan/app/ui/map/MapController.kt, app/src/main/java/com/wingspan/app/ui/map/MapViewModel.kt, app/src/main/java/com/wingspan/app/ui/map/MapScreen.kt, app/src/main/java/com/wingspan/app/domain/geo/Sector.kt, app/src/main/java/com/wingspan/app/ui/Formatters.kt
  - **Edit:** app/src/main/java/com/wingspan/app/ui/map/MapController.kt, app/src/main/java/com/wingspan/app/ui/map/MapScreen.kt, app/src/main/java/com/wingspan/app/ui/map/FanDetailSheet.kt
  - **Instructions:**
    1. `MapController.kt`: in `installOverlays` add sources `fans`, `fans-effective`, `fans-selected` and layers in canonical order: `FillLayer("fans-fill", "fans")` (`fillColor("#43A047")`, `fillOpacity(0.25f)`), `LineLayer("fans-outline", "fans")` (`lineColor("#2E7D32")`, `lineWidth(2f)`), `LineLayer("fans-effective", "fans-effective")` (`lineColor("#2E7D32")`, `lineWidth(1.5f)`, `lineDasharray(arrayOf(2f, 2f))`), `LineLayer("fans-selected", "fans-selected")` (`lineColor("#FFD600")`, `lineWidth(4f)`). Add `fun setFans(state: FanUiState?)`: for each `FanView`, a `Polygon` feature from `Sector.sectorOutline(origin, leftTrueDeg, rightTrueDeg, maxRangeM)` (full circle when `fan.fullCircle`) with number property `fanIndex`; an effective-range `LineString` from `Sector.arcPoints(origin, left, right, effectiveRangeM)` (full circle → closed ring); empty collections when `state == null` or `insideZone`. Add `fun setSelectedFan(state: FanUiState?, index: Int?)` writing the selected fan's outline to `fans-selected`. Extend `TapHit` with `val fanIndex: Int?`: after the zone query returns nothing, query `"fans-fill"` at the tap point and read `fanIndex`. Cache and re-apply after style reload.
    2. `FanDetailSheet.kt`: `@Composable fun FanDetailSheet(state: FanUiState, fan: FanView, onDismiss: () -> Unit)` — `ModalBottomSheet` listing: "Left limit: {Formatters.bearing(leftMagDeg)}", "Right limit: {bearing(rightMagDeg)}", "Width: {angle(widthDeg)}", "Maximum range: {distance(maxRangeM)}", "Effective range: {distance(effectiveRangeM)} ({limiter}-limited)", "Magnetic declination: {declination formatted with sign, one decimal}° (magnetic = true − declination)", "Position: GPS ±{accuracy} m" or "Position: manual". Full-circle fans show "Clear in all directions" instead of limits.
    3. `MapScreen.kt`: collect `fanState` and `selectedFanIndex`; `LaunchedEffect(fanState) { controller.setFans(fanState) }`; `LaunchedEffect(fanState, selectedFanIndex) { controller.setSelectedFan(fanState, selectedFanIndex) }`. Update tap routing: if editor `mode != Idle` → `editorViewModel.onMapTap(hit.position)`; else if `hit.zoneId != null` → `editorViewModel.onTap(hit, zones)`; else if `hit.fanIndex != null` → `mapViewModel.selectFan(hit.fanIndex)`; else `mapViewModel.selectFan(null)`. Show `FanDetailSheet` when a fan is selected. Show a top-center warning banner (`MaterialTheme.colorScheme.errorContainer`) "Inside a no-fire zone — no safe field of fire" when `fanState.insideZone`, or "No clear field of fire" when fans are empty and a position exists. Also show a small summary chip under the basemap chips: "Max {distance} · Eff {distance}".
  - **Verify:** `cd /home/nathan/wingspan && ./gradlew assembleDebug testDebugUnitTest`
  - **Done when:** build succeeds. **(manual, on device)**: green fans appear around the shooter and are cut by red zones with a visible margin; tapping a fan shows its magnetic left/right limits; changing settings resizes the fans.

Acceptance criteria for Phase 6:

- Fans are recomputed when position moves > 5 m, zones change, or settings change; computation runs off the main thread.
- Tapping a fan shows left/right magnetic bearings, width, max and effective ranges.
- The settings screen previews computed ranges and persists every value.

## Phase 7: Offline Basemap Regions

Goal: users can download the visible map area for a basemap over a zoom range, watch progress, and manage stored regions.

- [ ] **WS-7.1** Add `OfflineRepository` around MapLibre's `OfflineManager`
  - **Repos:** wingspan
  - **Read:** app/src/main/java/com/wingspan/app/data/map/Basemap.kt, app/src/main/java/com/wingspan/app/AppContainer.kt
  - **Edit:** app/src/main/java/com/wingspan/app/data/map/OfflineRepository.kt, app/src/main/java/com/wingspan/app/AppContainer.kt
  - **Instructions:**
    1. `OfflineRepository.kt` (all MapLibre offline classes are in `org.maplibre.android.offline`):
       - `@Serializable data class RegionMetadata(val name: String, val basemapKey: String, val createdAtMs: Long)` stored as UTF-8 JSON bytes in the region metadata.
       - `data class OfflineRegionInfo(val id: Long, val name: String, val basemap: Basemap, val createdAtMs: Long, val completedTiles: Long, val requiredTiles: Long, val completedBytes: Long, val isComplete: Boolean)`.
       - `data class DownloadProgress(val regionId: Long, val completedTiles: Long, val requiredTiles: Long, val completedBytes: Long, val isComplete: Boolean, val error: String?)`.
       - `class OfflineRepository(context: Context)`: `private val manager = OfflineManager.getInstance(context)`; `private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)`; `val activeDownloads = MutableStateFlow<Map<Long, DownloadProgress>>(emptyMap())`.
       - `init { manager.setMaximumAmbientCacheSize(200L * 1024 * 1024, null) }`.
       - `suspend fun list(): List<OfflineRegionInfo>` — `suspendCancellableCoroutine` over `manager.listOfflineRegions(callback)`, then for each region `suspendCancellableCoroutine` over `region.getStatus(callback)`; decode metadata with `runCatching` (fallback name `"Region <id>"`).
       - `fun startDownload(name: String, basemap: Basemap, south: Double, west: Double, north: Double, east: Double, minZoom: Int, maxZoom: Int, pixelRatio: Float)` — builds `OfflineTilePyramidRegionDefinition(basemap.remoteStyleUrl, LatLngBounds.from(north, east, south, west), minZoom.toDouble(), maxZoom.toDouble(), pixelRatio)`, calls `manager.createOfflineRegion(definition, metadataBytes, callback)`; on create sets `region.setObserver(...)` updating `activeDownloads[region.id]` from each `OfflineRegionStatus` (`completedTileCount`, `requiredResourceCount`, `completedResourceSize`, `isComplete`), on `onError` stores `error.message` and on `mapboxTileCountLimitExceeded` stores an error string; then `region.setDownloadState(OfflineRegion.STATE_ACTIVE)`. When `isComplete` or an error occurs call `region.setDownloadState(STATE_INACTIVE)` and remove the entry after emitting the final progress.
       - `suspend fun delete(id: Long)` — find the region by id via `list` logic and `region.delete(callback)`.
    2. `AppContainer.kt`: add `val offlineRepository = OfflineRepository(context)`.
  - **Verify:** `cd /home/nathan/wingspan && ./gradlew assembleDebug testDebugUnitTest`
  - **Done when:** build succeeds.

- [ ] **WS-7.2** Add the offline regions screen
  - **Repos:** wingspan
  - **Read:** app/src/main/java/com/wingspan/app/data/map/OfflineRepository.kt, app/src/main/java/com/wingspan/app/ui/WingspanApp.kt, app/src/main/java/com/wingspan/app/ui/settings/SettingsScreen.kt
  - **Edit:** app/src/main/java/com/wingspan/app/ui/offline/OfflineViewModel.kt, app/src/main/java/com/wingspan/app/ui/offline/OfflineScreen.kt, app/src/main/java/com/wingspan/app/ui/WingspanApp.kt
  - **Instructions:**
    1. `OfflineViewModel.kt`: `class OfflineViewModel(private val repo: OfflineRepository) : ViewModel()` with `val regions = MutableStateFlow<List<OfflineRegionInfo>>(emptyList())`, `val activeDownloads = repo.activeDownloads`, `fun refresh()` (launches `repo.list()`), `fun delete(id: Long)` (delete then refresh), and an `init` that refreshes and also refreshes whenever `activeDownloads` changes. Factory as in other view models.
    2. `OfflineScreen.kt`: `@Composable fun OfflineScreen(onBack: () -> Unit, viewModel: OfflineViewModel = ...)`. `Scaffold` + `TopAppBar("Offline maps")`; `LazyColumn` of `Card`s per region: name, basemap label, created date (`DateFormat.getDateTimeInstance()`), "{completedTiles} tiles · {completedBytes / 1_048_576} MB", a `LinearProgressIndicator` with `completed/required` when an active download exists for that id (and the error text if any), and a Delete `IconButton` with confirmation. Empty state text: "No offline regions. Use the map menu → Download this area."
    3. `WingspanApp.kt`: replace the `"offline"` placeholder with `OfflineScreen(onBack = { navController.popBackStack() })`.
  - **Verify:** `cd /home/nathan/wingspan && ./gradlew assembleDebug testDebugUnitTest`
  - **Done when:** build succeeds.

- [ ] **WS-7.3** Add "Download this area" from the map
  - **Repos:** wingspan
  - **Read:** app/src/main/java/com/wingspan/app/ui/map/MapController.kt, app/src/main/java/com/wingspan/app/ui/map/MapScreen.kt, app/src/main/java/com/wingspan/app/ui/map/MapMenu.kt, app/src/main/java/com/wingspan/app/domain/geo/TileMath.kt, app/src/main/java/com/wingspan/app/data/map/OfflineRepository.kt
  - **Edit:** app/src/main/java/com/wingspan/app/ui/map/MapController.kt, app/src/main/java/com/wingspan/app/ui/offline/DownloadAreaDialog.kt, app/src/main/java/com/wingspan/app/ui/map/MapMenu.kt, app/src/main/java/com/wingspan/app/ui/map/MapScreen.kt
  - **Instructions:**
    1. `MapController.kt`: add `data class Bounds(val south: Double, val west: Double, val north: Double, val east: Double)` and `fun visibleBounds(): Bounds?` from `map.projection.visibleRegion.latLngBounds` (`latitudeSouth`, `longitudeWest`, `latitudeNorth`, `longitudeEast`).
    2. `DownloadAreaDialog.kt`: `@Composable fun DownloadAreaDialog(basemap: Basemap, bounds: Bounds, currentZoom: Double, onConfirm: (name: String, minZoom: Int, maxZoom: Int) -> Unit, onDismiss: () -> Unit)` — `AlertDialog` with a name field (default `"Area " + date`), the basemap label, `minZoom = floor(currentZoom).toInt().coerceIn(0, basemap.maxZoom)` shown as text, a `Slider` for `maxZoom` from `minZoom` to `basemap.maxZoom` (default `min(minZoom + 4, basemap.maxZoom)`), and a live estimate "≈ {TileMath.tileCount(...)} tiles" shown in the error color with a warning when above 20 000. Confirm is disabled above 60 000 tiles.
    3. `MapMenu.kt`: add a "Download this area" item with an `onDownloadArea: () -> Unit` parameter.
    4. `MapScreen.kt`: on "Download this area" capture `controller.visibleBounds()` and `controller.currentZoom()` into local state and show the dialog; on confirm call `container.offlineRepository.startDownload(name, basemap, bounds..., minZoom, maxZoom, context.resources.displayMetrics.density)`, toast "Download started", and call `onOpenOffline()`.
  - **Verify:** `cd /home/nathan/wingspan && ./gradlew assembleDebug testDebugUnitTest`
  - **Done when:** build succeeds. **(manual, on device)**: downloading the visible area shows progress on the offline screen; with airplane mode on, the downloaded area still renders at the downloaded zooms.

Acceptance criteria for Phase 7:

- Regions download with visible progress, persist across restarts, and can be deleted.
- Downloaded tiles render while offline because the live style uses the same tile URLs as the remote style used for the download.

## Phase 8: Firing-Position Snapshots

Goal: capture a timestamped record plus map image of the current firing position, browse and annotate snapshots, re-display them on the map, and share or export them.

- [ ] **WS-8.1** Capture a snapshot from the map
  - **Repos:** wingspan
  - **Read:** app/src/main/java/com/wingspan/app/ui/map/MapController.kt, app/src/main/java/com/wingspan/app/ui/map/MapViewModel.kt, app/src/main/java/com/wingspan/app/ui/map/MapScreen.kt, app/src/main/java/com/wingspan/app/data/SnapshotRepository.kt
  - **Edit:** app/src/main/java/com/wingspan/app/ui/map/MapController.kt, app/src/main/java/com/wingspan/app/ui/map/MapViewModel.kt, app/src/main/java/com/wingspan/app/ui/snapshots/SnapshotCapture.kt, app/src/main/java/com/wingspan/app/ui/map/MapScreen.kt
  - **Instructions:**
    1. `MapController.kt`: add `fun captureBitmap(callback: (Bitmap) -> Unit)` calling `map?.snapshot { callback(it) }` (`MapLibreMap.snapshot(SnapshotReadyCallback)` renders the current map including all overlay layers).
    2. `SnapshotCapture.kt`: `object SnapshotCapture { fun toPng(bitmap: Bitmap): ByteArray }` (`Bitmap.CompressFormat.PNG`, quality 100, `ByteArrayOutputStream`), and `@Composable fun SnapshotNotesDialog(onConfirm: (notes: String) -> Unit, onDismiss: () -> Unit)` — `AlertDialog` titled "Save firing position" with a multi-line `OutlinedTextField` "Notes (what was fired, conditions, …)".
    3. `MapViewModel.kt` (constructor gains `snapshotRepository: SnapshotRepository`; update the factory): add `fun buildSnapshot(notes: String): FiringSnapshot?` from the current `fanState` (null when there is no position): `timestampMs = now`, `position = origin`, `positionSource = source.name`, `settings = latest LoadSettings` (keep the latest settings in a field collected from `settingsRepository.settings`), `maxRangeM`/`effectiveRangeM` from `range`, `declinationDeg`, `fans = fans.map { SnapshotFan(leftTrueDeg, rightTrueDeg, leftMagDeg, rightMagDeg, fullCircle) }`, `notes`, `imageFile = File("")` (assigned by the repository). Add `suspend fun saveSnapshot(snapshot: FiringSnapshot, png: ByteArray): Long = snapshotRepository.create(snapshot, png)`.
    4. `MapScreen.kt`: add a "Snapshot" `SmallFloatingActionButton` (`Icons.Default.Star`) to the bottom-end column, enabled only when `fanState != null` and the editor is idle. On press: `controller.captureBitmap { bitmap -> pendingPng = SnapshotCapture.toPng(bitmap); showNotesDialog = true }`. On dialog confirm: `viewModel.buildSnapshot(notes)?.let { scope.launch { viewModel.saveSnapshot(it, pendingPng); toast("Snapshot saved") } }`.
  - **Verify:** `cd /home/nathan/wingspan && ./gradlew assembleDebug testDebugUnitTest`
  - **Done when:** build succeeds. **(manual, on device)**: pressing Snapshot, entering notes, and confirming shows "Snapshot saved"; a PNG appears under `files/snapshots/` (visible via Android Studio Device Explorer).

- [ ] **WS-8.2** Add the snapshot list and detail screens
  - **Repos:** wingspan
  - **Read:** app/src/main/java/com/wingspan/app/data/SnapshotRepository.kt, app/src/main/java/com/wingspan/app/ui/WingspanApp.kt, app/src/main/java/com/wingspan/app/ui/Formatters.kt, app/src/main/java/com/wingspan/app/ui/offline/OfflineScreen.kt
  - **Edit:** app/src/main/java/com/wingspan/app/ui/snapshots/SnapshotsViewModel.kt, app/src/main/java/com/wingspan/app/ui/snapshots/SnapshotsScreen.kt, app/src/main/java/com/wingspan/app/ui/snapshots/SnapshotDetailScreen.kt, app/src/main/java/com/wingspan/app/ui/WingspanApp.kt
  - **Instructions:**
    1. `SnapshotsViewModel.kt`: `class SnapshotsViewModel(private val repo: SnapshotRepository) : ViewModel()` with `val snapshots: StateFlow<List<FiringSnapshot>>`, `suspend fun get(id: Long): FiringSnapshot?`, `fun updateNotes(id: Long, notes: String)`, `fun delete(id: Long)`, and a helper `fun summaryText(s: FiringSnapshot): String` producing:
       ```
       Wingspan firing position — {date time}
       Position: {lat, 5 decimals}, {lon, 5 decimals} ({GPS|manual})
       Load: {shot label} {material label}, {velocity}, {choke label}
       Max range {distance} · Effective {distance}
       Fans (magnetic): {left}–{right}, ... | Clear in all directions | No clear field of fire
       Notes: {notes}
       ```
       using `Formatters` with the snapshot's own `settings.unitSystem`. Factory as usual.
    2. `SnapshotsScreen.kt`: `@Composable fun SnapshotsScreen(onBack: () -> Unit, onOpen: (Long) -> Unit, viewModel: ...)` — `Scaffold` + `TopAppBar("Snapshots")`, `LazyColumn` of `Card`s (clickable → `onOpen(id)`) showing a 96 dp thumbnail (`BitmapFactory.decodeFile(imageFile.path)?.asImageBitmap()` loaded with `remember(imageFile)`; placeholder box if null), date/time, load summary line, and the first line of notes. Empty state: "No snapshots yet. Use the star button on the map."
    3. `SnapshotDetailScreen.kt`: `@Composable fun SnapshotDetailScreen(id: Long, onBack: () -> Unit, onShowOnMap: (Long) -> Unit, viewModel: ...)` — loads the snapshot with `LaunchedEffect(id)`; shows the full-width image, all summary fields as label/value rows, an editable notes `OutlinedTextField` with a "Save notes" button, and buttons "Show on map" (→ `onShowOnMap(id)`), "Share" (no-op until WS-8.3), "Delete" (confirm → delete → `onBack()`).
    4. `WingspanApp.kt`: replace the `"snapshots"` placeholder with `SnapshotsScreen(onBack, onOpen = { navController.navigate("snapshot/$it") })`; add route `"snapshot/{id}"` with `navArgument("id") { type = NavType.LongType }` → `SnapshotDetailScreen(id, onBack, onShowOnMap = { /* wired in WS-8.4 */ })`.
  - **Verify:** `cd /home/nathan/wingspan && ./gradlew assembleDebug testDebugUnitTest`
  - **Done when:** build succeeds. **(manual, on device)**: the list shows saved snapshots with thumbnails; the detail screen shows the image and fields; notes edits persist.

- [ ] **WS-8.3** Share a snapshot through the Android share sheet
  - **Repos:** wingspan
  - **Read:** app/src/main/AndroidManifest.xml, app/src/main/java/com/wingspan/app/ui/snapshots/SnapshotDetailScreen.kt, app/src/main/java/com/wingspan/app/ui/snapshots/SnapshotsViewModel.kt
  - **Edit:** app/src/main/res/xml/file_paths.xml, app/src/main/AndroidManifest.xml, app/src/main/java/com/wingspan/app/ui/snapshots/SnapshotShare.kt, app/src/main/java/com/wingspan/app/ui/snapshots/SnapshotDetailScreen.kt
  - **Instructions:**
    1. `file_paths.xml`: `<paths><files-path name="snapshots" path="snapshots/" /></paths>`.
    2. `AndroidManifest.xml`: inside `<application>` add
       ```xml
       <provider
           android:name="androidx.core.content.FileProvider"
           android:authorities="${applicationId}.fileprovider"
           android:exported="false"
           android:grantUriPermissions="true">
           <meta-data
               android:name="android.support.FILE_PROVIDER_PATHS"
               android:resource="@xml/file_paths" />
       </provider>
       ```
    3. `SnapshotShare.kt`: `object SnapshotShare { fun share(context: Context, snapshot: FiringSnapshot, summary: String) }` — `val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", snapshot.imageFile)`; `Intent(Intent.ACTION_SEND)` with `type = "image/png"`, `putExtra(Intent.EXTRA_STREAM, uri)`, `putExtra(Intent.EXTRA_TEXT, summary)`, `putExtra(Intent.EXTRA_SUBJECT, "Wingspan firing position")`, `addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)`; start `Intent.createChooser(intent, "Share snapshot")`.
    4. `SnapshotDetailScreen.kt`: wire the Share button to `SnapshotShare.share(context, snapshot, viewModel.summaryText(snapshot))`.
  - **Verify:** `cd /home/nathan/wingspan && ./gradlew assembleDebug testDebugUnitTest`
  - **Done when:** build succeeds. **(manual, on device)**: Share opens the system sheet; sending to a messaging app delivers the image with the text summary.

- [ ] **WS-8.4** Show a saved snapshot on the map
  - **Repos:** wingspan
  - **Read:** app/src/main/java/com/wingspan/app/ui/WingspanApp.kt, app/src/main/java/com/wingspan/app/ui/map/MapViewModel.kt, app/src/main/java/com/wingspan/app/ui/map/MapController.kt, app/src/main/java/com/wingspan/app/ui/map/MapScreen.kt, app/src/main/java/com/wingspan/app/domain/geo/Sector.kt
  - **Edit:** app/src/main/java/com/wingspan/app/ui/WingspanApp.kt, app/src/main/java/com/wingspan/app/ui/map/MapViewModel.kt, app/src/main/java/com/wingspan/app/ui/map/MapController.kt, app/src/main/java/com/wingspan/app/ui/map/MapScreen.kt
  - **Instructions:**
    1. `WingspanApp.kt`: change the map route to `"map?snapshotId={snapshotId}"` with `navArgument("snapshotId") { type = NavType.LongType; defaultValue = -1L }`, `startDestination = "map?snapshotId=-1"`; pass `snapshotId` into `MapScreen`. In the snapshot detail route, `onShowOnMap = { id -> navController.navigate("map?snapshotId=$id") { popUpTo("map?snapshotId={snapshotId}") { inclusive = true } } }`.
    2. `MapViewModel.kt`: add `val viewingSnapshot = MutableStateFlow<FiringSnapshot?>(null)`, `fun viewSnapshot(id: Long)` (loads from `snapshotRepository.get(id)`, sets the state, and emits a `cameraRequests` value for its position), `fun closeSnapshotView()`.
    3. `MapController.kt`: in `installOverlays` add source `snapshot-fans` and layers `FillLayer("snapshot-fans-fill")` (`fillColor("#FFB300")`, `fillOpacity(0.25f)`) and `LineLayer("snapshot-fans-outline")` (`lineColor("#FF8F00")`, `lineWidth(2f)`) at their canonical positions; add `fun setSnapshotFans(snapshot: FiringSnapshot?)` building sector polygons from `snapshot.position`, each `SnapshotFan` (true bearings, `fullCircle`), and `snapshot.maxRangeM`; also include a small circle polygon (`Sector.circleOutline(position, 3.0)`) marking the snapshot position. Cache and re-apply after style reload.
    4. `MapScreen.kt`: accept `snapshotId: Long`; `LaunchedEffect(snapshotId) { if (snapshotId >= 0) viewModel.viewSnapshot(snapshotId) }`; `LaunchedEffect(viewingSnapshot) { controller.setSnapshotFans(viewingSnapshot) }`; when `viewingSnapshot != null` show a top-center banner "Viewing snapshot from {date time}" with a Close `IconButton` (`Icons.Default.Close` → `closeSnapshotView()`), taking precedence over the manual-position banner.
  - **Verify:** `cd /home/nathan/wingspan && ./gradlew assembleDebug testDebugUnitTest`
  - **Done when:** build succeeds. **(manual, on device)**: "Show on map" centers the map on the snapshot position and draws its fans in amber alongside the current green fans; Close removes them.

- [ ] **WS-8.5** Export all snapshots as a zip bundle
  - **Repos:** wingspan
  - **Read:** app/src/main/java/com/wingspan/app/data/SnapshotRepository.kt, app/src/main/java/com/wingspan/app/ui/snapshots/SnapshotsViewModel.kt, app/src/main/java/com/wingspan/app/ui/snapshots/SnapshotsScreen.kt, app/src/main/java/com/wingspan/app/ui/editor/ZoneFileIo.kt
  - **Edit:** app/src/main/java/com/wingspan/app/ui/snapshots/SnapshotExport.kt, app/src/main/java/com/wingspan/app/ui/snapshots/SnapshotsViewModel.kt, app/src/main/java/com/wingspan/app/ui/snapshots/SnapshotsScreen.kt
  - **Instructions:**
    1. `SnapshotExport.kt`: `@Serializable data class SnapshotExportRecord(val id: Long, val timestampMs: Long, val lat: Double, val lon: Double, val positionSource: String, val settings: LoadSettings, val maxRangeM: Double, val effectiveRangeM: Double, val declinationDeg: Double, val fans: List<SnapshotFan>, val notes: String, val image: String)` and `object SnapshotExport { suspend fun writeZip(resolver: ContentResolver, uri: Uri, snapshots: List<FiringSnapshot>) }` on `Dispatchers.IO`: `ZipOutputStream(resolver.openOutputStream(uri))` with entry `snapshots.json` (pretty JSON array of records, `image = imageFile.name`) followed by one entry per existing image file under `images/<name>`. Provide `fun suggestedName() = "wingspan-snapshots-" + yyyyMMdd-HHmm + ".zip"`.
    2. `SnapshotsViewModel.kt`: add `suspend fun exportAll(resolver: ContentResolver, uri: Uri)` calling `SnapshotExport.writeZip(resolver, uri, repo.getAll())`.
    3. `SnapshotsScreen.kt`: add a top-bar action (`Icons.Default.Share`, content description "Export all") that launches `ActivityResultContracts.CreateDocument("application/zip")` with the suggested name and, on result, runs `exportAll` and toasts "Snapshots exported". Disable when the list is empty.
  - **Verify:** `cd /home/nathan/wingspan && ./gradlew assembleDebug testDebugUnitTest`
  - **Done when:** build succeeds. **(manual, on device)**: the exported zip contains `snapshots.json` and `images/*.png`.

Acceptance criteria for Phase 8:

- A snapshot stores the map image plus all data needed to reconstruct the fans, with editable notes.
- Snapshots can be shared (image + text) and re-displayed on the map.
- All snapshots can be exported as a single zip.

## Phase 9: Final Verification Checklist

Run only after all phases are done.

- [ ] **FINAL-9.1** Run lint and fix all errors
  - **Repos:** wingspan
  - **Read:** app/build.gradle.kts
  - **Edit:** any file reported by lint with severity Error (list them in the commit message)
  - **Instructions:** Run `./gradlew lintDebug`. Open `app/build/reports/lint-results-debug.xml` and fix every issue with `severity="Error"`. Warnings may remain, but do not suppress errors with `@Suppress`/`tools:ignore` unless the issue is a known false positive (explain in a code comment). Do not add `abortOnError = true`.
  - **Verify:** `cd /home/nathan/wingspan && ./gradlew lintDebug && ! grep -q 'severity="Error"' app/build/reports/lint-results-debug.xml && echo "no lint errors"`
  - **Done when:** the report contains no `severity="Error"` entries.

- [ ] **FINAL-9.2** Full build, unit tests, and unsigned release assembly
  - **Repos:** wingspan
  - **Read:** roadmap.md
  - **Edit:** none
  - **Instructions:** Run the complete build from a clean state to prove the repository is self-contained.
  - **Verify:** `cd /home/nathan/wingspan && ./gradlew clean assembleDebug assembleRelease testDebugUnitTest && ls -la app/build/outputs/apk/debug/app-debug.apk app/build/outputs/apk/release/app-release-unsigned.apk`
  - **Done when:** both APKs exist and all unit tests pass.

- [ ] **FINAL-9.3** Write `README.md`
  - **Repos:** wingspan
  - **Read:** roadmap.md
  - **Edit:** README.md
  - **Instructions:** Write a README with: one-paragraph overview; feature list (map/basemaps, no-fire zones + GeoJSON, settings + ballistics model summary with the Journee validation, fans + magnetic bearings, manual position, offline areas, snapshots); build instructions (Android Studio, or CLI: JDK 21, `./gradlew assembleDebug`, `adb install app/build/outputs/apk/debug/app-debug.apk`); the on-device acceptance checklist from FINAL-9.4; basemap attributions (USGS The National Map; Esri World Imagery); and a safety disclaimer stating that ranges are model estimates under ideal conditions, that users must apply their own safety margins and follow range rules, and that the app does not replace a range safety officer.
  - **Verify:** `cd /home/nathan/wingspan && test -s README.md && grep -c '^## ' README.md`
  - **Done when:** README exists with at least five sections.

- [ ] **FINAL-9.4** On-device acceptance (performed by the user, not the implementer)
  - **Repos:** none
  - **Read:** README.md
  - **Edit:** none
  - **Instructions:** Install `app-debug.apk` on a phone and confirm: (1) topo/imagery basemaps render and the choice persists; (2) GPS dot with accuracy circle appears; manual mode + long-press moves it; (3) drawing a polygon and a marker, dragging handles, midpoint insert, delete point, rename, delete all work; (4) GeoJSON export opens in geojson.io and re-imports; (5) settings change the computed ranges and fan sizes; fans are cut by zones with a visible margin; tapping a fan shows magnetic bearings that agree with a compass app within a few degrees; (6) an offline area downloads and renders in airplane mode; (7) snapshot capture, list, detail, notes, share, show-on-map, and zip export work.
  - **Verify:** manual
  - **Done when:** the user reports all seven checks pass (mark this step done only on the user's say-so).

## Notes For Future Implementers

- **Do not bump** AGP past 8.13.x, or `core-ktx`/Compose/lifecycle/navigation past the pinned versions, without moving to AGP 9 and `compileSdk 37` deliberately; the newest androidx releases hard-require them (`minCompileSdk=37`, `minAndroidGradlePluginVersion=9.1.0` in their AAR metadata).
- MapLibre is pinned to the **OpenGL** artifact (`android-sdk-opengl`); the default `android-sdk` artifact is Vulkan-only since 13.0 and less reliable on older devices. Package names are `org.maplibre.android.*` and `org.maplibre.geojson.*`.
- All MapLibre calls live in `ui/map/MapController.kt`. Overlay sources/layers are installed in one canonical order inside `installOverlays` and every overlay's last data is cached and re-applied after each style reload (a basemap switch destroys runtime layers).
- Offline downloads must reference an `https://` style URL (`Basemap.remoteStyleUrl`, served from this public repository on GitHub). The live map uses the identical style JSON from assets. Keep the two in sync — they are the same file. If the repository ever becomes private or moves, update `Basemap.remoteStyleUrl`; a future alternative is serving the style from an in-app localhost HTTP server.
- `domain/*` must stay Android-free; `DeclinationProvider` is the only seam to Android (`GeomagneticField`) and lives in `data/`.
- Fans are computed against **maximum** range with a ±2° margin; never compute them against effective range. The energy-threshold and choke tables are heuristics; changes to them belong in Settings defaults, not hard-coded elsewhere.
- Tile servers are not reachable from the sandbox; anything that needs tiles is a **(manual, on device)** check.
- Deferred to later phases: ARCore AR overlay (product Phase 2), KML import/export, vertex snapping/undo history for edits, persisting the manual position across restarts, altitude/temperature corrections to the ballistics model, buckshot sizes, an in-app HTTP server for offline styles.
