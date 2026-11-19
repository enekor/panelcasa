package com.n3k0chan.panelcasa.renfe

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class DisplayMode { AUTO, A, B }

/** Tren tal como lo pinta el panel. Instantes en milisegundos Unix. */
@Serializable
data class Train(
    val tripId: String,
    val line: String,
    val color: String,
    val textColor: String,
    val destination: String,
    val scheduled: Long,
    val predicted: Long,
    val delayMin: Int,
    val realtime: Boolean,
    val canceled: Boolean,
)

@Serializable
data class Block(val title: String, val show: Int, val trains: List<Train>)

@Serializable
data class Board(
    val station: String,
    /** «A»: un bloque por sentido. «B»: una lista única. */
    val mode: String,
    val blocks: List<Block>,
    val alerts: List<String>,
    /** El feed de tiempo real respondió hace poco. */
    val realtimeOk: Boolean,
    /** Hay salidas en las próximas 24 h; si no, el horario está caducado. */
    val scheduleOk: Boolean,
)

object BoardBuilder {
    /** Trenes que se mandan de más para que el panel pueda descartar los que ya salieron sin esperar datos. */
    private const val SPARE = 3
    private const val LEAVING_GRACE_MS = 60_000L

    fun effectiveMode(schedule: StationSchedule, mode: DisplayMode): String = when (mode) {
        DisplayMode.A -> "A"
        DisplayMode.B -> "B"
        DisplayMode.AUTO -> if (schedule.groups.size == 2) "A" else "B"
    }

    fun build(
        schedule: StationSchedule,
        now: Instant,
        realtime: RealtimeSnapshot?,
        alerts: List<ServiceAlert>,
        mode: DisplayMode,
        realtimeOk: Boolean = realtime != null,
        perDirection: Int = 2,
        singleList: Int = 4,
    ): Board {
        val effective = effectiveMode(schedule, mode)
        val upcoming = upcoming(schedule, now, realtime, 36L * 3600)
        val nowMs = now.toEpochMilli()
        val scheduleOk = upcoming.any { it.second.scheduled - nowMs < 24L * 3600 * 1000 }

        val blocks = if (effective == "A" && schedule.groups.isNotEmpty()) {
            schedule.groups.take(2).map { g ->
                val trains = upcoming.filter { it.first.group == g.id }.take(perDirection + SPARE).map { it.second }
                Block("Hacia " + (schedule.stopNames[g.destination] ?: g.destination), perDirection, trains)
            }
        } else {
            listOf(Block("Próximos trenes", singleList, upcoming.take(singleList + SPARE).map { it.second }))
        }

        return Board(
            station = schedule.station.name,
            mode = effective,
            blocks = blocks,
            alerts = alertsFor(schedule, alerts, now.epochSecond),
            realtimeOk = realtimeOk,
            scheduleOk = scheduleOk,
        )
    }

    /** Salidas desde ahora hasta [horizonSec], ordenadas por hora prevista. */
    fun upcoming(
        schedule: StationSchedule,
        now: Instant,
        realtime: RealtimeSnapshot?,
        horizonSec: Long,
    ): List<Pair<Departure, Train>> {
        val zone = ZoneId.of(schedule.timezone)
        val today = now.atZone(zone).toLocalDate()
        val nowMs = now.toEpochMilli()
        val limitMs = nowMs + horizonSec * 1000
        val out = ArrayList<Pair<Departure, Train>>()
        // Ayer incluido: los servicios que pasan de medianoche usan horas de 24:00 en adelante.
        for (offset in -1L..1L) {
            val day = today.plusDays(offset)
            val active = BooleanArray(schedule.services.size) { schedule.services[it].activeOn(day) }
            val baseMs = serviceDayStart(day, zone)
            for (d in schedule.departures) {
                if (!active[d.service]) continue
                val scheduled = baseMs + d.time * 1000L
                val rt = realtime?.trips?.get(d.tripId)
                val delay = rt?.delaySec ?: 0
                val predicted = scheduled + delay * 1000L
                if (predicted < nowMs - LEAVING_GRACE_MS || scheduled > limitMs) continue
                val route = schedule.routes[d.route]
                out.add(
                    d to Train(
                        tripId = d.tripId,
                        line = route.shortName,
                        color = route.color,
                        textColor = route.textColor,
                        destination = schedule.stopNames[d.dest] ?: d.dest,
                        scheduled = scheduled,
                        predicted = predicted,
                        delayMin = Math.round(delay / 60.0).toInt(),
                        realtime = rt != null,
                        canceled = rt?.canceled == true,
                    ),
                )
            }
        }
        out.sortWith(compareBy({ it.second.predicted }, { it.second.tripId }))
        return out
    }

    /** GTFS mide las horas desde «mediodía menos 12 h», que difiere de medianoche los días de cambio de hora. */
    fun serviceDayStart(day: LocalDate, zone: ZoneId): Long =
        day.atTime(12, 0).atZone(zone).minusHours(12).toInstant().toEpochMilli()

    /** Prefijos de Renfe como «#MadC4a » o «#AVISO Línea C6# ». */
    private val hashtag = Regex("^#(?:[^#\\n]{1,40}#|\\S+)\\s*")

    fun alertsFor(schedule: StationSchedule, alerts: List<ServiceAlert>, nowSec: Long): List<String> {
        val routeIds = schedule.routes.map { it.id }.toHashSet()
        val stationId = schedule.station.id
        return alerts.asSequence()
            .filter { it.activeAt(nowSec) }
            .filter { a -> a.routeIds.any { it in routeIds } || stationId in a.stopIds }
            .map { it.text.replace(hashtag, "").replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
    }
}
