package vn.aow.monika.apkinstall.adb

import java.io.OutputStream

/** Cửa sổ lệnh tới quyền shell của chính máy này (qua gỡ lỗi không dây). Tách interface để test bằng bản giả. */
interface AdbShell {
    /** Chạy lệnh `shell:` và trả toàn bộ đầu ra. */
    suspend fun shell(command: String): String

    /** Chạy lệnh `exec:` rồi ghi [size] byte vào đầu vào của lệnh (dùng để đẩy file). Trả đầu ra. */
    suspend fun execWrite(command: String, size: Long, writer: (OutputStream) -> Unit): String
}
