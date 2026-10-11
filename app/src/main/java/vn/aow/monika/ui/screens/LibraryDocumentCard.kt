package vn.aow.monika.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vn.aow.monika.R
import vn.aow.monika.library.LibraryDocument
import vn.aow.monika.ui.theme.*

@Composable
internal fun LibraryDocumentCard(document: LibraryDocument, onOpen: () -> Unit, onDelete: () -> Unit) {
    MonikaCard(Modifier.fillMaxWidth(), shape = Radius.large, padding = PaddingValues(16.dp)) {
        Text(document.name, style = Monika.type.cardTitle, color = Monika.colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("Mở bằng ứng dụng đọc tài liệu trong máy", style = Monika.type.caption, color = Monika.colors.textSecondary)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GradientButton("Mở tài liệu", onOpen, Modifier.weight(1f), height = 44.dp, icon = R.drawable.ic_fluent_open_24_regular)
            DarkButton("Xóa", onDelete)
        }
    }
}
