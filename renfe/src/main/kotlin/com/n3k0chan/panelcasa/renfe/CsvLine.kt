package com.n3k0chan.panelcasa.renfe

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Lector de líneas CSV sin comillas (el GTFS de Renfe no las usa) que guarda solo los límites de cada campo,
 * ya recortados de espacios, para no crear cadenas salvo cuando se piden.
 * Si una línea contiene comillas se trata con un análisis completo.
 */
internal class CsvLine(capacity: Int = 16) {
    var line: String = ""
        private set
    private var starts = IntArray(capacity)
    private var ends = IntArray(capacity)
    private var quoted: List<String>? = null
    var size = 0
        private set

    fun reset(text: String) {
        line = text
        size = 0
        quoted = null
        if (text.indexOf('"') >= 0) {
            val fields = parseQuoted(text)
            quoted = fields
            size = fields.size
            return
        }
        var start = 0
        val n = text.length
        while (true) {
            var end = text.indexOf(',', start)
            if (end < 0) end = n
            add(start, end)
            if (end >= n) break
            start = end + 1
        }
    }

    private fun add(s0: Int, e0: Int) {
        var s = s0
        var e = e0
        while (s < e && line[s].isWhitespace()) s++
        while (e > s && line[e - 1].isWhitespace()) e--
        if (size == starts.size) {
            starts = starts.copyOf(size * 2)
            ends = ends.copyOf(size * 2)
        }
        starts[size] = s
        ends[size] = e
        size++
    }

    fun str(i: Int): String {
        if (i < 0 || i >= size) return ""
        quoted?.let { return it[i] }
        return line.substring(starts[i], ends[i])
    }

    fun isEmpty(i: Int): Boolean = if (quoted != null) str(i).isEmpty() else i < 0 || i >= size || starts[i] == ends[i]

    /** Compara el campo con [s] sin crear cadenas. */
    fun fieldEquals(i: Int, s: String): Boolean {
        if (quoted != null) return str(i) == s
        if (i < 0 || i >= size) return s.isEmpty()
        val len = ends[i] - starts[i]
        return len == s.length && line.regionMatches(starts[i], s, 0, len)
    }

    fun int(i: Int, default: Int = 0): Int {
        if (quoted != null) return str(i).toIntOrNull() ?: default
        if (i < 0 || i >= size || starts[i] == ends[i]) return default
        var v = 0
        var neg = false
        var p = starts[i]
        if (line[p] == '-') { neg = true; p++ }
        while (p < ends[i]) {
            val c = line[p]
            if (c !in '0'..'9') return default
            v = v * 10 + (c - '0')
            p++
        }
        return if (neg) -v else v
    }

    fun double(i: Int, default: Double = 0.0): Double = str(i).toDoubleOrNull() ?: default

    /** Hora GTFS «H:MM:SS» o «HH:MM:SS» a segundos; puede superar 24 h. -1 si está vacía o es inválida. */
    fun timeSecs(i: Int): Int {
        if (quoted != null) return parseTime(str(i))
        if (i < 0 || i >= size || starts[i] == ends[i]) return -1
        return parseTime(line, starts[i], ends[i])
    }

    companion object {
        fun parseTime(s: String): Int = parseTime(s, 0, s.length)

        fun parseTime(s: String, start: Int, end: Int): Int {
            var total = 0
            var part = 0
            var parts = 0
            var p = start
            while (p <= end) {
                if (p == end || s[p] == ':') {
                    total = total * 60 + part
                    part = 0
                    parts++
                } else {
                    val c = s[p]
                    if (c !in '0'..'9') return -1
                    part = part * 10 + (c - '0')
                }
                p++
            }
            return if (parts == 3) total else -1
        }

        private fun parseQuoted(text: String): List<String> {
            val out = ArrayList<String>()
            val sb = StringBuilder()
            var inQuotes = false
            var i = 0
            while (i < text.length) {
                val c = text[i]
                when {
                    inQuotes && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { sb.append('"'); i++ }
                    c == '"' -> inQuotes = !inQuotes
                    c == ',' && !inQuotes -> { out.add(sb.toString().trim()); sb.setLength(0) }
                    else -> sb.append(c)
                }
                i++
            }
            out.add(sb.toString().trim())
            return out
        }
    }
}

/** Recorre un CSV llamando a [onRow] con la cabecera (nombre → columna) y la línea ya separada. */
internal inline fun readCsv(input: InputStream, onRow: (header: Map<String, Int>, row: CsvLine) -> Unit) {
    BufferedReader(InputStreamReader(input, Charsets.UTF_8), 1 shl 16).use { reader ->
        val first = reader.readLine() ?: return
        val headerLine = CsvLine()
        headerLine.reset(first.removePrefix(BOM))
        val header = HashMap<String, Int>()
        for (i in 0 until headerLine.size) header[headerLine.str(i)] = i
        val row = CsvLine(header.size.coerceAtLeast(4))
        while (true) {
            val text = reader.readLine() ?: break
            if (text.isBlank()) continue
            row.reset(text)
            onRow(header, row)
        }
    }
}

/** Marca de orden de bytes que algunos editores añaden al principio del fichero. */
internal val BOM: String = 0xFEFF.toChar().toString()
