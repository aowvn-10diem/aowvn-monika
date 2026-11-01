package vn.aow.monika.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.aow.monika.config.AppInfo
import vn.aow.monika.ui.theme.MonikaTheme

/** V157 [A71]: quyết định chặn và màn "Cần cập nhật". */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class ForceUpdateGateTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val app = AppInfo(
        latestVersionCode = 60,
        latestVersionName = "0.9.0",
        minVersionCode = 55,
        apkUrl = "https://github.com/aowvn-10diem/aowvn-monika/releases/download/v0.9.0/AowVN-Monika-v0.9.0.apk",
        changelog = "Sửa lỗi màn hình NDS.",
    )

    @Test fun blocksOnlyWhenServerSaysCurrentBuildIsTooOld() {
        assertTrue(needsForceUpdate(minVersionCode = 55, installedVersionCode = 50, configFromServer = true))
        assertFalse(needsForceUpdate(minVersionCode = 50, installedVersionCode = 50, configFromServer = true))
        assertFalse(needsForceUpdate(minVersionCode = 55, installedVersionCode = 60, configFromServer = true))
        // Chưa từng tải được cấu hình (chỉ có bản trong APK): không bao giờ chặn.
        assertFalse(needsForceUpdate(minVersionCode = 55, installedVersionCode = 50, configFromServer = false))
        // minVersionCode 0 = máy chủ không đặt mức tối thiểu.
        assertFalse(needsForceUpdate(minVersionCode = 0, installedVersionCode = 1, configFromServer = true))
    }

    @Test fun blockedScreenHidesAppAndOffersApkDownload() {
        var downloaded: String? = null
        rule.setContent {
            MonikaTheme {
                ForceUpdateGate(app, installedVersionCode = 50, configFromServer = true, apkDownloadAllowed = true, onDownloadApk = { downloaded = it }) {
                    Text("Nội dung app")
                }
            }
        }
        rule.onNodeWithText("Cần cập nhật").assertIsDisplayed()
        rule.onNodeWithText("Bản đang cài đã cũ. Cập nhật lên 0.9.0 để tiếp tục dùng Aow Monika.").assertIsDisplayed()
        rule.onNodeWithText("Nội dung app").assertDoesNotExist()
        rule.onNodeWithText("Tải bản mới").performClick()
        assertEquals(app.apkUrl, downloaded)
    }

    @Test fun playBuildOnlySaysUpdateOnGooglePlay() {
        rule.setContent {
            MonikaTheme {
                ForceUpdateGate(app, installedVersionCode = 50, configFromServer = true, apkDownloadAllowed = false) {
                    Text("Nội dung app")
                }
            }
        }
        rule.onNodeWithText("Cần cập nhật").assertIsDisplayed()
        rule.onNodeWithText("Cập nhật trên Google Play").assertIsDisplayed()
        rule.onNodeWithText("Tải bản mới").assertDoesNotExist()
    }

    @Test fun upToDateOrOfflineKeepsAppRunning() {
        rule.setContent {
            MonikaTheme {
                ForceUpdateGate(app, installedVersionCode = 60, configFromServer = true, apkDownloadAllowed = true) {
                    Text("Nội dung app")
                }
            }
        }
        rule.onNodeWithText("Nội dung app").assertIsDisplayed()
        rule.onNodeWithText("Cần cập nhật").assertDoesNotExist()
    }

    @Test fun offlineWithTooOldBuildIsNotBlocked() {
        rule.setContent {
            MonikaTheme {
                ForceUpdateGate(app, installedVersionCode = 50, configFromServer = false, apkDownloadAllowed = true) {
                    Text("Nội dung app")
                }
            }
        }
        rule.onNodeWithText("Nội dung app").assertIsDisplayed()
        rule.onNodeWithText("Cần cập nhật").assertDoesNotExist()
    }
}
