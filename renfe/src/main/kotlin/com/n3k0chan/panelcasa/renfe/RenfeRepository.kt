package com.n3k0chan.panelcasa.renfe

import com.n3k0chan.panelcasa.net.Fetcher
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/**
 * Descarga, procesado y almacenamiento local de los datos de Renfe.
 * Nunca borra el último horario válido hasta tener uno nuevo procesado.
 */
class RenfeRepository(
    private val dir: File,
    private val fetcher: Fetcher,
    private val scanner: GtfsScanner = GtfsScanner(),
    private val staticUrl: String = STATIC_URL,
    private val tripUpdatesUrl: String = TRIP_UPDATES_URL,
    private val alertsUrl: String = ALERTS_URL,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Serializable
    data class Meta(
        val etag: String? = null,
        val lastModified: String? = null,
        val downloadedAt: Long = 0,
        val checkedAt: Long = 0,
        val feedVersion: String = "",
        val lastScanMs: Long = 0,
        val lastScanAt: Long = 0,
    )

    enum class StaticOutcome { UPDATED, NOT_MODIFIED, SKIPPED }

    private val zipFile = File(dir, "gtfs.zip")
    private val newZipFile = File(dir, "gtfs.new.zip")
    private val metaFile = File(dir, "meta.json")
    private val indexFile = File(dir, "index.json")
    private val scheduleFile = File(dir, "schedule.json")

    init {
        dir.mkdirs()
    }

    fun meta(): Meta = read(metaFile) ?: Meta()
    fun loadIndex(): StationIndex? = read(indexFile)
    fun loadSchedule(): StationSchedule? = read(scheduleFile)
    fun hasZip(): Boolean = zipFile.isFile && zipFile.length() > 0

    /** Fase de una operación larga, para mostrar el progreso. */
    sealed class Progress {
        /** [total] = -1 si el servidor no indica el tamaño. */
        data class Downloading(val bytes: Long, val total: Long) : Progress()
        object Processing : Progress()
    }

    /** La estación está elegida pero ya no aparece en el horario descargado. */
    fun stationMissing(stationId: String?): Boolean {
        if (stationId == null) return false
        val index = loadIndex() ?: return false
        return index.stations.none { it.id == stationId }
    }

    /**
     * Comprueba si hay horario nuevo (petición condicional) y lo procesa.
     * [minCheckIntervalMs] evita volver a preguntar al servidor demasiado a menudo.
     */
    @Throws(IOException::class)
    fun refreshStatic(
        stationId: String?,
        minCheckIntervalMs: Long = DAY_MS,
        force: Boolean = false,
        onProgress: (Progress) -> Unit = {},
    ): StaticOutcome {
        val meta = meta()
        val now = clock()
        val gone = stationMissing(stationId)
        val missingLocal = !hasZip() || loadIndex() == null ||
            (stationId != null && !gone && loadSchedule()?.station?.id != stationId)
        if (!force && !missingLocal && now - meta.checkedAt < minCheckIntervalMs) return StaticOutcome.SKIPPED

        val conditional = hasZip() && !force
        newZipFile.delete()
        val progress: (Long, Long) -> Unit = { b, t -> onProgress(Progress.Downloading(b, t)) }
        val resp = if (conditional) {
            fetcher.download(staticUrl, newZipFile, meta.etag, meta.lastModified, progress)
        } else {
            fetcher.download(staticUrl, newZipFile, onProgress = progress)
        }
        return when {
            resp.ok -> {
                val version = resp.lastModified ?: java.time.Instant.ofEpochMilli(now).toString()
                // Se procesa el fichero nuevo antes de sustituir nada: si está corrupto, se conserva el anterior.
                onProgress(Progress.Processing)
                val start = System.nanoTime()
                val result = try {
                    scanWithFallback(newZipFile, stationId, version)
                } catch (e: Exception) {
                    newZipFile.delete()
                    write(metaFile, meta.copy(checkedAt = now))
                    throw IOException("Horario de Renfe descargado pero no válido: ${e.message}", e)
                }
                if (zipFile.exists() && !zipFile.delete()) throw IOException("No se puede sustituir $zipFile")
                if (!newZipFile.renameTo(zipFile)) throw IOException("No se puede guardar $zipFile")
                write(indexFile, result.index)
                if (result.schedule != null) write(scheduleFile, result.schedule) else scheduleFile.delete()
                write(
                    metaFile,
                    meta.copy(
                        etag = resp.etag, lastModified = resp.lastModified,
                        downloadedAt = now, checkedAt = now, feedVersion = version,
                        lastScanMs = (System.nanoTime() - start) / 1_000_000, lastScanAt = clock(),
                    ),
                )
                StaticOutcome.UPDATED
            }
            resp.notModified -> {
                write(metaFile, meta.copy(checkedAt = now))
                if (missingLocal) rescan(stationId, onProgress)
                StaticOutcome.NOT_MODIFIED
            }
            else -> throw IOException("Horario de Renfe: HTTP ${resp.code}")
        }
    }

    /**
     * Si la estación elegida ya no está en el horario, se acepta igualmente el horario nuevo
     * (sin horario de estación) para que la app pueda avisar y dejar elegir otra.
     */
    private fun scanWithFallback(zip: File, stationId: String?, version: String): GtfsScanner.Result = try {
        scanner.scan(zip, stationId, version)
    } catch (e: StationNotFoundException) {
        scanner.scan(zip, null, version)
    }

    /** Vuelve a procesar el GTFS guardado, sin red. Sirve para cambiar de estación. */
    @Throws(IOException::class)
    fun rescan(stationId: String?, onProgress: (Progress) -> Unit = {}): GtfsScanner.Result {
        if (!hasZip()) throw IOException("No hay horario descargado")
        onProgress(Progress.Processing)
        val start = System.nanoTime()
        val meta = meta()
        val result = scanWithFallback(zipFile, stationId, meta.feedVersion)
        write(indexFile, result.index)
        if (result.schedule != null) write(scheduleFile, result.schedule) else scheduleFile.delete()
        write(metaFile, meta.copy(lastScanMs = (System.nanoTime() - start) / 1_000_000, lastScanAt = clock()))
        return result
    }

    @Throws(IOException::class)
    fun fetchRealtime(): RealtimeSnapshot {
        val resp = fetcher.get(tripUpdatesUrl)
        val body = resp.body
        if (!resp.ok || body == null) throw IOException("Tiempo real de Renfe: HTTP ${resp.code}")
        return GtfsRealtime.parseTripUpdates(body)
    }

    @Throws(IOException::class)
    fun fetchAlerts(): List<ServiceAlert> {
        val resp = fetcher.get(alertsUrl)
        val body = resp.body
        if (!resp.ok || body == null) throw IOException("Avisos de Renfe: HTTP ${resp.code}")
        return GtfsRealtime.parseAlerts(body)
    }

    private inline fun <reified T> read(f: File): T? = try {
        if (f.isFile) json.decodeFromString<T>(f.readText()) else null
    } catch (e: Exception) {
        null
    }

    private inline fun <reified T> write(f: File, value: T) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(json.encodeToString(value))
        if (f.exists() && !f.delete()) throw IOException("No se puede sustituir $f")
        if (!tmp.renameTo(f)) throw IOException("No se puede guardar $f")
    }

    companion object {
        const val STATIC_URL = "https://ssl.renfe.com/ftransit/Fichero_CER_FOMENTO/fomento_transit.zip"
        const val TRIP_UPDATES_URL = "https://gtfsrt.renfe.com/trip_updates.pb"
        const val ALERTS_URL = "https://gtfsrt.renfe.com/alerts.pb"
        const val DAY_MS = 24L * 3600 * 1000
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
