package vn.aow.monika

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.azahar.AzaharExit
import vn.aow.monika.azahar.AzaharSession
import vn.aow.monika.ui.TestApp
import java.util.concurrent.CountDownLatch

/**
 * V205: lúc đóng màn chơi 3DS phải **dừng engine + chờ luồng engine thoát rồi mới** xoá dấu phiên;
 * và chuyển sang màn cài `.cia` thì không được kill tiến trình `:game`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class AzaharExitTest {
    @get:Rule val tmp = TemporaryFolder()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    /** Handoff `.cia`: màn chơi đóng nhưng màn cài còn chạy trong tiến trình `:game` → không kill. */
    @Test fun handoffToInstallNeverKillsGameProcess() {
        assertTrue(AzaharExit.shouldKillProcess(finishing = true, handoffToInstall = false))
        assertFalse(AzaharExit.shouldKillProcess(finishing = true, handoffToInstall = true))
        // Xoay màn/chuyển nền (không finishing) không phải lúc kill tiến trình.
        assertFalse(AzaharExit.shouldKillProcess(finishing = false, handoffToInstall = false))
        assertFalse(AzaharExit.shouldKillProcess(finishing = false, handoffToInstall = true))
    }

    /** Chưa từng có luồng engine (mở `.cia` rồi chuyển màn cài) → không có gì phải chờ, được xoá dấu. */
    @Test fun sessionWithoutEngineThreadMayEnd() {
        assertTrue(AzaharExit.canEndSession(null))
    }

    /** Dấu phiên chỉ được xoá sau `join`: engine còn sống (join hết hạn) thì giữ dấu để lần sau dọn cache. */
    @Test fun sessionMarkerSurvivesWhileEngineThreadIsAlive() {
        val dir = tmp.newFolder("azahar")
        AzaharSession.begin(app, dir)
        assertEquals(AzaharSession.STARTED, AzaharSession.read(app, dir))

        val release = CountDownLatch(1)
        val engine = Thread({
            try {
                release.await()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }, "NativeEmulation-gia")
        engine.start()
        try {
            engine.join(50) // hết hạn chờ trong khi engine còn chạy
            assertTrue(engine.isAlive)
            assertFalse(AzaharExit.canEndSession(engine))
            // Giống hệt onDestroy: điều kiện sai → không xoá dấu → lần mở sau coi cache là hỏng.
            assertEquals(AzaharSession.STARTED, AzaharSession.read(app, dir))
            assertTrue(AzaharSession.shaderCacheBroken(AzaharSession.read(app, dir)))
        } finally {
            release.countDown()
        }
        engine.join(2000)

        // Luồng đã thoát hẳn → lúc này mới được kết thúc phiên.
        assertTrue(AzaharExit.canEndSession(engine))
        AzaharSession.end(app, dir)
        assertNull(AzaharSession.read(app, dir))
        assertFalse(AzaharSession.shaderCacheBroken(AzaharSession.read(app, dir)))
    }
}
