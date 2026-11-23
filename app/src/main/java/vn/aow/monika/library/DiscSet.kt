package vn.aow.monika.library

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Kiểm bộ tệp đĩa của game trước khi nạp lõi (PS1).
 *
 * Lõi `pcsx_rearmed` nạp game qua tệp danh sách: `.cue` phải trỏ tới tệp đĩa có thật, `.m3u` phải trỏ tới các
 * `.cue` có thật (tài liệu libretro: bộ đĩa PS1 cần cue-sheet trỏ tới image). Thiếu tệp đĩa thì lõi không nạp
 * được game, mà trước đây người chơi chỉ thấy "mã lỗi 1" chung (issue #322, #578).
 *
 * Nguyên tắc: **chỉ chặn khi chắc chắn** — tệp danh sách đọc được trọn vẹn, mọi tên đều phân giải sạch, và tệp
 * đó thật sự không có. Còn nghi ngờ (bảng mã lạ, tên có ký tự điều khiển/nháy, đường dẫn tuyệt đối) thì chỉ trả
 * nghi ngờ để người gọi ghi vệt, **không** chặn.
 *
 * Không liên quan BIOS: `pcsx_rearmed` tự dùng HLE BIOS khi thư mục hệ thống không có tệp BIOS, và `melondsds`
 * đặt `melonds_sysfile_mode = builtin` nên cũng không cần firmware ngoài — vì vậy ở đây **không** kiểm/chặn BIOS.
 */
object DiscSet {
    /** Số tên tệp thiếu tối đa nêu trong một thông điệp. */
    private const val MAX_LISTED = 3

    /** Tệp danh sách đĩa thật chỉ vài KB; lớn hơn mức này thì không đoán. */
    private const val MAX_BYTES = 256 * 1024

    private const val BOM = '\uFEFF'

    /** Đuôi tệp có thể thuộc một bộ đĩa: nhập rời vẫn phải nằm chung thư mục với tệp danh sách. */
    private val DISC_FILES = setOf(
        "cue", "m3u", "bin", "iso", "img", "chd", "pbp", "ccd", "sub", "wav", "flac", "mp3", "ogg",
    )

    /** Đuôi tệp danh sách đĩa. */
    private val LIST_EXTENSIONS = setOf("cue", "m3u")

    /** Số tệp danh sách đọc nội dung tối đa cho một lần nhập: thư viện lớn vẫn nhập nhanh. */
    private const val MAX_LIST_READS = 200

    /** Hậu tố chống trùng tên thư mục do `Importer.newGameDir` thêm: "Game (2)". */
    private val DEDUP_SUFFIX = Regex(""" \(\d+\)$""")

    private val CUE_FILE = Regex("""^file\s+(?:"([^"]+)"|(\S+))""", RegexOption.IGNORE_CASE)
    private val ABSOLUTE_WINDOWS = Regex("""^[A-Za-z]:[\\/]""")

    /**
     * Bộ đĩa có thể thiếu tệp. [certain] = chắc chắn tệp không tồn tại và tên đọc sạch → người gọi **được phép**
     * chặn; `false` thì chỉ ghi vệt rồi để lõi tự báo lỗi.
     */
    data class Suspicion(val message: String, val certain: Boolean)

    /**
     * Trả nghi ngờ khi bộ đĩa thiếu tệp, hoặc `null` khi đủ tệp / không kiểm được / không áp dụng.
     * [folderLabel] là đường dẫn **tương đối** của thư mục game (không in đường dẫn tuyệt đối của máy), chỉ được
     * gọi khi thật có tệp thiếu.
     */
    fun inspect(game: File, folderLabel: () -> String = { game.parentFile?.name.orEmpty() }): Suspicion? {
        val references = references(game) ?: return null
        if (references.isEmpty()) return null
        val dir = game.parentFile ?: return null
        val clean = references.filter(::resolvable)
        val missing = clean.distinct().filterNot { exists(dir, it) }
        if (missing.isEmpty()) return null
        val listed = missing.take(MAX_LISTED).joinToString("\n") { "• $it" }
        val more = if (missing.size > MAX_LISTED) "\n• … và ${missing.size - MAX_LISTED} tệp nữa" else ""
        val where = folderLabel().takeIf { it.isNotBlank() }?.let { " trong thư mục \"$it\"" }.orEmpty()
        val message = "Thiếu tệp đĩa của game.\n\nTệp ${game.name} cần những tệp sau$where:\n$listed$more\n\n" +
            "Hãy nhập lại game kèm đủ tệp đĩa (.cue phải đi cùng tệp .bin của nó), rồi mở lại.\nBấm Quay lại để thoát."
        return Suspicion(message, certain = clean.size == references.size)
    }

    /**
     * Thư mục game đã có trong thư viện mà tệp [name] thuộc về, hoặc `null` khi chưa có (người gọi tạo thư mục mới).
     *
     * Người chơi hay đưa `.cue` và `.bin` vào bằng hai lần chọn (hoặc chọn nhiều tệp): nếu mỗi tệp rơi vào một thư
     * mục riêng thì `.cue` không thấy `.bin` của nó và lõi `pcsx_rearmed` báo "không nạp được game" (issue #322, #578).
     * Chỉ gom khi **chắc chắn** cùng một bộ: cùng tên gốc, hoặc tệp danh sách trong thư mục nhắc đúng tên này.
     * Thư mục đã có tệp cùng tên thì **không** gom (tránh ghi đè tệp đang có).
     */
    fun companionFolder(root: File, name: String): File? {
        if (name.substringAfterLast('.', "").lowercase() !in DISC_FILES) return null
        val stem = name.substringBeforeLast('.')
        val free = root.listFiles().orEmpty().filter { it.isDirectory }
            .filter { dir -> dir.listFiles().orEmpty().none { it.isFile && it.name.equals(name, ignoreCase = true) } }
        // (1) Đã có tệp danh sách cùng tên gốc: "Game.bin" đi với "Game.cue".
        free.firstOrNull { dir -> discLists(dir).any { it.nameWithoutExtension.equals(stem, ignoreCase = true) } }
            ?.let { return it }
        // (2) Thư mục mang đúng tên gốc và đã có tệp đĩa (nhập .bin trước, .cue sau — hoặc ngược lại).
        //     Bỏ hậu tố chống trùng của [Importer.newGameDir] ("Game (2)") nhưng giữ tên đĩa thật ("Game (Disc 1)").
        free.firstOrNull { dir ->
            dir.name.replace(DEDUP_SUFFIX, "").equals(stem, ignoreCase = true) &&
                dir.listFiles().orEmpty().any { it.isFile && it.extension.lowercase() in DISC_FILES }
        }?.let { return it }
        // (3) Tệp danh sách nhắc đúng tên tệp này ("Game (Track 1).bin" khác tên gốc với cue).
        var reads = 0
        for (dir in free) {
            for (list in discLists(dir)) {
                if (reads++ >= MAX_LIST_READS) return null // thư viện quá lớn: giữ cách cũ, không đoán
                val referenced = references(list).orEmpty()
                if (referenced.any { baseName(it).equals(name, ignoreCase = true) }) return dir
            }
        }
        return null
    }

    /** Tệp danh sách đĩa (`.cue`/`.m3u`) trong [folder]. */
    private fun discLists(folder: File): List<File> =
        folder.listFiles().orEmpty().filter { it.isFile && it.extension.lowercase() in LIST_EXTENSIONS }

    /** Tên tệp không kèm thư mục (cue có thể ghi "discs/Game.bin"). */
    private fun baseName(name: String): String = name.replace('\\', '/').substringAfterLast('/')

    /** Các tệp danh sách của [game] nhắc tới; `null` khi tệp không phải danh sách đĩa. */
    private fun references(game: File): List<String>? = when (game.extension.lowercase()) {
        "cue" -> names(game) { line -> CUE_FILE.find(line)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } } }
        "m3u" -> names(game) { line -> line.takeUnless { it.startsWith("#") } }
        else -> null
    }

    /** Các tên tệp được tệp danh sách nhắc tới; `null` khi không đọc chắc được (để lõi tự báo lỗi). */
    private fun names(game: File, pick: (String) -> String?): List<String>? {
        val text = text(game) ?: return null
        return text.lineSequence()
            .map { it.trim().removePrefix(BOM.toString()).trim() } // BOM dòng đầu (Notepad/Word) không phải tên tệp
            .filter { it.isNotEmpty() }
            .mapNotNull { pick(it) }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
    }

    /**
     * Nội dung tệp theo bảng mã mà BOM khai báo (UTF-8 / UTF-16 LE / UTF-16 BE). `null` = không chắc: quá lớn,
     * sai bảng mã (Shift-JIS, Latin-1…), hoặc lỗi đọc.
     */
    private fun text(game: File): String? {
        if (game.length() > MAX_BYTES) return null
        // diag: bỏ qua — không đọc được tệp danh sách đĩa, để lõi tự báo lỗi
        val bytes = runCatching { game.readBytes() }.getOrNull() ?: return null
        val (charset, offset) = when {
            bytes.hasPrefix(0xEF, 0xBB, 0xBF) -> Charsets.UTF_8 to 3
            bytes.hasPrefix(0xFF, 0xFE) -> Charsets.UTF_16LE to 2
            bytes.hasPrefix(0xFE, 0xFF) -> Charsets.UTF_16BE to 2
            else -> Charsets.UTF_8 to 0
        }
        // diag: bỏ qua — bảng mã lạ thì coi là không chắc và không chặn
        return runCatching {
            charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset))
                .toString()
        }.getOrNull()?.removePrefix(BOM.toString())
    }

    private fun ByteArray.hasPrefix(vararg prefix: Int): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it].toByte() }

    /** Tên phân giải sạch: tương đối, không ký tự điều khiển/nháy/lỗi giải mã, không phải đường dẫn tuyệt đối. */
    private fun resolvable(name: String): Boolean =
        name.isNotEmpty() &&
            name.none { it < ' ' || it == '\u007F' || it == '\uFFFD' || it == '"' } &&
            !name.startsWith("/") && !ABSOLUTE_WINDOWS.containsMatchIn(name)

    /**
     * Tệp có thật trong thư mục game. Lệch hoa/thường vẫn coi là có: thẻ nhớ/FAT không phân biệt hoa thường,
     * chặn ở đó là chặn oan.
     */
    private fun exists(dir: File, name: String): Boolean {
        val file = File(dir, name.replace('\\', '/'))
        if (file.isFile) return true
        val folder = file.parentFile ?: return false
        return folder.list()?.any { it.equals(file.name, ignoreCase = true) } == true
    }
}
