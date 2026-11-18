// scan/UploadGate.kt
package com.quantum_prof.vtscansuite.scan

import com.quantum_prof.vtscansuite.data.remote.VtHttpException

/** Was gerade hochgeladen werden soll – bestimmt den Text der Rückfrage. */
enum class UploadSubject {
    /** Die APK einer installierten App (gehört einem fremden Entwickler). */
    INSTALLED_APP,

    /** Die APK einer System- oder vorinstallierten App. Wird nie hochgeladen. */
    SYSTEM_APP,

    /** Eine Datei, die der Nutzer selbst ausgewählt hat. */
    PICKED_FILE
}

/**
 * Entscheidet, ob nach einem Hash-Lookup überhaupt ein Upload in Frage kommt.
 *
 * Grundregel: **nur** eine verifizierte Negativ-Antwort von VirusTotal (HTTP 404,
 * "NotFoundError") bedeutet, dass die Datei dort unbekannt ist. Jeder andere
 * Fehlschlag – Rate-Limit, ungültiger Schlüssel, Serverfehler, kein Netz – ist ein
 * Fehler und darf niemals zu einem Upload führen.
 */
object UploadGate {

    /** True nur bei einer echten "kein Bericht vorhanden"-Antwort von VirusTotal. */
    fun isVerifiedUnknown(error: Throwable?): Boolean =
        (error as? VtHttpException)?.code == 404
}

/** Der Nutzer hat den Upload dieser Datei abgelehnt – kein Fehler, nur ein Abbruch. */
class UploadDeclinedException : Exception("Upload cancelled. The file stayed on your device.")

/**
 * System-APKs werden nie hochgeladen: sie gehören dem Hersteller, nicht dem Nutzer,
 * und können gerätespezifische Bestandteile enthalten.
 */
class SystemAppUploadRefusedException : Exception(
    "VirusTotal has no report for this system app, and system APKs are never uploaded. " +
        "They belong to the device manufacturer and may contain device-specific parts."
)
