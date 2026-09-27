package com.wingspan.app.ui.report

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wingspan.app.AppContainer
import com.wingspan.app.data.ReportRepository
import com.wingspan.app.data.SettingsRepository
import com.wingspan.app.data.map.Basemap
import com.wingspan.app.domain.ballistics.LoadSettings
import com.wingspan.app.domain.geo.Geometry2D
import com.wingspan.app.domain.geo.LatLon
import com.wingspan.app.domain.geo.NoFireZone
import com.wingspan.app.domain.report.RangeReport
import com.wingspan.app.domain.report.ReportFan
import com.wingspan.app.domain.report.ReportLayout
import com.wingspan.app.domain.report.ReportPosition
import com.wingspan.app.domain.report.recordShot
import com.wingspan.app.ui.map.FanUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReportViewModel(
    private val reportRepository: ReportRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val report: StateFlow<RangeReport> = reportRepository.report
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RangeReport())

    val message = MutableSharedFlow<String>(extraBufferCapacity = 1)

    val reportMode = MutableStateFlow(false)

    fun setReportMode(on: Boolean) {
        reportMode.value = on
    }

    val activePositionId = MutableStateFlow<Long?>(null)

    fun selectPosition(id: Long?) {
        activePositionId.value = id
    }

    /**
     * Records a shot towards [tap] from [state]'s position. The load settings
     * are read from the repository so a shot is never stamped with defaults.
     */
    fun recordShot(state: FanUiState, tap: LatLon) {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val id = System.currentTimeMillis()
            val candidate = ReportPosition(
                id = id,
                timestampMs = id,
                position = state.origin,
                positionSource = state.source.name,
                maxRangeM = state.range.maxRangeM,
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
                settings = settings,
            )
            var receiverId: Long? = null
            reportRepository.update { report ->
                report.recordShot(candidate, tap, System.currentTimeMillis())
                    .also { receiverId = it.second }
                    .first
            }
            receiverId?.let { activePositionId.value = it }
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

    fun deletePosition(id: Long) {
        viewModelScope.launch { reportRepository.update { it.removePosition(id) } }
        if (activePositionId.value == id) activePositionId.value = null
    }

    fun clearReport() {
        viewModelScope.launch { reportRepository.clear() }
        activePositionId.value = null
    }

    fun suggestedPdfName(): String =
        "wingspan-report-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".pdf"

    suspend fun exportPdf(
        context: Context,
        resolver: ContentResolver,
        uri: Uri,
        basemap: Basemap,
        zones: List<NoFireZone>,
        settings: LoadSettings,
    ) {
        val currentReport = report.value
        val frame = ReportLayout.frameFor(currentReport)
        val background = frame?.let { ReportSnapshotter.capture(context, basemap, it) }
        try {
            withContext(Dispatchers.IO) {
                resolver.openOutputStream(uri)?.use { stream ->
                    PdfReportComposer.write(
                        stream,
                        currentReport,
                        zones,
                        settings,
                        background,
                        System.currentTimeMillis(),
                    )
                } ?: error("Unable to open output stream")
            }
            message.tryEmit("Report exported")
        } catch (e: Exception) {
            message.tryEmit("Report export failed")
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ReportViewModel(container.reportRepository, container.settingsRepository) }
        }
    }
}
