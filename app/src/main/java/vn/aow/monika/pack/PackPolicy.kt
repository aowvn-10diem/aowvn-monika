package vn.aow.monika.pack

/** Lựa chọn của người dùng cho một gói lớn đang chờ. */
enum class PackChoice { WAIT_WIFI, USE_CELLULAR }

enum class PackAction { START, ASK, WAIT }

/**
 * Quy tắc mạng cho tải trước (docs/plan-app-nhe.md 7.3). Hàm thuần để test.
 * - Wi-Fi (mạng không tính phí) → tải ngay.
 * - Gói ≤ 15 MB → tự tải, kể cả 4G (trừ khi bật Tiết kiệm dữ liệu).
 * - Gói lớn hơn (hoặc chưa biết dung lượng) → hỏi; "Tải luôn" → tải, "Đợi Wi-Fi" → chờ tới khi có Wi-Fi.
 */
object PackPolicy {
    const val AUTO_LIMIT_BYTES = 15L * 1024 * 1024

    fun decide(sizeBytes: Long, unmetered: Boolean, dataSaver: Boolean, choice: PackChoice?): PackAction = when {
        unmetered -> PackAction.START
        choice == PackChoice.USE_CELLULAR -> PackAction.START
        choice == PackChoice.WAIT_WIFI -> PackAction.WAIT
        sizeBytes in 1..AUTO_LIMIT_BYTES && !dataSaver -> PackAction.START
        else -> PackAction.ASK
    }
}
