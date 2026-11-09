package vn.aow.monika.diag

import android.app.ActivityManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V202 PR1: Android dọn tiến trình nền `IMPORTANCE_CACHED` là bình thường, không phải lỗi;
 * và crumb mạng cũ không được gán nhãn `net` cho ca lowmem không có trace.
 */
class DiagnosticsLowMemFilterTest {
    private val cached = ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED
    private val foreground = ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    private val foregroundService = ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE

    @Test fun cachedBackgroundProcessIsNotReported() {
        assertFalse(Diagnostics.shouldReportLowMem(cached, "vn.aow.monika", false))
    }

    @Test fun foregroundAndServiceProcessesAreReported() {
        assertTrue(Diagnostics.shouldReportLowMem(foreground, "vn.aow.monika", false))
        assertTrue(Diagnostics.shouldReportLowMem(foregroundService, "vn.aow.monika", false))
    }

    @Test fun cachedProcessWithMatchingDeathSessionIsReported() {
        assertTrue(Diagnostics.shouldReportLowMem(cached, "vn.aow.monika:game", true))
    }

    @Test fun webViewSandboxedProcessIsNeverReported() {
        val name = "vn.aow.monika:sandboxed_process0:org.chromium.content.app.SandboxedProcessService0:0"
        assertFalse(Diagnostics.shouldReportLowMem(foreground, name, true))
    }

    @Test fun emptyTraceDoesNotMixStaleNetworkCrumbs() {
        val hint = Diagnostics.componentHint("", listOf("net", "SocketTimeoutException"))
        assertEquals("", hint)
        // Trước V202: trace rỗng + crumb mạng → component "net" (chữ ký 3305de8b).
        assertEquals("", Components.of(null, null, hint))
    }

    @Test fun traceKeepsCrumbsAsContext() {
        val hint = Diagnostics.componentHint("at vn.aow.monika.Feed.load(Feed.kt:1)", listOf("begin: libretro · melondsds"))
        assertTrue(hint.contains("Feed.load"))
        assertTrue(hint.contains("begin: libretro"))
    }
}
