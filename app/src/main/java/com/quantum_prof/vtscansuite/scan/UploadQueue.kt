// scan/UploadQueue.kt
package com.quantum_prof.vtscansuite.scan

import com.quantum_prof.vtscansuite.data.model.AppScanOutcome
import com.quantum_prof.vtscansuite.data.model.AppScanRecord
import com.quantum_prof.vtscansuite.data.remote.BudgetExhaustedException
import com.quantum_prof.vtscansuite.data.remote.RateLimitException
import com.quantum_prof.vtscansuite.data.remote.UploadNotStartedException
import com.quantum_prof.vtscansuite.data.remote.VtHttpException
import com.quantum_prof.vtscansuite.data.repository.ApiBudgetRepository
import com.quantum_prof.vtscansuite.domain.usecase.InstalledApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

/**
 * Wo ein Eintrag steht. Jeder Eintrag endet in genau einem Zustand, und "abgeschickt"
 * ([SUBMITTED]) heißt nur: VirusTotal hat die Datei entgegengenommen. Eine Analyse oder ein
 * Urteil gibt es dann noch nicht; das holt später die normale Hash-Prüfung.
 */
enum class UploadItemStatus {
    /** Noch nicht an der Reihe, oder der Lauf wurde vorher angehalten. */
    PENDING,

    /** Wird gerade hochgeladen. */
    UPLOADING,

    /** VirusTotal hat den Upload angenommen und eine Analyse-ID geliefert. */
    SUBMITTED,

    /** Bestätigt nicht angekommen: abgelehnt, oder vor dem ersten Byte gescheitert. */
    FAILED,

    /**
     * Unklar, ob die Datei angekommen ist (Zeitüberschreitung, Verbindungsabbruch, Serverfehler
     * nach dem Senden). Wird nicht automatisch wiederholt, sonst könnte sie doppelt ankommen.
     */
    UNCERTAIN,

    /** Vom Nutzer abgebrochen. Die Datei gilt nicht als abgeschickt. */
    CANCELLED,

    /** Vor dem Upload verworfen, weil die Voraussetzungen nicht mehr stimmten. */
    SKIPPED
}

enum class SkipReason {
    APP_GONE,
    NOT_ELIGIBLE,
    CHANGED,
    UNREADABLE,
    HASH_CHANGED
}

data class UploadItem(
    val consented: ConsentedItem,
    val status: UploadItemStatus = UploadItemStatus.PENDING,
    val skipReason: SkipReason? = null,
    /** Kurze Erklärung für die Oberfläche, ohne Schlüssel, Adressen oder Inhalte. */
    val detail: String? = null
) {
    /** Hat der Eintrag sein Ende erreicht? Wartende und laufende nicht. */
    val isResolved: Boolean
        get() = status != UploadItemStatus.PENDING && status != UploadItemStatus.UPLOADING
}

enum class UploadStopReason {
    COMPLETED,
    DAILY_LIMIT,
    RATE_LIMITED,
    AUTH_FAILED,
    NO_API_KEY,
    CANCELLED,

    /** Ein unerwarteter Fehler im Ablauf selbst. Offene Einträge bleiben offen. */
    ERROR,

    /** Es läuft schon eine andere Arbeit an VirusTotal; es wurde nichts hochgeladen. */
    BUSY
}

/** Zähler über alle Einträge. Eine Quelle für Oberfläche, Benachrichtigung und Tests. */
interface UploadSnapshot {
    val items: List<UploadItem>

    val total: Int get() = items.size
    val submitted: Int get() = count(UploadItemStatus.SUBMITTED)
    val failed: Int get() = count(UploadItemStatus.FAILED)
    val uncertain: Int get() = count(UploadItemStatus.UNCERTAIN)
    val skipped: Int get() = count(UploadItemStatus.SKIPPED)
    val cancelled: Int get() = count(UploadItemStatus.CANCELLED)
    val pending: Int get() = count(UploadItemStatus.PENDING)

