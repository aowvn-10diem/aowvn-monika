package vn.aow.monika.library

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Kho lưu game do Monika quản lý: mọi save (SRAM), save state của lõi libretro và dữ liệu engine 3DS nằm trong thư mục app.
 * Xuất ra 1 file .zip (sao lưu / chuyển máy) và nhập lại; chỉ đọc/ghi các thư mục save, không đụng lõi hay cấu hình.
 */
object SaveVault {
    /** Thư mục (tương đối filesDir) thuộc về save của người chơi. */
    private val roots = listOf("saves", "states", "cheats", "azahar/sdmc", "azahar/states", "azahar/cheats", "azahar/nand/data")

    private fun files(c: Context) = roots.flatMap { r ->
        File(c.filesDir, r).walkTopDown().filter { it.isFile }.toList()
    }

    /** Số file & dung lượng đang có (hiện ở Cài đặt). */
    fun summary(c: Context): Pair<Int, Long> = files(c).let { it.size to it.sumOf(File::length) }

    fun export(c: Context, out: Uri): Int {
        var n = 0
        c.contentResolver.openOutputStream(out)?.use { os ->
            ZipOutputStream(os.buffered()).use { z ->
                for (f in files(c)) {
                    z.putNextEntry(ZipEntry(f.relativeTo(c.filesDir).invariantSeparatorsPath))
                    f.inputStream().use { it.copyTo(z) }
                    z.closeEntry(); n++
                }
            }
        }
        return n
    }

    /** Nhập: chỉ nhận đường dẫn nằm trong [roots], chặn "../". Trả về số file đã ghi. */
    fun import(c: Context, src: Uri): Int {
        var n = 0
        val base = c.filesDir.canonicalFile
        c.contentResolver.openInputStream(src)?.use { ins ->
            ZipInputStream(ins.buffered()).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    if (e.isDirectory || roots.none { e.name.startsWith("$it/") }) continue
                    val f = File(base, e.name).canonicalFile
                    if (!f.path.startsWith(base.path + File.separator)) continue
                    f.parentFile?.mkdirs()
                    f.outputStream().use { z.copyTo(it) }
                    n++
                }
            }
        }
        return n
    }
}
