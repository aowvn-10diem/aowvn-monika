package vn.aow.monika.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vn.aow.monika.R
import vn.aow.monika.library.Game
import vn.aow.monika.library.LibraryDocument
import vn.aow.monika.ui.theme.DockClearance
import vn.aow.monika.ui.theme.EmptyState
import vn.aow.monika.ui.theme.GradientButton
import vn.aow.monika.ui.theme.Monika
import vn.aow.monika.ui.theme.MonikaCard
import vn.aow.monika.ui.theme.MonikaIcon
import vn.aow.monika.ui.theme.Radius
import vn.aow.monika.ui.theme.Tag
import vn.aow.monika.ui.theme.primaryGradient
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Mục Tệp (V141): mọi thứ người dùng đã đưa vào kho Monika, gộp game · tài liệu · tệp khác.
 * Nguồn dữ liệu là danh sách game + tài liệu đã có (GameLibrary) và đúng thư mục kho hiện tại,
 * không tạo kho mới.
 */
internal enum class LibraryFileKind(val label: String, @DrawableRes val icon: Int) {
    GAME("Game", R.drawable.ic_fluent_xbox_controller_24_regular),
    DOCUMENT("Tài liệu", R.drawable.ic_fluent_document_24_regular),
    OTHER("Khác", R.drawable.ic_fluent_archive_24_regular),
}

/**
 * Một dòng trong mục Tệp.
 * [game] / [document] khác null = tệp đang được mục Chơi hoặc Đọc dùng → "xoá" chỉ ẩn khỏi mục Tệp,
 * dữ liệu gốc giữ nguyên.
 */
internal data class LibraryFileEntry(
    val key: String,
    val name: String,
    val kind: LibraryFileKind,
    val sizeBytes: Long,
    val addedAt: Long,
    val file: File,
    val game: Game? = null,
    val document: LibraryDocument? = null,
) {
    val referenced: Boolean get() = game != null || document != null
}

/**
 * Gộp game, tài liệu và phần còn lại của kho thành một danh sách tệp.
 * Mới thêm trước; cùng ngày thì theo tên. Không ném lỗi khi một tệp không đọc được.
 */
internal fun libraryFileEntries(
    games: List<Game>,
    documents: List<LibraryDocument>,
    root: File,
): List<LibraryFileEntry> {
    val entries = mutableListOf<LibraryFileEntry>()
    val gameDirs = HashSet<String>()
    for (game in games) {
        val dir = game.dir.absolutePath
        gameDirs += dir
        // Game đã bị dọn bộ đệm (chỉ còn thông tin bài viết) không còn tệp nào để liệt kê.
        if (!game.dir.exists()) continue
        entries += LibraryFileEntry(
            key = "game:$dir",
            name = game.name,
            kind = LibraryFileKind.GAME,
            // Game quét trong máy: chỉ tính đúng tệp chạy, không tính cả thư mục Download.
            sizeBytes = fileSize(if (game.external) game.entry ?: game.dir else game.dir),
            addedAt = game.dir.lastModified(),
            file = game.dir,
            game = game,
        )
    }
    val documentDirs = HashSet<String>()
    for (document in documents) {
        document.file.parentFile?.absolutePath?.let { documentDirs += it }
        entries += LibraryFileEntry(
            key = "doc:${document.key}",
            name = document.name,
            kind = LibraryFileKind.DOCUMENT,
            sizeBytes = runCatching { document.file.length() }.getOrDefault(0L),
            addedAt = document.file.lastModified(),
            file = document.file,
            document = document,
        )
    }
    // Phần còn lại của kho: thư mục lạ, tệp lẻ chưa nhận diện được.
    root.listFiles().orEmpty().forEach { file ->
        val path = file.absolutePath
        if (path in gameDirs || path in documentDirs) return@forEach
        // Thư mục chỉ chứa tài liệu đã liệt kê từng tệp bên trong, không lặp lại cả thư mục.
        if (documents.any { it.file.absolutePath.startsWith(path + File.separator) }) return@forEach
        entries += LibraryFileEntry(
            key = "file:$path",
            name = file.name,
            kind = LibraryFileKind.OTHER,
            sizeBytes = fileSize(file),
            addedAt = file.lastModified(),
            file = file,
        )
    }
    return entries.sortedWith(
        compareByDescending<LibraryFileEntry> { it.addedAt }.thenBy { it.name.lowercase(Locale.ROOT) },
    )
}

