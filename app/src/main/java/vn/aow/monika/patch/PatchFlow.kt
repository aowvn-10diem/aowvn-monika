package vn.aow.monika.patch

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Luồng "vá Việt hóa" cho 1 game trong Thư viện: đọc bản vá từ Uri → vá → ghi "(Việt hóa)" cạnh ROM gốc. Trả thông điệp cho người chơi. */
object PatchFlow {
    private const val MAX_BYTES = 256L * 1024 * 1024 // ROM lớn hơn không vá trong bộ nhớ (ISO dùng xdelta/PPF: chưa hỗ trợ)

    suspend fun run(context: Context, rom: File, patchUri: Uri): String = withContext(Dispatchers.IO) {
        try {
            if (rom.length() > MAX_BYTES) return@withContext "File game quá lớn để vá trong app (${rom.length() / (1024 * 1024)} MB)."
            val patchBytes = context.contentResolver.openInputStream(patchUri)?.use { it.readBytes() }
                ?: return@withContext "Không đọc được file bản vá."
            val fmt = RomPatcher.detect(patchBytes) ?: return@withContext "Không nhận ra định dạng bản vá (hỗ trợ IPS, BPS, UPS)."
            val result = RomPatcher.apply(rom.readBytes(), patchBytes)
            // Mỗi thư mục = 1 game: bản vá nằm trong thư mục riêng "<tên> (Việt hóa)" để hiện thành game riêng trong Thư viện.
            val root = vn.aow.monika.library.GameStorage.games(context)
            var dir = File(root, "${rom.parentFile?.name ?: rom.nameWithoutExtension} (Việt hóa)")
            var n = 2
            while (dir.exists()) dir = File(root, "${rom.parentFile?.name ?: rom.nameWithoutExtension} (Việt hóa $n)").also { n++ }
            if (!dir.mkdirs()) return@withContext "Không tạo được thư mục cho bản đã vá."
            rom.parentFile?.let { vn.aow.monika.library.GameMeta.read(it) }?.let { vn.aow.monika.library.GameMeta.write(dir, it.copy(title = it.title.ifBlank { rom.nameWithoutExtension } + " (Việt hóa)")) }
            val out = File(dir, rom.name)
            val tmp = File(out.parentFile, out.name + ".part")
            tmp.writeBytes(result)
            if (!tmp.renameTo(out)) { tmp.delete(); dir.deleteRecursively(); return@withContext "Không ghi được file sau khi vá." }
            "Đã vá (${fmt.label}). Game \"${dir.name}\" đã có trong Thư viện, ROM gốc giữ nguyên."
        } catch (e: PatchException) {
            e.message ?: "Vá không thành công."
        } catch (e: java.io.IOException) {
            "Không vá được: ${e.message}"
        } catch (e: OutOfMemoryError) {
            "Không đủ bộ nhớ để vá file này."
        }
    }
}
