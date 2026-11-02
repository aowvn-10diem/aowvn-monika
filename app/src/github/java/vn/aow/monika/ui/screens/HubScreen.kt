package vn.aow.monika.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import vn.aow.monika.ui.theme.ChipBar

/** Tab cuối menu nổi: gộp "Tải xuống" và "Cài đặt", chuyển bằng nút gạt ở đầu trang. */
@Composable
fun HubScreen(segment: MutableIntState, onOpenLibrary: () -> Unit) {
    val header: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            ChipBar(listOf(0, 1), segment.intValue, { if (it == 0) "Tải xuống" else "Cài đặt" }, { segment.intValue = it },
                accent = true, contentPadding = PaddingValues(0.dp))
        }
    }
    if (segment.intValue == 0) DownloadsScreen(onOpenLibrary = onOpenLibrary, header = header)
    else SettingsScreen(header = header)
}
