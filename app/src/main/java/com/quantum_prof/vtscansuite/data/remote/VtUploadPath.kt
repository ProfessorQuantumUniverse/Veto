// data/remote/VtUploadPath.kt
package com.quantum_prof.vtscansuite.data.remote

/**
 * Welchen Weg ein Upload nimmt, abhängig von der Dateigröße. Eine Stelle für Repository und
 * Kontingentplanung, damit beide nicht auseinanderlaufen.
 */
object VtUploadPath {
    /** Bis zu dieser Größe geht die Datei direkt an `files`. */
    const val DIRECT_LIMIT_BYTES: Long = 32L * 1024 * 1024

    /** Größere Dateien brauchen vorher eine eigens angeforderte Upload-Adresse. */
    fun needsUploadUrl(sizeBytes: Long): Boolean = sizeBytes > DIRECT_LIMIT_BYTES

    /** So viele API-Anfragen kostet ein Upload dieser Größe (ohne spätere Statusabfragen). */
    fun requestsFor(sizeBytes: Long): Int = if (needsUploadUrl(sizeBytes)) 2 else 1
}