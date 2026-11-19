package com.n3k0chan.panelcasa.renfe

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate

/** Estación con servicio, tal como aparece en el buscador. */
@Serializable
data class Station(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    /** Redes de Cercanías que paran aquí, p. ej. «Madrid». */
    val networks: List<String>,
    /** Líneas que paran aquí, p. ej. «C2», «C7». */
    val lines: List<String>,
)

@Serializable
data class StationIndex(
    val feedVersion: String,
    val stations: List<Station>,
)

/** Calendario de un servicio GTFS. [days]: bit 0 = lunes … bit 6 = domingo. Fechas como yyyymmdd. */
@Serializable
data class Service(
    val id: String,
    val days: Int,
    val start: Int,
    val end: Int,
    val added: List<Int> = emptyList(),
    val removed: List<Int> = emptyList(),
) {
    fun activeOn(date: LocalDate): Boolean {
        val ymd = date.toYmd()
        if (ymd in removed) return false
        if (ymd in added) return true
        if (ymd < start || ymd > end) return false
        return days and (1 shl dayBit(date.dayOfWeek)) != 0
    }

    companion object {
        fun dayBit(d: DayOfWeek): Int = d.value - 1
    }
}

fun LocalDate.toYmd(): Int = year * 10000 + monthValue * 100 + dayOfMonth

@Serializable
data class RouteInfo(
    val id: String,
    val shortName: String,
    val color: String,
    val textColor: String,
)

/**
 * Salida de un tren desde la estación.
 * [time]: segundos desde «mediodía menos 12 h» del día de servicio (puede pasar de 24 h).
 */
@Serializable
data class Departure(
    val tripId: String,
    val service: Int,
    val route: Int,
    val time: Int,
    val next: String,
    val dest: String,
    val group: Int,
)

/** Sentido deducido del recorrido. [destination]: destino más frecuente del grupo. */
@Serializable
data class DirectionGroup(
    val id: Int,
    val destination: String,
    val count: Int,
)

/** Todo lo necesario para calcular los próximos trenes de una estación, sin volver a leer el GTFS. */
@Serializable
data class StationSchedule(
    val feedVersion: String,
    val timezone: String,
    val station: Station,
    val services: List<Service>,
    val routes: List<RouteInfo>,
    val stopNames: Map<String, String>,
    val departures: List<Departure>,
    val groups: List<DirectionGroup>,
)

/** Códigos de red según el prefijo de route_id del GTFS de Renfe Cercanías. */
object Networks {
    private val names = mapOf(
        "10" to "Madrid",
        "20" to "Asturias",
        "30" to "Sevilla",
        "31" to "Cádiz",
        "32" to "Málaga",
        "40" to "Valencia",
        "41" to "Murcia/Alicante",
        "45" to "Cartagena",
        "46" to "Ferrol",
        "47" to "León",
        "51" to "Rodalies de Catalunya",
        "60" to "Bilbao",
        "61" to "San Sebastián",
        "62" to "Santander",
        "70" to "Zaragoza",
    )

    fun fromRouteId(routeId: String): String = names[routeId.take(2)] ?: "Cercanías"
}