    /** Einträge mit Ergebnis: alles außer wartend und laufend. */
    val resolved: Int get() = items.count { it.isResolved }

    /** Anteil der erledigten Einträge, zwischen 0 und 1. Laufende zählen nicht mit. */
    val fraction: Float get() = if (total == 0) 0f else (resolved.toFloat() / total).coerceIn(0f, 1f)

    private fun count(status: UploadItemStatus) = items.count { it.status == status }
}

sealed interface UploadQueueState {
    data object Idle : UploadQueueState

    data class Running(override val items: List<UploadItem>) : UploadQueueState, UploadSnapshot

    data class Finished(
        override val items: List<UploadItem>,
        val reason: UploadStopReason,
        /** Wartezeit, die VirusTotal bei einem Rate-Limit genannt hat. */
        val retryAfterSeconds: Long? = null,
        /** Frühester Zeitpunkt (Epoch-Millis), zu dem [UploadQueueManager.resume] wieder darf. */
        val retryNotBeforeMillis: Long? = null
    ) : UploadQueueState, UploadSnapshot {
        /** Gibt es noch Einträge, die in dieser Sitzung weiterlaufen dürfen? */
        val canResume: Boolean
            get() = pending > 0 && (reason == UploadStopReason.DAILY_LIMIT || reason == UploadStopReason.RATE_LIMITED || reason == UploadStopReason.BUSY)
    }
}

/** Alles, was der Ablauf von außen braucht, damit er ohne Android und Netz testbar bleibt. */
internal interface UploadEnvironment {
    suspend fun resolveApp(packageName: String): InstalledApp?
    suspend fun currentRecord(packageName: String): AppScanRecord?
    suspend fun hash(file: File): String
    fun isReadable(file: File): Boolean
    suspend fun submit(file: File): Result<String>

    /** Pause, die auch Tests überspringen können. */
    suspend fun pause(millis: Long)

    /** Der Eintrag ist (möglicherweise) angekommen: das alte "unbekannt" gilt nicht mehr. */
    suspend fun onSubmitted(packageName: String)
}

/**
 * Der Upload-Ablauf: strikt nacheinander, nur für Einträge der Einwilligung.
 *
 * Vor jedem Upload wird die installierte App neu geprüft und die APK neu gehasht; nur ein
 * unveränderter, noch bestätigt unbekannter Eintrag geht raus. Jeder Versuch reserviert
 * seine Anfragen vor dem Senden vom gemeinsamen Tageskontingent (das Repository reserviert).
 */
