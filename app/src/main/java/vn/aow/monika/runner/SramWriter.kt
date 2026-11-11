package vn.aow.monika.runner

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * V202 PR3: ghi SRAM nguyên tử — ghi `.tmp`, fsync, rồi đổi tên đè tệp cũ.
 * Không xoá `.srm` cũ trước khi biết chắc tệp mới đã sẵn sàng: lỗi giữa chừng thì save cũ vẫn nguyên.
 */
internal object SramWriter {

    /** V202 PR3: chỉ một lượt ghi tại một thời điểm — hai lượt `saveSram` sát nhau không tranh `.tmp`. */
    @Synchronized
    fun writeAtomic(file: File, bytes: ByteArray): Boolean {
        val dir = file.parentFile ?: return false
        val tmp = File(dir, file.name + ".tmp")
        return try {
            FileOutputStream(tmp).use { out ->
                out.write(bytes)
                out.fd.sync()
            }
            move(tmp, file)
            true
            // diag: bỏ qua lỗi ghi tệp tại đây — nơi gọi nhận false và báo Diagnostics (không nuốt im lặng).
        } catch (error: Exception) {
            tmp.delete()
            false
        }
    }

    /** Android API 26+: đổi tên đè nguyên tử; hệ tệp không hỗ trợ thì đổi tên đè thường (không xoá trước). */
    private fun move(from: File, to: File) {
        try {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            // diag: bỏ qua lỗi ATOMIC_MOVE khi hệ tệp không hỗ trợ — chuyển sang đổi tên đè thường ngay dưới.
        } catch (_: Exception) {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
