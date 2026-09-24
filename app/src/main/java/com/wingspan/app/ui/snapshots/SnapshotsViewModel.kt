package com.wingspan.app.ui.snapshots

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wingspan.app.AppContainer
import com.wingspan.app.data.FiringSnapshot
import com.wingspan.app.data.SnapshotFan
import com.wingspan.app.data.SnapshotRepository
import com.wingspan.app.ui.Formatters
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SnapshotsViewModel(private val repo: SnapshotRepository) : ViewModel() {

    val snapshots: StateFlow<List<FiringSnapshot>> = repo.snapshots
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun get(id: Long): FiringSnapshot? = repo.get(id)

    fun updateNotes(id: Long, notes: String) {
        viewModelScope.launch { repo.updateNotes(id, notes) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repo.delete(id) }
    }

    fun summaryText(s: FiringSnapshot): String {
        val units = s.settings.unitSystem
        val dateTime = DateFormat.getDateTimeInstance().format(Date(s.timestampMs))
        val sourceLabel = if (s.positionSource.equals("GPS", ignoreCase = true)) "GPS" else "manual"
        val lat = "%.5f".format(s.position.lat)
        val lon = "%.5f".format(s.position.lon)
        val loadLine = "${s.settings.shotSize.label} ${s.settings.material.label}, " +
            "${Formatters.velocity(s.settings.muzzleVelocityFps, units)}, ${s.settings.choke.label}"
        val rangesLine = "Max range ${Formatters.distance(s.maxRangeM, units)} · " +
            "Effective ${Formatters.distance(s.effectiveRangeM, units)}"
        return """
            Wingspan firing position — $dateTime
            Position: $lat, $lon ($sourceLabel)
            Load: $loadLine
            $rangesLine
            Fans (magnetic): ${fansText(s.fans)}
            Notes: ${s.notes}
        """.trimIndent()
    }

    internal fun fansText(fans: List<SnapshotFan>): String {
        if (fans.isEmpty()) return "No clear field of fire"
        if (fans.size == 1 && fans[0].fullCircle) return "Clear in all directions"
        return fans.joinToString(", ") { f ->
            if (f.fullCircle) {
                "Clear in all directions"
            } else {
                "${Formatters.angle(f.leftMagDeg)}–${Formatters.angle(f.rightMagDeg)}"
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SnapshotsViewModel(container.snapshotRepository)
            }
        }
    }
}
