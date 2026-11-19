package com.n3k0chan.panelcasa

import com.n3k0chan.panelcasa.meteo.Forecast
import com.n3k0chan.panelcasa.meteo.MeteoRepository
import com.n3k0chan.panelcasa.renfe.Board
import com.n3k0chan.panelcasa.renfe.BoardBuilder
import com.n3k0chan.panelcasa.renfe.RealtimeSnapshot
import com.n3k0chan.panelcasa.renfe.RenfeRepository
import com.n3k0chan.panelcasa.renfe.ServiceAlert
import com.n3k0chan.panelcasa.renfe.StationIndex
import com.n3k0chan.panelcasa.renfe.StationSchedule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/** Lo que recibe el panel web en window.panel.update(). */
@Serializable
data class PanelState(
    val renfe: Board?,
    val renfeStatus: Status,
    val meteo: Forecast?,
    val meteoStatus: Status,
) {
    @Serializable
    data class Status(val okAt: Long, val startedAt: Long, val message: String?)
}

/**
 * Obtiene y combina los datos. Los ciclos de red solo corren mientras el launcher está visible:
 * al abrir otra app se pausan y al volver se refresca todo de inmediato.
 * Todo el estado se toca en el hilo principal; el trabajo pesado va a Dispatchers.IO.
 */
