package vn.aow.monika.ui.theme

import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.lazy.rememberLazyListState

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/* Thành phần giao diện Aow Monika. Màn hình chỉ ghép từ các thành phần này. */

/** Bóng mềm theo spec (card / floating). Máy yếu → bóng nhẹ hơn. */
@Composable
fun Modifier.softShadow(shape: Shape, floating: Boolean = false): Modifier {
    val rich = Monika.motion.rich
    val elevation = if (floating) (if (rich) 18.dp else 8.dp) else (if (rich) 10.dp else 3.dp)
    return shadow(elevation, shape, ambientColor = Color(0x331E1818), spotColor = Color(if (floating) 0x40141215 else 0x201E1818))
}

/** Nhấn thẻ/nút → thu nhỏ 0.98 (tắt khi máy tắt hiệu ứng). */
@Composable
fun Modifier.pressable(interaction: MutableInteractionSource, onClick: () -> Unit, role: Role = Role.Button, target: Float = 0.97f): Modifier {
    val motion = Monika.motion
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && motion.enabled) target else 1f, tween(motion.fast.coerceAtLeast(1)), label = "press")
    return graphicsLayer { scaleX = scale; scaleY = scale }.clickable(interaction, null, role = role, onClick = onClick)
}

@Composable
fun CircleButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: CircleStyle = CircleStyle.Soft,
    size: Dp = 48.dp,
    badge: Boolean = false,
) {
    val c = Monika.colors
    val interaction = remember { MutableInteractionSource() }
    val (bg, tint) = when (style) {
        CircleStyle.Soft -> c.surface to c.text
        CircleStyle.Glass -> c.glass to Color.White
        CircleStyle.Dark -> c.surfaceDark to Color.White
    }
    Box(
        modifier.size(size).then(if (style == CircleStyle.Soft) Modifier.softShadow(Radius.pill) else Modifier)
            .clip(Radius.pill).background(bg)
            .then(if (style == CircleStyle.Glass) Modifier.border(1.dp, c.glassBorder, Radius.pill) else Modifier)
            .pressable(interaction, onClick),
        contentAlignment = Alignment.Center,
    ) {
        MonikaIcon(icon, contentDescription, Modifier.size(22.dp), tint = tint)
        if (badge) Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(8.dp).clip(Radius.pill).background(c.accentCoral))
    }
}

enum class CircleStyle { Soft, Glass, Dark }

/** Nút chính: gradient, cao 56, bo tròn hoàn toàn. */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    subtitle: String? = null,
    enabled: Boolean = true,
    height: Dp = 56.dp,
) {
    val c = Monika.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier.heightIn(min = height).then(if (enabled) Modifier.softShadow(Radius.pill) else Modifier).clip(Radius.pill)
            .background(if (enabled) primaryGradient() else Brush.linearGradient(listOf(c.track, c.track)))
            .then(if (enabled) Modifier.pressable(interaction, onClick) else Modifier)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            MonikaIcon(icon, null, Modifier.size(22.dp), tint = Color.White)
            Spacer(Modifier.width(10.dp))
        }
        Column {
            Text(text, style = Monika.type.button, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = Monika.type.caption, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
        }
    }
}

/** Nút phụ: charcoal, cao 52. */
@Composable
fun DarkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null, subtitle: String? = null, enabled: Boolean = true) {
    val c = Monika.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier.heightIn(min = 52.dp).clip(Radius.pill).background(if (enabled) c.surfaceDark else c.track)
            .then(if (enabled) Modifier.pressable(interaction, onClick) else Modifier).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            MonikaIcon(icon, null, Modifier.size(20.dp), tint = Color.White)
            Spacer(Modifier.width(8.dp))
        }
        Column {
            Text(text, style = Monika.type.button, color = if (enabled) Color.White else c.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = Monika.type.caption, color = c.textOnDarkSecondary, maxLines = 1)
        }
    }
}

/** Nút nhẹ dạng viên thuốc (vd. "Tạm dừng tất cả", "Xem tất cả"). */
@Composable
fun SoftPillButton(text: String, onClick: () -> Unit, @DrawableRes icon: Int? = null, modifier: Modifier = Modifier) {
    val c = Monika.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier.height(40.dp).clip(Radius.pill).background(c.surface).pressable(interaction, onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            MonikaIcon(icon, null, Modifier.size(18.dp), tint = c.text)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = Monika.type.caption.copy(fontWeight = Monika.type.bodyStrong.fontWeight), color = c.text)
    }
}

