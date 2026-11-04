package vn.aow.monika.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import vn.aow.monika.BuildConfig
import vn.aow.monika.diag.Diagnostics
import vn.aow.monika.security.OfficialCert
import vn.aow.monika.ui.theme.Monika
import vn.aow.monika.ui.theme.Radius

/**
 * [A78] V154: đọc chứng thư của APK đang chạy rồi hiện cảnh báo đỏ nếu không khớp bản chính thức.
 *
 * Chỉ cảnh báo, không chặn chạy (GPLv3 cho phép bản tự build); bản debug bỏ qua hoàn toàn.
 *
 * [message] do từng flavor cung cấp (`OFFICIAL_BUILD_WARNING` trong source set github/play):
 * bản GitHub được mời tải tại monika.aow.vn, còn bản Play **không được** dẫn link tải ngoài Play
 * (chính sách Google Play, A65/A68) nên phải dùng câu khác.
 */
@Composable
fun OfficialBuildWarning(officialSha256: String, message: String) {
    val context = LocalContext.current
    val actual = remember { Diagnostics.certSha256(context) }
    OfficialBuildWarning(actual, officialSha256, BuildConfig.DEBUG, message)
}

/** Phần vẽ tách riêng để test được đủ 4 ca trên JVM, không cần chứng thư thật. */
@Composable
internal fun OfficialBuildWarning(actualSha256: String, officialSha256: String, debugBuild: Boolean, message: String) {
    if (!OfficialCert.isForeignBuild(actualSha256, officialSha256, debugBuild)) return
    val c = Monika.colors
    Column(
        Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(Radius.medium)
            .background(c.danger.copy(alpha = 0.12f))
            .border(1.dp, c.danger, Radius.medium)
            .padding(PaddingValues(horizontal = 14.dp, vertical = 12.dp)),
    ) {
        // Không có nút tắt: thẻ chỉ biến mất khi app đúng chứng thư chính thức.
        Text(message, style = Monika.type.body, color = c.danger)
    }
}
