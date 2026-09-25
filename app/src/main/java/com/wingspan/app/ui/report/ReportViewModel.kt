package com.wingspan.app.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wingspan.app.AppContainer
import com.wingspan.app.data.ReportRepository
import com.wingspan.app.domain.geo.EnuProjection
import com.wingspan.app.domain.geo.Geometry2D
import com.wingspan.app.domain.geo.LatLon
import com.wingspan.app.domain.report.RangeReport
import com.wingspan.app.domain.report.ReportFan
import com.wingspan.app.domain.report.ReportPosition
import com.wingspan.app.domain.report.ReportShot
import com.wingspan.app.ui.map.FanUiState
import kotlin.math.atan2
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReportViewModel(private val reportRepository: ReportRepository) : ViewModel() {

    val report: StateFlow<RangeReport> = reportRepository.report
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RangeReport())

    val reportMode = MutableStateFlow(false)

    fun setReportMode(on: Boolean) {
        reportMode.value = on
    }

    val activePositionId = MutableStateFlow<Long?>(null)

    fun selectPosition(id: Long?) {
        activePositionId.value = id
    }

    fun addPosition(state: FanUiState) {
        val id = System.currentTimeMillis()
        val newPosition = ReportPosition(
            id = id,
            timestampMs = id,
            position = state.origin,
            positionSource = state.source.name,
            maxRangeM = state.range.fanRangeM,
            effectiveRangeM = state.range.effectiveRangeM,
            windBufferM = state.range.windBufferM,
            declinationDeg = state.declinationDeg,
            accuracyM = state.accuracyM,
            effectiveRangeLimiter = state.range.effectiveRangeLimiter,
            fans = state.fans.map { fanView ->
                ReportFan(
                    leftTrueDeg = fanView.fan.leftTrueDeg,
                    rightTrueDeg = fanView.fan.rightTrueDeg,
                    leftMagDeg = fanView.leftMagDeg,
                    rightMagDeg = fanView.rightMagDeg,
                    fullCircle = fanView.fan.fullCircle,
                )
            },
        )
        viewModelScope.launch { reportRepository.update { it.withPosition(newPosition) } }
        activePositionId.value = newPosition.id
    }

    fun addShotAt(positionId: Long, tap: LatLon) {
        val position = report.value.positions.find { it.id == positionId } ?: return
        val enu = EnuProjection(position.position).toEnu(tap)
        val bearing = Geometry2D.normalizeBearing(Math.toDegrees(atan2(enu.x, enu.y)))
        viewModelScope.launch {
            reportRepository.update { it.withShot(positionId, ReportShot(bearingTrueDeg = bearing)) }
        }
    }

    fun setShotBearingMagnetic(positionId: Long, index: Int, magneticDeg: Double) {
        val position = report.value.positions.find { it.id == positionId } ?: return
        val shot = position.shots.getOrNull(index) ?: return
        val trueDeg = Geometry2D.normalizeBearing(magneticDeg + position.declinationDeg)
        viewModelScope.launch {
            reportRepository.update {
                it.updateShot(positionId, index, shot.copy(bearingTrueDeg = trueDeg))
            }
        }
    }

    fun setShotLabel(positionId: Long, index: Int, label: String) {
        val position = report.value.positions.find { it.id == positionId } ?: return
        val shot = position.shots.getOrNull(index) ?: return
        viewModelScope.launch {
            reportRepository.update {
                it.updateShot(positionId, index, shot.copy(label = label))
            }
        }
    }

    fun removeShot(positionId: Long, index: Int) {
        viewModelScope.launch { reportRepository.update { it.removeShot(positionId, index) } }
    }

    fun removePosition(id: Long) {
        viewModelScope.launch { reportRepository.update { it.removePosition(id) } }
    }

    fun clearReport() {
        viewModelScope.launch { reportRepository.clear() }
        activePositionId.value = null
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ReportViewModel(container.reportRepository) }
        }
    }
}
