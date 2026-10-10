package vn.aow.monika.ui.theme

import android.app.ActivityManager
import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/*
 * Hệ thiết kế "Aow Monika" (theo design spec của sếp):
 * nền cream sáng, mặt charcoal tương phản, gradient cam–hồng–tím, bo góc lớn, bóng mềm, menu nổi.
 * Mọi màn hình chỉ lấy màu/chữ/bo góc/chuyển động từ đây.
 */

@Immutable
data class MonikaColors(
    val bg: Color,
    val bgWarm: Color,
    val surface: Color,
    val surfaceSoft: Color,
    val surfaceDark: Color,
    val surfaceDarkSoft: Color,
    val glass: Color,
    val glassBorder: Color,
    val text: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textOnDark: Color,
    val textOnDarkSecondary: Color,
    val chip: Color,
    val chipText: Color,
    val track: Color,
    val accentOrange: Color = Color(0xFFFFA44F),
    val accentCoral: Color = Color(0xFFFF806E),
    val accentPink: Color = Color(0xFFED62B7),
    val accentPurple: Color = Color(0xFF9975FF),
    val accentBlue: Color = Color(0xFF638EFF),
    val accentCyan: Color = Color(0xFF66CFF3),
    val accentGreen: Color = Color(0xFF63D68A),
    val accentYellow: Color = Color(0xFFFFC95C),
    val success: Color = Color(0xFF29B765),
    val warning: Color = Color(0xFFFFAD35),
    val danger: Color = Color(0xFFF25962),
    // Tay cầm nằm trên ảnh game: mặt sáng cố định cho cả theme sáng/tối.
    val controlSurface: Color = Color(0xFFF7F2EC),
    val controlInk: Color = Color(0xFF181719),
    val isDark: Boolean,
    val aow: AowColors = AowColors(),
)

/** Token vẽ lại từ ui-kit AowVN; nhãn cam dùng mực đen để đạt 4.5:1. */
@Immutable
data class AowColors(
    val orange: Color = Color(0xFFFD4C0F),
    val orangeLight: Color = Color(0xFFFC5217),
    val orangeSelected: Color = Color(0xFFEA2D01),
    val orangePressed: Color = Color(0xFFD92501),
    val dark: Color = Color(0xFF171919),
    val darkPressed: Color = Color(0xFF8E0F01),
    val disabled: Color = Color(0xFF79797A),
    val disabledDark: Color = Color(0xFF575859),
    val outline: Color = Color.Black,
    val focus: Color = Color(0xFFFFF61F),
    val padSurface: Color = Color(0xFF282C2F),
    val ink: Color = Color.Black,
    val onDark: Color = Color.White,
    val outerRim: Color = Color(0xFFFEFEFA),
    val xboxA: Color = Color(0xFF6CE12C),
    val xboxB: Color = Color(0xFFFF362A),
    val xboxX: Color = Color(0xFF1C97FB),
    val xboxY: Color = Color(0xFFF8DA03),
)

private val LightColors = MonikaColors(
    bg = Color(0xFFF4F5F2), bgWarm = Color(0xFFF7F2EC), surface = Color(0xFFFFFFFF), surfaceSoft = Color(0xFFF1F2EF),
    surfaceDark = Color(0xFF1E1D1F), surfaceDarkSoft = Color(0xFF29272A),
    glass = Color(0xB8181719), glassBorder = Color(0x24FFFFFF),
    text = Color(0xFF181719), textSecondary = Color(0xFF737077), textTertiary = Color(0xFFA4A1A6),
    textOnDark = Color.White, textOnDarkSecondary = Color(0xFFC8C5CB),
    chip = Color(0xFFECEDEA), chipText = Color(0xFF353337), track = Color(0xFFE1E1DE), isDark = false,
)

private val DarkColors = LightColors.copy(
    bg = Color(0xFF141315), bgWarm = Color(0xFF171517), surface = Color(0xFF1E1D1F), surfaceSoft = Color(0xFF29272A),
    surfaceDark = Color(0xFF29272A), surfaceDarkSoft = Color(0xFF343236),
    text = Color.White, textSecondary = Color(0xFFC8C5CB), textTertiary = Color(0xFF8C8990),
    chip = Color(0xFF29272A), chipText = Color(0xFFE6E3E8), track = Color(0xFF343236), isDark = true,
)

/** Gradient chính cam → san hô → hồng, góc 110°. */
fun primaryGradient(angleDeg: Float = 110f): Brush = angled(angleDeg, 0f to Color(0xFFFFB052), 0.5f to Color(0xFFFF7F78), 1f to Color(0xFFE95CC8))
fun secondaryGradient(): Brush = angled(135f, 0f to Color(0xFF8076FF), 1f to Color(0xFF628DF4))

private fun angled(angleDeg: Float, vararg stops: Pair<Float, Color>): Brush {
    val rad = Math.toRadians(angleDeg.toDouble() - 90)
    val dx = cos(rad).toFloat() * 500f
    val dy = sin(rad).toFloat() * 500f
    return Brush.linearGradient(*stops, start = Offset(250f - dx, 250f - dy), end = Offset(250f + dx, 250f + dy))
}

