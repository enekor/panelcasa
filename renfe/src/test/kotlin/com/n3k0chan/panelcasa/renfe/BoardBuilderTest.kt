package com.n3k0chan.panelcasa.renfe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDateTime
import java.time.ZoneId

class BoardBuilderTest {
    companion object {
        @get:ClassRule
        @JvmStatic
        val tmp = TemporaryFolder()

        lateinit var sol: StationSchedule
        lateinit var hub: StationSchedule

        @BeforeClass
        @JvmStatic
        fun setUp() {
            val zip = GtfsFixture.write(tmp.root)
            sol = GtfsScanner().scan(zip, "S", "v1").schedule!!
            hub = GtfsScanner().scan(zip, "H", "v1").schedule!!
        }

        private val madrid = ZoneId.of("Europe/Madrid")
        fun at(y: Int, m: Int, d: Int, h: Int, min: Int) = LocalDateTime.of(y, m, d, h, min).atZone(madrid).toInstant()
        fun ms(y: Int, m: Int, d: Int, h: Int, min: Int) = at(y, m, d, h, min).toEpochMilli()
    }

    @Test
    fun modeAShowsTwoBlocksWithTheirNextTrains() {
        // Lunes 16/11/2026, 07:00.
        val b = BoardBuilder.build(sol, at(2026, 11, 16, 7, 0), null, emptyList(), DisplayMode.AUTO)
        assertEquals("A", b.mode)
        assertEquals(listOf("Hacia Delicias", "Hacia Alcalá"), b.blocks.map { it.title })
        assertEquals(listOf("f1", "f2"), b.blocks[0].trains.take(2).map { it.tripId })
        assertEquals(ms(2026, 11, 16, 8, 0), b.blocks[0].trains[0].scheduled)
        assertEquals(listOf("r1", "r2"), b.blocks[1].trains.take(2).map { it.tripId })
        // r2 sale a las 24:30 del lunes: 00:30 del martes.
        assertEquals(ms(2026, 11, 17, 0, 30), b.blocks[1].trains[1].scheduled)
        assertEquals(2, b.blocks[0].show)
        assertTrue(b.scheduleOk)
        assertFalse(b.realtimeOk)
        assertFalse(b.blocks[0].trains[0].realtime)
        assertEquals("C1", b.blocks[0].trains[0].line)
        assertEquals("Delicias", b.blocks[0].trains[0].destination)
    }

    @Test
    fun afterMidnightPreviousServiceDayStillCounts() {
        val b = BoardBuilder.build(sol, at(2026, 11, 17, 0, 20), null, emptyList(), DisplayMode.AUTO)
        assertEquals("r2", b.blocks[1].trains.first().tripId)
        assertEquals(ms(2026, 11, 17, 0, 30), b.blocks[1].trains.first().scheduled)
    }

    @Test
    fun holidayRemovesWeekdayServiceAndAddsHolidayService() {
        // Martes 8/12/2026, festivo: sin servicio laborable, con servicio especial f4.
        val b = BoardBuilder.build(sol, at(2026, 12, 8, 7, 0), null, emptyList(), DisplayMode.B)
        val trips = b.blocks.single().trains.map { it.tripId }
        assertEquals("f4", trips.first())
        assertFalse("f1" in trips.take(1))
        assertEquals(ms(2026, 12, 8, 11, 0), b.blocks.single().trains.first().scheduled)
    }

    @Test
    fun weekendUsesWeekendService() {
        val b = BoardBuilder.build(sol, at(2026, 11, 21, 7, 0), null, emptyList(), DisplayMode.B)
        assertEquals("f3", b.blocks.single().trains.first().tripId)
    }

