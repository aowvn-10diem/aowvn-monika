package vn.aow.monika.ui.screens

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import vn.aow.monika.ui.theme.Monika

internal fun compactLibraryPath(path: String): String {
    val normalized = path.replace('\\', '/').trimEnd('/')
    val marker = "/Download/"
    val start = normalized.lastIndexOf(marker)
    return if (start >= 0) normalized.substring(start + 1)
        else normalized.split('/').filter { it.isNotEmpty() }.takeLast(2).joinToString("/")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LibraryFolderLabel(fullPath: String) {
    val clipboard = LocalClipboardManager.current
    Text("Thư mục: " + compactLibraryPath(fullPath) + " · Giữ để sao chép", style = Monika.type.caption,
        color = Monika.colors.textSecondary,
        modifier = Modifier.combinedClickable(onClick = {}, onLongClickLabel = "Sao chép đường dẫn đầy đủ",
            onLongClick = { clipboard.setText(AnnotatedString(fullPath)) }).padding(12.dp))
}
