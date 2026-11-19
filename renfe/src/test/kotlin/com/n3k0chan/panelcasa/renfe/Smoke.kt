package com.n3k0chan.panelcasa.renfe

import com.n3k0chan.panelcasa.net.HttpFetcher
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Prueba de extremo a extremo contra los servidores reales de Renfe (necesita red):
 * ./gradlew :renfe:smoke -Pstation=<stop_id>
 */
fun main(args: Array<String>) {
    val station = args.getOrNull(0)?.takeIf { it.isNotEmpty() } ?: "70108"
    val dir = Files.createTempDirectory("renfe-smoke").toFile()
    val repo = RenfeRepository(dir, HttpFetcher("pepe/1.0 pepe42rdh@hotmail.com"))
    val t0 = System.nanoTime()
    println("Horario: " + repo.refreshStatic(station) + " en ${(System.nanoTime() - t0) / 1_000_000} ms (descarga + procesado), versión ${repo.meta().feedVersion}")
    val schedule = repo.loadSchedule()!!
    val rt = repo.fetchRealtime()
    val alerts = repo.fetchAlerts()
    println("Tiempo real: ${rt.trips.size} viajes, marca ${Instant.ofEpochSecond(rt.timestamp)}; avisos: ${alerts.size}")
    val board = BoardBuilder.build(schedule, Instant.now(), rt, alerts, DisplayMode.AUTO)
    val fmt = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Europe/Madrid"))
    println("${board.station} · modo ${board.mode} · tiempo real ${board.realtimeOk} · horario válido ${board.scheduleOk}")
    for (b in board.blocks) {
        println("— ${b.title}")
        for (t in b.trains.take(b.show)) {
            val state = when {
                t.canceled -> "SUPRIMIDO"
                t.realtime -> "tiempo real, retraso ${t.delayMin} min"
                else -> "teórico"
            }
            println("   ${fmt.format(Instant.ofEpochMilli(t.scheduled))} → ${fmt.format(Instant.ofEpochMilli(t.predicted))}  ${t.line}  ${t.destination}  ($state)")
        }
    }
    board.alerts.forEach { println("Aviso: $it") }
    val upcoming = BoardBuilder.upcoming(schedule, Instant.now(), rt, 3 * 3600)
    println("Próximas 3 h: ${upcoming.size} salidas, ${upcoming.count { it.second.realtime }} con tiempo real")
    dir.deleteRecursively()
}
