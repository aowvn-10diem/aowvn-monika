package vn.aow.monika.library

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import vn.aow.monika.config.MonikaConfig
import java.io.File

data class LibraryDocument(val file: File) {
    val name: String get() = file.name
    val key: String get() = file.absolutePath
}

internal data class LibraryContents(val games: List<Game>, val documents: List<LibraryDocument>)

/** Nhận diện game trước: PDF hướng dẫn nằm cùng game không tạo bản sao trong Tài liệu. */
internal fun partitionLibrary(detected: List<Game>, config: MonikaConfig): LibraryContents {
    val games = mutableListOf<Game>()
    val documents = mutableListOf<LibraryDocument>()
    val extensions = config.documentExtensions.map { it.trim().removePrefix(".").lowercase(java.util.Locale.ROOT) }.toSet()
    for (game in detected) {
        // Giữ lối phục hồi game bị Android khóa, tải lại và giải nén kho chưa mở được.
        if (game.system != null || game.locked || game.evicted || game.needsExtract) {
            games += game
            continue
        }
        val files = runCatching {
            game.dir.walkTopDown().maxDepth(4).filter { it.isFile && it.name != ".monika.json" }.toList()
        }.getOrNull()
        if (files == null) { games += game.copy(locked = true); continue }
        if (files.isNotEmpty() && files.all { it.extension.lowercase(java.util.Locale.ROOT) in extensions }) {
            documents += files.sortedBy { it.name.lowercase(java.util.Locale.ROOT) }.map(::LibraryDocument)
        }
    }
    return LibraryContents(games, documents)
}

/** URI cấp quyền đọc cho ứng dụng ngoài; không dùng file:// hay cấp quyền ghi. */
internal fun documentViewIntent(context: Context, document: LibraryDocument): Intent {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", document.file)
    val mime = when (document.file.extension.lowercase(java.util.Locale.ROOT)) {
        "pdf" -> "application/pdf"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "txt" -> "text/plain"
        "epub" -> "application/epub+zip"
        else -> android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(document.file.extension.lowercase(java.util.Locale.ROOT)) ?: "application/octet-stream"
    }
    return Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .apply { clipData = ClipData.newRawUri(document.name, uri) }
}
