package vn.aow.monika.reader

/**
 * Cầu nối ghi lỗi đã xử lý của module `:reader`.
 *
 * Module không phụ thuộc `:app` (nơi có `Diagnostics`), nên app gắn hook này lúc mở màn đọc:
 * `ReaderDiag.onError = { what, t -> Diagnostics.recordHandled(context, "doc-sach", what, t) }`.
 * Lỗi trong module (dựng trang, mở tệp, đóng tài nguyên) đều đi qua đây thay vì bị nuốt im lặng.
 */
object ReaderDiag {

    /** Nhãn thành phần dùng khi app gắn hook vào `Diagnostics`. */
    const val COMPONENT = "doc-sach"

    @Volatile
    var onError: ((String, Throwable?) -> Unit)? = null

    /** Ghi một lỗi đã xử lý; không bao giờ ném ra ngoài. */
    fun report(what: String, t: Throwable? = null) {
        onError?.invoke(what, t)
    }
}
