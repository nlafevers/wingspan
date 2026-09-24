package com.wingspan.app.data.map

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * Serves bundled basemap style JSON (from `app/src/main/assets/styles/`) over loopback HTTP.
 *
 * Bound to the IPv4 loopback address only, on an OS-assigned ephemeral port, so nothing outside
 * the device's own process can ever reach it - no permission or firewall exception is needed.
 */
class LocalStyleServer(private val context: Context) : NanoHTTPD("127.0.0.1", 0) {

    /** Valid only once [startServing] has actually started the server. */
    val port: Int
        get() = listeningPort

    /** Starts the server on a daemon thread so it never blocks process teardown. */
    fun startServing() {
        start(SOCKET_READ_TIMEOUT, true)
    }

    override fun serve(session: IHTTPSession): Response {
        val assetPath = session.uri.removePrefix("/")
        return try {
            val bytes = context.assets.open(assetPath).use { it.readBytes() }
            newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                ByteArrayInputStream(bytes),
                bytes.size.toLong(),
            )
        } catch (e: IOException) {
            newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found: $assetPath")
        }
    }
}
