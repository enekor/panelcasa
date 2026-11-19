package com.n3k0chan.panelcasa.renfe

import java.text.Normalizer

/** Búsqueda por nombre sin distinguir tildes, mayúsculas ni signos. */
class StationSearch(private val index: StationIndex) {
    private val normalized = index.stations.map { normalize(it.name) }

    fun search(query: String, limit: Int = 50): List<Station> {
        val tokens = normalize(query).split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        return index.stations.indices.asSequence()
            .filter { i -> tokens.all { normalized[i].contains(it) } }
            .sortedWith(
                compareBy<Int>(
                    { if (normalized[it].startsWith(tokens[0])) 0 else 1 },
                    { normalized[it] },
                    { index.stations[it].id },
                ),
            )
            .take(limit)
            .map { index.stations[it] }
            .toList()
    }

    companion object {
        private val marks = Regex("\\p{M}+")
        private val nonAlnum = Regex("[^a-z0-9]+")

        fun normalize(s: String): String =
            Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
                .replace(marks, "")
                .replace(nonAlnum, " ")
                .trim()
    }
}
