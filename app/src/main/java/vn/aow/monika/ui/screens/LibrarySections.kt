package vn.aow.monika.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import vn.aow.monika.R
import vn.aow.monika.library.LibraryDocument
import vn.aow.monika.ui.theme.dockContentClearance
import vn.aow.monika.ui.theme.EmptyState
import vn.aow.monika.ui.theme.Monika
import vn.aow.monika.ui.theme.MonikaMenuSheet
import vn.aow.monika.ui.theme.SheetAction
import vn.aow.monika.ui.theme.SheetRow

/**
 * Ba mục của Thư viện (V138): Chơi · Đọc · Tệp.
 * Chỉ là khung + điều hướng; trang chi tiết game ở V139, đọc tài liệu trong app ở V142, quản lý tệp ở V141.
 */
internal enum class LibrarySection(val key: String, val label: String) {
    PLAY("play", "Chơi"),
    READ("read", "Đọc"),
    FILES("files", "Tệp");

    companion object {
        /** Giá trị lạ trong Prefs (bản cũ, sửa tay) → về mục Chơi. */
        fun of(key: String?): LibrarySection = values().firstOrNull { it.key == key } ?: PLAY
    }
}

/**
 * Mục Đọc: liệt kê tài liệu đã có trong kho Monika (nguồn dữ liệu sẵn có, không thêm nguồn mới).
 * Chưa có thì hiện trạng thái rỗng; trình đọc trong app làm ở V142.
 */
@Composable
internal fun LibraryReadPage(
    documents: List<LibraryDocument>,
    onOpen: (LibraryDocument) -> Unit,
    onDelete: (LibraryDocument) -> Unit,
) {
    if (documents.isEmpty()) {
        EmptyState(
            R.drawable.fluent3d_books, "Chưa có tài liệu",
            "Tài liệu (PDF, TXT, EPUB…) thêm từ máy hoặc đi kèm game sẽ hiện ở đây.",
        )
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = dockContentClearance()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "${documents.size} tài liệu", style = Monika.type.sectionTitle,
                color = Monika.colors.text, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
            )
        }
        items(documents, key = { it.key }) { document ->
            LibraryDocumentCard(document, onOpen = { onOpen(document) }, onDelete = { onDelete(document) })
        }
    }
}

/**
 * Nút "+ Thêm" (một cửa duy nhất): thêm file từ máy, quét cả máy, cấp quyền đọc tệp.
 * Dùng lại đúng luồng nhập sẵn có (picker → Importer, scanner) — không thêm đường nhập mới.
 *
 * [allowDeviceScan] = false cho bản Play: chỉ còn "Thêm file từ máy" (SAF), không quét cả máy
 * và không xin quyền đọc toàn bộ bộ nhớ. Bản web giữ mặc định true nên hành vi không đổi.
 */
@Composable
internal fun BoxScope.LibraryAddSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    canScanAll: Boolean,
    scanning: Boolean,
    onPickFiles: () -> Unit,
    onScanDevice: () -> Unit,
    onRequestAccess: () -> Unit,
    allowDeviceScan: Boolean = true,
) {
    MonikaMenuSheet(
        visible, onDismiss,
        title = "Thêm vào Thư viện",
        subtitle = "Game, file nén, tài liệu — app tự phân loại",
        actions = buildList {
            add(SheetAction("Thêm file từ máy", R.drawable.ic_fluent_folder_add_24_regular, highlight = true) { onPickFiles() })
            if (allowDeviceScan) {
                add(SheetAction(if (scanning) "Đang quét…" else "Quét cả máy", R.drawable.ic_fluent_search_24_regular, enabled = !scanning) {
                    if (canScanAll) onScanDevice() else onRequestAccess()
                })
                if (!canScanAll) add(SheetAction("Cho phép đọc mọi tệp", R.drawable.ic_fluent_lock_closed_24_regular) { onRequestAccess() })
            }
        },
        header = {
            when {
                !allowDeviceScan -> SheetRow(
                    "Chọn tệp có sẵn trong máy",
                    subtitle = "Game, file nén, tài liệu — app tự phân loại, không tải từ mạng.",
                    icon = R.drawable.ic_fluent_folder_add_24_regular,
                )
                canScanAll -> SheetRow(
                    "Đã cho phép đọc mọi tệp",
                    subtitle = "Game trong Zalo, Download… chơi thẳng từ chỗ cũ, không chép thêm.",
                    icon = R.drawable.ic_fluent_shield_checkmark_24_regular,
                )
                else -> SheetRow(
                    "Chưa có quyền đọc mọi tệp",
                    subtitle = "Cấp quyền để thấy game có sẵn trong máy.",
                    icon = R.drawable.ic_fluent_info_24_regular,
                )
            }
        },
    )
}

