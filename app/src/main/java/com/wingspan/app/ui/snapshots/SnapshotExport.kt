package com.wingspan.app.ui.snapshots

import android.content.ContentResolver
import android.net.Uri
import com.wingspan.app.data.FiringSnapshot
import com.wingspan.app.data.SnapshotFan
import com.wingspan.app.domain.ballistics.LoadSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SnapshotExportRecord(
    val id: Long,
    val timestampMs: Long,
    val lat: Double,
    val lon: Double,
    val positionSource: String,
    val settings: LoadSettings,
    val maxRangeM: Double,
    val effectiveRangeM: Double,
    val declinationDeg: Double,
    val fans: List<SnapshotFan>,
    val notes: String,
    val image: String,
)

/**
 * Writes all snapshots as a zip bundle through Android's [ContentResolver], as returned by the
 * system file picker's `Uri`s (Storage Access Framework).
 */
object SnapshotExport {

    suspend fun writeZip(resolver: ContentResolver, uri: Uri, snapshots: List<FiringSnapshot>) {
        withContext(Dispatchers.IO) {
            val records = snapshots.map { snapshot ->
                SnapshotExportRecord(
                    id = snapshot.id,
                    timestampMs = snapshot.timestampMs,
                    lat = snapshot.position.lat,
                    lon = snapshot.position.lon,
                    positionSource = snapshot.positionSource,
                    settings = snapshot.settings,
                    maxRangeM = snapshot.maxRangeM,
                    effectiveRangeM = snapshot.effectiveRangeM,
                    declinationDeg = snapshot.declinationDeg,
                    fans = snapshot.fans,
                    notes = snapshot.notes,
                    image = snapshot.imageFile.name,
                )
            }
            val json = Json { prettyPrint = true }.encodeToString(records)

            resolver.openOutputStream(uri)?.use { output ->
                ZipOutputStream(output).use { zipOut ->
                    zipOut.putNextEntry(ZipEntry("snapshots.json"))
                    zipOut.write(json.toByteArray(Charsets.UTF_8))
                    zipOut.closeEntry()

                    for (snapshot in snapshots) {
                        if (snapshot.imageFile.exists()) {
                            zipOut.putNextEntry(ZipEntry("images/${snapshot.imageFile.name}"))
                            snapshot.imageFile.inputStream().use { it.copyTo(zipOut) }
                            zipOut.closeEntry()
                        }
                    }
                }
            }
        }
    }

    fun suggestedName(): String =
        "wingspan-snapshots-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".zip"
}
