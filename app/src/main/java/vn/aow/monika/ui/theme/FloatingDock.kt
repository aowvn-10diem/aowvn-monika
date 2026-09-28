package vn.aow.monika.ui.theme

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

data class DockItem(val label: String, @DrawableRes val icon: Int, @DrawableRes val iconSelected: Int)

private val ItemSize = 58.dp
private val ItemGap = 8.dp

/**
 * Menu nổi (trung tâm của app): charcoal, bo tròn, cách mép dưới 18dp, nằm giữa vùng ngón cái.
 * Mục đang chọn là vòng tròn gradient TRƯỢT sang vị trí mới + nảy nhẹ (máy yếu: chỉ trượt nhanh; tắt hiệu ứng: nhảy thẳng).
 */
@Composable
fun FloatingDock(items: List<DockItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val motion = Monika.motion
    val target = (ItemSize + ItemGap) * selected.coerceAtLeast(0)
    val x by animateDpAsState(
        target,
        if (!motion.enabled) tween(0)
        else if (motion.rich) spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
        else tween(motion.normal, easing = motion.easing),
        label = "dockIndicator",
    )
    Box(
        modifier.navigationBarsPadding().padding(bottom = 18.dp)
            .softShadow(Radius.pill, floating = true).clip(Radius.pill)
            .background(Color(0xFF201F21)).height(72.dp).padding(horizontal = 7.dp),
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
                Box(
                    Modifier.size(ItemSize).clip(Radius.pill)
                        .clickable(remember { MutableInteractionSource() }, null, role = Role.Tab) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(if (on) item.iconSelected else item.icon), item.label,
                        Modifier.size(26.dp).graphicsLayer { scaleX = scale; scaleY = scale },
                        tint = if (on) Color.White else Color(0xFFD3CFD6),
                    )
                }
            }
        }
    }
}
