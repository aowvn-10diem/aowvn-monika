package vn.aow.monika.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import vn.aow.monika.R
import vn.aow.monika.ui.theme.*

/** Skeleton tĩnh: không có hoạt ảnh lặp khi máy chọn OFF. Mạng bị chặn ở 10 giây. */
@Composable
internal fun FeedStatus(loading: Boolean, onRetry: () -> Unit) {
    if (loading) {
        Column(Modifier.fillMaxWidth().padding(16.dp).testTag("v69-feed-loading"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Đang tải bài viết…", style = Monika.type.caption, color = Monika.colors.textSecondary)
            Box(Modifier.fillMaxWidth().height(160.dp).clip(Radius.large).background(Monika.colors.chip))
            Box(Modifier.fillMaxWidth(.8f).height(20.dp).clip(Radius.pill).background(Monika.colors.chip))
            Box(Modifier.fillMaxWidth(.6f).height(20.dp).clip(Radius.pill).background(Monika.colors.chip))
        }
    } else {
        EmptyState(R.drawable.fluent3d_newspaper, "Không tải được bài viết", "Kiểm tra kết nối rồi thử lại.") {
            DarkButton("Thử lại", onRetry)
        }
    }
}
