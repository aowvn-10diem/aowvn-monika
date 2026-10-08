package vn.aow.monika.library

import android.app.Application
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.config.MonikaConfig
import vn.aow.monika.config.SystemDef
import java.io.File

/** Quét máy tìm game: chỉ nhận đúng đuôi theo config, bỏ thư mục ẩn/hệ thống/thư mục game của Monika, giới hạn độ sâu và cỡ. */
@RunWith(RobolectricTestRunner::class)
@Config(application = vn.aow.monika.ui.TestApp::class, sdk = [34])
class DeviceScannerTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val root get() = Environment.getExternalStorageDirectory()
    private val cfg = MonikaConfig(
        systems = listOf(
            SystemDef("gba", "GBA", "libretro", extensions = listOf("gba")),
            SystemDef("nds", "NDS", "libretro", extensions = listOf("nds")),
            SystemDef("genesis", "Genesis", "libretro", extensions = listOf("md")),
            SystemDef("kirikiri", "Kirikiri", "external", extensions = listOf("xp3"), extensionsSniffed = listOf("exe")),
            SystemDef("apk", "APK", "apk", extensions = listOf("apk")),
        ),
    )

    @Before fun cleanDevice() {
        root.mkdirs()
        root.listFiles().orEmpty().forEach { it.deleteRecursively() }
        context.getSharedPreferences("device_scan", 0).edit().clear().commit()
        File(context.filesDir, "device-games.json").delete()
    }

    private fun put(path: String, size: Int = 20_000): File =
        File(root, path).apply { parentFile!!.mkdirs(); writeBytes(ByteArray(size) { 1 }) }

    private fun names(scanner: DeviceScanner) = scanner.found().map { it.relativeTo(root).path }.toSet()

    @Test fun scanKeepsOnlyConfiguredExtensionsAboveMinimumSize() {
        put("Games/a.gba"); put("Games/b.NDS")
        put("Games/tiny.gba", size = 100)
        put("Games/notes.txt"); put("Games/app.apk")

        val scanner = DeviceScanner(context)
        val count = scanner.scan(cfg)

        assertEquals(2, count)
        assertEquals(setOf("Games/a.gba", "Games/b.NDS"), names(scanner))
    }

    @Test fun scanSkipsHiddenAndSystemFoldersAndMonikaOwnLibrary() {
        put("Games/ok.gba")
        put(".secret/h.gba"); put("Games/.hidden.gba")
        put("Android/data/pkg/d.gba"); put("Android/obb/pkg/o.gba")
        put("DCIM/c.gba"); put("Music/m.gba")
        val own = File(GameStorage.games(context), "Mine").apply { mkdirs() }
        File(own, "own.gba").writeBytes(ByteArray(20_000))

        val scanner = DeviceScanner(context)
        scanner.scan(cfg)

        assertEquals(setOf("Games/ok.gba"), names(scanner))
    }

    @Test fun scanStopsBelowEightLevelsDeep() {
        val ok = (1..8).joinToString("/") { "d$it" } + "/deep.gba"
        val tooDeep = (1..9).joinToString("/") { "d$it" } + "/deeper.gba"
        put(ok); put(tooDeep)

        val scanner = DeviceScanner(context)
        scanner.scan(cfg)

        assertEquals(setOf(ok), names(scanner))
    }

    @Test fun ambiguousMdFilesNeedTheSegaHeader() {
        put("Games/notes.md") // Markdown, không phải ROM
        File(root, "Games/real.md").apply { parentFile!!.mkdirs(); writeBytes(ByteArray(0x200).also { "SEGA".toByteArray().copyInto(it, 0x100) }.copyOf(20_000)) }

        val scanner = DeviceScanner(context)
        scanner.scan(cfg)

        assertEquals(setOf("Games/real.md"), names(scanner))
    }

    @Test fun sniffedExeCountsOnlyNextToAnXp3() {
        put("Games/vn/game.xp3"); put("Games/vn/game.exe")
        put("Games/tools/setup.exe")

        val scanner = DeviceScanner(context)
        scanner.scan(cfg)

        assertEquals(setOf("Games/vn/game.xp3", "Games/vn/game.exe"), names(scanner))
    }

    @Test fun hiddenPathsAndDeletedFilesDisappearFromFoundButFilesAreNeverRemoved() {
        val a = put("Games/a.gba"); val b = put("Games/b.gba"); val c = put("Games/c.gba")
        val scanner = DeviceScanner(context)
        scanner.scan(cfg)
        assertEquals(3, scanner.found().size)

        scanner.hide(a.absolutePath)
        b.delete()

        assertEquals(setOf("Games/c.gba"), names(scanner))
        assertTrue(a.exists() && c.exists()) // Ẩn khỏi Thư viện không xóa file của người dùng.
        // Lần quét sau vẫn tôn trọng danh sách ẩn.
        scanner.scan(cfg)
        assertFalse(a.absolutePath in scanner.found().map { it.absolutePath })
    }

    @Test fun scanRecordsTimeAndReportsDirectoryProgressEveryFiftyFolders() {
        repeat(120) { put("Many/dir$it/g.gba") }
        val scanner = DeviceScanner(context)
        assertEquals(0L, scanner.lastScan)
        val progress = mutableListOf<Int>()

        val count = scanner.scan(cfg) { progress += it }

        assertEquals(120, count)
        assertTrue(progress.isNotEmpty() && progress.all { it % 50 == 0 })
        assertTrue(scanner.lastScan > 0L)
    }

    @Test fun foundIsEmptyBeforeAnyScanAndSurvivesCorruptStore() {
        val scanner = DeviceScanner(context)
        assertTrue(scanner.found().isEmpty())

        File(context.filesDir, "device-games.json").writeText("không phải json")
        assertTrue(scanner.found().isEmpty())
    }

    @Test @Config(sdk = [28]) fun oldAndroidAlwaysCanScanAll() {
        assertTrue(DeviceScanner(context).canScanAll())
    }
}
