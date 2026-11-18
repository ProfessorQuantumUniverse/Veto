// domain/usecase/ExtractApkUseCase.kt
package com.quantum_prof.vtscansuite.domain.usecase

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

data class InstalledApp(
    val name: String,
    val packageName: String,
    /** Null, wenn das Icon nicht gelesen werden konnte (und in Tests). */
    val icon: Drawable?,
    val apkFile: File,
    /** System- oder vorinstallierte App. Wird beim Sammelcheck nie mitgeprüft. */
    val isSystem: Boolean = false,
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
     * werden sie nie. Nicht lesbare APKs werden übersprungen.
     */
    fun getInstalledApps(includeSystem: Boolean = false): List<InstalledApp> {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val result = mutableListOf<InstalledApp>()

        for (appInfo in apps) {
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (isSystem && !includeSystem) continue

            val apkFile = File(appInfo.sourceDir)
            if (!apkFile.exists() || !apkFile.canRead()) continue

            val icon = runCatching { pm.getApplicationIcon(appInfo) }.getOrNull() ?: continue
            val packageInfo = runCatching { pm.getPackageInfo(appInfo.packageName, 0) }.getOrNull()
            result.add(
                InstalledApp(
                    name = runCatching { pm.getApplicationLabel(appInfo).toString() }
                        .getOrDefault(appInfo.packageName),
                    packageName = appInfo.packageName,
                    icon = icon,
                    apkFile = apkFile,
                    isSystem = isSystem,
                    versionName = packageInfo?.versionName,
                    versionCode = packageInfo?.longVersionCodeCompat() ?: 0L,
                    apkSize = apkFile.length()
                )
            )
        }
        return result.sortedWith(compareBy({ it.isSystem }, { it.name.lowercase() }))
    }

    private fun PackageInfo.longVersionCodeCompat(): Long = longVersionCode
}