@Immutable
data class MonikaType(
    val display: TextStyle,
    val pageTitle: TextStyle,
    val sectionTitle: TextStyle,
    val cardTitle: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val caption: TextStyle,
    val button: TextStyle,
)

/** Font: Manrope nếu có trong res/font, không thì font hệ thống. Đổi font: sửa 1 dòng ở MonikaTheme. */
private fun monikaType(family: FontFamily) = MonikaType(
    display = TextStyle(fontFamily = family, fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    pageTitle = TextStyle(fontFamily = family, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    sectionTitle = TextStyle(fontFamily = family, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    cardTitle = TextStyle(fontFamily = family, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    body = TextStyle(fontFamily = family, fontSize = 15.sp, lineHeight = 21.sp),
    bodyStrong = TextStyle(fontFamily = family, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    caption = TextStyle(fontFamily = family, fontSize = 12.sp, lineHeight = 16.sp),
    button = TextStyle(fontFamily = family, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
)

object Radius {
    val hero = RoundedCornerShape(28.dp)
    val large = RoundedCornerShape(24.dp)
    val medium = RoundedCornerShape(20.dp)
    val small = RoundedCornerShape(16.dp)
    val thumb = RoundedCornerShape(14.dp)
    val pill = RoundedCornerShape(percent = 50)
}

/**
 * Chuyển động tự điều chỉnh theo cấu hình máy:
 * - FULL: máy khỏe → đầy đủ hiệu ứng (trượt, co giãn, bóng).
 * - LITE: máy yếu (RAM thấp / ít nhân) → chỉ mờ dần, thời gian ngắn, bớt bóng.
 * - OFF: user tắt hiệu ứng trong Cài đặt Android (tỉ lệ hoạt ảnh = 0) → không chuyển động.
 */
enum class PerformanceTier { FULL, LITE, OFF }

@Immutable
data class MonikaMotion(val tier: PerformanceTier) {
    private val scale = when (tier) { PerformanceTier.FULL -> 1f; PerformanceTier.LITE -> 0.6f; PerformanceTier.OFF -> 0f }
    val fast get() = (140 * scale).toInt()
    val normal get() = (220 * scale).toInt()
    val slow get() = (320 * scale).toInt()
    val rich get() = tier == PerformanceTier.FULL
    val enabled get() = tier != PerformanceTier.OFF
    val easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
}

fun detectTier(context: Context): PerformanceTier {
    val animScale = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)
    if (animScale == 0f) return PerformanceTier.OFF
    val am = context.getSystemService(ActivityManager::class.java)
    val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
    val totalGb = mem.totalMem / (1024.0 * 1024 * 1024)
    val cores = Runtime.getRuntime().availableProcessors()
    return if (am.isLowRamDevice || totalGb < 3.0 || cores < 6) PerformanceTier.LITE else PerformanceTier.FULL
}

/** Lựa chọn hiệu ứng của user (Cài đặt → Hiệu ứng), đổi là giao diện áp dụng ngay. */
object MotionSetting {
    val mode = androidx.compose.runtime.mutableStateOf(vn.aow.monika.AppGraph.prefs.motionMode)
    fun set(value: String) {
        vn.aow.monika.AppGraph.prefs.motionMode = value
        mode.value = value
    }
}

val LocalMonikaColors = staticCompositionLocalOf { LightColors }
val LocalMonikaType = staticCompositionLocalOf { monikaType(FontFamily.Default) }
val LocalMonikaMotion = staticCompositionLocalOf { MonikaMotion(PerformanceTier.FULL) }

object Monika {
    val colors: MonikaColors @Composable get() = LocalMonikaColors.current
    val type: MonikaType @Composable get() = LocalMonikaType.current
    val motion: MonikaMotion @Composable get() = LocalMonikaMotion.current
}

@Composable
fun MonikaTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val c = if (isSystemInDarkTheme()) DarkColors else LightColors
    val type = remember { monikaType(AppFonts.family) }
    val mode = MotionSetting.mode.value
    val motion = remember(mode) {
        MonikaMotion(runCatching { PerformanceTier.valueOf(mode) }.getOrElse { detectTier(context) })
    }
    val scheme = (if (c.isDark) darkColorScheme() else lightColorScheme()).copy(
        primary = c.accentCoral, onPrimary = Color.White,
        secondary = c.accentPink, background = c.bg, onBackground = c.text,
        surface = c.surface, onSurface = c.text, surfaceVariant = c.surfaceSoft, onSurfaceVariant = c.textSecondary,
        surfaceContainer = c.surface, surfaceContainerHigh = c.surface, surfaceContainerHighest = c.surface,
        outline = c.track, outlineVariant = c.track, error = c.danger,
    )
    CompositionLocalProvider(LocalMonikaColors provides c, LocalMonikaType provides type, LocalMonikaMotion provides motion) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = Shapes(extraSmall = Radius.thumb, small = Radius.small, medium = Radius.medium, large = Radius.large, extraLarge = Radius.hero),
            typography = Typography(
                titleLarge = type.sectionTitle, titleMedium = type.cardTitle, bodyLarge = type.body, bodyMedium = type.body,
                bodySmall = type.caption, labelLarge = type.button,
            ),
            content = content,
        )
    }
}
