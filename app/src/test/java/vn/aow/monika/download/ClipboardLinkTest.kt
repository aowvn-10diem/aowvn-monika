package vn.aow.monika.download

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.ui.TestApp

/** V193 G2: nút "Dán từ bộ nhớ tạm" — lấy đúng text, rỗng thì trả null để UI báo nhẹ. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class ClipboardLinkTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val clipboard get() = app.getSystemService(ClipboardManager::class.java)

    @Test fun docTextVaCatKhoangTrang() {
        clipboard.setPrimaryClip(ClipData.newPlainText("link", "  https://pixeldrain.com/u/abc  "))
        assertEquals("https://pixeldrain.com/u/abc", ClipboardLink.read(app))
    }

    @Test fun clipboardRongTraNull() {
        clipboard.setPrimaryClip(ClipData.newPlainText("link", "   "))
        assertNull(ClipboardLink.read(app))
        clipboard.clearPrimaryClip()
        assertNull(ClipboardLink.read(app))
    }
}
