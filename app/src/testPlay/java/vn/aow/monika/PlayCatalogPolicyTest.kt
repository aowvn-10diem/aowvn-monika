package vn.aow.monika

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.config.ConfigRepository
import java.io.File

/**
 * [A65] phần còn lại (noibo#14): catalog Play do `scripts/play-config.py` sinh ra phải tắt
 * đăng nhập, đọc bài viết, diễn đàn và tự gửi báo lỗi; màn Cài đặt không được giữ công tắc
 * đã hết tác dụng. Test đỏ ngay khi ai bỏ một cờ trong script sinh catalog.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class PlayCatalogPolicyTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    /** Thư mục module `app`, không phụ thuộc thư mục làm việc của Gradle. */
    private fun appModule(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "src/play").isDirectory) dir = dir.parentFile
        return dir ?: error("Không tìm thấy thư mục app/src/play từ ${File("").absolutePath}")
    }

    @Test fun playCatalogTurnsOffAccountFeedForumAndAutoSend() {
        val cfg = ConfigRepository(app, OkHttpClient()).current
        assertFalse("Bản Play không bật đăng nhập", cfg.account.enabled)
        assertEquals("Bản Play không giữ URL đăng nhập", "", cfg.account.loginUrl)
        assertEquals("Bản Play không giữ database web", "", cfg.account.databaseUrl)
        assertEquals("Bản Play không giữ khoá web", "", cfg.account.apiKey)
        assertFalse("Bản Play không đọc bài viết ngoài", cfg.feed.enabled)
        assertEquals("Bản Play không giữ nguồn bài viết", "", cfg.feed.url)
        assertFalse("Bản Play không mở diễn đàn ngoài", cfg.forum.enabled)
        assertEquals("Bản Play không giữ URL diễn đàn", "", cfg.forum.baseUrl)
        assertFalse("Bản Play không tự gửi báo lỗi mặc định", cfg.crash.autoSend)
    }

    /** Catalog đặt `crash.autoSend=false` nên công tắc tự gửi trong Cài đặt hết tác dụng → không được hiện. */
    @Test fun playSettingsHasNoDeadAutoSendToggle() {
        val settings = File(appModule(), "src/play/java/vn/aow/monika/ui/screens/PlaySettings.kt").readText()
        assertFalse("Cài đặt bản Play còn công tắc tự gửi báo lỗi", settings.contains("autoSendCrash"))
    }
}
