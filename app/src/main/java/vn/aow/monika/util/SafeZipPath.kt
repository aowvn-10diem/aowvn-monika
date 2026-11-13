package vn.aow.monika.util

import java.io.File

/**
 * Kiểm tên mục trong file nén (zip/7z/rar) và làm sạch tên tệp đầu vào từ hệ thống.
 *
 * Hai nhóm dùng chung:
 * - Giải nén: [isSafeName]/[resolve] chặn zip-slip, đường dẫn tuyệt đối, ký tự NUL.
 * - Nhận tệp: [sanitizeSingleFileName] biến tên do ContentResolver/Content-Disposition/URL
 *   trả về thành một tên tệp đơn an toàn (không đường dẫn, không ký tự điều khiển, ≤ 180 byte).
 */
object SafeZipPath {

    /** Giới hạn tên tệp theo byte UTF-8 (đủ cho hầu hết hệ tệp, không cắt mất phần mở rộng). */
    const val MAX_NAME_BYTES = 180

    /** Tên mục có an toàn để ghi vào thư mục đích không. */
    fun isSafeName(name: String): Boolean {
        if (name.isEmpty()) return false
        if (name.indexOf('\u0000') >= 0) return false
        // Tên mục không bao giờ được chứa ký tự điều khiển (xuống dòng/tab) — tránh lọt qua log và đường dẫn.
        if (name.any { it.code < 0x20 || it.code == 0x7F }) return false
        val path = name.replace('\\', '/')
        if (path.startsWith("/")) return false                    // /etc/x, //may-chu/share
        if (path.length > 1 && path[1] == ':') return false        // C:/x, C:\x
        val parts = path.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty()) return false
        return parts.none { it == ".." }
    }

    /**
     * File đích cho [name] bên trong [root]; `null` = từ chối mục này (không ghi gì ra đĩa).
     * Sau khi resolve, đường dẫn chuẩn hoá vẫn phải nằm trong [root].
     */
    fun resolve(root: File, name: String): File? {
        if (!isSafeName(name)) return null
        val base = root.canonicalFile
        val out = File(base, name.replace('\\', '/')).canonicalFile
        val prefix = base.path.trimEnd(File.separatorChar) + File.separator
        return if (out.path.startsWith(prefix)) out else null
    }

    /**
     * Làm sạch **một** tên tệp do bên ngoài đưa vào (ContentResolver, Content-Disposition, URL).
     * - Bỏ mọi phần đường dẫn (`a/b/c.zip` → `c.zip`).
     * - Bỏ ký tự điều khiển (xuống dòng, tab, NUL…) và dấu chấm/space ở cuối.
     * - Từ chối rỗng, `.`, `..`.
     * - Cắt còn ≤ [MAX_NAME_BYTES] byte UTF-8 nhưng giữ phần mở rộng.
     * `null` = không có tên dùng được, nơi gọi tự đặt tên dự phòng.
     */
    fun sanitizeSingleFileName(raw: String): String? {
        var name = raw.replace('\\', '/').substringAfterLast('/')
        name = name.filterNot { it.code < 0x20 || it.code == 0x7F }
        // Ký tự hệ tệp không hợp lệ (Windows/SAF) đổi thành "_" như hành vi cũ, giữ tên dễ đọc.
        name = name.replace(Regex("""[:*?"<>|]"""), "_")
        name = name.trim().trimEnd('.', ' ')
        if (name.isEmpty() || name == "." || name == "..") return null
        if (!isSafeName(name)) return null
        return clamp(name)
    }

    private fun clamp(name: String): String {
        if (name.toByteArray(Charsets.UTF_8).size <= MAX_NAME_BYTES) return name
        val dot = name.lastIndexOf('.')
        val extension = if (dot > 0 && name.length - dot <= 12 &&
            name.substring(dot + 1).all { it.isLetterOrDigit() }) name.substring(dot) else ""
        val base = if (extension.isEmpty()) name else name.substring(0, dot)
        var end = base.length
        while (end > 0 && (base.substring(0, end) + extension).toByteArray(Charsets.UTF_8).size > MAX_NAME_BYTES) end--
        val cut = base.substring(0, end).trimEnd('.', ' ')
        return (cut + extension).ifEmpty { "tep" }
    }
}
