package vn.aow.monika.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import vn.aow.monika.Prefs
import vn.aow.monika.R
import vn.aow.monika.ui.theme.*

/** Giới thiệu một lần; đường thêm file thủ công luôn dùng được khi không cấp quyền. */
@Composable
internal fun LibraryStorageAccess(granted: Boolean, prefs: Prefs, onRequest: () -> Unit, onManual: () -> Unit) {
    val c = Monika.colors
    var explain by remember { mutableStateOf(!granted && !prefs.libraryStorageExplained) }
    LaunchedEffect(Unit) { prefs.libraryStorageExplained = true }
    if (!granted) MonikaCard(Modifier.fillMaxWidth(), padding = PaddingValues(16.dp)) {
        Text("Tìm game có sẵn trong máy", style = Monika.type.cardTitle, color = c.text)
        Text("Cấp quyền bộ nhớ để Monika tự tìm game trong Download, Zalo và các thư mục khác. Không cấp quyền vẫn thêm file được.",
            style = Monika.type.caption, color = c.textSecondary, modifier = Modifier.padding(top = 4.dp))
        GradientButton("Cấp quyền quét game", { explain = false; onRequest() },
            Modifier.fillMaxWidth().padding(top = 12.dp), icon = R.drawable.ic_fluent_folder_add_24_regular, height = 44.dp)
    }
    if (explain && !granted) AlertDialog(
        onDismissRequest = { explain = false },
        title = { Text("Tự tìm game trong máy", style = Monika.type.sectionTitle, color = c.text) },
        text = { Text("Cho phép Monika đọc bộ nhớ để tự quét và đưa game có sẵn vào Thư viện. File gốc không bị chép hay di chuyển. Bạn có thể từ chối và chọn từng file bằng Thêm game từ máy.",
            style = Monika.type.body, color = c.textSecondary) },
        confirmButton = { GradientButton("Cấp quyền", { explain = false; onRequest() }, height = 44.dp) },
        dismissButton = { DarkButton("Thêm game từ máy", { explain = false; onManual() }) },
        containerColor = c.surface, shape = Radius.large,
    )
}
