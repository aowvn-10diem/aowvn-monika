package vn.aow.monika.runner

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** V89: bước tiến độ khởi động NDS (lõi → ROM → khung game) và mốc 60 giây. Bổ sung cho [StartupProgressTest]; đồng hồ là biến giả. */
@OptIn(ExperimentalCoroutinesApi::class)
class StartupProgressStepsTest {
    @Test fun ndsFirstLaunchRecordsEveryStageInOrder() {
        var clock = 0L
        val crumbs = mutableListOf<String>()
        val p = StartupProgress({ clock }, crumbs::add)
        clock = 100; p.update("core-download", CoreProgress("core-download", 0, 4_000_000).text)
        clock = 2_100; p.update("core-download", CoreProgress("core-download", 2_000_000, 4_000_000).text)
        clock = 2_600; p.update("core-unzip", CoreProgress("core-unzip").text)
        clock = 3_600; p.update("game-prepare", "Đang chuẩn bị ROM: 1.5 MB")
        clock = 3_700; p.update("rom-read", "Đang nạp ROM: 1.5 MB")
        clock = 4_200; p.update("core-load-game", "Lõi đang khởi tạo game (retro_load_game) · ROM 1.5 MB")
        clock = 9_200; p.update("surface-wait", "Đang chuẩn bị khung game…")
        clock = 9_500; p.finish()
        assertEquals(
            listOf(
                "core-check=100ms", "begin=core-download",
                "core-download=2500ms", "begin=core-unzip",
                "core-unzip=1000ms", "begin=game-prepare",
                "game-prepare=100ms", "begin=rom-read",
                "rom-read=500ms", "begin=core-load-game",
                "core-load-game=5000ms", "begin=surface-wait",
                "surface-wait=300ms", "begin=done",
            ),
            crumbs,
        )
        val lines = p.describe().lines()
        assertEquals(
            listOf("core-check=100ms", "core-download=2500ms", "core-unzip=1000ms", "game-prepare=100ms",
                "rom-read=500ms", "core-load-game=5000ms", "surface-wait=300ms"),
            lines.take(7),
        )
        assertEquals("total=9500ms", lines.last())
        assertEquals("Game đã lên hình", p.text())
    }

    @Test fun warmLaunchUsesCoreCacheAndSameStageUpdateChangesTextOnly() {
        var clock = 0L
        val crumbs = mutableListOf<String>()
        val p = StartupProgress({ clock }, crumbs::add)
        clock = 40; p.update("core-cache", CoreProgress("core-cache", 3_000_000, 3_000_000).text)
        clock = 60; p.update("core-cache", "Vẫn dùng lõi đã lưu")
        assertEquals("Vẫn dùng lõi đã lưu", p.text())
        clock = 70; p.update("surface-wait", "Đang chuẩn bị khung game…")
        clock = 100; p.finish()
        assertEquals(
            listOf("core-check=40ms", "begin=core-cache", "core-cache=30ms", "begin=surface-wait", "surface-wait=30ms", "begin=done"),
            crumbs,
        )
    }

    @Test fun isSlowFlipsAtExactlySixtySecondsAndStopsOnceFinished() {
        var clock = 0L
        val p = StartupProgress({ clock })
        clock = 59_999; assertFalse(p.isSlow())
        clock = 60_000; assertTrue(p.isSlow())
        p.finish(); assertFalse(p.isSlow())
    }

    @Test fun awaitSoftTimeoutReturnsAtOnceWhenAlreadyPastSixtySeconds() = runTest {
        var clock = 0L
        val p = StartupProgress({ clock })
        clock = 61_000
        assertTrue(p.awaitSoftTimeout())
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test fun slowStartupDescribeNamesStuckStageAndTotal() {
        var clock = 0L
        val p = StartupProgress({ clock })
        clock = 1_000; p.update("rom-read", "Đang nạp ROM: 700.0 MB")
        clock = 60_000
        assertTrue(p.isSlow())
        assertEquals("core-check=1000ms\nrom-read=59000ms (hiện tại)\ntotal=60000ms", p.describe())
    }

    @Test fun updatesAfterFinishAreIgnoredAndAddNoBreadcrumbs() {
        var clock = 0L
        val crumbs = mutableListOf<String>()
        val p = StartupProgress({ clock }, crumbs::add)
        clock = 10; p.update("surface-wait", "Đang chuẩn bị khung game…")
        clock = 40; p.finish()
        val atFinish = crumbs.toList()
        clock = 50; p.update("core-load-game", "muộn")
        clock = 90; p.finish()
        assertEquals(atFinish, crumbs)
        assertEquals("Game đã lên hình", p.text())
        assertFalse(p.describe().contains("core-load-game"))
    }

    @Test fun coreProgressTextCoversEveryLaunchStage() {
        assertEquals("Đang kiểm tra lõi…", CoreProgress("core-check").text)
        assertEquals("Đang giải nén lõi…", CoreProgress("core-unzip").text)
        assertEquals("Dùng lõi đã lưu trong bộ nhớ đệm", CoreProgress("core-cache", 3_000_000, 3_000_000).text)
        assertEquals("Đang tải dữ liệu hệ thống…", CoreProgress("system-download").text)
        assertEquals("Đang kiểm tra lõi…", CoreProgress("unknown-step").text)
    }

    @Test fun coreDownloadPercentClampsAndIgnoresZeroTotal() {
        assertEquals(0, CoreProgress("core-download", 0, 4_000_000).percent)
        assertEquals(33, CoreProgress("core-download", 1_000_000, 3_000_000).percent)
        assertEquals(100, CoreProgress("core-download", 5_000_000, 4_000_000).percent)
        assertNull(CoreProgress("core-download", 1_000_000, 0).percent)
        assertEquals("Đang tải lõi: 1.0 MB", CoreProgress("core-download", 1_000_000, 0).text)
    }

    @Test fun megabytesUsesDotDecimalAndNeverShowsNegative() {
        assertEquals("1.0 MB", megabytes(1_000_000))
        assertEquals("1.5 MB", megabytes(1_500_000))
        assertEquals("0.0 MB", megabytes(-1))
    }
}
