package vn.aow.monika.runner

import java.io.File

/**
 * Tệp hệ thống mà lõi ĐỌC nhưng cấu hình KHÔNG khai `systemFiles` → app không tải hộ.
 *
 * Chỉ để BÁO (một dòng `Diagnostics.crumb`), KHÔNG chặn: lõi citra tự tạo thư mục `sysdata`
 * rỗng rồi vẫn nạp game; chỉ game nào gọi `GetSharedFont` mới thiếu chữ/đứng hình. Chặn ở đây
 * là chặn oan những game không dùng phông hệ thống — nên không đổi luồng chạy.
 */
internal object CoreSystemFiles {
    /** Lõi → tệp hệ thống app không tải hộ (chỉ để báo). */
    private val undeclared = mapOf("citra" to "shared_font.bin")

    /**
     * Nơi lõi đọc tệp, theo thứ tự ưu tiên: libretro đặt "user dir" của citra vào
     * `<save của game>/Citra/` (`citra_libretro.cpp` → `GetSaveDir()` + `"Citra/"` →
     * `SetUserPath`), rồi `<system>/Citra/` khi frontend không cấp save dir; `sysdata/` là
     * `file_util.cpp` nối vào user dir, tệp `shared_font.bin` là `apt.cpp`
     * (`LoadLegacySharedFont`).
     */
    fun dirs(systemDir: File, saveDir: File?): List<File> = listOfNotNull(
        saveDir?.let { File(File(it, "Citra"), "sysdata") },
        File(File(systemDir, "Citra"), "sysdata"),
    )

    /** Tên tệp còn thiếu; null = đã đủ, lõi không dùng tệp ngoài `systemFiles`, hoặc chưa biết lõi đọc ở đâu. */
    fun missing(coreId: String, systemDir: File, saveDir: File?, savePathOption: String? = null): String? {
        // Tuỳ chọn "Citra Default" = lõi giữ user dir riêng của nó (không phải <save>/Citra) — app không
        // biết chỗ đó nên KHÔNG báo, tránh báo thiếu oan.
        if (!usesGameSaveDir(savePathOption)) return null
        val name = undeclared[coreId] ?: return null
        return name.takeIf { candidate -> dirs(systemDir, saveDir).none { File(it, candidate).isFile } }
    }

    /**
     * Lõi có đặt user dir vào thư mục save của game không (`citra_use_libretro_save_path`):
     * chỉ "LibRetro Default" (mặc định của lõi) là có; "Citra Default" thì không.
     */
    fun usesGameSaveDir(savePathOption: String?): Boolean = (savePathOption ?: LIBRETRO_SAVE_PATH) == LIBRETRO_SAVE_PATH

    /**
     * Một dòng cho vệt chẩn đoán: thiếu tệp nào, lõi đọc ở đâu, và vì sao không chặn.
     *
     * Đường dẫn ghi **tương đối** so với [filesDir]: `Diagnostics.scrub` che mọi đường dẫn tuyệt đối
     * (`/data/user/0/…` → `<đường-dẫn>`), ghi tuyệt đối là mất đúng phần cần đọc lại.
     */
    fun crumb(coreId: String, filesDir: File, systemDir: File, saveDir: File?, savePathOption: String? = null): String? {
        val name = missing(coreId, systemDir, saveDir, savePathOption) ?: return null
        val where = dirs(systemDir, saveDir).map { label(it, filesDir) }.distinct().joinToString(" hoặc ")
        return "$coreId thiếu $name — không chặn (lõi tự tạo thư mục rỗng, game có thể mất chữ). " +
            "Lõi đọc, so với thư mục dữ liệu app: $where"
    }

    /** Nhãn tương đối trong [filesDir]; nằm ngoài thì chỉ nêu tên chuẩn, không lộ đường dẫn máy. */
    private fun label(dir: File, filesDir: File): String {
        // diag: bỏ qua — nhãn chỉ để ghi vệt chẩn đoán; khác ổ đĩa/thư mục lạ thì rơi về tên chuẩn bên dưới
        val relative = runCatching { dir.relativeTo(filesDir).invariantSeparatorsPath }.getOrNull()
        return relative?.takeIf { it.isNotEmpty() && !it.startsWith("..") } ?: "Citra/sysdata"
    }

    /** Mặc định của lõi, và cũng là giá trị duy nhất đặt user dir vào thư mục save của game. */
    const val LIBRETRO_SAVE_PATH = "LibRetro Default"
}
