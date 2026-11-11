package vn.aow.monika

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * V152 — targetSdk 36: app bật `enableOnBackInvokedCallback` nên Android 16 **không giao**
 * `KEYCODE_BACK` cho activity nào opt-in predictive back.
 *
 * Các activity game vẫn giữ Back kiểu cũ (RGSS/Kirikiri mở menu Monika qua `dispatchKeyEvent`,
 * midlet J2ME ánh xạ Back về phím menu). Vì vậy từng activity đó phải tự opt-out bằng
 * `android:enableOnBackInvokedCallback="false"` để hệ thống vẫn giao `KEYCODE_BACK`; app vẫn
 * opt-in chung. Test đỏ ngay khi ai gỡ cờ opt-out hoặc gỡ `configChanges` của midlet.
 */
class TargetSdk36BackOptOutTest {

    private fun appModule(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "src/play").isDirectory) dir = dir.parentFile
        return dir ?: error("Không tìm thấy thư mục app/src/play từ ${File("").absolutePath}")
    }

    /** Khối `<activity ...>` khai báo [name] (manifest có cả dạng nhiều dòng lẫn một dòng). */
    private fun activityBlock(manifest: String, name: String): String {
        val match = Regex("<activity\\b[^>]*android:name=\"[^\"]*" + Regex.escape(name) + "\"[^>]*>")
            .find(manifest)
        assertTrue("Không thấy activity $name trong manifest", match != null)
        return match!!.value
    }

    private val optOut = "android:enableOnBackInvokedCallback=\"false\""
    private val optIn = "android:enableOnBackInvokedCallback=\"true\""

    private fun configChanges(block: String): List<String> =
        Regex("android:configChanges=\"([^\"]+)\"").find(block)?.groupValues?.get(1)?.split("|").orEmpty()

    @Test fun gameActivitiesOptOutSoAndroid16StillDeliversBackKey() {
        for (flavor in listOf("github", "play")) {
            val manifest = File(appModule(), "src/$flavor/AndroidManifest.xml").readText()
            assertTrue("$flavor: application phải giữ opt-in predictive back", manifest.contains(optIn))
            for (activity in listOf("RgssGameActivity", "KirikiriGameActivity")) {
                val block = activityBlock(manifest, activity)
                assertTrue(
                    "$flavor/$activity: phải opt-out predictive back để còn nhận KEYCODE_BACK",
                    block.contains(optOut),
                )
                // Đổi kiểu điều hướng/mật độ màn hình không được dựng lại activity game (mất phiên).
                val changes = configChanges(block)
                for (expected in listOf("density", "layoutDirection", "navigation")) {
                    assertTrue("$flavor/$activity: configChanges thiếu $expected", expected in changes)
                }
            }
        }
    }

    @Test fun j2meMicroActivityOptsOutAndKeepsMidletAliveOnConfigChanges() {
        val manifest = File(appModule().parentFile, "j2me/src/main/AndroidManifest.xml").readText()
        val block = activityBlock(manifest, "javax.microedition.shell.MicroActivity")
        assertTrue(
            "MicroActivity: phải opt-out predictive back để Back còn tới onKeyUp/ánh xạ phím",
            block.contains(optOut),
        )
        val changes = configChanges(block)
        for (expected in listOf("density", "layoutDirection", "navigation")) {
            assertTrue("MicroActivity: configChanges thiếu $expected (đổi kiểu điều hướng sẽ dựng lại midlet)", expected in changes)
        }
    }
}