/** Thanh chip lọc. accent = chip chọn dùng gradient, không thì charcoal. */
@Composable
fun <T> ChipBar(
    items: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    accent: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    scroll: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
) {
    val c = Monika.colors
    ChipOverflowHint(scroll) {
    LazyRow(state = scroll, contentPadding = contentPadding, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items) { item ->
            val on = item == selected
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier.height(36.dp).clip(Radius.pill)
                    .background(
                        when {
                            on && accent -> primaryGradient()
                            on -> Brush.linearGradient(listOf(c.surfaceDark, c.surfaceDark))
                            else -> Brush.linearGradient(listOf(c.chip, c.chip))
                        }
                    )
                    .pressable(interaction, { onSelect(item) }, Role.Tab)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label(item), style = Monika.type.caption.copy(fontWeight = Monika.type.bodyStrong.fontWeight), color = if (on) Color.White else c.chipText, maxLines = 1)
            }
        }
    }
    }
}

/** Nhãn nhỏ trên thẻ (vd. "GBA", "Việt hóa"). onDark = nằm trên ảnh/nền tối. */
@Composable
fun Tag(text: String, onDark: Boolean = false, accent: Boolean = false) {
    val c = Monika.colors
    val bg: Brush = when {
        accent -> primaryGradient()
        onDark -> Brush.linearGradient(listOf(Color(0x33FFFFFF), Color(0x33FFFFFF)))
        else -> Brush.linearGradient(listOf(c.chip, c.chip))
    }
    Text(
        text, style = Monika.type.caption, maxLines = 1,
        color = if (onDark || accent) Color.White else c.chipText,
        modifier = Modifier.clip(Radius.pill).background(bg)
            .then(if (onDark) Modifier.border(1.dp, Color(0x40FFFFFF), Radius.pill) else Modifier)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val c = Monika.colors
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = Monika.type.sectionTitle, color = c.text, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(action + "  ›", style = Monika.type.caption, color = c.textSecondary, modifier = Modifier.clip(Radius.pill).clickable(onClick = onAction).padding(8.dp))
        }
    }
}

/** Thẻ nền trắng (hoặc charcoal khi dark = true), bo 20, bóng mềm. */
@Composable
fun MonikaCard(
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    shape: Shape = Radius.medium,
    padding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Monika.colors
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier.softShadow(shape).clip(shape).background(if (dark) c.surfaceDark else c.surface)
            .then(if (onClick != null) Modifier.pressable(interaction, onClick, target = 0.98f) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** Lớp phủ tối để chữ trên ảnh luôn đọc được (spec: scrim dưới + trái, 0.65). */
fun artworkScrim(strength: Float = 0.65f): Brush = Brush.verticalGradient(
    0f to Color.Transparent, 0.35f to Color(0x14000000), 1f to Color.Black.copy(alpha = strength + 0.2f),
)

@Composable
fun GradientProgress(progress: Float, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val c = Monika.colors
    Box(modifier.fillMaxWidth().height(height).clip(Radius.pill).background(c.track)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().clip(Radius.pill).background(primaryGradient(90f)))
    }
}

@Composable
fun Spinner(modifier: Modifier = Modifier) {
    CircularProgressIndicator(modifier.size(28.dp), color = Monika.colors.accentCoral, strokeWidth = 3.dp, trackColor = Monika.colors.track)
}

/** Ảnh minh họa 3D Fluent Emoji (Microsoft, MIT). */
@Composable
fun Illustration(@DrawableRes image: Int, modifier: Modifier = Modifier) {
    Image(painterResource(image), null, modifier)
}

@Composable
fun EmptyState(@DrawableRes image: Int, title: String, message: String, action: (@Composable () -> Unit)? = null) {
    val c = Monika.colors
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Illustration(image, Modifier.size(96.dp))
        Text(title, style = Monika.type.cardTitle, color = c.text)
        Text(message, style = Monika.type.body, color = c.textSecondary)
        action?.invoke()
    }
}

