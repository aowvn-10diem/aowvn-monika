package vn.aow.monika.library

import java.io.File
import java.util.Locale

/** Cây save giới hạn trong một title/disc; không nhập toàn bộ SD card hay memstick. */
internal object ConsoleSaveTrees {
    private fun check(ok: Boolean, text: String) = SaveFormats.checked(ok, text)

    fun allowed(target: SaveTarget, relative: String): Boolean = SaveFormats.safePath(relative) &&
        (target.system != "psp" || relative.substringBefore('/').matches(Regex("${target.identity}[A-Za-z0-9_-]{0,32}")) && '/' in relative)

    fun snapshot(target: SaveTarget): Map<String, File> {
        val root = target.directory ?: return emptyMap()
        val files = linkedMapOf<String, File>()
        var visited = 0
        fun walk(file: File, relative: String, depth: Int) {
            check(++visited <= 4096 && depth <= 16, "Cây save quá lớn hoặc quá sâu.")
            check(file.canonicalFile == file.absoluteFile, "Cây save có liên kết không an toàn.")
            if (file.isDirectory) {
                val children = file.listFiles() ?: throw SaveTransferException("Không đọc được thư mục save.")
                check(children.size <= 4096, "Thư mục save có quá nhiều mục.")
                children.forEach { child -> walk(child, if (relative.isEmpty()) child.name else "$relative/${child.name}", depth + 1) }
            } else {
                check(file.isFile && allowed(target, relative), "File save không khớp game hoặc đường dẫn an toàn.")
                check(files.size < SaveFormats.MAX_FILES - 1, "Cây save có quá nhiều file.")
                check(files.keys.none { it.equals("save/$relative", true) }, "Cây save có tên trùng hoa/thường.")
                files["save/$relative"] = file
            }
        }
        if (!root.exists()) return files
        check(root.isDirectory && root.canonicalFile == root.absoluteFile, "Thư mục save không an toàn.")
        if (target.system == "psp") {
            val children = root.listFiles() ?: throw SaveTransferException("Không đọc được kho PSP.")
            check(children.size <= 4096, "Kho PSP quá lớn.")
            children.filter { it.name.matches(Regex("${target.identity}[A-Za-z0-9_-]{0,32}")) }.forEach { walk(it, it.name, 1) }
        } else walk(root, "", 0)
        return files
    }

    fun titleRoot(userDir: File, title: String): File {
        val sd = GameSavePaths.child(userDir, "sdmc/Nintendo 3DS")
        val hex = Regex("[a-fA-F0-9]{32}")
        val roots = mutableListOf<File>()
        val ids = sd.listFiles().orEmpty(); check(ids.size <= 64, "Có quá nhiều ID thẻ 3DS.")
        ids.filter { it.name.matches(hex) && it.isDirectory }.forEach { id0 ->
            check(id0.canonicalFile == id0.absoluteFile, "ID thẻ 3DS có liên kết không an toàn.")
            val cards = id0.listFiles().orEmpty(); check(cards.size <= 64, "Có quá nhiều ID thẻ 3DS.")
            cards.filter { it.name.matches(hex) && it.isDirectory }.forEach { id1 ->
                check(id1.canonicalFile == id1.absoluteFile, "ID thẻ 3DS có liên kết không an toàn.")
                roots += GameSavePaths.child(id1, "title/${title.take(8)}/${title.takeLast(8)}/data/00000001")
            }
        }
        val existing = roots.filter { it.isDirectory }
        val selected = if (existing.isNotEmpty()) existing else roots
        check(selected.size == 1, "Hãy mở game 3DS một lần để tạo kho save; nếu có nhiều thẻ/title trùng, chưa thể chọn an toàn.")
        return selected.single()
    }

    fun external(target: SaveTarget, files: Map<String, ByteArray>, filename: String): Map<String, ByteArray> {
        check(files.isNotEmpty(), "ZIP không có save.")
        val result = linkedMapOf<String, ByteArray>()
        files.forEach { (path, bytes) ->
            val relative = if (target.system == "psp") {
                val marker = "PSP/SAVEDATA/"
                val at = path.uppercase(Locale.ROOT).indexOf(marker)
                if (at < 0) path else {
                    check(at == 0 || path[at - 1] == '/', "Chọn ZIP PSP/SAVEDATA hoặc thư mục mã game.")
                    path.substring(at + marker.length)
                }
            } else {
                val parts = path.split('/')
                val titleAt = parts.indexOfFirst { it.equals("title", true) }
                if (titleAt >= 0) {
                    check(parts.size > titleAt + 5 && (parts[titleAt + 1] + parts[titleAt + 2]).lowercase(Locale.ROOT) == target.identity &&
                        parts[titleAt + 3] == "data" && parts[titleAt + 4] == "00000001", "Cây Citra/Azahar không khớp title ID hoặc không phải save thường.")
                    parts.drop(titleAt + 5).joinToString("/")
                } else {
                    val idAt = parts.indexOfFirst { it.matches(Regex("(?i)(?:0x)?[0-9a-f]{16}(?:[ _-].*)?")) }
                    if (idAt >= 0) {
                        val id = parts[idAt].removePrefix("0x").removePrefix("0X").take(16).lowercase(Locale.ROOT)
                        check(id == target.identity && parts.size > idAt + 2, "Bản sao Checkpoint không khớp title ID hoặc thiếu thư mục bản sao.")
                        parts.drop(idAt + 2).joinToString("/")
                    } else {
                        val name = File(filename).nameWithoutExtension.removePrefix("0x").removePrefix("0X").lowercase(Locale.ROOT)
                        check(name == target.identity, "Dump/JKSM không mang title ID: đóng gói nội dung save vào ZIP tên <title ID>.zip; không đoán game theo tên.")
                        check(parts.firstOrNull()?.equals("JKSV", true) != true, "Với JKSM, chỉ đóng gói nội dung một bản save đã giải mã, không nhập toàn kho JKSV.")
                        path
                    }
                }
            }
            check(allowed(target, relative) && result.keys.none { it.equals("save/$relative", true) }, "ZIP có file trùng hoặc không khớp mã game.")
            if (target.system == "3ds" && bytes.size >= 260) check(bytes.copyOfRange(256, 260).toString(Charsets.US_ASCII) !in setOf("DISA", "DIFF"),
                "Save 3DS còn là container mã hóa; cần dump file đã giải mã từ Checkpoint/JKSM.")
            result["save/$relative"] = bytes
        }
        validate(target, result)
        return result
    }

    fun validate(target: SaveTarget, files: Map<String, ByteArray>) {
        if (target.system != "psp") return
        val saves = files.filterKeys { it.startsWith("save/") }
        saves.keys.map { it.removePrefix("save/").substringBefore('/') }.distinct().forEach { dir ->
            check(dir.matches(Regex("${target.identity}[A-Za-z0-9_-]{0,32}")), "Thư mục PSP không khớp DISC_ID.")
            val sfo = saves["save/$dir/PARAM.SFO"] ?: throw SaveTransferException("Thư mục PSP thiếu PARAM.SFO nguyên bản.")
            check(ConsoleSaveFormats.sfo(sfo, "SAVEDATA_DIRECTORY") == dir, "PARAM.SFO không khớp thư mục PSP.")
        }
    }
}
