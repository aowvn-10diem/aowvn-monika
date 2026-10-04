package vn.aow.monika.runner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import vn.aow.monika.R
import vn.aow.monika.ui.theme.MonikaMenuSheet
import vn.aow.monika.ui.theme.MonikaTheme
import vn.aow.monika.ui.theme.SheetAction

/** Nút menu nhỏ + menu popup chung của Monika (tiếng Việt) trên màn chơi Ren'Py. Phím Back của máy vẫn do Ren'Py xử lý (menu game). */
@Composable
fun RenpyOverlay(title: String, open: Boolean, onOpen: (Boolean) -> Unit, onExit: () -> Unit) {
    MonikaTheme {
        Box(Modifier.fillMaxSize()) {
            GameMenuButton(open, { onOpen(!open) }, Modifier.align(Alignment.TopEnd))
            MonikaMenuSheet(
                open, { onOpen(false) }, title = title, subtitle = "Ren'Py (visual novel)",
                actions = listOf(
                    SheetAction("Chơi tiếp", R.drawable.ic_fluent_play_24_regular, highlight = true) {},
                    SheetAction("Thoát game", R.drawable.ic_fluent_door_arrow_left_24_regular, onClick = onExit),
                ),
            )
        }
    }
}
