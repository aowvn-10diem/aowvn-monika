package vn.aow.monika.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Hệ thiết kế theo Microsoft Fluent 2 (https://fluent2.microsoft.design/):
 * - Màu: bảng "brand ramp" + các lớp nền trung tính (background 1–4), nét viền (stroke) mảnh.
 * - Chữ: thang Fluent (Caption → Title → Display), tiêu đề dùng Semibold.
 * - Bo góc: 4dp cho điều khiển, 8dp cho thẻ, 12dp cho hộp thoại.
 * - Độ nổi: bóng nhẹ + viền 1dp thay cho mảng màu đậm.
 * Đổi màu thương hiệu: chỉ sửa khối "Brand ramp" bên dưới.
 */

// Brand ramp (hồng đỏ AowVN, theo cách Fluent chia sắc độ 60 → 160).
private val Brand60 = Color(0xFFA3163F)
private val Brand70 = Color(0xFFBC1D4B)
private val Brand80 = Color(0xFFD42A5B) // Màu chính ở nền sáng
private val Brand90 = Color(0xFFE2426F)
private val Brand100 = Color(0xFFEC6189)
private val Brand110 = Color(0xFFF383A3) // Màu chính ở nền tối
private val Brand120 = Color(0xFFF8A6BE)
private val Brand150 = Color(0xFF4A1426)
private val Brand160 = Color(0xFFFDEEF2)

@Immutable
data class FluentColors(
    val brandForeground: Color,
    val brandPressed: Color,
    val brandBackground: Color,
    val onBrand: Color,
    val brandSubtle: Color,
    val onBrandSubtle: Color,
    val background1: Color,
    val background2: Color,
    val background3: Color,
    val background4: Color,
    val foreground1: Color,
    val foreground2: Color,
    val foreground3: Color,
    val foregroundDisabled: Color,
    val stroke1: Color,
    val stroke2: Color,
    val success: Color,
    val danger: Color,
    val isDark: Boolean,
)

private val LightFluent = FluentColors(
    brandForeground = Brand80, brandPressed = Brand60, brandBackground = Brand80, onBrand = Color.White,
    brandSubtle = Brand160, onBrandSubtle = Brand70,
    background1 = Color(0xFFFFFFFF), background2 = Color(0xFFFAFAFA), background3 = Color(0xFFF5F5F5), background4 = Color(0xFFF0F0F0),
    foreground1 = Color(0xFF242424), foreground2 = Color(0xFF424242), foreground3 = Color(0xFF616161), foregroundDisabled = Color(0xFFBDBDBD),
    stroke1 = Color(0xFFD1D1D1), stroke2 = Color(0xFFE0E0E0),
    success = Color(0xFF107C10), danger = Color(0xFFC50F1F), isDark = false,
)

private val DarkFluent = FluentColors(
    brandForeground = Brand110, brandPressed = Brand90, brandBackground = Brand100, onBrand = Color(0xFF1F1F1F),
    brandSubtle = Brand150, onBrandSubtle = Brand120,
    background1 = Color(0xFF292929), background2 = Color(0xFF1F1F1F), background3 = Color(0xFF141414), background4 = Color(0xFF0A0A0A),
    foreground1 = Color(0xFFFFFFFF), foreground2 = Color(0xFFD6D6D6), foreground3 = Color(0xFFADADAD), foregroundDisabled = Color(0xFF5C5C5C),
    stroke1 = Color(0xFF666666), stroke2 = Color(0xFF3D3D3D),
    success = Color(0xFF54B054), danger = Color(0xFFDC626D), isDark = true,
)

/** Thang chữ Fluent 2. */
@Immutable
data class FluentType(
    val display: TextStyle = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold),
    val title1: TextStyle = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    val title2: TextStyle = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    val title3: TextStyle = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    val body1Strong: TextStyle = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    val body1: TextStyle = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    val body2: TextStyle = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    val caption1Strong: TextStyle = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    val caption1: TextStyle = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    val caption2: TextStyle = TextStyle(fontSize = 11.sp, lineHeight = 14.sp),
)

object FluentRadius {
    val control = RoundedCornerShape(4.dp)
    val card = RoundedCornerShape(8.dp)
    val dialog = RoundedCornerShape(12.dp)
    val circular = RoundedCornerShape(percent = 50)
}

val LocalFluentColors = staticCompositionLocalOf { LightFluent }
val LocalFluentType = staticCompositionLocalOf { FluentType() }

object Fluent {
    val colors: FluentColors @Composable get() = LocalFluentColors.current
    val type: FluentType @Composable get() = LocalFluentType.current
}

@Composable
fun MonikaTheme(content: @Composable () -> Unit) {
    val c = if (isSystemInDarkTheme()) DarkFluent else LightFluent
    val type = FluentType()
    // Map sang Material 3 để các thành phần hệ thống (hộp thoại, ô nhập, vòng xoay) cũng mang màu Fluent.
    val scheme = (if (c.isDark) darkColorScheme() else lightColorScheme()).copy(
        primary = c.brandBackground, onPrimary = c.onBrand,
        primaryContainer = c.brandSubtle, onPrimaryContainer = c.onBrandSubtle,
        secondary = c.brandForeground, onSecondary = c.onBrand,
        background = c.background2, onBackground = c.foreground1,
        surface = c.background1, onSurface = c.foreground1,
        surfaceVariant = c.background3, onSurfaceVariant = c.foreground2,
        surfaceContainer = c.background1, surfaceContainerHigh = c.background1, surfaceContainerHighest = c.background1,
        outline = c.stroke1, outlineVariant = c.stroke2, error = c.danger,
    )
    CompositionLocalProvider(LocalFluentColors provides c, LocalFluentType provides type) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = Shapes(
                extraSmall = FluentRadius.control, small = FluentRadius.control,
                medium = FluentRadius.card, large = FluentRadius.dialog, extraLarge = FluentRadius.dialog,
            ),
            typography = Typography(
                headlineSmall = type.title2, titleLarge = type.title2, titleMedium = type.body1Strong,
                bodyLarge = type.body1, bodyMedium = type.body2, bodySmall = type.caption1,
                labelLarge = type.body2.copy(fontWeight = FontWeight.SemiBold), labelMedium = type.caption1, labelSmall = type.caption2,
            ),
            content = content,
        )
    }
}
