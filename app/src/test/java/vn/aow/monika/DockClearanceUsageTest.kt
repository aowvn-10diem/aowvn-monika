package vn.aow.monika

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * V152 — targetSdk 36 (edge-to-edge): màn có thanh nổi Monika phải bù thêm inset thanh hệ thống
 * bằng `dockContentClearance()`, không để `DockClearance` trần (nội dung bị thanh điều hướng che).
 *
 * `LibraryFiles.kt` đã đúng từ main; test khoá cả `LibrarySections.kt` và `GameDetailScreen.kt`
 * để không tái phát.
 */
class DockClearanceUsageTest {

    private fun appModule(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "src/main/java/vn/aow/monika/ui/screens").isDirectory) dir = dir.parentFile
        return dir ?: error("Không tìm thấy thư mục app/src/main/java/vn/aow/monika/ui/screens")
    }

    @Test fun screensAddNavigationBarInsetToDockClearance() {
        val screens = File(appModule(), "src/main/java/vn/aow/monika/ui/screens")
        val bare = screens.listFiles { file -> file.extension == "kt" }.orEmpty()
            .filter { it.readText().contains("bottom = DockClearance") }
            .map { it.name }
        assertTrue("Còn màn dùng DockClearance trần (thiếu inset thanh điều hướng): $bare", bare.isEmpty())
        for (name in listOf("LibrarySections.kt", "GameDetailScreen.kt")) {
            val text = File(screens, name).readText()
            assertTrue("$name phải dùng dockContentClearance()", text.contains("bottom = dockContentClearance()"))
        }
    }
}
