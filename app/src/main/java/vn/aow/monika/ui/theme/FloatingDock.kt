package vn.aow.monika.ui.theme

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
import androidx.compose.animation.core.Animatable
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

data class DockItem(val label: String, @DrawableRes val icon: Int, @DrawableRes val iconSelected: Int, /** Số / chấm đỏ góc icon (vd. thông báo mới). "" = chỉ chấm. */ val badge: String? = null)

private val ItemSize = 58.dp
private val ItemGap = 8.dp

/**
 * Menu nổi (trung tâm của app): bo tròn, cách mép dưới 18dp, nằm giữa vùng ngón cái.
 * - Nổi rõ cả trên nền tối: nền than sáng hơn nền app, viền sáng mảnh, quầng cam mờ phía dưới.
 * - Mở app: menu trượt lên + hiện dần, từng nút bật ra lần lượt (máy yếu: chỉ trượt; tắt hiệu ứng: hiện ngay).
 * - Mục đang chọn là vòng tròn gradient TRƯỢT sang vị trí mới + nảy nhẹ.
 */
@Composable
fun FloatingDock(items: List<DockItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val motion = Monika.motion
    val dark = isSystemInDarkTheme()
    val target = (ItemSize + ItemGap) * selected.coerceAtLeast(0)
    val x by animateDpAsState(
        target,
        if (!motion.enabled) tween(0)
        else if (motion.rich) spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
        else tween(motion.normal, easing = motion.easing),
        label = "dockIndicator",
    )
    // Hiệu ứng xuất hiện: chỉ chạy 1 lần khi mở app.
    val enter = remember { Animatable(if (motion.enabled) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (motion.enabled) enter.animateTo(1f, if (motion.rich) spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessLow) else tween(motion.slow, easing = motion.easing))
    }
    val e = enter.value
    val shape = Radius.pill
    Box(
        modifier.navigationBarsPadding().padding(bottom = 18.dp)
            .graphicsLayer {
                translationY = (1f - e) * 120.dp.toPx()
                alpha = e.coerceIn(0f, 1f)
                val sc = 0.9f + 0.1f * e.coerceAtMost(1.05f)
                scaleX = sc; scaleY = sc
            }
            // Quầng cam mờ dưới menu giúp tách khỏi nền tối.
            .shadow(if (motion.rich) 22.dp else 10.dp, shape, ambientColor = Color(0x66FF7A32), spotColor = Color(if (dark) 0x99FF7A32 else 0x55141215))
            .clip(shape)
            .background(Brush.verticalGradient(if (dark) listOf(Color(0xFF34323A), Color(0xFF26252B)) else listOf(Color(0xFF2A292D), Color(0xFF1C1B1E))))
            .border(1.dp, Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x0FFFFFFF))), shape)
            .height(72.dp).padding(horizontal = 7.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (selected >= 0) {
            Box(Modifier.offset(x = x).size(ItemSize).clip(Radius.pill).background(primaryGradient()))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ItemGap), verticalAlignment = Alignment.CenterVertically) {
            items.forEachIndexed { i, item ->
                val on = i == selected
                val scale by animateFloatAsState(
                    if (on && motion.rich) 1.08f else 1f,
                    if (motion.enabled) spring(dampingRatio = 0.5f) else tween(0),
                    label = "dockIcon",
                )
                // Từng nút bật ra lần lượt theo tiến độ xuất hiện của menu.
                val pop = if (motion.rich) ((e - i * 0.08f) / 0.6f).coerceIn(0f, 1f) else 1f
                Box(
                    Modifier.size(ItemSize).clip(Radius.pill)
                        .clickable(remember { MutableInteractionSource() }, null, role = Role.Tab) { onSelect(i) }
                        .semantics { contentDescription = item.label },
                    contentAlignment = Alignment.Center,
                ) {
                    MonikaIcon(
                        if (on) item.iconSelected else item.icon, null,
                        // Emoji 3D không đổi màu được → tab chưa chọn mờ bớt, tab đang chọn rõ.
                        Modifier.size(26.dp).graphicsLayer { val k = scale * (0.6f + 0.4f * pop); scaleX = k; scaleY = k; alpha = pop * (if (on) 1f else 0.72f) },
                        tint = if (on) Color.White else Color(0xFFEDE9F0),
                    )
                    item.badge?.let { b ->
                        Box(
                            Modifier.align(Alignment.TopEnd).offset(x = (-8).dp, y = 8.dp).height(if (b.isEmpty()) 10.dp else 18.dp)
                                .widthIn(min = if (b.isEmpty()) 10.dp else 18.dp).clip(Radius.pill).background(Color(0xFFF25962))
                                .border(2.dp, Color(0xFF2A292D), Radius.pill).padding(horizontal = if (b.isEmpty()) 0.dp else 4.dp),
                            contentAlignment = Alignment.Center,
                        ) { if (b.isNotEmpty()) androidx.compose.material3.Text(b, color = Color.White, fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp), maxLines = 1) }
                    }
                }
            }
        }
    }
}
