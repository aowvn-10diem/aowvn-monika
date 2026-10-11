package vn.aow.monika.runner

/**
 * V78a: khung game NDS ở màn dọc. Hai màn xếp chồng (tỉ lệ 2:3) nên khung phải cao 1,5× bề rộng thì hai màn mới
 * rộng hết cỡ; chỉ cắt bớt khi không còn chỗ cho tay cầm bên dưới (khi đó lõi tự thu nhỏ giữ tỉ lệ).
 */
internal object DsFrame {
    /** Hai màn NDS chồng dọc: rộng/cao. */
    const val ASPECT = 256f / 384f
    /**
     * Chừa cho tay cầm ảo NDS ở dưới (dp), chưa kể thanh tên/menu sát đáy [NDS_BOTTOM_BAR_DP]. Tay cầm cao ≈ 350 dp trên máy 393×851 dp
     * (đo từ ảnh chụp `6-tay-cam-nds`); chỉ chừa phần dưới hàng L/R (≈ 44 dp) — hai nút vai nằm ở 2 mép nên chồng lên góc dưới của màn cảm ứng,
     * đổi lại hai màn hình rộng hơn.
     */
    const val PAD_RESERVE_DP = 306
    /** Khe giữa mép trên (sau camera/thanh trạng thái) và màn trên, dp. Tiêu đề NDS tự ẩn nên không cần chừa 76 dp. */
    const val TOP_GAP_DP = 4

    /** Chiều cao khung (px) ở màn dọc: đủ để hai màn rộng hết cỡ, tối đa phần còn lại sau khi chừa tay cầm, không thấp hơn 40 % màn. */
    fun portraitHeight(widthPx: Int, heightPx: Int, topPx: Int, padReservePx: Int, aspect: Float = ASPECT): Int {
        val want = (widthPx / aspect).toInt()
        val room = heightPx - topPx - padReservePx
        return minOf(want, maxOf(room, heightPx * 4 / 10))
    }
}
