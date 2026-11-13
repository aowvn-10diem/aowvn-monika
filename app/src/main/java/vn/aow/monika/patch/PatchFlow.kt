package vn.aow.monika.patch

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.aow.monika.diag.Diagnostics
import java.io.File

/** Luồng "vá Việt hóa" cho 1 game trong Thư viện: đọc bản vá từ Uri → vá → ghi "(Việt hóa)" cạnh ROM gốc. Trả thông điệp cho người chơi. */
object PatchFlow {
    private const val MAX_BYTES = 256L * 1024 * 1024 // ROM lớn hơn không vá trong bộ nhớ (ISO dùng xdelta/PPF: chưa hỗ trợ)

    /**
     * Người chơi chọn ROM/bản vá không phù hợp (ROM quá lớn, bản vá sai định dạng hoặc của ROM khác)
     * là luồng bình thường — chỉ ghi vệt, không lập báo cáo `op:va-rom`. Lỗi thật (mkdirs/rename/OOM)
     * vẫn đi qua [Diagnostics.opFail] không kèm `expected`.
     */
    private fun inputFail(context: Context, step: String, e: PatchException, rom: File) =
        Diagnostics.opFail(context, Diagnostics.Op.PATCH_ROM, step, e, rom.name, expected = { it is PatchException })

    suspend fun run(context: Context, rom: File, patchUri: Uri): String = withContext(Dispatchers.IO) {
        try {
            if (rom.length() > MAX_BYTES) {
                inputFail(context, "kiem-tra-rom", PatchException("ROM vượt giới hạn vá ${rom.length() / (1024 * 1024)} MB"), rom)
                return@withContext "File game quá lớn để vá trong app (${rom.length() / (1024 * 1024)} MB)."
            }
            val patchBytes = context.contentResolver.openInputStream(patchUri)?.use { it.readBytes() }
                ?: run {
                    inputFail(context, "doc-ban-va", PatchException("không mở được luồng bản vá"), rom)
                    return@withContext "Không đọc được file bản vá."
                }
            val fmt = RomPatcher.detect(patchBytes) ?: run {
                inputFail(context, "nhan-dang-ban-va", PatchException("không nhận ra định dạng bản vá"), rom)
                return@withContext "Không nhận ra định dạng bản vá (hỗ trợ IPS, BPS, UPS)."
            }
            val result = RomPatcher.apply(rom.readBytes(), patchBytes)
            // Mỗi thư mục = 1 game: bản vá nằm trong thư mục riêng "<tên> (Việt hóa)" để hiện thành game riêng trong Thư viện.
            val root = vn.aow.monika.library.GameStorage.games(context)
            var dir = File(root, "${rom.parentFile?.name ?: rom.nameWithoutExtension} (Việt hóa)")
            var n = 2
            while (dir.exists()) dir = File(root, "${rom.parentFile?.name ?: rom.nameWithoutExtension} (Việt hóa $n)").also { n++ }
            if (!dir.mkdirs()) {
                Diagnostics.opFail(context, Diagnostics.Op.PATCH_ROM, "tao-thu-muc", java.io.IOException("mkdirs thất bại"), rom.name)
                return@withContext "Không tạo được thư mục cho bản đã vá."
            }
            rom.parentFile?.let { vn.aow.monika.library.GameMeta.read(it) }?.let { vn.aow.monika.library.GameMeta.write(dir, it.copy(title = it.title.ifBlank { rom.nameWithoutExtension } + " (Việt hóa)")) }
            val out = File(dir, rom.name)
            val tmp = File(out.parentFile, out.name + ".part")
            tmp.writeBytes(result)
            if (!tmp.renameTo(out)) {
                tmp.delete(); dir.deleteRecursively()
                Diagnostics.opFail(context, Diagnostics.Op.PATCH_ROM, "doi-ten-file", java.io.IOException("renameTo thất bại"), rom.name)
                return@withContext "Không ghi được file sau khi vá."
            }
            "Đã vá (${fmt.label}). Game \"${dir.name}\" đã có trong Thư viện, ROM gốc giữ nguyên."
        } catch (e: PatchException) {
            // Bản vá hỏng / sai ROM là lựa chọn của người chơi (BPS/UPS kiểm CRC32): ghi vệt, không báo.
            inputFail(context, "ap-ban-va", e, rom)
            e.message ?: "Vá không thành công."
        } catch (e: java.io.IOException) {
            Diagnostics.opFail(context, Diagnostics.Op.PATCH_ROM, "doc-ghi", e, rom.name)
            "Không vá được: ${e.message}"
        } catch (e: OutOfMemoryError) {
            Diagnostics.opFail(context, Diagnostics.Op.PATCH_ROM, "het-bo-nho", e, rom.name)
            "Không đủ bộ nhớ để vá file này."
        }
    }
}