class DataEngine(
    private val prefs: Prefs,
    private val renfe: RenfeRepository,
    private val meteo: MeteoRepository,
    private val diagnostics: Diagnostics,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val staticMutex = Mutex()
    private val startedAt = clock()

    private var schedule: StationSchedule? = null
    private var realtime: RealtimeSnapshot? = null
    private var realtimeOkAt = 0L
    private var alerts: List<ServiceAlert> = emptyList()
    private var alertsAt = 0L
    private var renfeMessage: String? = null
    private var meteoMessage: String? = null
    private var meteoOkAt = 0L
    private var loops: Job? = null

    private val _panel = MutableStateFlow("")
    /** JSON listo para el panel web. */
    val panel: StateFlow<String> = _panel

    private val _busy = MutableStateFlow<String?>(null)
    /** Tarea larga en curso (descarga o procesado del horario), para mostrarla en ajustes. */
    val busy: StateFlow<String?> = _busy

    fun init() {
        scope.launch {
            schedule = withContext(Dispatchers.IO) { renfe.loadSchedule() }
            if (schedule != null && schedule?.station?.id != prefs.stationId) schedule = null
            publish()
        }
    }

    val hasStation: Boolean get() = prefs.stationId != null
    val currentStation get() = schedule?.station

    fun onForeground() {
        if (loops?.isActive == true) return
        diagnostics.event("Panel en primer plano: refresco inmediato")
        loops = scope.launch {
            launch { staticLoop() }
            launch { realtimeLoop() }
            launch { alertsLoop() }
            launch { meteoLoop() }
        }
    }

    fun onBackground() {
        loops?.cancel()
        loops = null
    }

    /** Vuelve a calcular el panel con la hora actual y los últimos datos. */
    fun republish() {
        scope.launch { publish() }
    }

    // ---------- ciclos ----------

    private suspend fun staticLoop() {
        while (true) {
            val stale = schedule?.let { s -> BoardBuilder.upcoming(s, Instant.ofEpochMilli(clock()), null, 24 * 3600).isEmpty() } ?: true
            // Comprobación diaria; si el horario está caducado o falta, cada hora.
            refreshStatic(if (stale) HOUR_MS else RenfeRepository.DAY_MS)
            delay(HOUR_MS)
        }
    }

    private suspend fun realtimeLoop() {
        while (true) {
            if (schedule != null) {
                try {
                    val snap = withContext(Dispatchers.IO) { renfe.fetchRealtime() }
                    realtime = snap
                    realtimeOkAt = clock()
                    diagnostics.realtimeOkAt = realtimeOkAt
                } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                    diagnostics.error("tiempo real de Renfe", e)
                }
            }
            publish()
            delay(REALTIME_MS)
        }
    }

    private suspend fun alertsLoop() {
        while (true) {
            if (schedule != null) {
                try {
                    alerts = withContext(Dispatchers.IO) { renfe.fetchAlerts() }
                    alertsAt = clock()
                    publish()
                } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                    diagnostics.error("avisos de Renfe", e)
                    // Avisos de hace más de 30 min ya no se muestran.
                    if (clock() - alertsAt > 30 * 60_000L) alerts = emptyList()
                }
            }
            delay(ALERTS_MS)
        }
    }

    private suspend fun meteoLoop() {
        while (true) {
            refreshMeteo()
            delay(METEO_CHECK_MS)
        }
    }

    // ---------- operaciones ----------

    private suspend fun refreshStatic(minIntervalMs: Long) {
        val station = prefs.stationId
        staticMutex.withLock {
            val needsDownload = !renfe.hasZip()
            if (needsDownload) _busy.value = "Descargando el horario de Renfe…"
            try {
                val outcome = measuredScan { renfe.refreshStatic(station, minIntervalMs) }
                diagnostics.staticCheckedAt = renfe.meta().checkedAt
                if (outcome != RenfeRepository.StaticOutcome.SKIPPED) {
                    diagnostics.event("Horario de Renfe: $outcome (${renfe.meta().feedVersion})")
                }
                schedule = withContext(Dispatchers.IO) { renfe.loadSchedule() }?.takeIf { it.station.id == station }
                renfeMessage = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                diagnostics.error("horario de Renfe", e)
                if (schedule == null) renfeMessage = "No se pudo descargar el horario de Renfe. Se reintentará en una hora."
            } finally {
                _busy.value = null
            }
        }
        publish()
    }

    /** Índice de estaciones para el buscador; lo descarga si hace falta. */
    suspend fun ensureIndex(): StationIndex? {
        withContext(Dispatchers.IO) { renfe.loadIndex() }?.let { return it }
        refreshStatic(0)
        return withContext(Dispatchers.IO) { renfe.loadIndex() }
    }

    /** Cambia de estación reprocesando el horario guardado, sin descargar nada si ya lo hay. */
    fun changeStation(id: String, onDone: (Boolean) -> Unit) {
        scope.launch {
            var ok = false
            staticMutex.withLock {
                _busy.value = "Procesando el horario de la estación…"
                try {
                    measuredScan {
                        if (renfe.hasZip()) renfe.rescan(id) else renfe.refreshStatic(id, force = true)
                    }
                    prefs.stationId = id
                    schedule = withContext(Dispatchers.IO) { renfe.loadSchedule() }
                    realtime = null
                    renfeMessage = null
                    ok = schedule?.station?.id == id
                    diagnostics.event("Estación elegida: ${schedule?.station?.name} ($id)")
                } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                    diagnostics.error("cambio de estación", e)
                } finally {
                    _busy.value = null
                }
            }
            publish()
            onDone(ok)
            if (ok && loops?.isActive == true) {
                // Datos nuevos de inmediato: meteo en la nueva ubicación y tiempo real.
                launch { refreshMeteo() }
            }
        }
    }

    fun locationChanged() {
        scope.launch { refreshMeteo() }
    }

    private suspend fun refreshMeteo() {
        val (lat, lon) = prefs.manualCoords ?: schedule?.station?.let { it.lat to it.lon } ?: run {
            meteoMessage = "Elige una estación en ajustes para ver el tiempo."
            publish()
            return
        }
        try {
            val outcome = withContext(Dispatchers.IO) { meteo.refresh(lat, lon) }
            if (outcome != MeteoRepository.Outcome.SKIPPED) {
                meteoOkAt = clock()
                diagnostics.meteoOkAt = meteoOkAt
                diagnostics.event("Meteo: $outcome")
            }
            meteoMessage = null
        } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            diagnostics.error("meteo", e)
        }
        publish()
    }

    /** Ejecuta un procesado del horario midiendo tiempo y pico de heap. */
    private suspend fun <T> measuredScan(block: () -> T): T = withContext(Dispatchers.IO) {
        val rt = Runtime.getRuntime()
        val peak = AtomicLong(rt.totalMemory() - rt.freeMemory())
        val running = AtomicBoolean(true)
        val sampler = thread(isDaemon = true, name = "scan-mem") {
            while (running.get()) {
                peak.accumulateAndGet(rt.totalMemory() - rt.freeMemory()) { a, b -> maxOf(a, b) }
                Thread.sleep(50)
            }
        }
        val before = renfe.meta().lastScanAt
        try {
            block()
        } finally {
            running.set(false)
            sampler.join()
            val meta = renfe.meta()
            if (meta.lastScanAt != before) {
                diagnostics.lastScanMs = meta.lastScanMs
                diagnostics.lastScanAt = meta.lastScanAt
                diagnostics.lastScanPeakHeapMb = peak.get() / 1_048_576
                diagnostics.event("Horario procesado en ${meta.lastScanMs} ms, pico de heap ${peak.get() / 1_048_576} MB")
            }
        }
    }

    private suspend fun publish() {
        val now = clock()
        val s = schedule
        val stationId = prefs.stationId
        val rtFresh = now - realtimeOkAt < REALTIME_FRESH_MS
        val mode = prefs.displayMode
        val rt = realtime.takeIf { rtFresh }
        val alertList = alerts
        val manual = prefs.manualCoords
        val state = withContext(Dispatchers.Default) {
            val board = s?.let { BoardBuilder.build(it, Instant.ofEpochMilli(now), rt, alertList, mode, realtimeOk = rtFresh) }
            val zone = runCatching { ZoneId.of(s?.timezone ?: "Europe/Madrid") }.getOrDefault(ZoneId.systemDefault())
            val forecast = if (manual != null || s != null) meteo.forecast(Instant.ofEpochMilli(now), zone) else null
            PanelState(
                renfe = board,
                renfeStatus = PanelState.Status(
                    realtimeOkAt, startedAt,
                    when {
                        stationId == null -> "Elige una estación en ajustes (rueda dentada del panel lateral)."
                        s == null -> renfeMessage ?: _busy.value ?: "Preparando el horario de Renfe…"
                        else -> renfeMessage
                    },
                ),
                meteo = forecast,
                meteoStatus = PanelState.Status(meteoOkAt, startedAt, meteoMessage),
            )
        }
        _panel.value = json.encodeToString(PanelState.serializer(), state)
    }

    companion object {
        const val REALTIME_MS = 60_000L
        const val REALTIME_FRESH_MS = 3 * 60_000L
        const val ALERTS_MS = 5 * 60_000L
        const val METEO_CHECK_MS = 5 * 60_000L
        const val HOUR_MS = 3600_000L
        private val json = Json { encodeDefaults = true }
    }
}
