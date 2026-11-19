package com.n3k0chan.panelcasa.renfe

import java.io.File
import java.io.IOException
import java.util.BitSet
import java.util.zip.ZipFile

/**
 * Lee el GTFS de Cercanías en una sola pasada por stop_times.txt (el fichero grande, ~270 MB sin comprimir)
 * sin descomprimirlo a disco ni cargarlo en memoria.
 *
 * Produce a la vez el índice de estaciones para el buscador y, si se indica [stationId],
 * el horario de esa estación con los sentidos deducidos del recorrido.
 */
class GtfsScanner(
    /** Paradas siguientes que se usan para decidir si dos trenes van en el mismo sentido. */
    private val downstreamWindow: Int = 8,
) {
    class Result(val index: StationIndex, val schedule: StationSchedule?)

    @Throws(IOException::class)
    fun scan(zip: File, stationId: String?, feedVersion: String): Result = ZipFile(zip).use { zf ->
        fun open(name: String) = zf.getEntry(name)?.let { zf.getInputStream(it) }
        fun require(name: String) = open(name) ?: throw IOException("Falta $name en el GTFS")

        // agency.txt: zona horaria
        var timezone = "Europe/Madrid"
        open("agency.txt")?.let { input ->
            readCsv(input) { h, r -> h["agency_timezone"]?.let { if (!r.isEmpty(it)) timezone = r.str(it) } }
        }

        // stops.txt
        val stopIndex = HashMap<String, Int>()
        val stopIds = ArrayList<String>()
        val stopNames = ArrayList<String>()
        val stopLat = ArrayList<Double>()
        val stopLon = ArrayList<Double>()
        readCsv(require("stops.txt")) { h, r ->
            val id = r.str(h.getValue("stop_id"))
            if (id.isEmpty() || id in stopIndex) return@readCsv
            stopIndex[id] = stopIds.size
            stopIds.add(id)
            stopNames.add(r.str(h["stop_name"] ?: -1).ifEmpty { id })
            stopLat.add(r.double(h["stop_lat"] ?: -1))
            stopLon.add(r.double(h["stop_lon"] ?: -1))
        }

        // routes.txt
        val routeIndex = HashMap<String, Int>()
        val routes = ArrayList<RouteInfo>()
        readCsv(require("routes.txt")) { h, r ->
            val id = r.str(h.getValue("route_id"))
            if (id.isEmpty() || id in routeIndex) return@readCsv
            routeIndex[id] = routes.size
            routes.add(
                RouteInfo(
                    id = id,
                    shortName = r.str(h["route_short_name"] ?: -1).ifEmpty { r.str(h["route_long_name"] ?: -1) },
                    color = normalizeColor(r.str(h["route_color"] ?: -1), "6B7280"),
                    textColor = normalizeColor(r.str(h["route_text_color"] ?: -1), "FFFFFF"),
                ),
            )
        }
        if (routes.size > 0xFFFF) throw IOException("Demasiadas líneas en el GTFS")

        // calendar.txt y calendar_dates.txt
        val serviceIndex = HashMap<String, Int>()
        val services = ArrayList<ServiceBuilder>()
        fun service(id: String): ServiceBuilder = serviceIndex[id]?.let { services[it] }
            ?: ServiceBuilder(id).also { serviceIndex[id] = services.size; services.add(it) }
        open("calendar.txt")?.let { input ->
            readCsv(input) { h, r ->
                val s = service(r.str(h.getValue("service_id")))
                val dayCols = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
                for ((bit, col) in dayCols.withIndex()) if (r.int(h[col] ?: -1) == 1) s.days = s.days or (1 shl bit)
                s.start = r.int(h["start_date"] ?: -1)
                s.end = r.int(h["end_date"] ?: -1)
            }
        }
        open("calendar_dates.txt")?.let { input ->
            readCsv(input) { h, r ->
                val s = service(r.str(h.getValue("service_id")))
                val date = r.int(h.getValue("date"))
                when (r.int(h.getValue("exception_type"))) {
                    1 -> s.added.add(date)
                    2 -> s.removed.add(date)
                }
            }
        }
        if (services.size > 0xFFFF) throw IOException("Demasiados servicios en el GTFS")

        // trips.txt: trip → (línea, servicio) empaquetado en un Int
        val trips = HashMap<String, Int>(1 shl 18)
        readCsv(require("trips.txt")) { h, r ->
            val route = routeIndex[r.str(h.getValue("route_id"))] ?: return@readCsv
            val svc = service(r.str(h.getValue("service_id")))
            val svcIdx = serviceIndex.getValue(svc.id)
            trips[r.str(h.getValue("trip_id"))] = (route shl 16) or svcIdx
        }

        val station = stationId?.let { stopIndex[it] ?: throw StationNotFoundException(it) } ?: -1

        // stop_times.txt
        val stopRoutes = arrayOfNulls<BitSet>(stopIds.size)
        val uf = UnionFind(stopIds.size)
        val deps = ArrayList<RawDeparture>()
        var curTrip: String? = null
        var curPacked = -1
        var n = 0
        var seqs = IntArray(64)
        var stops = IntArray(64)
        var times = IntArray(64)
        val seenTrips = HashSet<String>()

        fun flush() {
            val trip = curTrip ?: return
            if (n == 0 || curPacked < 0) return
            sortBySequence(n, seqs, stops, times)
            val route = curPacked ushr 16
            for (i in 0 until n) {
                val bs = stopRoutes[stops[i]] ?: BitSet().also { stopRoutes[stops[i]] = it }
                bs.set(route)
            }
            if (station >= 0) {
                for (i in 0 until n - 1) {
                    if (stops[i] != station || times[i] < 0) continue
                    val next = stops[i + 1]
                    val last = minOf(n - 1, i + downstreamWindow)
                    for (j in i + 2..last) uf.union(next, stops[j])
                    deps.add(RawDeparture(trip, curPacked and 0xFFFF, route, times[i], next, stops[n - 1]))
                }
            }
        }

        readCsv(require("stop_times.txt")) { h, r ->
            val tripCol = h.getValue("trip_id")
            val curr = curTrip
            if (curr == null || !r.fieldEquals(tripCol, curr)) {
                flush()
                val id = r.str(tripCol)
                if (!seenTrips.add(id)) {
                    // El fichero no viene agrupado por viaje: se procesaría mal la siguiente parada.
                    throw IOException("stop_times.txt no está agrupado por trip_id ($id)")
                }
                curTrip = id
                curPacked = trips[id] ?: -1
                n = 0
            }
            if (curPacked < 0) return@readCsv
            val stop = stopIndex[r.str(h.getValue("stop_id"))] ?: return@readCsv
            if (n == seqs.size) {
                seqs = seqs.copyOf(n * 2); stops = stops.copyOf(n * 2); times = times.copyOf(n * 2)
            }
            seqs[n] = r.int(h.getValue("stop_sequence"))
            stops[n] = stop
            var t = r.timeSecs(h.getValue("departure_time"))
            if (t < 0) t = r.timeSecs(h["arrival_time"] ?: -1)
            times[n] = t
            n++
        }
        flush()

        // Índice de estaciones
        val stations = ArrayList<Station>()
        for (i in stopIds.indices) {
            val bs = stopRoutes[i] ?: continue
            stations.add(stationOf(i, bs, stopIds, stopNames, stopLat, stopLon, routes))
        }
        stations.sortWith(compareBy<Station>({ it.name.lowercase() }, { it.id }))
        val index = StationIndex(feedVersion, stations)

        val schedule = if (station < 0) null else buildSchedule(
            station, deps, uf, timezone, feedVersion, stopIds, stopNames,
            stations.first { it.id == stopIds[station] }, routes, services,
        )
        Result(index, schedule)
    }

    private fun buildSchedule(
        station: Int,
        deps: List<RawDeparture>,
        uf: UnionFind,
        timezone: String,
        feedVersion: String,
        stopIds: List<String>,
        stopNames: List<String>,
        stationInfo: Station,
        routes: List<RouteInfo>,
        services: List<ServiceBuilder>,
    ): StationSchedule {
        // Sentidos: componentes de la unión de paradas siguientes.
        val rootToGroup = LinkedHashMap<Int, Int>()
        val groupDest = ArrayList<HashMap<Int, Int>>()
        val depGroup = IntArray(deps.size)
        for ((k, d) in deps.withIndex()) {
            val root = uf.find(d.next)
            val g = rootToGroup.getOrPut(root) { groupDest.add(HashMap()); groupDest.size - 1 }
            depGroup[k] = g
            groupDest[g].merge(d.dest, 1, Int::plus)
        }
        // Grupos ordenados por número de salidas, con identificador estable 0..n-1.
        val order = groupDest.indices.sortedWith(
            compareByDescending<Int> { groupDest[it].values.sum() }
                .thenBy { stopNames[mostFrequent(groupDest[it])] },
        )
        val remap = IntArray(order.size)
        for ((newId, old) in order.withIndex()) remap[old] = newId
        val groups = order.mapIndexed { newId, old ->
            DirectionGroup(newId, stopIds[mostFrequent(groupDest[old])], groupDest[old].values.sum())
        }

        // Compactar servicios y líneas a los usados.
        val svcMap = HashMap<Int, Int>()
        val usedServices = ArrayList<Service>()
        val routeMap = HashMap<Int, Int>()
        val usedRoutes = ArrayList<RouteInfo>()
        val names = HashMap<String, String>()
        names[stopIds[station]] = stopNames[station]
        val departures = deps.mapIndexed { k, d ->
            val s = svcMap.getOrPut(d.service) { usedServices.add(services[d.service].build()); usedServices.size - 1 }
            val r = routeMap.getOrPut(d.route) { usedRoutes.add(routes[d.route]); usedRoutes.size - 1 }
            names[stopIds[d.next]] = stopNames[d.next]
            names[stopIds[d.dest]] = stopNames[d.dest]
            Departure(d.trip, s, r, d.time, stopIds[d.next], stopIds[d.dest], remap[depGroup[k]])
        }.sortedWith(compareBy({ it.time }, { it.tripId }))

        return StationSchedule(feedVersion, timezone, stationInfo, usedServices, usedRoutes, names, departures, groups)
    }

    private fun stationOf(
        i: Int, bs: BitSet, stopIds: List<String>, stopNames: List<String>,
        stopLat: List<Double>, stopLon: List<Double>, routes: List<RouteInfo>,
    ): Station {
        val nets = sortedSetOf<String>()
        val lines = HashSet<String>()
        var r = bs.nextSetBit(0)
        while (r >= 0) {
            nets.add(Networks.fromRouteId(routes[r].id))
            lines.add(routes[r].shortName)
            r = bs.nextSetBit(r + 1)
        }
        return Station(stopIds[i], stopNames[i], stopLat[i], stopLon[i], nets.toList(), lines.sortedWith(LineComparator))
    }

    private fun mostFrequent(counts: Map<Int, Int>): Int =
        counts.entries.maxWith(compareBy<Map.Entry<Int, Int>> { it.value }.thenByDescending { it.key }).key

    private class RawDeparture(val trip: String, val service: Int, val route: Int, val time: Int, val next: Int, val dest: Int)

    private class ServiceBuilder(val id: String) {
        var days = 0
        var start = 0
        var end = 0
        val added = ArrayList<Int>()
        val removed = ArrayList<Int>()
        fun build() = Service(id, days, start, end, added.sorted(), removed.sorted())
    }

    private class UnionFind(n: Int) {
        private val parent = IntArray(n) { it }
        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            var c = x
            while (parent[c] != r) { val nx = parent[c]; parent[c] = r; c = nx }
            return r
        }
        fun union(a: Int, b: Int) {
            val ra = find(a); val rb = find(b)
            if (ra != rb) parent[maxOf(ra, rb)] = minOf(ra, rb)
        }
    }

    companion object {
        private fun normalizeColor(c: String, default: String): String {
            val v = c.removePrefix("#").uppercase()
            return if (v.length == 6 && v.all { it in '0'..'9' || it in 'A'..'F' }) v else default
        }

        private fun sortBySequence(n: Int, seqs: IntArray, stops: IntArray, times: IntArray) {
            var sorted = true
            for (i in 1 until n) if (seqs[i] < seqs[i - 1]) { sorted = false; break }
            if (sorted) return
            val idx = (0 until n).sortedBy { seqs[it] }
            val s2 = IntArray(n) { seqs[idx[it]] }
            val st2 = IntArray(n) { stops[idx[it]] }
            val t2 = IntArray(n) { times[idx[it]] }
            s2.copyInto(seqs); st2.copyInto(stops); t2.copyInto(times)
        }
    }
}

/** Ordena líneas de forma natural: C1, C2, C10, R2N… */
object LineComparator : Comparator<String> {
    private val re = Regex("^([^0-9]*)([0-9]*)(.*)$")
    override fun compare(a: String, b: String): Int {
        val ma = re.matchEntire(a)!!.groupValues
        val mb = re.matchEntire(b)!!.groupValues
        val p = ma[1].compareTo(mb[1])
        if (p != 0) return p
        val na = ma[2].toIntOrNull() ?: -1
        val nb = mb[2].toIntOrNull() ?: -1
        if (na != nb) return na.compareTo(nb)
        return ma[3].compareTo(mb[3])
    }
}

/** La estación elegida no aparece en el horario (Renfe la ha quitado o ha cambiado su código). */
class StationNotFoundException(val stationId: String) :
    IOException("La estación $stationId no aparece en el horario de Renfe")
