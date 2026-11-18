// domain/usecase/ExtractApkUseCase.kt
package com.quantum_prof.vtscansuite.domain.usecase

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * Eine installierte App, so wie die Liste sie braucht.
 *
 * Ohne Icon: das lädt [AppIconLoader] erst, wenn eine Zeile sichtbar wird. Vorher lagen
 * bei eingeschalteten System-Apps über zweihundert Drawables gleichzeitig im Speicher,
 * und das Aufbauen der Liste dauerte entsprechend lange.
 */
data class InstalledApp(
    val name: String,
    val packageName: String,
    val apkFile: File,
    /** System- oder vorinstallierte App. Wird beim Sammelcheck nie mitgeprüft. */
    val isSystem: Boolean = false,
    /** Von Chrome angelegte Webseiten-Verknüpfung (WebAPK), keine echte App. */
    val isWebApk: Boolean = false,
    val versionName: String? = null,
    /** Zusammen mit [apkSize] die Grundlage dafür, ob ein gespeicherter Hash noch gilt. */
    val versionCode: Long = 0L,
    val apkSize: Long = 0L
)

class ExtractApkUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Listet installierte Apps auf.
     *
     * [includeSystem] nimmt System- und vorinstallierte Apps dazu. Die sind nur zum
     * Ansehen gedacht: VirusTotal kennt ihre APKs meist gar nicht, und hochgeladen
     * werden sie nie. [hideWebApks] blendet die Verknüpfungen aus, die Chrome für
     * "zum Startbildschirm hinzufügen" anlegt. Nicht lesbare APKs werden übersprungen.
     */
    fun getInstalledApps(
        includeSystem: Boolean = false,
        hideWebApks: Boolean = true
    ): List<InstalledApp> {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val result = mutableListOf<InstalledApp>()

        for (appInfo in apps) {
            val isSystem = appInfo.isSystemApp()
            if (isSystem && !includeSystem) continue

            val isWebApk = isWebApk(appInfo.packageName)
            if (isWebApk && hideWebApks) continue

            val apkFile = File(appInfo.sourceDir)
            if (!apkFile.exists() || !apkFile.canRead()) continue

            val packageInfo = runCatching { pm.getPackageInfo(appInfo.packageName, 0) }.getOrNull()
            result.add(
                InstalledApp(
                    name = runCatching { pm.getApplicationLabel(appInfo).toString() }
                        .getOrDefault(appInfo.packageName),
                    packageName = appInfo.packageName,
                    apkFile = apkFile,
                    isSystem = isSystem,
                    isWebApk = isWebApk,
                    versionName = packageInfo?.versionName,
                    versionCode = packageInfo?.longVersionCode ?: 0L,
                    apkSize = apkFile.length()
                )
            )
        }
        return result.sortedWith(compareBy({ it.isSystem }, { it.name.lowercase() }))
    }

    /**
     * Alle installierten Paketnamen, unabhängig von Filtern. Dient dazu, Ergebnisse zu
     * deinstallierten Apps zu verwerfen, ohne dabei ausgeblendete Apps mitzuräumen.
     */
    fun installedPackageNames(): Set<String> = runCatching {
        context.packageManager.getInstalledApplications(0).map { it.packageName }.toSet()
    }.getOrDefault(emptySet())

    /** Wie viele WebAPK-Verknüpfungen gerade installiert sind (für die Einstellung). */
    fun countWebApks(): Int = runCatching {
        context.packageManager.getInstalledApplications(0)
            .count { it.packageName.startsWith(WEBAPK_PREFIX) }
    }.getOrDefault(0)

    companion object {
        /** Chrome legt Webseiten-Verknüpfungen unter diesem Präfix an. */
        const val WEBAPK_PREFIX = "org.chromium.webapk."

        /** Verzeichnisse, aus denen nur das System selbst Apps mitbringt. */
        private val SYSTEM_DIRS = listOf("/system/", "/system_ext/", "/product/", "/vendor/", "/apex/")

        /** Chrome-Verknüpfung statt echter App. */
        fun isWebApk(packageName: String): Boolean = packageName.startsWith(WEBAPK_PREFIX)

        /** Liegt die APK in einem Verzeichnis, aus dem nur das System selbst liefert? */
        fun isSystemPath(sourceDir: String?): Boolean {
            val path = sourceDir ?: return false
            return SYSTEM_DIRS.any { path.startsWith(it) }
        }

        /**
         * System-App, wenn das Flag gesetzt ist, es sich um ein aktualisiertes System-Paket
         * handelt, oder die APK aus einem Systemverzeichnis kommt. Das Flag allein reicht
         * nicht: manches Vorinstallierte liegt nur im Image, ohne FLAG_SYSTEM zu tragen.
         */
        fun ApplicationInfo.isSystemApp(): Boolean {
            if (flags and ApplicationInfo.FLAG_SYSTEM != 0) return true
            if (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0) return true
            return isSystemPath(sourceDir)
        }
    }
}
