package vn.aow.monika

import android.app.Activity
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import vn.aow.monika.diag.Diagnostics
import vn.aow.monika.play.PlayLinks

/**
 * Nhóm `unknown/op-fail`: máy không có app nhận liên kết/không chia sẻ được là chuyện của máy người chơi.
 * Trước đây mỗi lần như vậy sinh một báo cáo `op:mo-lien-ket` / `op:chia-se-hoi-nhom` (tên op ngoài
 * [Diagnostics.Op], mỗi tên tự thành một nhóm `unknown`). Nay chỉ còn vệt sự kiện.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class PlayLinksNoReceiverTest {
    @Before fun prepare() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        AppGraph.init(app)
        // Kiểm thử không gửi mạng; chỉ đọc kho báo cáo cục bộ.
        Diagnostics.opSender = null
        Diagnostics.resetOpThrottle()
        // Không có activity nào đăng ký nhận ACTION_VIEW/ACTION_CHOOSER → startActivity ném
        // ActivityNotFoundException, đúng ca "máy không có trình duyệt/app nhận".
        shadowOf(app).checkActivities(true)
    }

    @Test fun missingReceiverLeavesOnlyCrumbNotAnOpFailReport() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val before = Diagnostics.list(activity).size
        PlayLinks.open(activity, "https://facebook.com/aowvn")
        PlayLinks.share(activity, null, "Fixture question")
        assertEquals(before, Diagnostics.list(activity).size)
    }
}
