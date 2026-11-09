package vn.aow.monika.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import vn.aow.monika.config.ExternalApp
import vn.aow.monika.ui.theme.Monika

/**
 * Bản Play: không kèm link tải ngoài (A68) nên chỉ nhắc ứng dụng cần cài.
 * Bản GitHub có bản đầy đủ (nút cài + link) trong SettingsScreen của flavor github;
 * GameDetailScreen dùng chung nên mỗi flavor tự cung cấp phần này.
 */
@Composable
fun ExternalAppDetails(app: ExternalApp, installed: Boolean) {
    val c = Monika.colors
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            app.description.ifBlank { "Cần cài ${app.name} để chơi game này." },
            style = Monika.type.body, color = c.textSecondary,
        )
        if (app.packageNames.isNotEmpty()) {
            Text(
                "Gói ứng dụng: " + app.packageNames.joinToString(", "),
                style = Monika.type.caption, color = c.textSecondary,
            )
        }
        Text(
            if (installed) "Đã cài trên máy." else "Cài ${app.name} từ Google Play rồi mở lại trang game.",
            style = Monika.type.caption, color = c.textSecondary,
        )
    }
}
