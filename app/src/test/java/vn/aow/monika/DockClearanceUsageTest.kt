package vn.aow.monika

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * V152 — targetSdk 36 (edge-to-edge bắt buộc): chỗ nào chừa đáy cho menu nổi cũng phải cộng thêm
 * inset thanh hệ thống, nếu không hàng cuối bị menu nổi + thanh điều hướng che.
 *
 * Hai cách đúng đang dùng trong app:
 *  1. `dockContentClearance()` — cộng sẵn inset thanh điều hướng (dùng cho `contentPadding`/`padding`);
 *  2. `.navigationBarsPadding()` đặt **trước** `padding(bottom = DockClearance)` (màn có chuỗi modifier riêng).
 *
 * Test quét **cả ba source set** (`main`, `github`, `play`) chứ không chỉ `src/main`: lỗi ở
 * `LibraryScreen` (tab "Chơi" của cả hai flavor) từng lọt vì test cũ chỉ quét `src/main`.
 */
class DockClearanceUsageTest {

    private fun repoRoot(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "app/src").isDirectory) dir = dir.parentFile
        return dir ?: error("Không tìm thấy app/src từ ${File("").absolutePath}")
    }

    private fun appFile(path: String) = File(File(repoRoot(), "app/src"), path)

    /** Mọi tệp Kotlin của app ở cả ba source set. */
    private fun kotlinSources(): List<File> =
        listOf("main", "github", "play")
            .map { File(File(repoRoot(), "app/src"), it) }
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }

    private fun File.relativePath() = relativeTo(repoRoot()).path.replace('\\', '/')

    /** Tệp khai báo hằng số (chỗ duy nhất được dùng `DockClearance` mà không cần inset). */
    private fun isDeclaration(text: String) = text.contains("val DockClearance =")

    /**
     * Luật chung cho cả ba source set: dùng `DockClearance` (110dp của menu nổi) thì phải tự bù inset
     * thanh điều hướng ở đâu đó trong cùng màn, hoặc dùng `dockContentClearance()`.
     */
    @Test fun rawDockClearanceNeedsNavigationBarInsetInEverySourceSet() {
        val insetHelpers = listOf("navigationBarsPadding", "windowInsetsPadding", "safeDrawingPadding")
        val offenders = kotlinSources().filter { file ->
            val text = file.readText()
            text.contains("DockClearance") &&
                !isDeclaration(text) &&
                insetHelpers.none { text.contains(it) }
        }.map { it.relativePath() }

        assertTrue(
            "Tệp dùng `DockClearance` trần mà không bù inset thanh điều hướng " +
                "(dùng `dockContentClearance()` hoặc `.navigationBarsPadding()` trước `padding(bottom = …)`): $offenders",
            offenders.isEmpty(),
        )
    }

    /** Các màn đã sửa phải dùng đúng helper, không quay lại `bottom = DockClearance` trần. */
    @Test fun screensUseDockContentClearance() {
        val screens = listOf(
            "main/java/vn/aow/monika/ui/screens/GameDetailScreen.kt",
            "main/java/vn/aow/monika/ui/screens/LibrarySections.kt",
            "main/java/vn/aow/monika/ui/screens/LibraryFiles.kt",
            "github/java/vn/aow/monika/ui/screens/DownloadsScreen.kt",
            "github/java/vn/aow/monika/ui/screens/GamesScreen.kt",
            "github/java/vn/aow/monika/ui/screens/SearchScreen.kt",
            "github/java/vn/aow/monika/ui/screens/LibraryScreen.kt",
            "github/java/vn/aow/monika/ui/screens/HomeScreen.kt",
            "play/java/vn/aow/monika/ui/screens/LibraryScreen.kt",
        )
        for (path in screens) {
            val file = appFile(path)
            assertTrue("Không thấy tệp $path", file.isFile)
            val text = file.readText()
            assertTrue("$path phải dùng dockContentClearance() cho đệm đáy", text.contains("dockContentClearance()"))
            assertTrue("$path còn `bottom = DockClearance` trần", !text.contains("bottom = DockClearance"))
        }
    }
}
