package vn.aow.monika.runner

import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import vn.aow.monika.R
import vn.aow.monika.ui.theme.MonikaMenuSheet
import vn.aow.monika.ui.theme.MonikaTheme
import vn.aow.monika.ui.theme.SheetAction

/**
 * Phím ảo + menu Monika trên màn chơi RPG Maker XP/VX/Ace (mkxp-z). Dùng lại D-pad/nút A B của bố cục RPG ([PadLayout.RPG]);
 * mã phím tay cầm của bố cục được đổi sang phím bàn phím mà RGSS hiểu bằng [rgssKey].
 */
@Composable
fun RgssOverlay(
    state: InGameState,
    title: String,
    shiftHeld: Boolean,
    onSend: (action: Int, androidKey: Int) -> Unit,
    onShift: () -> Unit,
    onOpacity: () -> Unit,
    onExit: () -> Unit,
) {
    MonikaTheme {
        Box(Modifier.fillMaxSize()) {
            VirtualPad(PadLayout.RPG, state, onSend, { _, _, _ -> }, Modifier.align(Alignment.BottomCenter))
            GameMenuButton(state.menuOpen, { state.menuOpen = !state.menuOpen }, Modifier.align(Alignment.TopEnd))
            MonikaMenuSheet(
                state.menuOpen, { state.menuOpen = false }, title = title, subtitle = "RPG Maker (XP/VX/Ace)",
                actions = listOf(
                    SheetAction("Chơi tiếp", R.drawable.ic_fluent_play_24_regular, highlight = true) {},
                    SheetAction(if (shiftHeld) "Chạy nhanh (giữ Shift): ĐANG BẬT" else "Chạy nhanh (giữ Shift): tắt", R.drawable.ic_fluent_top_speed_24_regular,
                        highlight = shiftHeld, keepOpen = true, onClick = onShift),
                    SheetAction("Độ mờ phím ${(state.opacity * 100).toInt()}%", R.drawable.ic_fluent_eye_24_regular, keepOpen = true, onClick = onOpacity),
                    vn.aow.monika.ui.gameReportAction { state.menuOpen = false },
                    SheetAction("Thoát game", R.drawable.ic_fluent_door_arrow_left_24_regular, onClick = onExit),
                ),
            )
        }
    }
}

/**
 * Phím tay cầm (mã Android của [GamePadOverlay]) → phím bàn phím mà RGSS/mkxp-z gán mặc định:
 * nút "A" của bố cục (BUTTON_B) = Đồng ý (Enter), nút "B" (BUTTON_A) = Hủy/Menu (X), START = Enter, SELECT = X. Mũi tên giữ nguyên.
 */
fun rgssKey(androidKey: Int): Int = when (androidKey) {
    KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_START -> KeyEvent.KEYCODE_ENTER
    KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_SELECT -> KeyEvent.KEYCODE_X
    else -> androidKey
}
