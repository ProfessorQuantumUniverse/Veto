// data/remote/ProgressRequestBody.kt
package com.quantum_prof.vtscansuite.data.remote

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer

/**
 * Umhüllt einen RequestBody und meldet den Upload-Fortschritt (0f...1f).
 * Der Callback wird gedrosselt (nur bei Prozent-Änderung) aufgerufen.
 */
class ProgressRequestBody(
    private val delegate: RequestBody,
    private val onProgress: (Float) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType? = delegate.contentType()

    override fun contentLength(): Long = delegate.contentLength()

    override fun writeTo(sink: BufferedSink) {
        val total = contentLength()
        var lastPercent = -1
        val countingSink = object : ForwardingSink(sink) {
            var bytesWritten = 0L
            override fun write(source: Buffer, byteCount: Long) {
                super.write(source, byteCount)
                bytesWritten += byteCount
                if (total > 0) {
                    val percent = ((bytesWritten * 100) / total).toInt()
                    if (percent != lastPercent) {
                        lastPercent = percent
                        onProgress((bytesWritten.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        val buffered = countingSink.buffer()
        delegate.writeTo(buffered)
        buffered.flush()
    }
}
