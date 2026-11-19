package com.n3k0chan.panelcasa

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.view.View
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Puerta de versión: Android 7.0 (API 24) sin columna de casa; Android 10 (API 29) con ella. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class)
class VersionGateLayoutTest {
    private fun launch(): MainActivity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

    @Test
    @Config(sdk = [24])
    fun android7HasNoHomeColumnAndPanelUsesAllTheSpace() {
        val a = launch()
        val root = a.window.decorView
        assertNull("Sin columna de casa en Android 7", root.findViewWithTag<View>(HomeColumn.TAG))
        assertNotNull(root.findViewWithTag<View>("sidebar"))
        assertNotNull(root.findViewWithTag<View>("panel-webview"))
        val web = root.findViewWithTag<View>("web-container")
        val row = web.parent as LinearLayout
        assertEquals("Panel lateral + panel web, nada más", 2, row.childCount)
        assertEquals(1f, (web.layoutParams as LinearLayout.LayoutParams).weight)
        assertFalse(VersionGate.homeEnabled())
    }

    @Test
    @Config(sdk = [28])
    fun android9HasNoHomeColumn() {
        assertNull(launch().window.decorView.findViewWithTag<View>(HomeColumn.TAG))
    }

    @Test
    @Config(sdk = [29])
    fun android10HasHomeColumnWithOneThird() {
        val a = launch()
        val root = a.window.decorView
        val home = root.findViewWithTag<View>(HomeColumn.TAG)
        assertNotNull(home)
        val web = root.findViewWithTag<View>("web-container")
        assertEquals(1f, (home.layoutParams as LinearLayout.LayoutParams).weight)
        assertEquals(2f, (web.layoutParams as LinearLayout.LayoutParams).weight)
        assertTrue(VersionGate.homeEnabled())
    }

    @Test
    @Config(sdk = [24])
    fun firstRunOpensStationPicker() {
        val a = launch()
        val next = shadowOf(a).nextStartedActivity
        assertEquals(StationPickerActivity::class.java.name, next.component?.className)
        assertTrue(next.getBooleanExtra(StationPickerActivity.EXTRA_FIRST_RUN, false))
    }

    @Test
    @Config(sdk = [24])
    fun backButtonDoesNotCloseLauncher() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        @Suppress("DEPRECATION")
        controller.get().onBackPressed()
        assertFalse(controller.get().isFinishing)
    }

    @Test
    @Config(sdk = [24])
    fun manifestDeclaresOnlyExpectedPermissions() {
        val ctx = ApplicationProvider.getApplicationContext<PanelApp>()
        @Suppress("DEPRECATION")
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
        val perms = info.requestedPermissions?.toSet() ?: emptySet()
        assertEquals(setOf("android.permission.INTERNET", "android.permission.RECEIVE_BOOT_COMPLETED"), perms)
        assertFalse(perms.any { it.contains("LOCATION") })
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [24])
class AppsTest {
    private val ctx = ApplicationProvider.getApplicationContext<PanelApp>()

    private fun install(pkg: String, cls: String, label: String) {
        val ri = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = pkg
                name = cls
                nonLocalizedLabel = label
                applicationInfo = ApplicationInfo().apply { packageName = pkg }
            }
            nonLocalizedLabel = label
        }
        shadowOf(ctx.packageManager).addResolveInfoForIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), ri)
    }

    @Test
    fun pinnedKeepsOrderAndDropsUninstalledApps() {
        install("com.android.settings", "com.android.settings.Settings", "Ajustes")
        install("com.example.radio", "com.example.radio.Main", "Radio")
        val apps = Apps(ctx)
        val keys = listOf(
            ComponentName("com.example.radio", "com.example.radio.Main").flattenToString(),
            ComponentName("com.example.gone", "com.example.gone.Main").flattenToString(),
            ComponentName("com.android.settings", "com.android.settings.Settings").flattenToString(),
        )
        assertEquals(listOf("Radio", "Ajustes"), apps.pinned(keys).map { it.label })
        assertEquals(listOf("com.android.settings/com.android.settings.Settings"), apps.defaultPinned())
    }

    @Test
    fun ownAppIsNotListed() {
        install(ctx.packageName, MainActivity::class.java.name, "Panel")
        install("com.example.radio", "com.example.radio.Main", "Radio")
        assertEquals(listOf("Radio"), Apps(ctx).all().map { it.label })
    }
}
