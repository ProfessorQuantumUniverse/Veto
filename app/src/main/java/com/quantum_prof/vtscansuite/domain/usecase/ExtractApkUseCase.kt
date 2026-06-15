// domain/usecase/ExtractApkUseCase.kt
package com.quantum_prof.vtscansuite.domain.usecase

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

data class InstalledApp(
    val name: String,
    val packageName: String,
    val icon: Drawable,
    val apkFile: File
)

class ExtractApkUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Listet alle installierten Apps auf, die vom Benutzer stammen (keine System-Apps).
     */
    fun getInstalledApps(): List<InstalledApp> {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val result = mutableListOf<InstalledApp>()

        for (appInfo in apps) {
            // Nur User-Apps filtern
            if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0) {
                val label = pm.getApplicationLabel(appInfo).toString()
                val packageName = appInfo.packageName
                val icon = pm.getApplicationIcon(appInfo)
                val apkFile = File(appInfo.sourceDir)
                if (apkFile.exists()) {
                    result.add(InstalledApp(label, packageName, icon, apkFile))
                }
            }
        }
        return result.sortedBy { it.name }
    }
}