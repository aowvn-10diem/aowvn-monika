package vn.aow.monika.library

import java.io.File

/**
 * Kiểm bộ tệp đĩa của game trước khi nạp lõi (PS1).
 *
 * Lõi `pcsx_rearmed` nạp game qua tệp danh sách: `.cue` phải trỏ tới tệp đĩa có thật, `.m3u` phải trỏ tới các
 * `.cue` có thật (tài liệu libretro: bộ đĩa PS1 cần cue-sheet trỏ tới image). Thiếu tệp đĩa thì lõi không nạp
 * được game, mà trước đây người chơi chỉ thấy "mã lỗi 1" chung (issue #322, #578).
 *
 * Không liên quan BIOS: `pcsx_rearmed` tự dùng HLE BIOS khi thư mục hệ thống không có tệp BIOS, và `melondsds`
 * đặt `melonds_sysfile_mode = builtin` nên cũng không cần firmware ngoài — vì vậy ở đây **không** kiểm/chặn BIOS.
 */
object DiscSet {
    /** Số tên tệp thiếu tối đa nêu trong một thông điệp (đủ để người chơi biết thiếu gì). */
    private const val MAX_LISTED = 3

    private val CUE_FILE = Regex("""^file\s+(?:"([^"]+)"|(\S+))""", RegexOption.IGNORE_CASE)
    private val ABSOLUTE_WINDOWS = Regex("""^[A-Za-z]:[\\/]""")

    /**
     * Trả thông điệp tiếng Việt nêu tên tệp còn thiếu, hoặc `null` khi đủ tệp / không kiểm được / không áp dụng.
     * [folderLabel] là đường dẫn **tương đối** của thư mục game (không in đường dẫn tuyệt đối của máy).
     */
    fun problem(game: File, folderLabel: String = game.parentFile?.name.orEmpty()): String? {
        val names = when (game.extension.lowercase()) {
            "cue" -> references(game) { line -> CUE_FILE.find(line)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } } }
            "m3u" -> references(game) { line -> line.takeUnless { it.startsWith("#") } }
            else -> return null
        }
        val dir = game.parentFile ?: return null
        // Tên tuyệt đối, tên rỗng hoặc tên bị lỗi giải mã (cue Shift-JIS đọc theo UTF-8) thì bỏ qua: không chặn oan.
        val checked = names.map { it.trim() }
            .filter { it.isNotEmpty() && !it.contains('\uFFFD') && !it.startsWith("/") && !ABSOLUTE_WINDOWS.containsMatchIn(it) }
        if (checked.isEmpty()) return null
        val missing = checked.distinct().filterNot { exists(dir, it) }
        if (missing.isEmpty()) return null
        val listed = missing.take(MAX_LISTED).joinToString("\n") { "• $it" }
        val more = if (missing.size > MAX_LISTED) "\n• … và ${missing.size - MAX_LISTED} tệp nữa" else ""
        val where = folderLabel.takeIf { it.isNotBlank() }?.let { " trong thư mục \"$it\"" }.orEmpty()
        return "Thiếu tệp đĩa của game.\n\nTệp ${game.name} cần những tệp sau$where:\n$listed$more\n\n" +
            "Hãy nhập lại game kèm đủ tệp đĩa (.cue phải đi cùng tệp .bin của nó), rồi mở lại.\nBấm Quay lại để thoát."
    }

    /** Mọi tên tệp được tệp danh sách nhắc tới; không đọc được thì trả rỗng để lõi tự báo lỗi. */
    private fun references(game: File, pick: (String) -> String?): List<String> {
        // diag: bỏ qua — không đọc được tệp danh sách đĩa, để lõi tự báo lỗi
        val text = runCatching { game.readText(Charsets.UTF_8) }.getOrNull() ?: return emptyList()
        return text.lineSequence().mapNotNull { pick(it.trim()) }.toList()
    }

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
