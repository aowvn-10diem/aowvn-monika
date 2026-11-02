package vn.aow.monika.runner

import vn.aow.monika.config.CoreScreen

/**
 * V160: giữ trạng thái "chiều màn hình nào đã thật sự ghi vào lõi" để xoay máy chỉ ghi khi đổi chiều.
 * Chỉ đánh dấu SAU khi lõi nhận cập nhật ([write] chạy xong): xoay sớm lúc view chưa tạo
 * (`onConfigurationChanged` trước khi `GLRetroView` có) không bị nuốt mất — lần gọi sau, khi view
 * đã sẵn sàng, sẽ ghi lại đúng bố cục của chiều hiện tại.
 */
class ScreenLayoutSwitch {
    /** Chiều đã ghi được vào lõi; false = dọc (bố cục dọc đã có sẵn từ lúc nạp game). */
    var appliedLandscape = false
        private set

    /**
     * Ghi bố cục cho chiều [landscape] nếu cần. [write] = null khi view chưa sẵn sàng.
     * Trả về true khi đã ghi và đánh dấu chiều; false khi không có gì để ghi hoặc view chưa sẵn sàng.
     * Đọc lại lựa chọn đã lưu mỗi lần gọi ([saved] = `CoreOptions.saved`): người chơi có thể vừa đổi bố cục trong game.
     */
    fun apply(screen: CoreScreen, saved: Map<String, String>, landscape: Boolean, write: ((Pair<String, String>) -> Unit)?): Boolean {
        // Chỉ ghi khi đổi chiều thật sự (tránh ghi lại mỗi lần cấu hình đổi vì lý do khác).
        if (landscape == appliedLandscape) return false
        val value = DsScreen.rotationLayout(screen, saved, landscape)
        if (value.isBlank() || write == null) return false
        write(screen.layoutKey to value)
        appliedLandscape = landscape
        return true
    }
}
