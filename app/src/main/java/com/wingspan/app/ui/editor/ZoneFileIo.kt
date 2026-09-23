package com.wingspan.app.ui.editor

import android.content.ContentResolver
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads and writes GeoJSON text through Android's [ContentResolver], as returned by the
 * system file picker's `Uri`s (Storage Access Framework).
 */
object ZoneFileIo {

    suspend fun writeText(resolver: ContentResolver, uri: Uri, text: String) {
        withContext(Dispatchers.IO) {
            resolver.openOutputStream(uri)?.use { stream ->
                stream.write(text.toByteArray(Charsets.UTF_8))
            }
        }
    }

    suspend fun readText(resolver: ContentResolver, uri: Uri): String {
        return withContext(Dispatchers.IO) {
            resolver.openInputStream(uri)?.use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            } ?: throw IllegalArgumentException("Unable to open $uri")
        }
    }

    fun suggestedExportName(): String =
        "wingspan-zones-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".geojson"
}
