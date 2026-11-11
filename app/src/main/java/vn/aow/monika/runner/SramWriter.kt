package vn.aow.monika.runner

import java.io.File
import java.io.FileOutputStream

/**
 * V202 PR3: ghi SRAM nguyên tử — ghi `.tmp`, fsync, rồi đổi tên đè tệp cũ.
 * Lỗi giữa chừng thì tệp `.srm` cũ vẫn nguyên (không bị cắt cụt).
 */
internal object SramWriter {

    fun writeAtomic(file: File, bytes: ByteArray): Boolean {
        val dir = file.parentFile ?: return false
        val tmp = File(dir, file.name + ".tmp")
        return try {
            FileOutputStream(tmp).use { out ->
                out.write(bytes)
                out.fd.sync()
            }
            if (tmp.renameTo(file)) {
                true
            } else {
                // Một số hệ tệp (Windows) không cho đổi tên đè tệp đã có; Android/Linux đổi được.
                if (file.delete() && tmp.renameTo(file)) {
                    true
                } else {
                    tmp.delete()
                    false
                }
            }
            // diag: bỏ qua lỗi ghi tệp tại đây — nơi gọi nhận false và báo Diagnostics (không nuốt im lặng).
        } catch (error: Exception) {
            tmp.delete()
            false
        }
    }
}
