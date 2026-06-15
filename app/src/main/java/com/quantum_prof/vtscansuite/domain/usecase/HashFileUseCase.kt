// domain/usecase/HashFileUseCase.kt
package com.quantum_prof.vtscansuite.domain.usecase

import java.io.File
import java.security.MessageDigest
import javax.inject.Inject

class HashFileUseCase @Inject constructor() {
    /**
     * Berechnet den SHA-256 Hash einer Datei, ohne den Arbeitsspeicher zu überlasten.
     */
    operator fun invoke(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        file.inputStream().use { input ->
            var bytesRead = input.read(buffer)
            while (bytesRead != -1) {
                digest.update(buffer, 0, bytesRead)
                bytesRead = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}