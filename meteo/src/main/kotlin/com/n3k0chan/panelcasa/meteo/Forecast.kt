package com.n3k0chan.panelcasa.meteo

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

@Serializable
data class Current(val temp: Double, val symbol: String)

/** [time] en milisegundos Unix; [precip] en mm durante la hora. */
@Serializable
data class Hour(val time: Long, val temp: Double, val symbol: String, val precip: Double)

/** [date] en formato ISO (yyyy-mm-dd), en la zona horaria local. */
@Serializable
data class Day(val date: String, val min: Double, val max: Double, val symbol: String, val precip: Double)

@Serializable
data class Forecast(
    val updatedAt: Long,
    val current: Current?,
    val hours: List<Hour>,
    val days: List<Day>,
)

/** Convierte la respuesta «compact» de Locationforecast 2.0 en lo que muestra el panel. */
object MetParser {
    private val json = Json { ignoreUnknownKeys = true }

    private class Entry(
        val time: Instant,
        val temp: Double?,
        val sym1: String?,
        val sym6: String?,
        val sym12: String?,
        val precip1: Double?,
        val precip6: Double?,
    )

    @Throws(IOException::class)
    fun parse(body: String, now: Instant, zone: ZoneId, hours: Int = 6, days: Int = 4): Forecast {
        val root = try {
            json.parseToJsonElement(body).jsonObject
        } catch (e: Exception) {
            throw IOException("Respuesta de Met.no no válida", e)
        }
        val props = root["properties"]?.jsonObject ?: throw IOException("Met.no: falta properties")
        val updatedAt = props["meta"]?.jsonObject?.get("updated_at")?.jsonPrimitive?.content
            ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L
        val entries = props["timeseries"]?.jsonArray?.mapNotNull { el ->
            val o = el.jsonObject
            val time = runCatching { Instant.parse(o["time"]!!.jsonPrimitive.content) }.getOrNull() ?: return@mapNotNull null
            val data = o["data"]?.jsonObject ?: return@mapNotNull null
            Entry(
                time = time,
                temp = data.path("instant", "details")?.num("air_temperature"),
                sym1 = data.path("next_1_hours", "summary")?.str("symbol_code"),
                sym6 = data.path("next_6_hours", "summary")?.str("symbol_code"),
                sym12 = data.path("next_12_hours", "summary")?.str("symbol_code"),
                precip1 = data.path("next_1_hours", "details")?.num("precipitation_amount"),
                precip6 = data.path("next_6_hours", "details")?.num("precipitation_amount"),
            )
        }?.sortedBy { it.time } ?: throw IOException("Met.no: falta timeseries")
        if (entries.isEmpty()) throw IOException("Met.no: sin datos")

        // Actual: la última entrada que ya ha empezado (o la primera si todas son futuras).
        val currentEntry = entries.lastOrNull { !it.time.isAfter(now) } ?: entries.first()
        val current = currentEntry.temp?.let { t ->
            (currentEntry.sym1 ?: currentEntry.sym6 ?: currentEntry.sym12)?.let { Current(t, it) }
        }

        val hourList = entries.asSequence()
            .filter { it.time.isAfter(currentEntry.time) && it.sym1 != null && it.temp != null }
            .take(hours)
            .map { Hour(it.time.toEpochMilli(), it.temp!!, it.sym1!!, it.precip1 ?: 0.0) }
            .toList()

        val today = now.atZone(zone).toLocalDate()
        val dayList = (0 until days).mapNotNull { offset ->
            buildDay(entries, today.plusDays(offset.toLong()), zone, if (offset == 0) currentEntry.time else null)
        }

        return Forecast(updatedAt, current, hourList, dayList)
    }

    private fun buildDay(entries: List<Entry>, date: LocalDate, zone: ZoneId, from: Instant?): Day? {
        val inDay = entries.filter {
            it.time.atZone(zone).toLocalDate() == date && (from == null || !it.time.isBefore(from))
        }
        val temps = inDay.mapNotNull { it.temp }
        if (temps.isEmpty()) return null
        // Símbolo del día: el periodo de 6 h que empieza más cerca del mediodía local.
        val noon = date.atTime(12, 0).atZone(zone).toInstant()
        val symbol = inDay.filter { it.sym6 != null }.minByOrNull { abs(it.time.epochSecond - noon.epochSecond) }?.sym6
            ?: inDay.firstNotNullOfOrNull { it.sym12 }
            ?: inDay.firstNotNullOfOrNull { it.sym1 }
            ?: return null
        return Day(date.toString(), temps.min(), temps.max(), daySymbol(symbol), precipitation(inDay))
    }

    /** Suma la precipitación cubriendo el día sin solapes: bloques de 6 h si los hay, horas sueltas si no. */
    private fun precipitation(inDay: List<Entry>): Double {
        var total = 0.0
        var cursor = Instant.MIN
        for (e in inDay) {
            if (e.time.isBefore(cursor)) continue
            when {
                e.precip6 != null && e.time.epochSecond % (6 * 3600) == 0L -> { total += e.precip6; cursor = e.time.plusSeconds(6 * 3600) }
                e.precip1 != null -> { total += e.precip1; cursor = e.time.plusSeconds(3600) }
                e.precip6 != null -> { total += e.precip6; cursor = e.time.plusSeconds(6 * 3600) }
            }
        }
        return Math.round(total * 10) / 10.0
    }

    /** Para la previsión diaria siempre se usa la variante de día. */
    fun daySymbol(symbol: String): String = symbol.removeSuffix("_night").removeSuffix("_polartwilight").let {
        if (it != symbol) it + "_day" else symbol
    }

    private fun JsonObject.path(vararg keys: String): JsonObject? {
        var o: JsonObject = this
        for (k in keys) o = o[k] as? JsonObject ?: return null
        return o
    }

    private fun JsonObject.num(key: String): Double? = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.doubleOrNull
    private fun JsonObject.str(key: String): String? = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.content
}
