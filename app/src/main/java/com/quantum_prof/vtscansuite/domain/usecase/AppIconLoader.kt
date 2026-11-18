// domain/usecase/AppIconLoader.kt
package com.quantum_prof.vtscansuite.domain.usecase

import android.content.Context
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lädt App-Icons einzeln und erst, wenn eine Listenzeile sie wirklich braucht.
 *
 * Vorher wurden beim Aufbau der Liste alle Icons auf einmal geladen – bei eingeblendeten
 * System-Apps über zweihundert Stück. Das kostete Zeit und Speicher, bevor überhaupt
 * etwas zu sehen war.
 */
@Singleton
class AppIconLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cache = LruCache<String, ImageBitmap>(CACHE_ENTRIES)

    suspend fun load(packageName: String): ImageBitmap? {
        cache.get(packageName)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager
                    .getApplicationIcon(packageName)
                    .toBitmap(ICON_PX, ICON_PX)
                    .asImageBitmap()
            }.getOrNull()
        }
        if (bitmap != null) cache.put(packageName, bitmap)
        return bitmap
    }

    private companion object {
        /** Reicht für ein paar Bildschirme Liste; alles Ältere wird verworfen. */
        const val CACHE_ENTRIES = 120

        /** Dargestellt werden 40dp – 96px decken auch dichte Displays ab. */
        const val ICON_PX = 96
    }
}
