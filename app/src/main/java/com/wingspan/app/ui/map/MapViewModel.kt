package com.wingspan.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wingspan.app.AppContainer
import com.wingspan.app.data.SettingsRepository
import com.wingspan.app.data.ZoneRepository
import com.wingspan.app.data.location.LocationFix
import com.wingspan.app.data.location.LocationProvider
import com.wingspan.app.data.map.Basemap
import com.wingspan.app.domain.ballistics.RangeCalculator
import com.wingspan.app.domain.ballistics.RangeResult
import com.wingspan.app.domain.ballistics.UnitSystem
import com.wingspan.app.domain.geo.DeclinationProvider
import com.wingspan.app.domain.geo.Fan
import com.wingspan.app.domain.geo.FanCalculator
import com.wingspan.app.domain.geo.LatLon
import com.wingspan.app.domain.geo.NoFireZone
import com.wingspan.app.domain.geo.trueToMagnetic
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PositionSource { GPS, MANUAL }

data class ShooterPosition(val position: LatLon, val source: PositionSource, val accuracyM: Double?)

data class FanView(val index: Int, val fan: Fan, val leftMagDeg: Double, val rightMagDeg: Double)

data class FanUiState(
    val origin: LatLon,
    val source: PositionSource,
    val accuracyM: Double?,
    val range: RangeResult,
    val fans: List<FanView>,
    val insideZone: Boolean,
    val declinationDeg: Double,
    val units: UnitSystem,
)

private fun distanceM(a: LatLon, b: LatLon): Double {
    val earthRadiusM = 6371000.0
    val lat1 = Math.toRadians(a.lat)
    val lat2 = Math.toRadians(b.lat)
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
    return 2 * earthRadiusM * asin(sqrt(h))
}

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModel(
    private val settingsRepository: SettingsRepository,
    private val locationProvider: LocationProvider,
    private val zoneRepository: ZoneRepository,
    private val declinationProvider: DeclinationProvider,
) : ViewModel() {

    val basemap: StateFlow<Basemap> = settingsRepository.basemapKey
        .map(Basemap::fromKey)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Basemap.USGS_TOPO)

    val zones: StateFlow<List<NoFireZone>> = zoneRepository.zones
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setBasemap(b: Basemap) {
        viewModelScope.launch { settingsRepository.setBasemapKey(b.key) }
    }

    private val gpsFix = MutableStateFlow<LocationFix?>(null)
    private val manualPosition = MutableStateFlow<LatLon?>(null)
    val manualMode = MutableStateFlow(false)
    val hasLocationPermission = MutableStateFlow(false)

    private var collectingLocation = false
    private var centeredOnce = false

    val cameraRequests = MutableSharedFlow<LatLon>(extraBufferCapacity = 1)

    val shooter: StateFlow<ShooterPosition?> = combine(gpsFix, manualPosition, manualMode) { fix, manual, isManual ->
        if (isManual && manual != null) {
            ShooterPosition(manual, PositionSource.MANUAL, null)
        } else if (fix != null) {
            ShooterPosition(fix.position, PositionSource.GPS, fix.accuracyM)
        } else {
            null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val rangeResult: Flow<RangeResult> = settingsRepository.settings
        .map { RangeCalculator.compute(it.toBallisticInput()) }
        .flowOn(Dispatchers.Default)

    private val throttledShooter = shooter.distinctUntilChanged { old, new ->
        old != null && new != null && old.source == new.source && distanceM(old.position, new.position) < 5.0
    }

    private data class FanInput(
        val shooterPosition: ShooterPosition,
        val zones: List<NoFireZone>,
        val range: RangeResult,
        val units: UnitSystem,
    )

    private var lastFanCount: Int? = null

    val selectedFanIndex = MutableStateFlow<Int?>(null)

    val fanState: StateFlow<FanUiState?> = combine(
        throttledShooter,
        zones,
        rangeResult,
        settingsRepository.settings,
    ) { s, z, r, cfg ->
        s?.let { FanInput(it, z, r, cfg.unitSystem) }
    }.mapLatest { input ->
        input?.let { withContext(Dispatchers.Default) { compute(it) } }
    }.onEach { state ->
        val count = state?.fans?.size
        if (lastFanCount != null && count != lastFanCount) {
            selectedFanIndex.value = null
        }
        lastFanCount = count
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private fun compute(input: FanInput): FanUiState {
        val origin = input.shooterPosition.position
        val range = input.range
        val result = FanCalculator.compute(
            origin = origin,
            maxRangeM = range.fanRangeM,
            zones = input.zones,
            windBufferM = range.windBufferM,
        )
        val declination = declinationProvider.declinationDeg(origin)
        val fanViews = result.fans.mapIndexed { index, fan ->
            val leftMag: Double
            val rightMag: Double
            if (fan.fullCircle) {
                leftMag = 0.0
                rightMag = 360.0
            } else {
                leftMag = trueToMagnetic(fan.leftTrueDeg, declination)
                rightMag = trueToMagnetic(fan.rightTrueDeg, declination)
            }
            FanView(index, fan, leftMag, rightMag)
        }
        return FanUiState(
            origin = origin,
            source = input.shooterPosition.source,
            accuracyM = input.shooterPosition.accuracyM,
            range = range,
            fans = fanViews,
            insideZone = result.insideZone,
            declinationDeg = declination,
            units = input.units,
        )
    }

    fun selectFan(i: Int?) {
        selectedFanIndex.value = i
    }

    fun onLocationPermissionResult(granted: Boolean) {
        hasLocationPermission.value = granted
        if (granted && !collectingLocation) {
            collectingLocation = true
            viewModelScope.launch {
                locationProvider.updates().collect { fix ->
                    gpsFix.value = fix
                    if (!centeredOnce) {
                        centeredOnce = true
                        cameraRequests.tryEmit(fix.position)
                    }
                }
            }
        }
    }

    fun toggleManualMode() {
        val turningOn = !manualMode.value
        manualMode.value = turningOn
        if (turningOn && manualPosition.value == null) {
            gpsFix.value?.let { manualPosition.value = it.position }
        }
    }

    fun setManualPosition(p: LatLon) {
        if (manualMode.value) {
            manualPosition.value = p
        }
    }

    fun recenter() {
        shooter.value?.let { cameraRequests.tryEmit(it.position) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MapViewModel(
                    container.settingsRepository,
                    container.locationProvider,
                    container.zoneRepository,
                    container.declinationProvider,
                )
            }
        }
    }
}
