// scan/BulkCheckPlan.kt
package com.quantum_prof.vtscansuite.scan

import com.quantum_prof.vtscansuite.data.model.AppScanRecord
import com.quantum_prof.vtscansuite.data.repository.ApiBudgetRepository
import com.quantum_prof.vtscansuite.domain.usecase.InstalledApp

/**
 * Entscheidet, **was** ein Sammellauf prüft und **wie viel** er sich vornehmen darf.
 *
 * Bewusst frei von Android und Netzwerk, damit genau die beiden Regeln testbar sind, auf
 * die es ankommt: System-Apps sind nie dabei, und das Tageskontingent ist die Obergrenze.
 */
object BulkCheckPlan {

    /**
     * Die Apps, für die eine Anfrage nötig ist: keine System-Apps, und nichts, wofür schon
     * ein Ergebnis zur installierten Fassung vorliegt.
     */
    fun pending(
        apps: List<InstalledApp>,
        known: Map<String, AppScanRecord>
    ): List<InstalledApp> = apps
        .filterNot { it.isSystem }
        .filterNot { app -> known[app.packageName]?.matches(app.versionCode, app.apkSize) == true }

    /**
     * Wie viele Apps ein einzelner Lauf höchstens prüft: die Obergrenze pro Lauf, begrenzt
     * durch das, was vom Tageskontingent übrig ist. Nie negativ.
     */
    fun allowance(usedToday: Int): Int = minOf(
        ApiBudgetRepository.MAX_PER_RUN,
        ApiBudgetRepository.remaining(usedToday)
    ).coerceAtLeast(0)
}