/** Dung lượng tệp (thư mục: cộng dồn bên trong). Tệp không đọc được tính 0, không làm hỏng mục Tệp. */
internal fun fileSize(file: File): Long = runCatching {
    if (file.isDirectory) file.walkTopDown().maxDepth(6).filter { it.isFile }.sumOf { it.length() }
    else file.length()
}.getOrDefault(0L)

/** "512 B" · "48 KB" · "1,4 MB" · "2,3 GB". */
internal fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes < kb -> "$bytes B"
        bytes < mb -> "${(bytes / kb).toLong()} KB"
        bytes < gb -> oneDecimal(bytes / mb) + " MB"
        else -> oneDecimal(bytes / gb) + " GB"
    }
}

private fun oneDecimal(value: Double): String =
    String.format(Locale.ROOT, "%.1f", value).replace('.', ',')

/** Ngày thêm tệp: 02/11/2026. Không rõ thời điểm → "—". */
internal fun formatFileDate(addedAt: Long): String =
    if (addedAt <= 0L) "—" else SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).format(Date(addedAt))

/**
 * Mục Tệp: danh sách tệp kèm loại, dung lượng, ngày thêm; bấm một dòng để mở đúng hành động.
 * Chưa có gì thì trạng thái rỗng + "+ Thêm" mở đúng luồng nhập sẵn có.
 */
@Composable
internal fun LibraryFilesPage(
    entries: List<LibraryFileEntry>,
    onOpen: (LibraryFileEntry) -> Unit,
    onDelete: (LibraryFileEntry) -> Unit,
    onAdd: () -> Unit,
) {
    if (entries.isEmpty()) {
        EmptyState(
            R.drawable.fluent3d_file_folder, "Chưa có tệp nào",
            "Game, tài liệu và tệp khác bạn thêm vào Thư viện sẽ hiện ở đây, kèm dung lượng và ngày thêm.",
        ) {
            GradientButton("+ Thêm", onAdd, icon = R.drawable.ic_fluent_folder_add_24_regular)
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = DockClearance),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "${entries.size} tệp · ${formatFileSize(entries.sumOf { it.sizeBytes })}",
                style = Monika.type.sectionTitle, color = Monika.colors.text,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
            )
        }
        items(entries, key = { it.key }) { entry ->
            LibraryFileRow(entry, onOpen = { onOpen(entry) }, onDelete = { onDelete(entry) })
        }
    }
}

/** Một tệp: biểu tượng theo loại, tên, loại · dung lượng · ngày thêm, nút xoá. */
@Composable
internal fun LibraryFileRow(entry: LibraryFileEntry, onOpen: () -> Unit, onDelete: () -> Unit) {
    val c = Monika.colors
    MonikaCard(
        Modifier.fillMaxWidth(), shape = Radius.large, padding = PaddingValues(14.dp),
        onClick = onOpen,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(Radius.thumb).background(primaryGradient()),
                contentAlignment = Alignment.Center,
            ) {
                MonikaIcon(entry.kind.icon, null, Modifier.size(22.dp), tint = Color.White)
            }
            Column(
                Modifier.weight(1f).padding(start = 12.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    entry.name, style = Monika.type.cardTitle, color = c.text,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Tag(entry.kind.label)
                    Text(
                        "${formatFileSize(entry.sizeBytes)} · ${formatFileDate(entry.addedAt)}",
                        style = Monika.type.caption, color = c.textSecondary, maxLines = 1,
                    )
                }
            }
            // Nút xoá nhẹ (nền chip) để không tranh chỗ với hành động chính là bấm mở tệp.
            Box(
                Modifier.size(40.dp).clip(Radius.pill).background(c.chip).clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                MonikaIcon(R.drawable.ic_fluent_delete_24_regular, "Xoá tệp", Modifier.size(20.dp), tint = c.chipText)
            }
        }
    }
}