internal class UploadQueueRunner(
    private val env: UploadEnvironment,
    private val onUpdate: (List<UploadItem>) -> Unit
) {
    class Outcome(val reason: UploadStopReason, val retryAfterSeconds: Long? = null)

    /** Wurde für den Eintrag in Arbeit schon gesendet? Nur für die Einordnung eines Ablauf-Fehlers. */
    private var submitStarted = false

    /**
     * Arbeitet alle wartenden Einträge ab. Bei Abbruch werden offene Einträge auf
     * [UploadItemStatus.CANCELLED] gesetzt, bevor die Abbruch-Ausnahme weitergereicht wird.
     */
    suspend fun run(items: MutableList<UploadItem>): Outcome {
        var needsSpacing = false
        try {
            for (index in items.indices) {
                if (items[index].status != UploadItemStatus.PENDING) continue
                currentCoroutineContext().ensureActive()

                val app = when (val check = revalidate(items[index].consented)) {
                    is Revalidation.Skip -> {
                        set(items, index, UploadItemStatus.SKIPPED, skip = check.reason)
                        continue
                    }

                    is Revalidation.Ok -> check.app
                }

                submitStarted = false
                set(items, index, UploadItemStatus.UPLOADING)
                val stop = upload(items, index, app, needsSpacing)
                needsSpacing = true
                if (stop != null) return stop
            }
            return Outcome(UploadStopReason.COMPLETED)
        } catch (e: CancellationException) {
            cancelOpen(items)
            onUpdate(items.toList())
            throw e
        } catch (_: Exception) {
            // Ein Fehler im Ablauf selbst (nicht im Upload): der Lauf darf nicht hängen bleiben.
            // Wurde noch nichts gesendet, ist der Eintrag unberührt; sonst ist unklar, ob er ankam.
            val open = if (submitStarted) UploadItemStatus.UNCERTAIN else UploadItemStatus.PENDING
            for (i in items.indices) {
                if (items[i].status == UploadItemStatus.UPLOADING) {
                    items[i] = items[i].copy(status = open)
                }
            }
            onUpdate(items.toList())
            return Outcome(UploadStopReason.ERROR)
        }
    }

    private sealed interface Revalidation {
        data class Ok(val app: InstalledApp) : Revalidation
        data class Skip(val reason: SkipReason) : Revalidation
    }

    private suspend fun revalidate(consented: ConsentedItem): Revalidation {
        val app = env.resolveApp(consented.packageName) ?: return Revalidation.Skip(SkipReason.APP_GONE)
        if (app.isSystem || app.isWebApk) return Revalidation.Skip(SkipReason.NOT_ELIGIBLE)
        if (!env.isReadable(app.apkFile)) return Revalidation.Skip(SkipReason.UNREADABLE)
        if (app.versionCode != consented.versionCode || app.apkSize != consented.apkSize) {
            return Revalidation.Skip(SkipReason.CHANGED)
        }
        val record = env.currentRecord(consented.packageName)
        if (record == null || record.outcome != AppScanOutcome.UNKNOWN) {
            return Revalidation.Skip(SkipReason.NOT_ELIGIBLE)
        }
        val hash = try {
            env.hash(app.apkFile)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return Revalidation.Skip(SkipReason.UNREADABLE)
        }
        if (!hash.equals(consented.sha256, ignoreCase = true)) {
            return Revalidation.Skip(SkipReason.HASH_CHANGED)
        }
        return Revalidation.Ok(app)
    }

    /**
     * Ein Eintrag mit höchstens [MAX_RETRIES] sicheren Wiederholungen. Null heißt: weiter.
     * Das gemeinsame Tageskontingent reserviert das Repository vor jeder Anfrage (1, über 32 MB
     * zwei); ist es verbraucht, geht nichts raus und der Fehler hält den Lauf an.
     */
    private suspend fun upload(
        items: MutableList<UploadItem>,
        index: Int,
        app: InstalledApp,
        spaceFirstAttempt: Boolean
    ): Outcome? {
        var attempt = 0
        var spacing = spaceFirstAttempt
        while (true) {
            if (spacing) env.pause(ApiBudgetRepository.MIN_INTERVAL_MS)
            spacing = true
            currentCoroutineContext().ensureActive()

            submitStarted = true
            val result = env.submit(app.apkFile)

            val error = result.exceptionOrNull()
            if (error == null) {
                set(items, index, UploadItemStatus.SUBMITTED)
                env.onSubmitted(app.packageName)
                return null
            }
            if (error is CancellationException) throw error

            when (val verdict = classify(error)) {
                is Verdict.Stop -> {
                    // Abgelehnt, bevor die Datei angenommen wurde: der Eintrag bleibt offen.
                    set(items, index, UploadItemStatus.PENDING)
                    return Outcome(verdict.reason, verdict.retryAfterSeconds)
                }

                Verdict.SafeRetry -> {
                    if (attempt >= MAX_RETRIES) {
                        set(items, index, UploadItemStatus.FAILED, detail = "No connection. Nothing was uploaded.")
                        return null
                    }
                    env.pause(BACKOFF_MS[attempt.coerceAtMost(BACKOFF_MS.lastIndex)])
                    attempt++
                    spacing = false // die Wartezeit hat den Abstand schon abgedeckt
                }

                is Verdict.Failed -> {
                    set(items, index, UploadItemStatus.FAILED, detail = verdict.detail)
                    return null
                }

                is Verdict.Uncertain -> {
                    set(items, index, UploadItemStatus.UNCERTAIN, detail = verdict.detail)
                    env.onSubmitted(app.packageName)
                    return null
                }
            }
        }
    }

    internal sealed interface Verdict {
        /** Der ganze Lauf hält an; der Eintrag bleibt offen. */
        data class Stop(val reason: UploadStopReason, val retryAfterSeconds: Long? = null) : Verdict

        /** Vor dem Senden gescheitert, sicher wiederholbar. */
        data object SafeRetry : Verdict

        /** Bestätigt nicht angekommen. */
        data class Failed(val detail: String) : Verdict

        /** Möglicherweise angekommen: nicht automatisch wiederholen. */
        data class Uncertain(val detail: String) : Verdict
    }

    private fun set(
        items: MutableList<UploadItem>,
        index: Int,
        status: UploadItemStatus,
        skip: SkipReason? = null,
        detail: String? = null
    ) {
        items[index] = items[index].copy(status = status, skipReason = skip, detail = detail)
        onUpdate(items.toList())
    }

    companion object {
        /** Wiederholungen nur nach Fehlern, bei denen sicher nichts gesendet wurde. */
        const val MAX_RETRIES = 2

        /**
         * Wartezeit vor der ersten und zweiten Wiederholung. Beide liegen über dem Mindestabstand
         * der Anfragen ([ApiBudgetRepository.MIN_INTERVAL_MS]) und ersetzen ihn.
         */
        val BACKOFF_MS = longArrayOf(30_000L, 60_000L)

        /** Setzt alles Offene auf "abgebrochen". Abgeschlossene Einträge bleiben, wie sie sind. */
        fun cancelOpen(items: MutableList<UploadItem>) {
            for (i in items.indices) {
                val status = items[i].status
                if (status == UploadItemStatus.PENDING || status == UploadItemStatus.UPLOADING) {
                    items[i] = items[i].copy(status = UploadItemStatus.CANCELLED)
                }
            }
        }

        /**
         * Ordnet einen Fehlschlag ein. Der Grundsatz: nur wiederholen oder als "nicht
         * angekommen" melden, wenn sicher ist, dass nichts angenommen wurde. Alles andere
         * nach dem Senden ist unklar.
         */
        fun classify(error: Throwable): Verdict {
            if (error is UploadNotStartedException) {
                val cause = error.cause ?: return Verdict.Failed("The upload did not start.")
                return when (val inner = classify(cause)) {
                    // Vor dem ersten Byte gescheitert: nichts kann angekommen sein.
                    is Verdict.Uncertain -> Verdict.Failed("The upload did not start. Nothing was uploaded.")
                    else -> inner
                }
            }
            return when (error) {
                is RateLimitException ->
                    Verdict.Stop(UploadStopReason.RATE_LIMITED, error.retryAfterSeconds)

                // Vor dem Senden entschieden: nichts ging raus, der Eintrag bleibt offen.
                is BudgetExhaustedException -> Verdict.Stop(UploadStopReason.DAILY_LIMIT)

                is VtHttpException -> when {
                    error.code == 401 || error.code == 403 -> Verdict.Stop(UploadStopReason.AUTH_FAILED)
                    error.code in 400..499 ->
                        Verdict.Failed("VirusTotal rejected the file (code ${error.code}).")

                    else -> Verdict.Uncertain(
                        "VirusTotal answered with an error (code ${error.code}). The file may or may not have arrived."
                    )
                }

                is UnknownHostException, is ConnectException, is NoRouteToHostException -> Verdict.SafeRetry
                is SSLHandshakeException -> Verdict.Failed("The secure connection could not be set up.")
                is IOException ->
                    if (error.message?.startsWith("Blocked:") == true) {
                        Verdict.Failed("The request was blocked to protect your API key.")
                    } else {
                        Verdict.Uncertain("The connection broke during the upload. The file may or may not have arrived.")
                    }

                else -> Verdict.Uncertain("The upload ended unexpectedly. The file may or may not have arrived.")
            }
        }
    }
}
