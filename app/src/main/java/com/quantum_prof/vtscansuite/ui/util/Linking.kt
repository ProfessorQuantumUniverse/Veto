// ui/util/Linking.kt
package com.quantum_prof.vtscansuite.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Direkter Link zur VirusTotal API-Key-Seite (leitet ggf. zum Login/Registrieren weiter). */
const val VT_API_KEY_URL = "https://www.virustotal.com/gui/my-apikey"

/** Öffnet eine URL im Browser. */
fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
