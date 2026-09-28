package vn.aow.monika.ui

import androidx.test.core.app.ActivityScenario
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.aow.monika.MonikaApp

/** Mở app y như người dùng bấm icon: Application thật + màn hình chính (bắt crash khi khởi động). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = LaunchTest.App::class, sdk = [34])
class LaunchTest {
    /** App thật, chỉ bỏ bước khởi tạo J2ME. */
    class App : MonikaApp() {
        override fun initJ2me() {}
        // Robolectric không chạy ContentProvider khởi tạo WorkManager như máy thật → tự khởi tạo.
        override fun onCreate() {
            androidx.work.WorkManager.initialize(this, androidx.work.Configuration.Builder().build())
            super.onCreate()
        }
    }

    @Test fun coldStart() {
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            s.onActivity { org.robolectric.shadows.ShadowLooper.idleMainLooper() }
        }
    }
}
