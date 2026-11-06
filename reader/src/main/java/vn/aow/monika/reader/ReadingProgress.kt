package vn.aow.monika.reader

import android.content.Context

/**
 * Nhớ trang đang đọc dở của từng tài liệu.
 * Ghi vào SharedPreferences "monika" — cùng kho với `Prefs` của app, nên app đọc/ghi qua
 * `Prefs.readingPage(key)` / `Prefs.setReadingPage(key, page)` là thấy ngay.
 * Khoá theo đường dẫn tệp (tệp không có đường dẫn thì theo tên).
 */
class ReadingProgress(context: Context) {

    private val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Trang đã lưu (0-based). Chưa đọc lần nào → 0. */
    fun page(key: String): Int = sp.getInt(pageKey(key), 0).coerceAtLeast(0)

    fun setPage(key: String, page: Int) {
        val safeKey = pageKey(key)
        sp.edit()
            .putInt(safeKey, page.coerceAtLeast(0))
            .putString(nameKey(key), displayName(key))
            .apply()
    }

    /** Tên tệp đã lưu lần cuối, để biết khoá này của tài liệu nào khi đọc mã. */
    fun lastName(key: String): String? = sp.getString(nameKey(key), null)

    companion object {
        private const val PREFS_NAME = "monika"
        internal const val PREFIX = "read_page_"

        /**
         * Khoá ổn định cho 1 tài liệu: băm FNV-1a 64-bit của đường dẫn đã chuẩn hoá
         * (không nhét cả đường dẫn vào tên khoá; Windows/Android khác dấu "\" vẫn ra cùng trang).
         */
        fun pageKey(key: String): String = PREFIX + hash64(normalize(key))

        internal fun nameKey(key: String): String = "read_name_" + hash64(normalize(key))

        /** Tệp không có đường dẫn → dùng tên; rỗng → "unknown" để không ghi vào khoá chung. */
        internal fun normalize(key: String): String {
            val trimmed = key.trim()
            if (trimmed.isEmpty()) return "unknown"
            return trimmed.replace('\\', '/').trimEnd('/')
        }

        private fun displayName(key: String): String {
            val path = normalize(key)
            return if (path == "unknown") "unknown" else path.substringAfterLast('/').ifBlank { path }
        }

        internal fun hash64(value: String): String {
            var hash = -0x340d631b7bdddcdbL // 0xcbf29ce484222325 — FNV-1a 64-bit offset basis
            for (b in value.toByteArray(Charsets.UTF_8)) {
                hash = hash xor (b.toLong() and 0xff)
                hash *= 0x100000001b3L // FNV prime
            }
            return java.lang.Long.toHexString(hash)
        }
    }
}
