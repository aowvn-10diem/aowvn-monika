package vn.aow.monika.diag

import android.content.Context
import java.io.File

/**
 * Vệt sự kiện ("breadcrumbs"): mỗi tiến trình ghi nối đuôi vào 1 file nhỏ `diag/crumbs/<pid>.log`.
 * Khi tiến trình chết (kể cả native crash, không kịp chạy mã Java), lần mở sau vẫn đọc lại được "trước khi chết app đang làm gì".
 * File giới hạn ~24 KB (cắt đầu), tự xóa file của tiến trình đã được xử lý.
 */
object Breadcrumbs {
    private const val MAX_BYTES = 24_000
    private const val KEEP_LINES = 40
    private val lock = Any()

    private fun dir(c: Context) = File(c.filesDir, "diag/crumbs").apply { mkdirs() }
    private fun file(c: Context, pid: Int) = File(dir(c), "$pid.log")

    fun add(c: Context, tag: String, msg: String, pid: Int = android.os.Process.myPid()) {
        runCatching {
            val line = "${java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(System.currentTimeMillis())} [$tag] ${msg.replace('\n', ' ').take(200)}\n"
            synchronized(lock) {
                val f = file(c, pid)
                if (f.length() > MAX_BYTES) f.writeText(f.readLines().takeLast(KEEP_LINES).joinToString("\n", postfix = "\n"))
                f.appendText(line)
            }
        }
    }

    fun read(c: Context, pid: Int, max: Int = KEEP_LINES): List<String> =
        runCatching { synchronized(lock) { file(c, pid).takeIf { it.exists() }?.readLines()?.takeLast(max).orEmpty() } }.getOrDefault(emptyList())

    fun drop(c: Context, pid: Int) { runCatching { file(c, pid).delete() } }

    /** Dọn file của tiến trình cũ đã quá 3 ngày (không còn báo cáo nào cần). */
    fun sweep(c: Context) {
        runCatching { dir(c).listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 3L * 24 * 3600 * 1000 }?.forEach { it.delete() } }
    }
}
