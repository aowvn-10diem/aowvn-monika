package vn.aow.monika.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Đổi màu thương hiệu: sửa 2 dòng dưới.
private val Brand = Color(0xFFE8457C)
private val BrandDark = Color(0xFFFF8FB1)

private val Light = lightColorScheme(primary = Brand, secondary = Color(0xFF2E9E6B))
private val Dark = darkColorScheme(primary = BrandDark, secondary = Color(0xFF6FD6A4))

@Composable
fun MonikaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
