package vn.aow.monika.ui.theme

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** 1 nút trong menu popup. [badge] = chấm/số đỏ góc trên (vd. số thông báo mới). */
data class SheetAction(
    val label: String,
    @DrawableRes val icon: Int,
    val highlight: Boolean = false,
    val badge: String? = null,
    val enabled: Boolean = true,
    /** Bấm xong vẫn giữ menu (vd. đổi tốc độ, đổi độ mờ phím). */
    val keepOpen: Boolean = false,
    val onClick: () -> Unit,
)

/** Số menu popup đang mở (dock ẩn khi > 0). */
object SheetsOpen {
    val count = androidx.compose.runtime.mutableIntStateOf(0)
}

/** Màu cố định của menu popup: than tối như menu nổi — giống nhau ở app, giả lập, J2ME, game web. */
object SheetColors {
    val text = Color.White
    val textSecondary = Color(0xFFC8C5CB)
    val tile = Color(0x1AFFFFFF)
    val row = Color(0x12FFFFFF)
    val background = Brush.verticalGradient(listOf(Color(0xFF34323A), Color(0xFF232227)))
    val border = Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x0FFFFFFF)))
}

/**
 * Menu popup dưới đáy — MỘT thiết kế cho cả Aow Monika lẫn mọi giả lập:
 * thẻ than bo tròn nổi cách mép, trượt lên từ dưới, nền sau tối mờ (chạm ra ngoài / nút Back để đóng).
 * Nội dung: tiêu đề · [header] tùy biến · lưới nút tròn 4 cột (icon trong vòng tròn + chữ).
 * Đặt ở cuối 1 Box full màn hình để phủ lên trên.
 */
@Composable
fun BoxScope.MonikaMenuSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    actions: List<SheetAction>,
    title: String? = null,
    subtitle: String? = null,
    columns: Int = 4,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    /** Nội dung dưới lưới nút (vd. danh sách thông báo). */
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val motion = Monika.motion
    if (visible) BackHandler(onBack = onDismiss)
    // Menu đang mở → menu nổi (dock) trượt xuống, không đè lên menu popup.
    if (visible) androidx.compose.runtime.DisposableEffect(Unit) {
        SheetsOpen.count.intValue++
        onDispose { SheetsOpen.count.intValue-- }
    }
    AnimatedVisibility(
        visible, Modifier.matchParentSize(),
        enter = fadeIn(tween(if (motion.enabled) motion.normal else 0)),
        exit = fadeOut(tween(if (motion.enabled) motion.fast else 0)),
    ) {
        Box(
            Modifier.fillMaxSize().background(Color(0x99000000))
                .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss),
        )
    }
    AnimatedVisibility(
        visible, Modifier.align(Alignment.BottomCenter),
        enter = if (motion.enabled) slideInVertically(tween(motion.slow, easing = motion.easing)) { it / 2 } + fadeIn(tween(motion.normal)) else fadeIn(tween(0)),
        exit = if (motion.enabled) slideOutVertically(tween(motion.normal, easing = motion.easing)) { it / 2 } + fadeOut(tween(motion.fast)) else fadeOut(tween(0)),
    ) {
        SheetCard(onDismiss, actions, title, subtitle, columns, header, footer)
    }
}

@Composable
private fun SheetCard(
    onDismiss: () -> Unit,
    actions: List<SheetAction>,
    title: String?,
    subtitle: String?,
    columns: Int,
    header: (@Composable ColumnScope.() -> Unit)?,
    footer: (@Composable ColumnScope.() -> Unit)?,
) {
    val shape = Radius.hero
    Column(
        Modifier.windowInsetsPadding(WindowInsets.navigationBars).windowInsetsPadding(WindowInsets.systemBars.only(androidx.compose.foundation.layout.WindowInsetsSides.Horizontal))
            .padding(horizontal = 12.dp).padding(bottom = 12.dp)
            .widthIn(max = 560.dp).fillMaxWidth().heightIn(max = 620.dp)
            .shadow(24.dp, shape, ambientColor = Color(0x66FF7A32), spotColor = Color(0x66000000))
            .clip(shape).background(SheetColors.background).border(1.dp, SheetColors.border, shape)
            // Chạm vào thẻ không làm đóng menu.
            .clickable(remember { MutableInteractionSource() }, null) {}
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp).clip(Radius.pill).background(Color(0x40FFFFFF)))
        if (title != null) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = Monika.type.cardTitle, color = SheetColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    subtitle?.let { Text(it, style = Monika.type.caption, color = SheetColors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
                SheetClose(onDismiss)
            }
        }
        if (header != null) {
            Spacer(Modifier.height(12.dp))
            header()
        }
        Spacer(Modifier.height(14.dp))
        actions.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                row.forEach { a -> SheetTile(a, onDismiss, Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (footer != null) {
            Spacer(Modifier.height(4.dp))
            footer()
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun SheetClose(onDismiss: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(Radius.pill).background(SheetColors.tile)
            .clickable(role = Role.Button, onClickLabel = "Đóng", onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) { Icon(painterResource(vn.aow.monika.R.drawable.ic_fluent_dismiss_24_regular), "Đóng", Modifier.size(18.dp), tint = SheetColors.text) }
}

/** Nút tròn + chữ bên dưới. Nút nổi bật (highlight) = gradient cam→hồng. */
@Composable
fun SheetTile(a: SheetAction, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier.alpha(if (a.enabled) 1f else 0.4f).clip(Radius.medium)
            .pressable(interaction, { if (a.enabled) { if (!a.keepOpen) onDismiss(); a.onClick() } })
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Box(
                Modifier.size(56.dp).clip(Radius.pill)
                    .background(if (a.highlight) primaryGradient() else Brush.linearGradient(listOf(SheetColors.tile, SheetColors.tile)))
                    .border(1.dp, Color(0x1FFFFFFF), Radius.pill),
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(a.icon), null, Modifier.size(24.dp), tint = Color.White) }
            a.badge?.let { b ->
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-2).dp).heightIn(min = 18.dp).widthIn(min = 18.dp)
                        .clip(Radius.pill).background(Color(0xFFF25962)).border(2.dp, Color(0xFF34323A), Radius.pill).padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center,
                ) { if (b.isNotEmpty()) Text(b, style = Monika.type.caption, color = Color.White, maxLines = 1) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            a.label, style = Monika.type.caption, color = SheetColors.text, textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

/** Dòng chọn trong menu (vd. ô lưu, bài thông báo): nền kính mờ bo tròn. */
@Composable
fun SheetRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    maxTitleLines: Int = 2,
    @DrawableRes icon: Int? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().clip(Radius.medium).background(SheetColors.row)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        icon?.let { Icon(painterResource(it), null, Modifier.size(20.dp), tint = SheetColors.text) }
        Column(Modifier.weight(1f).padding(start = if (icon != null || leading != null) 12.dp else 0.dp)) {
            Text(title, style = Monika.type.bodyStrong, color = SheetColors.text, maxLines = maxTitleLines, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = Monika.type.caption, color = SheetColors.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis) }
        }
        trailing?.invoke()
    }
}

/** Nút chọn dạng viên thuốc trong menu (ô lưu 1/2/3, cỡ phím…). */
@Composable
fun SheetChip(text: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(Radius.pill)
            .background(if (on) primaryGradient() else Brush.linearGradient(listOf(SheetColors.tile, SheetColors.tile)))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = Monika.type.bodyStrong, color = Color.White, maxLines = 1) }
}
