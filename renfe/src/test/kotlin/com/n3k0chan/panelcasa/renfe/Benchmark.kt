package com.n3k0chan.panelcasa.renfe

import java.io.File
import java.time.Instant
import kotlin.concurrent.thread

/**
 * Mide el procesado del GTFS real: ./gradlew :renfe:benchmark -Pzip=<ruta> -Pstation=<stop_id>
 * Se ejecuta con 256 MB de heap, el límite habitual de una app en una tablet de 2 GB.
 */
fun main(args: Array<String>) {
    val zip = File(args.getOrNull(0)?.takeIf { it.isNotEmpty() } ?: error("Falta -Pzip"))
    val station = args.getOrNull(1)?.takeIf { it.isNotEmpty() }
    val rt = Runtime.getRuntime()
    System.gc()
    val base = rt.totalMemory() - rt.freeMemory()
    val peakRef = java.util.concurrent.atomic.AtomicLong(base)
    val running = java.util.concurrent.atomic.AtomicBoolean(true)
    val sampler = thread(isDaemon = true) {
        while (running.get()) {
            peakRef.accumulateAndGet(rt.totalMemory() - rt.freeMemory()) { a, b -> maxOf(a, b) }
            Thread.sleep(5)
        }
    }
    val t0 = System.nanoTime()
    val result = GtfsScanner().scan(zip, station, "benchmark")
    val ms = (System.nanoTime() - t0) / 1_000_000
    running.set(false)
    sampler.join()
    val peak = peakRef.get()
    println("Tiempo: $ms ms")
    println("Memoria: base ${base / 1_048_576} MB, pico ${peak / 1_048_576} MB (heap máx ${rt.maxMemory() / 1_048_576} MB)")
    println("Estaciones con servicio: ${result.index.stations.size}")
    val s = result.schedule ?: return
    println("Estación: ${s.station.name} (${s.station.id}) · líneas ${s.station.lines} · redes ${s.station.networks}")
    println("Salidas en el fichero: ${s.departures.size} · servicios ${s.services.size}")
    println("Sentidos: " + s.groups.joinToString { "${s.stopNames[it.destination]} (${it.count})" })
    println("Modo automático: ${BoardBuilder.effectiveMode(s, DisplayMode.AUTO)}")
    val json = RenfeRepository.json.encodeToString(StationSchedule.serializer(), s)
    println("Tamaño del horario guardado: ${json.length / 1024} KB")
    val board = BoardBuilder.build(s, Instant.now(), null, emptyList(), DisplayMode.AUTO)
    for (b in board.blocks) {
        println("— ${b.title}")
        for (t in b.trains.take(b.show)) println("   ${Instant.ofEpochMilli(t.scheduled)}  ${t.line}  ${t.destination}")
    }
}
