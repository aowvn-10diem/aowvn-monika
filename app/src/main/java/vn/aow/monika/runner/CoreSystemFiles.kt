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
     * Nơi lõi tìm tệp, theo thứ tự ưu tiên: libretro đặt "user dir" của citra vào thư mục save
     * của game (`citra_use_libretro_save_path`), rồi tới thư mục `system` mà app truyền cho lõi.
     */
    fun dirs(systemDir: File, saveDir: File?): List<File> = listOfNotNull(
        saveDir?.let { File(File(it, "Citra"), "sysdata") },
        File(File(systemDir, "Citra"), "sysdata"),
        File(systemDir, "sysdata"),
    )

    /** Tên tệp còn thiếu; null = đã đủ, hoặc lõi không dùng tệp nào ngoài `systemFiles`. */
    fun missing(coreId: String, systemDir: File, saveDir: File?): String? {
        val name = undeclared[coreId] ?: return null
        return name.takeIf { candidate -> dirs(systemDir, saveDir).none { File(it, candidate).isFile } }
    }

    /** Một dòng cho vệt chẩn đoán: thiếu tệp nào, đặt vào đâu, và vì sao không chặn. */
    fun crumb(coreId: String, systemDir: File, saveDir: File?): String? {
        val name = missing(coreId, systemDir, saveDir) ?: return null
        val where = dirs(systemDir, saveDir).first().absolutePath
        return "$coreId thiếu $name — không chặn (lõi tự tạo thư mục rỗng rồi chạy tiếp); đặt tệp vào: $where"
    }
}
