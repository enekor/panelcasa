package com.n3k0chan.panelcasa.meteo

import com.n3k0chan.panelcasa.net.Fetcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId

/** La muestra es una respuesta real de Met.no para Madrid descargada el 19/11/2026 a las 16:02 UTC. */
class MetParserTest {
    private val body = javaClass.getResourceAsStream("/met_compact.json")!!.use { String(it.readBytes(), Charsets.UTF_8) }
    private val madrid = ZoneId.of("Europe/Madrid")
    private val now = Instant.parse("2026-11-19T16:30:00Z")

    @Test
    fun currentIsTheEntryThatAlreadyStarted() {
        val f = MetParser.parse(body, now, madrid)
        assertEquals(Current(13.1, "fair_day"), f.current)
        assertEquals(Instant.parse("2026-11-19T13:19:04Z").toEpochMilli(), f.updatedAt)
    }

    @Test
    fun nextSixHours() {
        val f = MetParser.parse(body, now, madrid)
        assertEquals(6, f.hours.size)
        assertEquals(Instant.parse("2026-11-19T17:00:00Z").toEpochMilli(), f.hours.first().time)
        assertEquals(Instant.parse("2026-11-19T22:00:00Z").toEpochMilli(), f.hours.last().time)
        assertTrue(f.hours.all { it.symbol.isNotEmpty() })
    }

    @Test
    fun fourDaysStartingTodayInLocalTime() {
        val f = MetParser.parse(body, now, madrid)
        assertEquals(listOf("2026-11-19", "2026-11-20", "2026-11-21", "2026-11-22"), f.days.map { it.date })
        for (d in f.days) {
            assertTrue("${d.date}: mín ≤ máx", d.min <= d.max)
            assertTrue("${d.date}: símbolo de día", !d.symbol.endsWith("_night"))
        }
        // Hoy solo cuenta desde la hora actual: la mínima no puede ser menor que la de las horas que quedan.
        val today = f.days.first()
        assertTrue(today.max >= 13.1 - 1e-9 || today.max >= f.current!!.temp)
    }

    @Test
    fun daySymbolUsesDayVariant() {
        assertEquals("clearsky_day", MetParser.daySymbol("clearsky_night"))
        assertEquals("fair_day", MetParser.daySymbol("fair_polartwilight"))
        assertEquals("rain", MetParser.daySymbol("rain"))
    }

    @Test(expected = IOException::class)
    fun invalidBodyFails() {
        MetParser.parse("<html>", now, madrid)
    }
}

class MeteoRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val body = javaClass.getResourceAsStream("/met_compact.json")!!.readBytes()

    private class FakeFetcher(var code: Int, var body: ByteArray?) : Fetcher {
        val urls = ArrayList<String>()
        val ifModifiedSince = ArrayList<String?>()
        override fun get(url: String, etag: String?, lastModified: String?, maxBytes: Int): Fetcher.Response {
            urls.add(url)
            ifModifiedSince.add(lastModified)
            return Fetcher.Response(code, if (code == 200) body else null, null, "Thu, 19 Nov 2026 16:02:13 GMT", "Thu, 19 Nov 2026 16:32:59 GMT")
        }
        override fun download(url: String, dest: File, etag: String?, lastModified: String?) = throw UnsupportedOperationException()
    }

    private val t0 = Instant.parse("2026-11-19T16:03:00Z").toEpochMilli()

    @Test
    fun urlUsesFourDecimalsAndDot() {
        assertEquals(
            "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=40.4169&lon=-3.7035",
            MeteoRepository.url(40.416912345, -3.70349999),
        )
    }

    @Test
    fun fetchesThenRespectsHourlyLimitThenAsksConditionally() {
        var now = t0
        val fetcher = FakeFetcher(200, body)
        val repo = MeteoRepository(tmp.root, fetcher, clock = { now })
        assertEquals(MeteoRepository.Outcome.UPDATED, repo.refresh(40.4169, -3.7035))
        now += 40 * 60_000L // pasado Expires (16:32) pero no una hora
        assertEquals(MeteoRepository.Outcome.SKIPPED, repo.refresh(40.4169, -3.7035))
        now += 21 * 60_000L
        fetcher.code = 304
        assertEquals(MeteoRepository.Outcome.NOT_MODIFIED, repo.refresh(40.4169, -3.7035))
        assertEquals(listOf(null, "Thu, 19 Nov 2026 16:02:13 GMT"), fetcher.ifModifiedSince)
        assertNotNull(repo.forecast(Instant.ofEpochMilli(now), ZoneId.of("Europe/Madrid")))
    }

    @Test
    fun changingLocationFetchesImmediatelyWithoutConditionalHeader() {
        var now = t0
        val fetcher = FakeFetcher(200, body)
        val repo = MeteoRepository(tmp.root, fetcher, clock = { now })
        repo.refresh(40.4169, -3.7035)
        now += 60_000
        assertEquals(MeteoRepository.Outcome.UPDATED, repo.refresh(41.3790, 2.1400))
        assertEquals(listOf<String?>(null, null), fetcher.ifModifiedSince)
        assertTrue(fetcher.urls.last().contains("lat=41.3790&lon=2.1400"))
    }

    @Test
    fun invalidResponseKeepsPreviousForecast() {
        var now = t0
        val fetcher = FakeFetcher(200, body)
        val repo = MeteoRepository(tmp.root, fetcher, clock = { now })
        repo.refresh(40.4169, -3.7035)
        now += 2 * MeteoRepository.HOUR_MS
        fetcher.body = "no es json".toByteArray()
        try {
            repo.refresh(40.4169, -3.7035)
            throw AssertionError("Debería fallar")
        } catch (e: IOException) {
            // esperado
        }
        assertNotNull(repo.forecast(Instant.ofEpochMilli(t0), ZoneId.of("Europe/Madrid")))
    }

    @Test
    fun noDataYieldsNull() {
        assertNull(MeteoRepository(tmp.root, FakeFetcher(500, null)).forecast(Instant.now(), ZoneId.of("UTC")))
    }

    @Test(expected = IOException::class)
    fun serverErrorFails() {
        MeteoRepository(tmp.root, FakeFetcher(503, null)).refresh(40.0, -3.0)
    }
}