/** Header: nút tròn trái — tiêu đề giữa — nút tròn phải. Nền trong suốt. */
@Composable
fun MonikaHeader(
    title: String,
    subtitle: String? = null,
    left: (@Composable () -> Unit)? = null,
    right: @Composable RowScope.() -> Unit = {},
    /** Thay tiêu đề chữ bằng nội dung riêng (vd. logo chữ Monika ở Trang chủ). */
    titleContent: (@Composable () -> Unit)? = null,
) {
    val c = Monika.colors
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(104.dp)) { left?.invoke() }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            if (titleContent != null) { titleContent(); return@Column }
            Text(title, style = Monika.type.cardTitle.copy(fontSize = Monika.type.sectionTitle.fontSize), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = Monika.type.caption, color = c.textSecondary, maxLines = 1)
        }
        Row(Modifier.width(104.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), content = right)
    }
}

/** Logo chữ "Monika" (bộ nhận diện M Portal), tự đổi bản sáng/tối theo giao diện. */
@Composable
fun MonikaWordmark(modifier: Modifier = Modifier) {
    val res = if (androidx.compose.foundation.isSystemInDarkTheme()) vn.aow.monika.R.drawable.monika_wordmark_on_dark else vn.aow.monika.R.drawable.monika_wordmark_on_light
    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(res), "Aow Monika", modifier.height(40.dp))
}

/** Nền màn hình: cream + chừa đáy cho menu nổi. */
@Composable
fun Screen(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Monika.colors.bg), content = content)
}

/** Khoảng đệm đáy nội dung để menu nổi không che (spec: 110dp). */
val DockClearance = 110.dp

@Composable
fun dockContentClearance(insets: androidx.compose.foundation.layout.WindowInsets = androidx.compose.foundation.layout.WindowInsets.navigationBars): Dp = DockClearance +
    with(androidx.compose.ui.platform.LocalDensity.current) {
        insets.getBottom(this).toDp()
    }


@Composable
fun Modifier.dockOffset(visible: Boolean): Modifier {
    val motion = Monika.motion
    val y by animateDpAsState(if (visible) 0.dp else 120.dp, tween(motion.normal.coerceAtLeast(1), easing = motion.easing), label = "dock")
    return offset(y = y)
}

/** Fade chỉ xuất hiện khi còn mục bên phải; lớp vẽ không nhận chạm/che gesture. */
@Composable
fun ChipOverflowHint(state: androidx.compose.foundation.lazy.LazyListState, content: @Composable () -> Unit) {
    val c = Monika.colors
    Box(Modifier.fillMaxWidth()) {
        content()
        if (state.canScrollForward) Box(Modifier.matchParentSize()) {
            Box(Modifier.align(Alignment.CenterEnd).width(32.dp).fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(c.bg.copy(alpha = 0f), c.bg)))
                .semantics { contentDescription = "Còn hệ máy bên phải" })
        }
    }
}


/** Bốn trạng thái dùng chung cho V85a/b/c; không đổi giao thức phím của overlay. */
enum class AowButtonState { NORMAL, SELECTED, PRESSED, DISABLED }
enum class AowButtonStyle { ORANGE, DARK }

object AowPixelMetrics {
    val cornerStep = 3.dp
    val outline = 2.dp
    val rim = 1.dp
    val focus = 3.dp
    val shadow = 4.dp
    val press = 2.dp
    val minTouch = 48.dp
    val buttonHeight = 56.dp
}

/** Hai bậc vuông ở mỗi góc, tự thu nhỏ với kích thước nhỏ; không đường cong. */
object AowPixelShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val s = with(density) { AowPixelMetrics.cornerStep.toPx() }
            .coerceAtMost(minOf(size.width, size.height) / 4f)
        val w = size.width; val h = size.height
        return Outline.Generic(Path().apply {
            moveTo(2*s, 0f); lineTo(w-2*s, 0f)
            lineTo(w-2*s, s); lineTo(w-s, s); lineTo(w-s, 2*s); lineTo(w, 2*s)
            lineTo(w, h-2*s); lineTo(w-s, h-2*s); lineTo(w-s, h-s); lineTo(w-2*s, h-s)
            lineTo(w-2*s, h); lineTo(2*s, h)
            lineTo(2*s, h-s); lineTo(s, h-s); lineTo(s, h-2*s); lineTo(0f, h-2*s)
            lineTo(0f, 2*s); lineTo(s, 2*s); lineTo(s, s); lineTo(2*s, s); close()
        })
    }
}

