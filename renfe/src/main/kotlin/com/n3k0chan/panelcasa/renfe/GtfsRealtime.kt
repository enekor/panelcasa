package com.n3k0chan.panelcasa.renfe

import java.io.IOException

/** Estado en tiempo real de un viaje. */
data class TripRealtime(val delaySec: Int, val canceled: Boolean)

/** Actualizaciones de viaje de un feed GTFS Realtime. [timestamp] en segundos Unix. */
data class RealtimeSnapshot(val timestamp: Long, val trips: Map<String, TripRealtime>)

/** Aviso del servicio. Tiempos en segundos Unix; 0 = sin límite. */
data class ServiceAlert(
    val id: String,
    val routeIds: List<String>,
    val stopIds: List<String>,
    val text: String,
    val start: Long,
    val end: Long,
) {
    fun activeAt(epochSec: Long): Boolean = (start == 0L || epochSec >= start) && (end == 0L || epochSec <= end)
}

/**
 * Decodificador mínimo de GTFS Realtime (protobuf) para los campos que usa el panel.
 * Ignora todo lo demás, así que tolera campos nuevos o extensiones.
 */
object GtfsRealtime {
    private const val SCHEDULE_CANCELED = 3L

    @Throws(IOException::class)
    fun parseTripUpdates(bytes: ByteArray): RealtimeSnapshot {
        var timestamp = 0L
        val trips = HashMap<String, TripRealtime>()
        val r = ProtoReader(bytes)
        while (r.hasMore()) {
            val tag = r.readTag()
            when (tag.field) {
                1 -> if (tag.wire == ProtoReader.LEN) timestamp = parseHeaderTimestamp(r.sub()) else r.skip(tag.wire)
                2 -> if (tag.wire == ProtoReader.LEN) parseEntityTrip(r.sub(), trips) else r.skip(tag.wire)
                else -> r.skip(tag.wire)
            }
        }
        return RealtimeSnapshot(timestamp, trips)
    }

    @Throws(IOException::class)
    fun parseAlerts(bytes: ByteArray): List<ServiceAlert> {
        val out = ArrayList<ServiceAlert>()
        val r = ProtoReader(bytes)
        while (r.hasMore()) {
            val tag = r.readTag()
            if (tag.field == 2 && tag.wire == ProtoReader.LEN) parseEntityAlert(r.sub())?.let(out::add) else r.skip(tag.wire)
        }
        return out
    }

    private fun parseHeaderTimestamp(r: ProtoReader): Long {
        var ts = 0L
        while (r.hasMore()) {
            val tag = r.readTag()
            if (tag.field == 3 && tag.wire == ProtoReader.VARINT) ts = r.readVarint() else r.skip(tag.wire)
        }
        return ts
    }

    private fun parseEntityTrip(r: ProtoReader, out: MutableMap<String, TripRealtime>) {
        while (r.hasMore()) {
            val tag = r.readTag()
            if (tag.field == 3 && tag.wire == ProtoReader.LEN) parseTripUpdate(r.sub(), out) else r.skip(tag.wire)
        }
    }

    private fun parseTripUpdate(r: ProtoReader, out: MutableMap<String, TripRealtime>) {
        var tripId: String? = null
        var canceled = false
        var tripDelay: Int? = null
        var stopDelay: Int? = null
        while (r.hasMore()) {
            val tag = r.readTag()
            when {
                tag.field == 1 && tag.wire == ProtoReader.LEN -> {
                    val t = r.sub()
                    while (t.hasMore()) {
                        val tt = t.readTag()
                        when {
                            tt.field == 1 && tt.wire == ProtoReader.LEN -> tripId = t.readString()
                            tt.field == 4 && tt.wire == ProtoReader.VARINT -> canceled = t.readVarint() == SCHEDULE_CANCELED
                            else -> t.skip(tt.wire)
                        }
                    }
                }
                tag.field == 2 && tag.wire == ProtoReader.LEN -> {
                    val d = parseStopTimeUpdateDelay(r.sub())
                    if (stopDelay == null && d != null) stopDelay = d
                }
                tag.field == 5 && tag.wire == ProtoReader.VARINT -> tripDelay = r.readVarint().toInt()
                else -> r.skip(tag.wire)
            }
        }
        val id = tripId?.trim() ?: return
        out[id] = TripRealtime(tripDelay ?: stopDelay ?: 0, canceled)
    }

    /** Retraso de la primera StopTimeUpdate: llegada o, si no hay, salida. */
    private fun parseStopTimeUpdateDelay(r: ProtoReader): Int? {
        var arrival: Int? = null
        var departure: Int? = null
        while (r.hasMore()) {
            val tag = r.readTag()
            when {
                tag.field == 2 && tag.wire == ProtoReader.LEN -> arrival = parseEventDelay(r.sub())
                tag.field == 3 && tag.wire == ProtoReader.LEN -> departure = parseEventDelay(r.sub())
                else -> r.skip(tag.wire)
            }
        }
        return arrival ?: departure
    }

    private fun parseEventDelay(r: ProtoReader): Int? {
        var delay: Int? = null
        while (r.hasMore()) {
            val tag = r.readTag()
            if (tag.field == 1 && tag.wire == ProtoReader.VARINT) delay = r.readVarint().toInt() else r.skip(tag.wire)
        }
        return delay
    }

