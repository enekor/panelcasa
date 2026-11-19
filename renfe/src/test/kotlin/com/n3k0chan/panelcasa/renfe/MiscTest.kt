package com.n3k0chan.panelcasa.renfe

import com.n3k0chan.panelcasa.net.Fetcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class CsvLineTest {
    @Test
    fun trimsPaddedFieldsAndParsesTimes() {
        val l = CsvLine()
        l.reset("1022J17098C8a,06:35:00,25:36:00,10206  ,005   ")
        assertEquals("1022J17098C8a", l.str(0))
        assertEquals(25 * 3600 + 36 * 60, l.timeSecs(2))
        assertEquals("10206", l.str(3))
        assertEquals(5, l.int(4))
        assertTrue(l.fieldEquals(0, "1022J17098C8a"))
        assertFalse(l.fieldEquals(0, "1022J17098C8"))
    }

    @Test
    fun handlesQuotedFields() {
        val l = CsvLine()
        l.reset("1,\"Sol, Centro\",\"dice \"\"hola\"\"\",x")
        assertEquals(4, l.size)
        assertEquals("Sol, Centro", l.str(1))
        assertEquals("dice \"hola\"", l.str(2))
    }

    @Test
    fun invalidTimeIsNegative() {
        assertEquals(-1, CsvLine.parseTime("8:00"))
        assertEquals(-1, CsvLine.parseTime("aa:00:00"))
        assertEquals(8 * 3600, CsvLine.parseTime("8:00:00"))
    }
}

class StationSearchTest {
    private val index = StationIndex(
        "v",
        listOf(
            Station("1", "Madrid-Chamartín-Clara Campoamor", 0.0, 0.0, listOf("Madrid"), listOf("C2")),
            Station("2", "Chamartín", 0.0, 0.0, listOf("Madrid"), listOf("C1")),
            Station("3", "Alcalá de Henares", 0.0, 0.0, listOf("Madrid"), listOf("C2")),
            Station("4", "L'Hospitalet de Llobregat", 0.0, 0.0, listOf("Rodalies de Catalunya"), listOf("R2")),
        ),
    )
    private val search = StationSearch(index)

    @Test
    fun ignoresAccentsAndCase() {
        assertEquals(listOf("2", "1"), search.search("CHAMARTIN").map { it.id })
        assertEquals(listOf("3"), search.search("alcala henares").map { it.id })
        assertEquals(listOf("4"), search.search("hospitalet").map { it.id })
    }

    @Test
    fun emptyQueryReturnsNothing() {
        assertTrue(search.search("  ").isEmpty())
    }

    @Test
    fun lineOrderingIsNatural() {
        assertEquals(listOf("C1", "C2", "C10", "R2", "R2N"), listOf("C10", "R2N", "C2", "R2", "C1").sortedWith(LineComparator))
    }
}

class RenfeRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    /** Servidor falso: sirve un fichero o un código HTTP. */
    private class FakeFetcher(var source: File?, var code: Int = 200) : Fetcher {
        var downloads = 0
        var lastEtag: String? = null
        override fun get(url: String, etag: String?, lastModified: String?, maxBytes: Int) = throw IOException("sin red")
        override fun download(url: String, dest: File, etag: String?, lastModified: String?): Fetcher.Response {
            downloads++
            lastEtag = etag
            if (code == 200) source!!.copyTo(dest, overwrite = true)
            return Fetcher.Response(code, null, "\"e$downloads\"", "Thu, 19 Nov 2026 04:09:00 GMT", null)
        }
    }

    @Test
    fun downloadsScansAndPersists() {
        val fetcher = FakeFetcher(GtfsFixture.write(tmp.newFolder()))
        val repo = RenfeRepository(tmp.newFolder("data"), fetcher)
        assertEquals(RenfeRepository.StaticOutcome.UPDATED, repo.refreshStatic("S"))
        assertNotNull(repo.loadIndex())
        assertEquals("S", repo.loadSchedule()!!.station.id)
        assertEquals("Thu, 19 Nov 2026 04:09:00 GMT", repo.meta().feedVersion)
    }

    @Test
    fun recentCheckIsSkippedAndLaterCheckIsConditional() {
        var now = 1_000_000L
        val fetcher = FakeFetcher(GtfsFixture.write(tmp.newFolder()))
        val repo = RenfeRepository(tmp.newFolder("data"), fetcher, clock = { now })
        repo.refreshStatic("S")
        assertEquals(RenfeRepository.StaticOutcome.SKIPPED, repo.refreshStatic("S"))
        now += RenfeRepository.DAY_MS + 1
        fetcher.code = 304
        assertEquals(RenfeRepository.StaticOutcome.NOT_MODIFIED, repo.refreshStatic("S"))
        assertEquals("\"e1\"", fetcher.lastEtag)
    }

    @Test
    fun corruptDownloadKeepsPreviousData() {
        var now = 1_000_000L
        val good = GtfsFixture.write(tmp.newFolder())
        val fetcher = FakeFetcher(good)
        val repo = RenfeRepository(tmp.newFolder("data"), fetcher, clock = { now })
        repo.refreshStatic("S")
        val corrupt = File(tmp.newFolder(), "bad.zip").apply { writeText("esto no es un zip") }
        fetcher.source = corrupt
        now += RenfeRepository.DAY_MS + 1
        try {
            repo.refreshStatic("S")
            throw AssertionError("Debería fallar")
        } catch (e: IOException) {
            // esperado
        }
        assertEquals("S", repo.loadSchedule()!!.station.id)
        // El zip anterior sigue y permite cambiar de estación sin red.
        assertEquals("H", repo.rescan("H").schedule!!.station.id)
    }

    @Test
    fun changingStationRescansWithoutNetwork() {
        val fetcher = FakeFetcher(GtfsFixture.write(tmp.newFolder()))
        val repo = RenfeRepository(tmp.newFolder("data"), fetcher)
        repo.refreshStatic("S")
        fetcher.code = 500
        repo.rescan("T")
        assertEquals("T", repo.loadSchedule()!!.station.id)
        assertEquals(1, fetcher.downloads)
    }

    @Test(expected = IOException::class)
    fun serverErrorWithoutLocalDataFails() {
        RenfeRepository(tmp.newFolder("data"), FakeFetcher(null, code = 503)).refreshStatic("S")
    }
}
