package vn.aow.monika.ui

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.aow.monika.diag.Diagnostics
import vn.aow.monika.ui.theme.MonikaTheme
import java.io.File

/**
 * V166 (nhóm `unknown/anr`): mở "Nhật ký lỗi" phải đọc/parse báo cáo ở luồng nền.
 * `Diagnostics.list` đọc tới 30 file JSON (mỗi file tới 60 KB) rồi scrub từng trường; làm trong composition
 * (main thread) là chặn UI vài giây trên máy yếu → ANR của tiến trình chính (không có phiên game → `unknown/anr`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class ErrorLogMainThreadTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val threads = java.util.Collections.synchronizedList(mutableListOf<Thread>())

    @Before fun seedReports() {
        File(app.filesDir, "diag").deleteRecursively()
        // 20 báo cáo cỡ thật (detail dài như log/tombstone) — đúng lượng việc màn Nhật ký lỗi phải làm.
        repeat(20) { i ->
            Diagnostics.save(
                app,
                Diagnostics.Report(
                    id = 1_000L + i, time = 1_000L + i, kind = "anr",
                    title = "App bị treo, không phản hồi (tiến trình chính) — app:chính",
                    app = "0.9.0 (1)", device = "máy thử",
                    detail = "at vn.aow.monika.ui.MainActivity.onCreate(MainActivity.kt:1)\n".repeat(150),
                ),
            )
        }
        Diagnostics.listProbe = { threads += it }
        rule.mainClock.autoAdvance = false
    }

    @After fun reset() {
        Diagnostics.listProbe = null
        Diagnostics.logOpen.value = false
        File(app.filesDir, "diag").deleteRecursively()
    }

    @Test fun errorLogIsParsedOffTheMainThread() {
        Diagnostics.logOpen.value = true
        rule.setContent { MonikaTheme { Box { CrashUi() } } }
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            synchronized(threads) { threads.isNotEmpty() }
        }
        val main = Looper.getMainLooper().thread
        val onMain = synchronized(threads) { threads.count { it == main } }
        assertTrue("đọc/parse báo cáo phải ở luồng nền, đang chạy $onMain lần trên main thread", onMain == 0)
    }
}
