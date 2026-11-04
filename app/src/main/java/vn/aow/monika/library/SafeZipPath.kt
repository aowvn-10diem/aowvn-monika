package vn.aow.monika.library

import java.io.File

/**
 * Kiểm tên mục trong file nén (zip/7z) trước khi ghi ra đĩa — chống zip-slip.
 *
 * Chỉ nhận đường dẫn TƯƠNG ĐỐI nằm trong thư mục đích; từ chối đoạn `..`, đường dẫn tuyệt đối
 * (`/x`, `C:\x`, `\\may-chu\share`) và tên có ký tự NUL.
 */
object SafeZipPath {

    /** Tên mục có an toàn để ghép vào thư mục đích không. */
    fun isSafeName(name: String): Boolean {
        if (name.isEmpty()) return false
        if (name.indexOf('\u0000') >= 0) return false
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
}
