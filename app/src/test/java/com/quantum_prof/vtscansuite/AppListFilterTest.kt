package com.quantum_prof.vtscansuite

import com.quantum_prof.vtscansuite.domain.usecase.ExtractApkUseCase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Was in der App-Liste auftaucht. Beides hat Folgen: eine falsch als normal erkannte
 * System-App käme in den Sammellauf, und WebAPK-Verknüpfungen würden Kontingent kosten,
 * obwohl VirusTotal sie nie kennt.
 */
class AppListFilterTest {

    @Test
    fun `chrome shortcuts are recognised`() {
        assertTrue(ExtractApkUseCase.isWebApk("org.chromium.webapk.a1b2c3"))
        assertTrue(ExtractApkUseCase.isWebApk("org.chromium.webapk.ff00"))
    }

    @Test
    fun `real apps are not mistaken for shortcuts`() {
        assertFalse(ExtractApkUseCase.isWebApk("org.chromium.chrome"))
        assertFalse(ExtractApkUseCase.isWebApk("com.android.chrome"))
        // Kein naives "contains": der Präfix muss am Anfang stehen.
        assertFalse(ExtractApkUseCase.isWebApk("com.evil.org.chromium.webapk.fake"))
    }

    @Test
    fun `apks from system directories count as system`() {
        assertTrue(ExtractApkUseCase.isSystemPath("/system/app/Foo/Foo.apk"))
        assertTrue(ExtractApkUseCase.isSystemPath("/system_ext/priv-app/Bar/Bar.apk"))
        assertTrue(ExtractApkUseCase.isSystemPath("/product/app/Baz/Baz.apk"))
        assertTrue(ExtractApkUseCase.isSystemPath("/vendor/app/Qux/Qux.apk"))
        assertTrue(ExtractApkUseCase.isSystemPath("/apex/com.android.foo/app/A.apk"))
    }

    @Test
    fun `user installed apks do not`() {
        assertFalse(ExtractApkUseCase.isSystemPath("/data/app/~~abc==/com.example-1/base.apk"))
        assertFalse(ExtractApkUseCase.isSystemPath(null))
        // Ein Verzeichnis, das nur so heißt, liegt trotzdem unter /data.
        assertFalse(ExtractApkUseCase.isSystemPath("/data/app/system/base.apk"))
    }
}
