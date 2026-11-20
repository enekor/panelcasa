package com.n3k0chan.panelcasa.net

import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * Lee certificados PEM de forma tolerante: ignora cualquier texto fuera de los bloques
 * «-----BEGIN CERTIFICATE-----» y pasa al lector de Android solo el binario (DER).
 * Necesario porque el lector de Android 7.0 (Conscrypt) falla con WRONG_TAG si hay texto delante,
 * como en los ficheros que publica HARICA. java.util.Base64 no existe en Android 7.0, de ahí el decodificador propio.
 */
object Pem {
    private val block = Regex("-----BEGIN CERTIFICATE-----([A-Za-z0-9+/=\\s]+)-----END CERTIFICATE-----")

    @Throws(IOException::class)
    fun certificates(text: String): List<X509Certificate> {
        val cf = CertificateFactory.getInstance("X.509")
        val certs = block.findAll(text).map { m ->
            val der = base64(m.groupValues[1])
            cf.generateCertificate(ByteArrayInputStream(der)) as X509Certificate
        }.toList()
        if (certs.isEmpty()) throw IOException("No hay ningún certificado PEM")
        return certs
    }

    fun base64(s: String): ByteArray {
        val out = java.io.ByteArrayOutputStream(s.length * 3 / 4)
        var buffer = 0
        var bits = 0
        for (c in s) {
            val v = when (c) {
                in 'A'..'Z' -> c - 'A'
                in 'a'..'z' -> c - 'a' + 26
                in '0'..'9' -> c - '0' + 52
                '+' -> 62
                '/' -> 63
                '=' -> break
                else -> if (c.isWhitespace()) continue else throw IOException("Carácter no válido en Base64: $c")
            }
            buffer = (buffer shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xFF)
            }
        }
        return out.toByteArray()
    }
}
