package vn.aow.monika.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.aow.monika.config.ConfigRepository
import vn.aow.monika.config.SecurityConfig
import vn.aow.monika.security.OfficialCert
import vn.aow.monika.ui.screens.FOREIGN_BUILD_WARNING
import vn.aow.monika.ui.screens.OfficialBuildWarning
import vn.aow.monika.ui.theme.MonikaTheme

/**
 * V154 [A78]: app tự kiểm chứng thư ký lúc mở.
 * (a) khớp → không cảnh báo · (b) khác → thẻ đỏ nhưng KHÔNG chặn màn hình ·
 * (c) giá trị chính thức rỗng → bỏ qua · (d) bản debug → bỏ qua.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class OfficialBuildWarningTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Before fun clock() { rule.mainClock.autoAdvance = false }

    private val official = SecurityConfig.OFFICIAL_CERT_SHA256
    private val foreign = "f".repeat(64)

    /** Dựng như Trang chủ thật: thẻ cảnh báo (nếu có) nằm cùng nội dung, không phải hộp thoại chặn. */
    private fun show(actual: String, expected: String, debug: Boolean) {
        rule.setContent { MonikaTheme { Column {
            OfficialBuildWarning(actual, expected, debug)
            Text("Nội dung Trang chủ")
        } } }
        rule.mainClock.advanceTimeBy(1_000)
    }

    private fun assertNoWarning() {
        rule.onNodeWithText(FOREIGN_BUILD_WARNING).assertDoesNotExist()
        rule.onNodeWithText("Nội dung Trang chủ").assertIsDisplayed()
    }

    @Test fun matchingCertShowsNoWarning() {
        assertFalse(OfficialCert.isForeignBuild(official, official, debugBuild = false))
        show(official, official, debug = false)
        assertNoWarning()
    }

    @Test fun differentCertWarnsWithoutBlockingTheScreen() {
        assertTrue(OfficialCert.isForeignBuild(foreign, official, debugBuild = false))
        show(foreign, official, debug = false)
        rule.onNodeWithText(FOREIGN_BUILD_WARNING).assertIsDisplayed()
        // Vẫn dùng được app: GPLv3 cho phép bản tự build, cảnh báo không phải màn chặn.
        rule.onNodeWithText("Nội dung Trang chủ").assertIsDisplayed()
        rule.shot("v154-canh-bao-app-gia-mao")
    }

    @Test fun blankOfficialCertSkipsCheck() {
        assertFalse(OfficialCert.isForeignBuild(foreign, "", debugBuild = false))
        show(foreign, "", debug = false)
        assertNoWarning()
    }

    @Test fun debugBuildSkipsCheck() {
        assertFalse(OfficialCert.isForeignBuild(foreign, official, debugBuild = true))
        show(foreign, official, debug = true)
        assertNoWarning()
    }

    @Test fun unreadableCertDoesNotAccuseTheUser() {
        assertFalse(OfficialCert.isForeignBuild("", official, debugBuild = false))
        show("", official, debug = false)
        assertNoWarning()
    }

    @Test fun officialValueIsThePmValueAndOldJsonKeepsIt() {
        assertEquals("c46902e97029a96593f43c38abd9871170a7519d90992a0b5466c752d45ab20c", official)
        // JSON cũ (chưa có khóa security) vẫn đọc được và dùng đúng chứng thư phát hành.
        assertEquals(official, ConfigRepository.parse("{}").security.officialCertSha256)
        // Cấu hình sau này đổi được, không cần sửa code.
        val custom = "a".repeat(64)
        assertEquals(custom, ConfigRepository.parse("""{"security":{"officialCertSha256":"$custom"}}""").security.officialCertSha256)
    }
}
