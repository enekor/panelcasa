package com.n3k0chan.panelcasa.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.Date
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Cadena real de api.met.no (servidor → GEANT TLS RSA 1 → HARICA 2021 firmada en cruzado por Hellenic 2015)
 * frente a un validador que, como Android 7.0, solo conoce Hellenic RootCA 2011.
 */
class BundledRootsTrustManagerTest {
    private fun cert(name: String): X509Certificate =
        javaClass.getResourceAsStream("/tls/$name.pem")!!.use { CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate }

    private val chain = arrayOf(cert("metno_leaf"), cert("metno_geant_tls_rsa_1"), cert("metno_harica_2021_cross_2015"))
    private val harica2021 = cert("harica_tls_rsa_root_2021")
    private val isrgX1 = cert("isrg_root_x1")
    private val validDate = Date.from(Instant.parse("2026-11-20T12:00:00Z"))

    /** Validador «de sistema» con solo la raíz griega que trae Android 7.0. */
    private val android7Like: X509TrustManager = run {
        val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null)
            setCertificateEntry("hellenic2011", cert("android7_hellenic_rootca_2011"))
        }
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(ks) }
            .trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    @Test(expected = CertificateException::class)
    fun android7RootsAloneRejectMetNo() {
        android7Like.checkServerTrusted(chain, "ECDHE_RSA")
    }

    @Test
    fun bundledHarica2021AcceptsMetNoAndReportsFallback() {
        val events = ArrayList<String>()
        val tm = BundledRootsTrustManager(android7Like, listOf(harica2021, isrgX1), { events.add(it) }, { validDate })
        tm.checkServerTrusted(chain, "ECDHE_RSA")
        assertEquals(1, events.size)
        assertTrue(events[0].contains("api.met.no"))
    }

    @Test
    fun withoutTheRightRootStillRejects() {
        val tm = BundledRootsTrustManager(android7Like, listOf(isrgX1), now = { validDate })
        try {
            tm.checkServerTrusted(chain, "ECDHE_RSA")
            throw AssertionError("Debería rechazarse")
        } catch (e: CertificateException) {
            assertTrue("Conserva el error original y adjunta el del respaldo", e.suppressed.isNotEmpty())
        }
    }

    @Test(expected = CertificateException::class)
    fun expiredLeafIsRejected() {
        val after = Date.from(Instant.parse("2027-06-01T00:00:00Z"))
        BundledRootsTrustManager(android7Like, listOf(harica2021), now = { after }).checkServerTrusted(chain, "ECDHE_RSA")
    }

    @Test(expected = CertificateException::class)
    fun missingIntermediateIsRejected() {
        BundledRootsTrustManager(android7Like, listOf(harica2021), now = { validDate })
            .checkServerTrusted(arrayOf(chain[0]), "ECDHE_RSA")
    }

    @Test(expected = CertificateException::class)
    fun chainWithOnlyARootIsRejected() {
        BundledRootsTrustManager(android7Like, listOf(harica2021), now = { validDate })
            .checkServerTrusted(arrayOf(harica2021), "ECDHE_RSA")
    }

    @Test
    fun primarySuccessDoesNotUseFallback() {
        var fallback = false
        val trustsEverythingFromHarica = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        BundledRootsTrustManager(trustsEverythingFromHarica, listOf(isrgX1), { fallback = true }).checkServerTrusted(chain, "ECDHE_RSA")
        assertTrue(!fallback)
    }
}
