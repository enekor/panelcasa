package com.n3k0chan.panelcasa.renfe

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Las muestras .pb y .json se descargaron de gtfsrt.renfe.com con la misma marca de tiempo de cabecera,
 * así que el decodificador debe obtener exactamente lo mismo que dice el JSON oficial.
 */
class GtfsRealtimeTest {
    private fun res(name: String) = javaClass.getResourceAsStream("/rt/$name")!!.use { it.readBytes() }
    private fun json(name: String) = Json.parseToJsonElement(String(res(name), Charsets.UTF_8)).jsonObject

    @Test
    fun tripUpdatesMatchOfficialJson() {
        val snap = GtfsRealtime.parseTripUpdates(res("trip_updates.pb"))
        val js = json("trip_updates.json")
        assertEquals(js["header"]!!.jsonObject["timestamp"]!!.jsonPrimitive.long, snap.timestamp)
        val entities = js["entity"]!!.jsonArray.map { it.jsonObject }.filter { "tripUpdate" in it }
        assertEquals(entities.size, snap.trips.size)
        for (e in entities) {
            val tu = e["tripUpdate"]!!.jsonObject
            val trip = tu["trip"]!!.jsonObject
            val id = trip["tripId"]!!.jsonPrimitive.content
            val expectedDelay = tu["delay"]?.jsonPrimitive?.int ?: firstStopDelay(tu) ?: 0
            val canceled = trip["scheduleRelationship"]?.jsonPrimitive?.content == "CANCELED"
            assertEquals("Retraso de $id", TripRealtime(expectedDelay, canceled), snap.trips[id])
        }
        assertTrue("La muestra debe incluir retrasos negativos para probar varints de 10 bytes", snap.trips.values.any { it.delaySec < 0 })
    }

    private fun firstStopDelay(tu: JsonObject): Int? {
        val stu = tu["stopTimeUpdate"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        return (stu["arrival"] ?: stu["departure"])?.jsonObject?.get("delay")?.jsonPrimitive?.int
    }

    @Test
    fun alertsMatchOfficialJson() {
        val alerts = GtfsRealtime.parseAlerts(res("alerts.pb"))
        val entities = json("alerts.json")["entity"]!!.jsonArray.map { it.jsonObject }.filter { "alert" in it }
        assertEquals(entities.size, alerts.size)
        // Renfe repite identificadores entre avisos, así que se compara por posición.
        for ((i, e) in entities.withIndex()) {
            val id = e["id"]!!.jsonPrimitive.content
            val a = e["alert"]!!.jsonObject
            val got = alerts[i]
            assertEquals(id, got.id)
            val routes = a["informedEntity"]?.jsonArray?.mapNotNull { it.jsonObject["routeId"]?.jsonPrimitive?.content?.trim() } ?: emptyList()
            assertEquals(routes, got.routeIds)
            val text = a["descriptionText"]!!.jsonObject["translation"]!!.jsonArray.first().jsonObject["text"]!!.jsonPrimitive.content
            assertEquals(text.trim(), got.text)
            val period = a["activePeriod"]?.jsonArray?.firstOrNull()?.jsonObject
            assertEquals(period?.get("start")?.jsonPrimitive?.long ?: 0L, got.start)
        }
    }

    @Test
    fun negativeVarintDecodesAsInt32() {
        // TripUpdate{ trip{ trip_id:"x" }, delay:-60 } dentro de FeedEntity{ id:"e", trip_update }.
        val delay = varint(-60L)
        val trip = byteArrayOf(0x0A, 1, 'x'.code.toByte())
        val tripUpdate = byteArrayOf(0x0A, trip.size.toByte()) + trip + byteArrayOf(0x28) + delay
        val entity = byteArrayOf(0x0A, 1, 'e'.code.toByte(), 0x1A, tripUpdate.size.toByte()) + tripUpdate
        val feed = byteArrayOf(0x12, entity.size.toByte()) + entity
        assertEquals(-60, GtfsRealtime.parseTripUpdates(feed).trips.getValue("x").delaySec)
    }

    @Test(expected = IOException::class)
    fun truncatedFeedFails() {
        val bytes = res("trip_updates.pb")
        GtfsRealtime.parseTripUpdates(bytes.copyOf(bytes.size / 2))
    }

    @Test(expected = IOException::class)
    fun garbageFails() {
        GtfsRealtime.parseTripUpdates("<html>error</html>".toByteArray())
    }

    private fun varint(v: Long): ByteArray {
        val out = ArrayList<Byte>()
        var x = v
        while (true) {
            val b = (x and 0x7F).toInt()
            x = x ushr 7
            if (x == 0L) { out.add(b.toByte()); break }
            out.add((b or 0x80).toByte())
        }
        return out.toByteArray()
    }
}
