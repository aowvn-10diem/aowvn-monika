package vn.aow.monika.reader

import android.graphics.Bitmap

/**
 * Lỗi mở/đọc PDF — đủ rõ để màn đọc báo cho người dùng thay vì văng app.
 * Thứ tự khai báo không quan trọng; [message] là câu tiếng Việt hiển thị thẳng.
 */
enum class ReaderError(val message: String) {
    MISSING("Không tìm thấy tệp PDF. Có thể tệp đã bị xoá hoặc chuyển chỗ."),
    ENCRYPTED("PDF có mật khẩu nên chưa mở được. Hãy bỏ mật khẩu rồi thêm lại vào Thư viện."),
    CORRUPT("PDF này hỏng hoặc không có trang nào đọc được."),
    UNSUPPORTED("Máy này chưa dựng được PDF bằng bộ đọc có sẵn của Android."),
    TIMEOUT("Mở tệp PDF quá lâu. Tệp có thể quá lớn hoặc hỏng — thử lại hoặc chọn tệp khác."),
    PAGE_TIMEOUT("Trang này dựng quá lâu nên chưa hiện được. Thử lại hoặc sang trang khác."),
}

/** Lỗi đọc PDF kèm loại lỗi để màn đọc chọn thông báo. */
class ReaderException(val error: ReaderError, cause: Throwable? = null) : Exception(error.message, cause)

/** Một trang đã dựng sẵn: ảnh + tỉ lệ khung để màn đọc giữ đúng hình. */
class ReaderPage(val bitmap: Bitmap) {
    /** Rộng/cao, luôn > 0 để Compose không chia cho 0. */
    val aspect: Float get() = if (bitmap.height <= 0) 1f else bitmap.width.toFloat() / bitmap.height
}

/**
 * Nguồn trang của một tài liệu PDF. Đóng được và không giữ file descriptor sau khi đóng.
 * Tách giao diện để màn đọc/test không phụ thuộc trực tiếp vào PdfRenderer.
 */
interface PdfBook : AutoCloseable {
    val pageCount: Int

    /** Dựng trang [index] (đã kẹp trong 0..pageCount-1). */
    fun openPage(index: Int): ReaderPage
}
