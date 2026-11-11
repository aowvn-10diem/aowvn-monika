package vn.aow.monika.azahar

import java.io.File

/**
 * Tệp `.cia` là **gói cài đặt** (game, bản cập nhật, DLC) chứ không phải ROM chạy trực tiếp.
 * Đưa thẳng vào engine ([org.citra.citra_emu.NativeLibrary.run]) thì engine không mở được nội dung
 * và thoát bằng `core-error` (hoặc sập native trên máy yếu) — xem đặc tả V205.
 *
 * Vì vậy người chơi mở `.cia` từ thư viện được dẫn sang màn cài ([AzaharInstallActivity],
 * cài bằng `CiaInstallWorker`), và màn cài **không bao giờ xoá tệp gốc** của người chơi.
 */
object AzaharCia {
    /** Thư mục bộ đệm Monika tự chép `.cia` vào khi người chơi chọn từ bộ nhớ máy (SAF). */
    fun stagingDir(cacheDir: File): File = File(cacheDir, "cia")

    /** Đuôi `.cia`, không phân biệt hoa/thường. */
    fun isCia(path: String): Boolean = File(path).extension.equals("cia", ignoreCase = true)

    /** Tên bản sao trong bộ đệm: giữ tên gốc cho dễ đọc, thêm số thứ tự chống trùng. */
    fun stagedName(index: Int, name: String): String = "$index-" + name.replace('/', '_').replace('\\', '_')

    /**
     * Tệp có nằm trong thư mục bộ đệm của Monika không (chỉ những tệp này mới được xoá sau khi cài).
     * So sánh đường dẫn tuyệt đối; thư mục bộ đệm do Monika tạo nên không có đường dẫn lạ.
     */
    fun isStagedCopy(stagingDir: File, file: File): Boolean {
        val root = stagingDir.absolutePath.trimEnd(File.separatorChar) + File.separator
        val path = file.absolutePath
        return path.length > root.length && path.startsWith(root)
    }
}