    private fun parseEntityAlert(r: ProtoReader): ServiceAlert? {
        var id = ""
        var alert: ServiceAlert? = null
        while (r.hasMore()) {
            val tag = r.readTag()
            when {
                tag.field == 1 && tag.wire == ProtoReader.LEN -> id = r.readString()
                tag.field == 5 && tag.wire == ProtoReader.LEN -> alert = parseAlert(r.sub())
                else -> r.skip(tag.wire)
            }
        }
        return alert?.copy(id = id)
    }

    private fun parseAlert(r: ProtoReader): ServiceAlert {
        var start = 0L
        var end = 0L
        var periods = 0
        val routes = ArrayList<String>()
        val stops = ArrayList<String>()
        var header: String? = null
        var description: String? = null
        while (r.hasMore()) {
            val tag = r.readTag()
            when {
                tag.field == 1 && tag.wire == ProtoReader.LEN -> {
                    val t = r.sub()
                    var s = 0L
                    var e = 0L
                    while (t.hasMore()) {
                        val tt = t.readTag()
                        when {
                            tt.field == 1 && tt.wire == ProtoReader.VARINT -> s = t.readVarint()
                            tt.field == 2 && tt.wire == ProtoReader.VARINT -> e = t.readVarint()
                            else -> t.skip(tt.wire)
                        }
                    }
                    // Si hay varios periodos, se toma la envolvente; 0 significa abierto.
                    if (periods == 0) {
                        start = s
                        end = e
                    } else {
                        start = if (start == 0L || s == 0L) 0L else minOf(start, s)
                        end = if (end == 0L || e == 0L) 0L else maxOf(end, e)
                    }
                    periods++
                }
                tag.field == 5 && tag.wire == ProtoReader.LEN -> {
                    val t = r.sub()
                    while (t.hasMore()) {
                        val tt = t.readTag()
                        when {
                            tt.field == 2 && tt.wire == ProtoReader.LEN -> routes.add(t.readString().trim())
                            tt.field == 5 && tt.wire == ProtoReader.LEN -> stops.add(t.readString().trim())
                            else -> t.skip(tt.wire)
                        }
                    }
                }
                tag.field == 10 && tag.wire == ProtoReader.LEN -> header = parseTranslated(r.sub())
                tag.field == 11 && tag.wire == ProtoReader.LEN -> description = parseTranslated(r.sub())
                else -> r.skip(tag.wire)
            }
        }
        return ServiceAlert("", routes, stops, (description ?: header ?: "").trim(), start, end)
    }

    /** Texto en español si lo hay; si no, el primero. */
    private fun parseTranslated(r: ProtoReader): String? {
        var first: String? = null
        var spanish: String? = null
        while (r.hasMore()) {
            val tag = r.readTag()
            if (tag.field == 1 && tag.wire == ProtoReader.LEN) {
                val t = r.sub()
                var text: String? = null
                var lang: String? = null
                while (t.hasMore()) {
                    val tt = t.readTag()
                    when {
                        tt.field == 1 && tt.wire == ProtoReader.LEN -> text = t.readString()
                        tt.field == 2 && tt.wire == ProtoReader.LEN -> lang = t.readString()
                        else -> t.skip(tt.wire)
                    }
                }
                if (text != null) {
                    if (first == null) first = text
                    // Renfe etiqueta como «es» también el valenciano y el catalán, siempre después del castellano.
                    if (spanish == null && lang != null && lang.startsWith("es")) spanish = text
                }
            } else {
                r.skip(tag.wire)
            }
        }
        return spanish ?: first
    }
}

/** Lector de formato de cable protobuf con comprobación de límites. */
class ProtoReader(private val buf: ByteArray, private var pos: Int = 0, private val limit: Int = buf.size) {
    class Tag(val field: Int, val wire: Int)

    fun hasMore(): Boolean = pos < limit

    fun readTag(): Tag {
        val v = readVarint()
        val field = (v ushr 3).toInt()
        if (field <= 0) throw IOException("Etiqueta protobuf inválida")
        return Tag(field, (v and 7).toInt())
    }

    fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (shift < 64) {
            if (pos >= limit) throw IOException("Varint truncado")
            val b = buf[pos++].toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
        }
        throw IOException("Varint demasiado largo")
    }

    private fun readLength(): Int {
        val len = readVarint()
        if (len < 0 || len > limit - pos) throw IOException("Longitud protobuf fuera de rango")
        return len.toInt()
    }

    fun sub(): ProtoReader {
        val len = readLength()
        val r = ProtoReader(buf, pos, pos + len)
        pos += len
        return r
    }

    fun readString(): String {
        val len = readLength()
        val s = String(buf, pos, len, Charsets.UTF_8)
        pos += len
        return s
    }

    fun skip(wire: Int) {
        when (wire) {
            VARINT -> readVarint()
            I64 -> advance(8)
            LEN -> advance(readLength())
            I32 -> advance(4)
            else -> throw IOException("Tipo de cable protobuf no soportado: $wire")
        }
    }

    private fun advance(n: Int) {
        if (n > limit - pos) throw IOException("Mensaje protobuf truncado")
        pos += n
    }

    companion object {
        const val VARINT = 0
        const val I64 = 1
        const val LEN = 2
        const val I32 = 5
    }
}
