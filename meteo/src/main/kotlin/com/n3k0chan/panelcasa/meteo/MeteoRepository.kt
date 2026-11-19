package com.n3k0chan.panelcasa.meteo

import com.n3k0chan.panelcasa.net.Fetcher
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Descarga la previsión de MET Norway respetando sus condiciones de uso:
 * coordenadas con 4 decimales como máximo, User-Agent identificativo (lo pone el Fetcher),
 * caché según «Expires» y peticiones condicionales con «If-Modified-Since».
 * Además nunca pide más de una vez por hora.
 */
class MeteoRepository(
    private val dir: File,
    private val fetcher: Fetcher,
    private val clock: () -> Long = System::currentTimeMillis,
    private val minIntervalMs: Long = HOUR_MS,
) {
    @Serializable
    data class State(
        val lat: Double = 0.0,
        val lon: Double = 0.0,
        val lastModified: String? = null,
        val expiresAt: Long = 0,
        val fetchedAt: Long = 0,
    )

    enum class Outcome { UPDATED, NOT_MODIFIED, SKIPPED }

    private val stateFile = File(dir, "meteo_state.json")
    private val bodyFile = File(dir, "meteo.json")

    init {
        dir.mkdirs()
    }

    fun state(): State = try {
        if (stateFile.isFile) json.decodeFromString(State.serializer(), stateFile.readText()) else State()
    } catch (e: Exception) {
        State()
    }

    /** Momento a partir del cual se puede volver a pedir. */
    fun nextAllowedAt(): Long {
        val s = state()
        return maxOf(s.expiresAt, s.fetchedAt + minIntervalMs)
    }

    @Throws(IOException::class)
    fun refresh(lat: Double, lon: Double): Outcome {
        val la = round4(lat)
        val lo = round4(lon)
        val s = state()
        val now = clock()
        val sameLocation = s.lat == la && s.lon == lo && bodyFile.isFile
        if (sameLocation && now < nextAllowedAt()) return Outcome.SKIPPED

        val resp = fetcher.get(url(la, lo), lastModified = if (sameLocation) s.lastModified else null)
        val expires = parseHttpDate(resp.expires) ?: (now + HOUR_MS)
        val body = resp.body
        return when {
            resp.ok && body != null -> {
                val text = String(body, Charsets.UTF_8)
                // Validar antes de guardar: si la respuesta no es válida se conserva la anterior.
                MetParser.parse(text, Instant.ofEpochMilli(now), ZoneId.of("UTC"))
                writeAtomic(bodyFile, text)
                saveState(State(la, lo, resp.lastModified, expires, now))
                Outcome.UPDATED
            }
            resp.notModified && sameLocation -> {
                saveState(s.copy(expiresAt = expires, fetchedAt = now))
                Outcome.NOT_MODIFIED
            }
            else -> throw IOException("Met.no: HTTP ${resp.code}")
        }
    }

    /** Previsión a partir de la última respuesta guardada, calculada para [now]. */
    fun forecast(now: Instant, zone: ZoneId): Forecast? = try {
        if (bodyFile.isFile) MetParser.parse(bodyFile.readText(), now, zone) else null
    } catch (e: IOException) {
        null
    }

    private fun saveState(s: State) = writeAtomic(stateFile, json.encodeToString(State.serializer(), s))

    private fun writeAtomic(f: File, text: String) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(text)
        if (f.exists() && !f.delete()) throw IOException("No se puede sustituir $f")
        if (!tmp.renameTo(f)) throw IOException("No se puede guardar $f")
    }

    companion object {
        const val HOUR_MS = 3600_000L
        private const val BASE = "https://api.met.no/weatherapi/locationforecast/2.0/compact"
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun round4(v: Double): Double = Math.round(v * 10_000.0) / 10_000.0

        fun url(lat: Double, lon: Double): String =
            String.format(Locale.ROOT, "%s?lat=%.4f&lon=%.4f", BASE, round4(lat), round4(lon))

        fun parseHttpDate(s: String?): Long? = s?.let {
            runCatching { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
        }
    }
}
