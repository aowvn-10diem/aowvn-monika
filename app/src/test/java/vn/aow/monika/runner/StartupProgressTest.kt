package vn.aow.monika.runner

import kotlinx.coroutines.async
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StartupProgressTest {
    @Test fun recordsEachBoundaryWithoutRestartingSameStep() {
        var clock = 0L
        val crumbs = mutableListOf<String>()
        val p = StartupProgress({ clock }, crumbs::add)
        clock = 5; p.update("core-download", "0 MB")
        clock = 20; p.update("core-download", "1 MB")
        clock = 25; p.update("core-unzip", "Giải nén")
        clock = 30; p.update("rom-read", "ROM 500 MB")
        clock = 50; p.update("core-load-game", "Khởi tạo")
        clock = 100; p.finish()
        val report = p.describe()
        for (line in listOf("core-check=5ms", "core-download=20ms", "core-unzip=5ms", "rom-read=20ms", "core-load-game=50ms", "total=100ms"))
            assertTrue(line, report.contains(line))
        assertTrue(crumbs.contains("core-load-game=50ms"))
    }
    @Test fun softTimeoutDoesNotFinishOrCancelLoading() = runTest {
        val p = StartupProgress({ testScheduler.currentTime })
        val timeout = async { p.awaitSoftTimeout() }
        runCurrent(); advanceTimeBy(59_999); runCurrent()
        assertFalse(timeout.isCompleted)
        advanceTimeBy(1); runCurrent(); assertTrue(timeout.await())
        p.update("core-load-game", "Vẫn nạp")
        assertEquals("Vẫn nạp", p.text())
        p.finish(); assertFalse(p.isSlow())
    }
    @Test fun firstFrameBeforeTimeoutSuppressesWarning() = runTest {
        val p = StartupProgress({ testScheduler.currentTime })
        val timeout = async { p.awaitSoftTimeout() }
        runCurrent(); advanceTimeBy(30_000); p.finish()
        advanceUntilIdle(); assertFalse(timeout.await())
    }
    @Test fun downloadsWithAndWithoutContentLengthShowMegabytes() {
        val known = CoreProgress("core-download", 2_000_000, 4_000_000)
        assertEquals(50, known.percent); assertTrue(known.text.contains("2.0 MB / 4.0 MB · 50%"))
        val unknown = CoreProgress("core-download", 2_000_000)
        assertNull(unknown.percent); assertTrue(unknown.text.contains("2.0 MB")); assertFalse(unknown.text.contains("%"))
    }
}
