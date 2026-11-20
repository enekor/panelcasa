package com.n3k0chan.panelcasa

import com.n3k0chan.panelcasa.meteo.Current
import com.n3k0chan.panelcasa.meteo.Day
import com.n3k0chan.panelcasa.meteo.Forecast
import com.n3k0chan.panelcasa.meteo.Hour
import com.n3k0chan.panelcasa.renfe.Block
import com.n3k0chan.panelcasa.renfe.Board
import com.n3k0chan.panelcasa.renfe.Train
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ScreenScheduleTest {
    @Test
    fun disabledIsNeverNight() {
        assertFalse(ScreenSchedule.isNight(false, 0, 7, 3))
    }

    @Test
    fun rangeWithinDay() {
        assertTrue(ScreenSchedule.isNight(true, 1, 7, 1))
        assertTrue(ScreenSchedule.isNight(true, 1, 7, 6))
        assertFalse(ScreenSchedule.isNight(true, 1, 7, 7))
        assertFalse(ScreenSchedule.isNight(true, 1, 7, 0))
    }

    @Test
    fun rangeAcrossMidnight() {
        assertTrue(ScreenSchedule.isNight(true, 23, 7, 23))
        assertTrue(ScreenSchedule.isNight(true, 23, 7, 2))
        assertFalse(ScreenSchedule.isNight(true, 23, 7, 7))
        assertFalse(ScreenSchedule.isNight(true, 23, 7, 22))
    }

    @Test
    fun equalHoursMeansOff() {
        assertFalse(ScreenSchedule.isNight(true, 5, 5, 5))
    }
}

class VersionGateTest {
    @Test
    fun threshold() {
        assertFalse(VersionGate.homeEnabled(24))
        assertFalse(VersionGate.homeEnabled(28))
        assertTrue(VersionGate.homeEnabled(29))
        assertTrue(VersionGate.homeEnabled(35))
    }

    @Test
    fun columnNeedsBothVersionAndModule() {
        assertFalse(VersionGate.homeColumnVisible(35, moduleReady = false))
        assertFalse(VersionGate.homeColumnVisible(24, moduleReady = true))
        assertFalse(VersionGate.homeColumnVisible(28, moduleReady = true))
        assertTrue(VersionGate.homeColumnVisible(29, moduleReady = true))
    }
}

/**
 * Contrato entre Kotlin y el panel web: todos los campos que lee panel/app.js existen en el JSON que se envía.
 * Si alguien renombra un campo en un lado, esta prueba falla.
 */
class PanelContractTest {
    private val state = PanelState(
        renfe = Board(
            station = "Sol", mode = "A",
            blocks = listOf(Block("Hacia X", 2, listOf(Train("t", "C1", "75B6E0", "FFFFFF", "X", 1L, 2L, 0, true, false)))),
            alerts = listOf("aviso"), realtimeOk = true, scheduleOk = true,
        ),
        renfeStatus = PanelState.Status(1L, 1L, null),
        meteo = Forecast(1L, Current(10.0, "fair_day"), listOf(Hour(1L, 9.0, "rain", 0.2)), listOf(Day("2026-11-19", 5.0, 12.0, "rain", 1.0))),
        meteoStatus = PanelState.Status(1L, 1L, null),
    )

    private fun keys(o: kotlinx.serialization.json.JsonObject) = o.keys

    @Test
    fun jsonHasEveryFieldTheWebPanelReads() {
        val json = Json { encodeDefaults = true }.encodeToString(PanelState.serializer(), state)
        val root = Json.parseToJsonElement(json).jsonObject
        assertTrue(keys(root).containsAll(listOf("renfe", "renfeStatus", "meteo", "meteoStatus")))
        val renfe = root["renfe"]!!.jsonObject
        assertTrue(keys(renfe).containsAll(listOf("station", "mode", "blocks", "alerts", "realtimeOk", "scheduleOk")))
        val block = renfe["blocks"]!!.jsonArray[0].jsonObject
        assertTrue(keys(block).containsAll(listOf("title", "show", "trains")))
        val train = block["trains"]!!.jsonArray[0].jsonObject
        assertTrue(keys(train).containsAll(listOf("line", "color", "textColor", "destination", "scheduled", "predicted", "delayMin", "realtime", "canceled")))
        assertTrue(keys(root["renfeStatus"]!!.jsonObject).containsAll(listOf("okAt", "startedAt", "message")))
        val meteo = root["meteo"]!!.jsonObject
        assertTrue(keys(meteo).containsAll(listOf("current", "hours", "days")))
        assertTrue(keys(meteo["current"]!!.jsonObject).containsAll(listOf("temp", "symbol")))
        assertTrue(keys(meteo["hours"]!!.jsonArray[0].jsonObject).containsAll(listOf("time", "temp", "symbol", "precip")))
        assertTrue(keys(meteo["days"]!!.jsonArray[0].jsonObject).containsAll(listOf("date", "min", "max", "symbol", "precip")))
    }

    @Test
    fun webPanelReadsTheSameFieldNames() {
        val js = File("../panel/app.js").readText()
        for (field in listOf("renfeStatus", "meteoStatus", "realtimeOk", "scheduleOk", "predicted", "scheduled", "delayMin", "textColor", "startedAt", "okAt")) {
            assertTrue("panel/app.js debería usar «$field»", js.contains(field))
        }
        assertEquals(true, js.contains("window.panel"))
    }
}

/**
 * Android 7.0 no lee certificados con texto alrededor del bloque PEM (WRONG_TAG), ni en la app ni en
 * network_security_config. Cada raíz empaquetada debe ser exactamente un bloque PEM y nada más.
 */
class BundledRootsFormatTest {
    @Test
    fun everyBundledRootIsAPlainSinglePemBlock() {
        val files = File("src/main/res/raw").listFiles { f -> f.name.endsWith(".pem") }!!
        assertEquals(4, files.size)
        for (f in files) {
            val text = f.readText().trim()
            assertTrue("${f.name} debe empezar por BEGIN", text.startsWith("-----BEGIN CERTIFICATE-----"))
            assertTrue("${f.name} debe terminar en END", text.endsWith("-----END CERTIFICATE-----"))
            assertEquals("${f.name}: un solo certificado", 1, Regex("BEGIN CERTIFICATE").findAll(text).count())
            assertFalse("${f.name}: sin retornos de carro", text.contains('\r'))
        }
    }
}
