package vn.aow.monika.runner

/**
 * Dịch mã lỗi libretrodroid thành thông điệp tiếng Việt nêu việc người chơi làm được.
 *
 * Mã lấy từ `libretrodroid/src/main/cpp/errorcodes.cpp`: 0 = không `dlopen` được `.so` của lõi,
 * 1 = `retro_load_game()` trả false (tệp game hỏng/thiếu tệp đi kèm), 2 = thiếu OpenGL ES 3,
 * 3 = lỗi đọc/ghi save state, 4 = lỗi cheat, -1 = ngoại lệ khác trước khung hình đầu.
 *
 * Trước đây mọi mã đều hiện "File game hỏng hoặc lõi không hợp" — câu đó sai với mã 0/2/3/4 và không chỉ được
 * việc gì để làm (issue #322, #578 `pcsx_rearmed`, #567 `melondsds`).
 */
object CoreLoadError {
    /** Thông điệp đầy đủ hiện trên màn chờ. */
    fun text(code: Int, coreId: String): String = when (code) {
        0 -> "Lõi giả lập $coreId không nạp được: tệp lõi thiếu hoặc hỏng.\n" +
            "Vào Cài đặt → Lõi giả lập, bấm Xóa ở dòng $coreId rồi mở lại game để tải lại lõi.\n" +
            "Bấm Quay lại để thoát."
        1 -> "Lõi $coreId không nạp được game này.\n" +
            "Thường do tệp game hỏng hoặc thiếu tệp đi kèm (với PS1: tệp .cue phải đi cùng .bin của nó).\n" +
            "Hãy nhập lại game đầy đủ, hoặc thử đổi sang lõi khác.\nBấm Quay lại để thoát."
        2 -> "Máy này không có OpenGL ES 3 mà lõi $coreId cần.\n" +
            "Hãy thử đổi sang lõi khác.\nBấm Quay lại để thoát."
        3 -> "Không đọc được trạng thái lưu (save state) của game khi chạy lõi $coreId.\n" +
            "Xóa trạng thái đã lưu của game rồi mở lại.\nBấm Quay lại để thoát."
        4 -> "Mã cheat đang bật làm lõi $coreId dừng.\n" +
            "Tắt mã cheat của game rồi mở lại.\nBấm Quay lại để thoát."
        else -> "Lõi $coreId báo lỗi chưa rõ nguyên nhân (mã $code).\n" +
            "Hãy thử đổi sang lõi khác, hoặc gửi báo lỗi để AowVN xem.\nBấm Quay lại để thoát."
    }

    /** Câu ngắn cho thông báo nhanh (toast). */
    fun short(code: Int, coreId: String): String = when (code) {
        0 -> "Lõi $coreId không nạp được — vào Cài đặt → Lõi giả lập để xóa và tải lại."
        1 -> "Lõi $coreId không đọc được tệp game (hỏng hoặc thiếu tệp đi kèm)."
        2 -> "Máy thiếu OpenGL ES 3 cho lõi $coreId — thử đổi lõi khác."
        3 -> "Không đọc được save state của game — xóa trạng thái đã lưu rồi mở lại."
        4 -> "Mã cheat đang bật làm lõi dừng — tắt cheat rồi mở lại."
        else -> "Lỗi chạy game (mã $code)."
    }
}
