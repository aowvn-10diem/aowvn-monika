package vn.aow.monika.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vn.aow.monika.AppGraph
import vn.aow.monika.R
import vn.aow.monika.browser.InAppBrowserActivity
import vn.aow.monika.ui.theme.Monika
import vn.aow.monika.ui.theme.Radius

/**
 * Thanh kêu gọi ủng hộ/vote AowVN, đặt ngay dưới tiêu đề ở MỌI tab (một dòng gọn, bấm là mở trang ủng hộ).
 * Cùng địa chỉ với thẻ lớn ở Trang chủ: `donateUrl`, trống thì dùng `voteUrl` trong config.
 */
@Composable
fun SupportStrip(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val cfg by AppGraph.config.config.collectAsState()
    val url = cfg.account.donateUrl.ifBlank { cfg.account.voteUrl }
    val c = Monika.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).clip(Radius.pill).background(c.surface)
            .clickable(onClickLabel = "Ủng hộ AowVN") { InAppBrowserActivity.start(context, url) }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(R.drawable.ic_fluent_heart_24_filled), null, Modifier.size(20.dp), tint = c.accentCoral)
        androidx.compose.material3.Text(
            "Ủng hộ / vote cho AowVN để Monika miễn phí mãi", style = Monika.type.caption.copy(fontWeight = Monika.type.bodyStrong.fontWeight),
            color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        androidx.compose.material3.Text("Ủng hộ", style = Monika.type.caption.copy(fontWeight = Monika.type.bodyStrong.fontWeight), color = c.accentCoral)
    }
}