data class AowButtonPalette(val face: Color, val label: Color)

fun AowColors.buttonPalette(style: AowButtonStyle, state: AowButtonState): AowButtonPalette {
    val face = when (style) {
        AowButtonStyle.ORANGE -> when (state) {
            AowButtonState.NORMAL -> orange
            AowButtonState.SELECTED -> orangeSelected
            AowButtonState.PRESSED -> orangePressed
            AowButtonState.DISABLED -> disabled
        }
        AowButtonStyle.DARK -> when (state) {
            AowButtonState.NORMAL, AowButtonState.SELECTED -> dark
            AowButtonState.PRESSED -> darkPressed
            AowButtonState.DISABLED -> disabledDark
        }
    }
    // Cam nhấn sẫm hơn: chữ trắng mới đủ tương phản; các mặt cam còn lại dùng đen.
    val label = if (style == AowButtonStyle.DARK || state == AowButtonState.PRESSED) onDark else ink
    return AowButtonPalette(face, label)
}

/** Mặt nút chung để overlay giữ vùng chạm, mã phím và rung riêng của V70a. */
@Composable
fun AowPixelSurface(
    state: AowButtonState,
    modifier: Modifier = Modifier,
    style: AowButtonStyle = AowButtonStyle.DARK,
    shape: Shape = AowPixelShape,
    content: @Composable BoxScope.(Color) -> Unit,
) {
    val c = Monika.colors.aow
    val palette = c.buttonPalette(style, state)
    val rimWidth = if (state == AowButtonState.SELECTED) AowPixelMetrics.focus else AowPixelMetrics.rim
    val rimColor = if (state == AowButtonState.SELECTED) c.focus else c.outerRim
    val motion = Monika.motion
    val press by animateDpAsState(
        if (state == AowButtonState.PRESSED && motion.enabled) AowPixelMetrics.press else 0.dp,
        tween(motion.fast.coerceAtLeast(1)), label = "aow-pixel-press",
    )
    Box(modifier.padding(bottom = AowPixelMetrics.shadow), propagateMinConstraints = true) {
        Box(Modifier.matchParentSize().offset(y = AowPixelMetrics.shadow).clip(shape).background(c.outline))
        Box(
            Modifier.offset(y = press).clip(shape).background(palette.face)
                // Border ngoài vẽ sau border trong: giữ vòng vàng/sáng ngoài và đen bên trong.
                .border(rimWidth, rimColor, shape)
                .border(AowPixelMetrics.outline + rimWidth, c.outline, shape),
            contentAlignment = Alignment.Center,
        ) { content(palette.label) }
    }
}

/** Nút hành động có focus bàn phím, click/hủy chuẩn Compose và trạng thái vô hiệu. */
@Composable
fun AowPixelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: AowButtonStyle = AowButtonStyle.ORANGE,
    enabled: Boolean = true,
    selected: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val state = when {
        !enabled -> AowButtonState.DISABLED
        pressed -> AowButtonState.PRESSED
        selected || focused -> AowButtonState.SELECTED
        else -> AowButtonState.NORMAL
    }
    val description = when (state) {
        AowButtonState.NORMAL -> "Sẵn sàng"
        AowButtonState.SELECTED -> "Đang chọn"
        AowButtonState.PRESSED -> "Đang nhấn"
        AowButtonState.DISABLED -> "Vô hiệu"
    }
    AowPixelSurface(state, modifier.heightIn(min = AowPixelMetrics.buttonHeight)
        .semantics(mergeDescendants = true) {
            stateDescription = description
            this.selected = selected || focused
            if (!enabled) disabled()
        }
        .clickable(interactionSource, null, enabled = enabled, role = Role.Button, onClick = onClick), style) { label ->
        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text.uppercase(java.util.Locale.ROOT), color = label, style = Monika.type.button,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Text(">", color = label, style = Monika.type.button)
        }
    }
}
