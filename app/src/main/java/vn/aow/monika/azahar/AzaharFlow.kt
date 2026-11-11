package vn.aow.monika.azahar

/**
 * V205: phân loại tệp đầu vào của engine 3DS.
 *
 * `.cia` là **gói cài** (game, bản cập nhật, DLC) phải cài vào NAND rồi mới chơi được — không phải ROM
 * để `NativeLibrary.run()`. Đưa `.cia` vào [AzaharActivity] như ROM sẽ hỏng luồng mở game (crash native /
 * treo ở bước nạp), nên mọi chỗ nhận tệp 3DS phải rẽ nhánh bằng hàm này trước khi chạy engine.
 */
object AzaharFlow {
    /** Tên tệp `.cia` (không phân biệt hoa/thường). Chỉ xét đuôi, không tin nội dung tệp. */
    fun isInstallerPackage(path: String): Boolean {
        if (path.isBlank()) return false
        val name = path.substringAfterLast('/').substringAfterLast('\\')
        val ext = name.substringAfterLast('.', "")
        return ext.equals("cia", ignoreCase = true)
    }
}
