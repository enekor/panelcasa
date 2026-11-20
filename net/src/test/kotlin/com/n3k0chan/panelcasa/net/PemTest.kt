package com.n3k0chan.panelcasa.net

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import kotlin.random.Random

class PemTest {
    private val pem = javaClass.getResourceAsStream("/tls/harica_tls_rsa_root_2021.pem")!!.use { String(it.readBytes(), Charsets.US_ASCII) }

    @Test
    fun ignoresTextAroundTheBlockLikeHaricaFiles() {
        // Formato tal como lo publica HARICA: líneas de texto antes del bloque PEM (rompían Android 7.0).
        val withHeader = "Subject: CN=HARICA TLS RSA Root CA 2021,O=Hellenic Academic\nIssuer: lo mismo\n\n" + pem + "\nfin\n"
        val certs = Pem.certificates(withHeader)
        assertEquals(1, certs.size)
        assertEquals(Pem.certificates(pem)[0], certs[0])
        assertEquals(true, certs[0].subjectX500Principal.name.contains("HARICA TLS RSA Root CA 2021"))
    }

    @Test
    fun base64MatchesTheJdk() {
        val r = Random(42)
        repeat(50) {
            val bytes = ByteArray(r.nextInt(0, 300)).also { r.nextBytes(it) }
            val encoded = java.util.Base64.getMimeEncoder().encodeToString(bytes)
            assertArrayEquals(bytes, Pem.base64(encoded))
        }
    }

    @Test(expected = IOException::class)
    fun noBlockFails() {
        Pem.certificates("Subject: nada")
    }
}
