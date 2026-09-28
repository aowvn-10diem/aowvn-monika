package vn.aow.monika.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/* Thành phần giao diện theo Fluent 2. Màn hình chỉ dùng các thành phần này để giữ phong cách thống nhất. */

enum class FluentButtonStyle { Accent, Outline, Subtle }

@Composable
fun FluentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: FluentButtonStyle = FluentButtonStyle.Accent,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
) {
    val c = Fluent.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val (bg, fg, stroke) = when {
        !enabled -> Triple(c.background3, c.foregroundDisabled, c.stroke2)
        style == FluentButtonStyle.Accent -> Triple(if (pressed) c.brandPressed else c.brandBackground, c.onBrand, Color.Transparent)
        style == FluentButtonStyle.Outline -> Triple(if (pressed) c.background3 else c.background1, c.foreground1, c.stroke1)
        else -> Triple(if (pressed) c.background3 else Color.Transparent, c.brandForeground, Color.Transparent)
    }
    Row(
        modifier
            .heightIn(min = 40.dp)
            .clip(FluentRadius.control)
            .background(bg)
            .border(1.dp, stroke, FluentRadius.control)
            .clickable(interaction, null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), null, Modifier.size(20.dp), tint = fg)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = Fluent.type.body2.copy(fontWeight = Fluent.type.body1Strong.fontWeight), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun FluentIconButton(@DrawableRes icon: Int, contentDescription: String, onClick: () -> Unit, tint: Color = Fluent.colors.foreground2) {
    Box(
        Modifier.size(44.dp).clip(FluentRadius.circular).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(painterResource(icon), contentDescription, Modifier.size(24.dp), tint = tint) }
}

/** Thẻ Fluent: nền sáng, viền 1dp, bóng nhẹ, bo 8dp. */
@Composable
fun FluentCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Fluent.colors
    Column(
        modifier
            .shadow(if (c.isDark) 0.dp else 2.dp, FluentRadius.card, ambientColor = Color(0x14000000), spotColor = Color(0x24000000))
            .clip(FluentRadius.card)
            .background(c.background1)
            .border(1.dp, c.stroke2, FluentRadius.card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** Thanh tiêu đề Fluent (nền trung tính, chữ Semibold). */
@Composable
fun FluentTopBar(
    title: String,
    subtitle: String? = null,
    navigation: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = Fluent.colors
    Column(Modifier.fillMaxWidth().background(c.background1).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (navigation != null) navigation() else Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(title, style = Fluent.type.title2, color = c.foreground1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = Fluent.type.caption1, color = c.foreground3, maxLines = 1)
            }
            actions()
        }
        FluentDivider()
    }
}

@Composable
fun FluentDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Fluent.colors.stroke2))
}

/** Thanh nút dạng viên thuốc (Fluent "Pill button bar") dùng để lọc. */
@Composable
fun <T> FluentPillBar(items: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    val c = Fluent.colors
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().background(c.background1),
    ) {
        items(items) { item ->
            val isSelected = item == selected
            Box(
                Modifier
                    .clip(FluentRadius.circular)
                    .background(if (isSelected) c.brandBackground else c.background3)
                    .clickable(role = Role.Tab) { onSelect(item) }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Text(label(item), style = Fluent.type.body2, color = if (isSelected) c.onBrand else c.foreground2)
            }
        }
    }
}

/** Nhãn nhỏ (Fluent Tag/Badge). */
@Composable
fun FluentTag(text: String, brand: Boolean = false) {
    val c = Fluent.colors
    Text(
        text,
        style = Fluent.type.caption2,
        color = if (brand) c.onBrandSubtle else c.foreground2,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (brand) c.brandSubtle else c.background3)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Tiêu đề nhóm (Fluent section header). */
@Composable
fun FluentSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(text, style = Fluent.type.caption1Strong, color = Fluent.colors.foreground3, modifier = modifier.padding(top = 8.dp, bottom = 4.dp))
}

@Composable
fun FluentSpinner(modifier: Modifier = Modifier) {
    CircularProgressIndicator(modifier.size(28.dp), color = Fluent.colors.brandForeground, strokeWidth = 3.dp, trackColor = Fluent.colors.stroke2)
}

/** Trạng thái rỗng / lỗi: icon lớn + thông điệp + hành động. */
@Composable
fun FluentEmptyState(@DrawableRes icon: Int, title: String, message: String, action: (@Composable () -> Unit)? = null) {
    val c = Fluent.colors
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Fluent3D(icon, Modifier.size(96.dp))
        Text(title, style = Fluent.type.title3, color = c.foreground1)
        Text(message, style = Fluent.type.body2, color = c.foreground3)
        action?.invoke()
    }
}

/** Ảnh 3D Fluent Emoji (Microsoft, MIT) — dùng cho minh họa, không dùng làm icon điều khiển. */
@Composable
fun Fluent3D(@DrawableRes image: Int, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Image(painterResource(image), null, modifier)
}
