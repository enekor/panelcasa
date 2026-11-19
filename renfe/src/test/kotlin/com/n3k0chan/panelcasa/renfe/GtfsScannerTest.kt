package com.n3k0chan.panelcasa.renfe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class GtfsScannerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun scan(station: String?) = GtfsScanner().scan(GtfsFixture.write(tmp.root), station, "v1")

    @Test
    fun indexListsOnlyStopsWithServiceWithLinesAndNetworks() {
        val index = scan(null).index
        val ids = index.stations.map { it.id }
        assertFalse("Una parada sin trenes no debe aparecer", "Z" in ids)
        val sol = index.stations.single { it.id == "S" }
        assertEquals(listOf("C1"), sol.lines)
        assertEquals(listOf("Madrid"), sol.networks)
        assertEquals(40.4169, sol.lat, 1e-9)
        val hub = index.stations.single { it.id == "H" }
        assertEquals(listOf("R2", "R3", "R4"), hub.lines)
        assertEquals(listOf("Rodalies de Catalunya"), hub.networks)
        assertEquals(listOf("Valencia"), index.stations.single { it.id == "T" }.networks)
    }

    @Test
    fun scanWithoutStationHasNoSchedule() {
        assertNull(scan(null).schedule)
    }

    @Test
    fun throughStationHasTwoDirectionsAndExpressJoinsItsDirection() {
        val s = scan("S").schedule!!
        assertEquals(2, s.groups.size)
        val byTrip = s.departures.associateBy { it.tripId }
        // f2 se salta Chamartín (siguiente parada Delicias) pero va en el mismo sentido que f1.
        assertEquals(byTrip.getValue("f1").group, byTrip.getValue("f2").group)
        assertEquals(byTrip.getValue("f1").group, byTrip.getValue("f3").group)
        assertEquals(byTrip.getValue("r1").group, byTrip.getValue("r2").group)
        assertTrue(byTrip.getValue("f1").group != byTrip.getValue("r1").group)
        // Grupo 0: el de más salidas (4 hacia Delicias frente a 2 hacia Alcalá).
        assertEquals("D", s.groups[0].destination)
        assertEquals("A", s.groups[1].destination)
        assertEquals("A", BoardBuilder.effectiveMode(s, DisplayMode.AUTO))
    }

    @Test
    fun unorderedStopSequenceIsSorted() {
        val r1 = scan("S").schedule!!.departures.single { it.tripId == "r1" }
        assertEquals("B", r1.next)
        assertEquals("A", r1.dest)
        assertEquals(8 * 3600 + 10 * 60, r1.time)
    }

    @Test
    fun timesPastMidnightAreKept() {
        val r2 = scan("S").schedule!!.departures.single { it.tripId == "r2" }
        assertEquals(24 * 3600 + 30 * 60, r2.time)
    }

    @Test
    fun hubWithThreeBranchesUsesSingleList() {
        val s = scan("H").schedule!!
        assertEquals(3, s.groups.size)
        assertFalse("Un tren que termina en la estación no es una salida", s.departures.any { it.tripId == "h4" })
        assertEquals("B", BoardBuilder.effectiveMode(s, DisplayMode.AUTO))
    }

    @Test
    fun terminusUsesSingleList() {
        val s = scan("T").schedule!!
        assertEquals(1, s.groups.size)
        assertEquals(listOf("t1"), s.departures.map { it.tripId })
        assertEquals("B", BoardBuilder.effectiveMode(s, DisplayMode.AUTO))
        assertEquals("A", BoardBuilder.effectiveMode(s, DisplayMode.A))
    }

    @Test
    fun paddedFieldsAndRouteColorsAreNormalized() {
        val s = scan("T").schedule!!
        val route = s.routes.single()
        assertEquals("40T0001C3", route.id)
        assertEquals("C3", route.shortName)
        assertEquals("6B7280", route.color)
        assertEquals("FFFFFF", route.textColor)
    }

    @Test
    fun scheduleKeepsOnlyUsedServicesAndNames() {
        val s = scan("S").schedule!!
        assertEquals(setOf("WK", "WE", "FEST"), s.services.map { it.id }.toSet())
        assertEquals("Sol", s.stopNames["S"])
        assertEquals("Delicias", s.stopNames["D"])
        assertEquals("Europe/Madrid", s.timezone)
    }

    @Test(expected = IOException::class)
    fun unknownStationFails() {
        scan("NOPE")
    }

    @Test(expected = IOException::class)
    fun stopTimesNotGroupedByTripFails() {
        val broken = GtfsFixture.files.getValue("stop_times.txt").trimIndent() + "\nf1,08:15:00,08:15:00,D,006"
        GtfsScanner().scan(GtfsFixture.write(tmp.root, overrides = mapOf("stop_times.txt" to broken)), "S", "v1")
    }

    @Test(expected = IOException::class)
    fun missingRequiredFileFails() {
        GtfsScanner().scan(GtfsFixture.write(tmp.root, overrides = mapOf("trips.txt" to null)), "S", "v1")
    }
}