    @Test
    fun realtimeDelayIsAppliedAndReorders() {
        val rt = RealtimeSnapshot(
            timestamp = at(2026, 11, 16, 7, 0).epochSecond,
            trips = mapOf("f1" to TripRealtime(delaySec = 35 * 60, canceled = false), "r1" to TripRealtime(0, canceled = true)),
        )
        val b = BoardBuilder.build(sol, at(2026, 11, 16, 7, 0), rt, emptyList(), DisplayMode.AUTO)
        val forward = b.blocks[0].trains
        assertEquals(listOf("f2", "f1"), forward.take(2).map { it.tripId })
        val f1 = forward[1]
        assertEquals(35, f1.delayMin)
        assertEquals(ms(2026, 11, 16, 8, 35), f1.predicted)
        assertEquals(ms(2026, 11, 16, 8, 0), f1.scheduled)
        assertTrue(f1.realtime)
        assertFalse(forward[0].realtime)
        val r1 = b.blocks[1].trains.first()
        assertEquals("r1", r1.tripId)
        assertTrue(r1.canceled)
        assertTrue(b.realtimeOk)
    }

    @Test
    fun departedTrainsDisappearAfterGrace() {
        val b = BoardBuilder.build(sol, at(2026, 11, 16, 8, 2), null, emptyList(), DisplayMode.AUTO)
        assertEquals("f2", b.blocks[0].trains.first().tripId)
        val within = BoardBuilder.build(sol, at(2026, 11, 16, 8, 0).plusSeconds(30), null, emptyList(), DisplayMode.AUTO)
        assertEquals("f1", within.blocks[0].trains.first().tripId)
    }

    @Test
    fun hubShowsSingleListOfFour() {
        val b = BoardBuilder.build(hub, at(2026, 11, 16, 8, 0), null, emptyList(), DisplayMode.AUTO)
        assertEquals("B", b.mode)
        assertEquals(1, b.blocks.size)
        assertEquals(4, b.blocks[0].show)
        assertEquals(listOf("h1", "h2", "h3"), b.blocks[0].trains.take(3).map { it.tripId })
    }

    @Test
    fun forcedModeAOnHubUsesTwoLargestDirections() {
        val b = BoardBuilder.build(hub, at(2026, 11, 16, 8, 0), null, emptyList(), DisplayMode.A)
        assertEquals("A", b.mode)
        assertEquals(2, b.blocks.size)
    }

    @Test
    fun expiredScheduleIsReported() {
        val b = BoardBuilder.build(sol, at(2027, 3, 1, 7, 0), null, emptyList(), DisplayMode.AUTO)
        assertFalse(b.scheduleOk)
        assertTrue(b.blocks.all { it.trains.isEmpty() })
    }

    @Test
    fun alertsAreFilteredByStationRoutesAndTime() {
        val now = at(2026, 11, 16, 7, 0).epochSecond
        val alerts = listOf(
            ServiceAlert("1", listOf("10T0001C1"), emptyList(), "#MadC1 Retrasos  por incidencia.\r\n", now - 60, 0),
            ServiceAlert("2", listOf("51T0001R2"), emptyList(), "Otra red", now - 60, 0),
            ServiceAlert("3", listOf("10T0002C1"), emptyList(), "Caducado", now - 7200, now - 3600),
            ServiceAlert("4", emptyList(), listOf("S"), "Ascensor fuera de servicio", 0, 0),
            ServiceAlert("5", listOf("10T0002C1"), emptyList(), "#MadC1 Retrasos  por incidencia.", now - 60, 0),
            ServiceAlert("6", listOf("10T0002C1"), emptyList(), "#AVISO Línea C1# Avería de la infraestructura.", now - 60, 0),
        )
        val b = BoardBuilder.build(sol, at(2026, 11, 16, 7, 0), null, alerts, DisplayMode.AUTO)
        assertEquals(
            listOf("Retrasos por incidencia.", "Ascensor fuera de servicio", "Avería de la infraestructura."),
            b.alerts,
        )
    }

    @Test
    fun serviceDayStartHandlesDaylightSavingChange() {
        // 29/03/2026: cambio a horario de verano. «Mediodía menos 12 h» es 12:00 CEST − 12 h = 22:00 UTC del día 28 (23:00 hora local).
        val start = BoardBuilder.serviceDayStart(java.time.LocalDate.of(2026, 3, 29), madrid)
        assertEquals(java.time.Instant.parse("2026-03-28T22:00:00Z").toEpochMilli(), start)
    }
}
