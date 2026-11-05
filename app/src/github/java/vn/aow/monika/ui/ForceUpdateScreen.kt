package vn.aow.monika.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import vn.aow.monika.R
import vn.aow.monika.config.AppInfo
import vn.aow.monika.ui.theme.GradientButton
import vn.aow.monika.ui.theme.Illustration
import vn.aow.monika.ui.theme.Monika
import vn.aow.monika.ui.theme.Screen

/**
 * V157 [A71]: máy chủ đặt `app.minVersionCode` → bản cài cũ hơn mức đó bị chặn toàn màn hình.
 *
 * Chỉ chặn khi cấu hình đến từ máy chủ ([configFromServer]). Chưa từng tải được cấu hình (máy mới cài,
 * mất mạng lần đầu) thì app chạy bình thường: mất mạng không bao giờ tự khóa người dùng.
 */
fun needsForceUpdate(minVersionCode: Int, installedVersionCode: Int, configFromServer: Boolean): Boolean =
    configFromServer && minVersionCode > 0 && installedVersionCode < minVersionCode

/** Chặn khi cần, không thì hiện [content]. */
@Composable
fun ForceUpdateGate(
    app: AppInfo,
    installedVersionCode: Int,
    configFromServer: Boolean,
    apkDownloadAllowed: Boolean,
    onDownloadApk: (String) -> Unit = {},
    content: @Composable () -> Unit,
) {
    if (!needsForceUpdate(app.minVersionCode, installedVersionCode, configFromServer)) {
        content()
        return
    }
    ForceUpdateScreen(app, apkDownloadAllowed, onDownloadApk)
}

@Composable
private fun ForceUpdateScreen(app: AppInfo, apkDownloadAllowed: Boolean, onDownloadApk: (String) -> Unit) {
    val c = Monika.colors
    Screen {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Illustration(R.drawable.fluent3d_joystick, Modifier.size(96.dp))
            Text("Cần cập nhật", style = Monika.type.sectionTitle, color = c.text, textAlign = TextAlign.Center)
            Text(
                if (app.latestVersionName.isNotBlank()) "Bản đang cài đã cũ. Cập nhật lên ${app.latestVersionName} để tiếp tục dùng Aow Monika."
                else "Bản đang cài đã cũ. Cập nhật để tiếp tục dùng Aow Monika.",
                style = Monika.type.body, color = c.textSecondary, textAlign = TextAlign.Center,
            )
            if (app.changelog.isNotBlank()) {
                Text(app.changelog, style = Monika.type.caption, color = c.textSecondary, textAlign = TextAlign.Center)
            }
            if (apkDownloadAllowed) {
                if (app.apkUrl.isNotBlank()) {
                    GradientButton(
                        "Tải bản mới",
                        { onDownloadApk(app.apkUrl) },
                        Modifier.fillMaxWidth().widthIn(max = 360.dp),
                        icon = R.drawable.ic_fluent_arrow_download_24_regular,
                    )
                } else {
                    Text("Chưa có liên kết tải, bạn thử lại sau.", style = Monika.type.caption, color = c.textSecondary, textAlign = TextAlign.Center)
                }
            } else {
                // [A65] bản Play: chỉ nhắc cập nhật trên Google Play, không mở link APK ngoài.
                Text("Cập nhật trên Google Play", style = Monika.type.bodyStrong, color = c.text, textAlign = TextAlign.Center)
            }
        }
    }
}
