package vn.aow.monika

import org.junit.Assert.assertEquals
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
 *
 * Cùng lúc test khoá **hai flavor khớp nhau**: cùng một activity phải có cùng `configChanges` và
 * cùng cờ predictive back; chỉ những activity nằm trong [GITHUB_ONLY_ACTIVITIES] mới được phép chỉ
 * có ở bản github (Play không tải/cài ngoài). Lệch do sửa một bên (như `EnginePrepActivity` từng
 * thiếu `configChanges` ở github) là đỏ ngay.
 */
class TargetSdk36BackOptOutTest {

    private fun appModule(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "src/play").isDirectory) dir = dir.parentFile
        return dir ?: error("Không tìm thấy thư mục app/src/play từ ${File("").absolutePath}")
    }

    private fun manifest(flavor: String) = File(appModule(), "src/$flavor/AndroidManifest.xml").readText()

    /** Khối `<activity ...>` khai báo [name] (manifest có cả dạng nhiều dòng lẫn một dòng). */
    private fun activityBlock(manifest: String, name: String): String {
        val match = Regex("<activity\\b[^>]*android:name=\"[^\"]*" + Regex.escape(name) + "\"[^>]*>")
            .find(manifest)
        assertTrue("Không thấy activity $name trong manifest", match != null)
        return match!!.value
    }

    /** Tên (đầy đủ) của mọi `<activity>` trong [manifest]. */
    private fun activityNames(manifest: String): Set<String> =
        Regex("<activity\\b[^>]*android:name=\"([^\"]+)\"")
            .findAll(manifest)
            .map { it.groupValues[1] }
            .toSet()

    private val optOut = "android:enableOnBackInvokedCallback=\"false\""
    private val optIn = "android:enableOnBackInvokedCallback=\"true\""

    private fun configChanges(block: String): List<String> =
        Regex("android:configChanges=\"([^\"]+)\"").find(block)?.groupValues?.get(1)?.split("|").orEmpty()

    /** Activity/`configChanges` mà mọi activity chạy game phải khai (API 36 bỏ khoá xoay ở màn lớn). */
    private val resizeSurvivingChanges = listOf(
        "orientation", "screenSize", "screenLayout", "smallestScreenSize",
        "density", "uiMode", "layoutDirection", "keyboard", "keyboardHidden", "navigation",
    )

    /** Activity chạy game/prep: đổi cỡ màn hình không được dựng lại (mất phiên game/tiến trình tải). */
    private val gameActivities = listOf(
        "RetroActivity", "EnginePrepActivity", "RgssGameActivity", "RenpyGameActivity",
        "KirikiriGameActivity", "AzaharActivity", "AzaharInstallActivity", "WebGameActivity",
    )

    /** Chỉ có ở bản github vì Play không tải/cài ngoài — khác biệt có chủ đích, không phải lệch. */
    private val githubOnlyActivities = setOf(
        ".browser.InAppBrowserActivity",
        ".apkinstall.ApkInstallActivity",
    )

    @Test fun gameActivitiesOptOutSoAndroid16StillDeliversBackKey() {
        for (flavor in listOf("github", "play")) {
            val text = manifest(flavor)
            assertTrue("$flavor: application phải giữ opt-in predictive back", text.contains(optIn))
            for (activity in listOf("RgssGameActivity", "KirikiriGameActivity")) {
                val block = activityBlock(text, activity)
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

    /** Mọi activity chạy game (kể cả `EnginePrepActivity`) sống qua đổi cỡ màn hình ở CẢ hai flavor. */
    @Test fun everyGameActivityKeepsSessionOnConfigChangeInBothFlavors() {
        for (flavor in listOf("github", "play")) {
            val text = manifest(flavor)
            for (activity in gameActivities) {
                val changes = configChanges(activityBlock(text, activity))
                for (expected in resizeSurvivingChanges) {
                    assertTrue(
                        "$flavor/$activity: configChanges thiếu $expected (API 36 đổi cỡ màn hình sẽ dựng lại activity)",
                        expected in changes,
                    )
                }
            }
        }
    }

    /** Hai flavor phải khai cùng bộ activity với cùng `configChanges`/cờ back; khác chỉ ở allowlist. */
    @Test fun bothFlavorsDeclareTheSameActivitiesWithTheSameConfigChanges() {
        val github = manifest("github")
        val play = manifest("play")
        val onlyGithub = activityNames(github) - activityNames(play)
        val onlyPlay = activityNames(play) - activityNames(github)

        assertEquals("Activity chỉ có ở bản github ngoài danh sách cố ý (Play không tải/cài ngoài): $onlyGithub", githubOnlyActivities, onlyGithub)
        assertEquals("Bản Play không được khai activity riêng ngoài bản github: $onlyPlay", emptySet<String>(), onlyPlay)

        for (name in activityNames(github) intersect activityNames(play)) {
            val g = activityBlock(github, name)
            val p = activityBlock(play, name)
            assertEquals(
                "$name: configChanges lệch giữa github và play",
                configChanges(p).sorted(),
                configChanges(g).sorted(),
            )
            assertEquals(
                "$name: cờ predictive back lệch giữa github và play",
                p.contains(optOut),
                g.contains(optOut),
            )
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
