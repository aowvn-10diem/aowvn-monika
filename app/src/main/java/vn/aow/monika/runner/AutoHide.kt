package vn.aow.monika.runner

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import vn.aow.monika.ui.theme.Monika

/**
 * A31: thanh tên game, nút menu và chip gợi ý nổi trên game tự ẩn ở MỌI giả lập để không che màn hình game.
 * Hiện khi vào game / đang tải / đang mở menu; đang chơi thì ẩn sau [HIDE_MS]; chạm vùng mép ([RevealEdge]) hiện lại
 * rồi lại tự ẩn. Chỉ là trạng thái + giờ đếm; mỗi giả lập tự quyết vẽ gì và đặt vùng chạm ở đâu.
 */
class AutoHide {
    /** Đang hiện (chỉ có nghĩa khi [rememberAutoHide] bật). */
    var shown by mutableStateOf(true)
    /** Tăng mỗi lần hiện lại → hiệu ứng đếm giờ chạy lại từ đầu. */
    var seq by mutableIntStateOf(0)

    /** Hiện ngay (đặt đồng bộ, không chờ hiệu ứng nền) rồi đếm lại giờ tự ẩn. */
    fun reveal() { shown = true; seq++ }

    companion object {
        /** Thời gian (ms) chờ trước khi tự ẩn lúc đang chơi. */
        const val HIDE_MS = 3000L
    }
}

/**
 * Chạy giờ đếm tự ẩn cho [auto]. [enabled] = false: luôn hiện. [pinned] = true (đang mở menu / chỉnh phím / bảng tùy chọn /
 * đang tải): luôn hiện và không đếm; hết ghim thì đếm lại từ đầu. Thời gian chờ dùng `delay` (đồng hồ coroutine), không dùng giờ hệ thống.
 */
@Composable
fun AutoHideEffect(auto: AutoHide, enabled: Boolean = true, pinned: Boolean = false) {
    LaunchedEffect(enabled, pinned, auto.seq) {
        if (!enabled || pinned) { auto.shown = true; return@LaunchedEffect }
        kotlinx.coroutines.delay(AutoHide.HIDE_MS)
        auto.shown = false
    }
}

/** Trạng thái tự ẩn gắn với vòng đời của composable gọi (cho thành phần không có `InGameState`). */
@Composable
fun rememberAutoHide(enabled: Boolean = true, pinned: Boolean = false): AutoHide {
    val auto = remember { AutoHide() }
    AutoHideEffect(auto, enabled, pinned)
    return auto
}

/** Hiện thật sự = không bật tự ẩn, hoặc đang ghim, hoặc chưa tới giờ ẩn. Tính thẳng lúc vẽ. */
fun AutoHide.visible(enabled: Boolean, pinned: Boolean): Boolean = !enabled || pinned || shown

/** Độ đậm 0..1 của phần tử tự ẩn: mờ dần/hiện dần theo `Monika.motion` (0 = ẩn hẳn, bỏ khỏi cây giao diện). */
@Composable
fun autoHideAlpha(visible: Boolean): Float =
    animateFloatAsState(if (visible) 1f else 0f, tween(Monika.motion.normal), label = "autoHide").value

/** Vùng chạm vô hình sát mép để hiện lại phần tử đang ẩn. Chỉ đặt khi đang ẩn. */
@Composable
fun RevealEdge(auto: AutoHide, modifier: Modifier) {
    Box(modifier.pointerInput(auto) { awaitEachGesture { awaitFirstDown(); auto.reveal() } })
}
