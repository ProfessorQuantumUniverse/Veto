// scan/UploadEligibility.kt
package com.quantum_prof.vtscansuite.scan

import com.quantum_prof.vtscansuite.data.model.AppScanOutcome
import com.quantum_prof.vtscansuite.data.model.AppScanRecord
import com.quantum_prof.vtscansuite.domain.usecase.InstalledApp
import java.io.File

/** Warum eine App nicht zum Upload angeboten wird. */
enum class UploadIneligible {
    /** System- und vorinstallierte Apps werden nie hochgeladen. */
    SYSTEM_APP,

    /** Chrome-Verknüpfung, keine echte App. */
    WEB_APK,

    /** Es gibt kein gespeichertes Ergebnis. Fehlt eines, ist die App nicht "unbekannt". */
    NO_RESULT,

    /** Das Ergebnis sagt, VirusTotal kennt die Datei (oder ist kein bestätigtes "unbekannt"). */
    NOT_CONFIRMED_UNKNOWN,

    /** Das Ergebnis gehört zu einer anderen Fassung (Version oder APK-Größe weichen ab). */
    STALE_RESULT,

    /** Die APK ist nicht (mehr) lesbar. */
    UNREADABLE
}

/** Eine App, die der Nutzer zum Upload auswählen darf. */
data class UploadCandidate(
    val packageName: String,
    val name: String,
    val versionName: String?,
    val versionCode: Long,
    val apkSize: Long,
    val sha256: String
)

sealed interface UploadEligibility {
    data class Eligible(val candidate: UploadCandidate) : UploadEligibility
    data class Ineligible(val reason: UploadIneligible) : UploadEligibility
}

/**
 * Entscheidet, welche Apps zum Upload angeboten werden.
 *
 * "Unbekannt" heißt hier ausschließlich: ein gespeichertes Ergebnis mit
 * [AppScanOutcome.UNKNOWN]. Das wird nur nach einer bestätigten 404-Antwort von VirusTotal
 * geschrieben (siehe [UploadGate]); Rate-Limit, ungültiger Schlüssel, Serverfehler oder
 * fehlendes Netz erzeugen nie ein Ergebnis. Eine App ganz ohne Ergebnis gilt deshalb
 * **nicht** als unbekannt.
 */
object UploadEligibilityRules {

    fun evaluate(
        app: InstalledApp,
        record: AppScanRecord?,
        isReadable: (File) -> Boolean = ::defaultReadable
    ): UploadEligibility {
        val reason = when {
            app.isSystem -> UploadIneligible.SYSTEM_APP
            app.isWebApk -> UploadIneligible.WEB_APK
            record == null -> UploadIneligible.NO_RESULT
            record.outcome != AppScanOutcome.UNKNOWN || record.sha256.isBlank() ->
                UploadIneligible.NOT_CONFIRMED_UNKNOWN
            !record.matches(app.versionCode, app.apkSize) -> UploadIneligible.STALE_RESULT
            !isReadable(app.apkFile) -> UploadIneligible.UNREADABLE
            else -> null
        }
        if (reason != null) return UploadEligibility.Ineligible(reason)
        return UploadEligibility.Eligible(
            UploadCandidate(
                packageName = app.packageName,
                name = app.name,
                versionName = app.versionName,
                versionCode = app.versionCode,
                apkSize = app.apkSize,
                sha256 = record!!.sha256
            )
        )
    }

    /** Alle Apps, die angeboten werden dürfen, in der Reihenfolge der Liste. */
    fun candidates(
        apps: List<InstalledApp>,
        records: Map<String, AppScanRecord>,
        isReadable: (File) -> Boolean = ::defaultReadable
    ): List<UploadCandidate> = apps.mapNotNull { app ->
        (evaluate(app, records[app.packageName], isReadable) as? UploadEligibility.Eligible)?.candidate
    }

    private fun defaultReadable(file: File): Boolean = file.isFile && file.canRead()
}

/** Ein Eintrag der Einwilligung: genau das, wofür der Nutzer sein Ja gegeben hat. */
data class ConsentedItem(
    val packageName: String,
    val name: String,
    val versionName: String?,
    val versionCode: Long,
    val apkSize: Long,
    val sha256: String
)

/**
 * Die Einwilligung des Nutzers für **einen** Upload-Stapel: ein unveränderlicher Schnappschuss
 * der ausgewählten Apps. Nur was hier steht, darf in die Warteschlange. Es gibt keinen Weg,
 * nachträglich etwas hinzuzufügen; für weitere Apps braucht es eine neue Einwilligung.
 *
 * Sie lebt nur im Speicher und wird nie gespeichert: nach dem Ende des Prozesses ist sie weg.
 */
class UploadConsent private constructor(
    val items: List<ConsentedItem>,
    val grantedAtMillis: Long
) {
    companion object {
        /** Obergrenze je Stapel. Gilt hier, nicht erst in der Oberfläche. */
        const val MAX_ITEMS = 25

        /**
         * Legt die Einwilligung an. Doppelte Pakete zählen einmal. Leer oder mehr als
         * [MAX_ITEMS] ist ein Fehler, es wird nichts stillschweigend abgeschnitten.
         */
        fun create(
            selected: List<UploadCandidate>,
            nowMillis: Long = System.currentTimeMillis()
        ): UploadConsent {
            val unique = selected.distinctBy { it.packageName }
            require(unique.isNotEmpty()) { "Nothing selected." }
            require(unique.size <= MAX_ITEMS) { "At most $MAX_ITEMS apps per upload batch." }
            val items = unique.map {
                ConsentedItem(it.packageName, it.name, it.versionName, it.versionCode, it.apkSize, it.sha256)
            }
            return UploadConsent(items.toList(), nowMillis)
        }
    }
}
