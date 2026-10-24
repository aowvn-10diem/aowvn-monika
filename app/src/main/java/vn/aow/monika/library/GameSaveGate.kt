package vn.aow.monika.library

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.OverlappingFileLockException

/** Khóa OS dùng chung giữa tiến trình chính và :game; tiến trình chết tự nhả, tải trước không giữ khóa. */
object GameSaveGate {
    fun acquire(filesDir: File): Closeable {
        val file = RandomAccessFile(GameSavePaths.child(filesDir, "save-transfer.lock"), "rw")
        try {
            val lock = try { file.channel.tryLock() }
            // diag: bỏ qua tranh khóa trong cùng JVM là trạng thái bận dự kiến, trả lỗi cố định cho UI.
            catch (_: OverlappingFileLockException) { null }
            if (lock == null) throw SaveTransferException("Game hoặc thao tác chuyển save đang mở. Hãy thoát màn chơi rồi thử lại.")
            return Closeable { try { lock.release() } finally { file.close() } }
        // diag: bỏ qua lỗi mở khóa được ném lại sau đóng file, không giấu lỗi lưu game.
        } catch (error: Throwable) {
            file.close()
            throw error
        }
    }
}
