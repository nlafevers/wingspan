package com.wingspan.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wingspan.app.AppContainer
import com.wingspan.app.data.SettingsRepository
import com.wingspan.app.data.location.LocationFix
import com.wingspan.app.data.location.LocationProvider
import com.wingspan.app.data.map.Basemap
import com.wingspan.app.domain.geo.LatLon
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PositionSource { GPS, MANUAL }

data class ShooterPosition(val position: LatLon, val source: PositionSource, val accuracyM: Double?)

class MapViewModel(
    private val settingsRepository: SettingsRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    val basemap: StateFlow<Basemap> = settingsRepository.basemapKey
        .map(Basemap::fromKey)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Basemap.USGS_TOPO)

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
            initializer { MapViewModel(container.settingsRepository, container.locationProvider) }
        }
    }
}
