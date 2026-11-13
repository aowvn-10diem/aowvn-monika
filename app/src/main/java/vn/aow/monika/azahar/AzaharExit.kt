package vn.aow.monika.azahar

/**
 * Quyết định lúc đóng màn chơi 3DS ([AzaharActivity.onDestroy]) — tách thành hàm thuần để test được
 * thứ tự bắt buộc khi thoát: dừng engine → chờ luồng engine thoát → **chỉ khi đã thoát** mới xoá dấu
 * phiên ([AzaharSession]) → kill tiến trình `:game`.
 */
object AzaharExit {
    /** Chờ tối đa ngần này cho luồng engine thoát (chờ ở luồng nền, không chặn main thread). */
    const val JOIN_TIMEOUT_MS = 1500L

    /**
     * Có được kill tiến trình `:game` không. Chuyển sang màn cài `.cia` ([AzaharInstallActivity]) thì
     * màn đó cần tiến trình sống tiếp — kill ở đây sẽ giết màn cài giữa lúc đang cài.
     */
    fun shouldKillProcess(finishing: Boolean, handoffToInstall: Boolean): Boolean = finishing && !handoffToInstall

    /**
     * Có được xoá dấu phiên không: **chỉ khi** luồng engine đã thoát hẳn. Còn sống sau hạn chờ thì phải
     * giữ dấu [AzaharSession.STARTED]; kill tiến trình lúc engine đang ghi bộ đệm shader để lại cache
     * hỏng, và chính dấu còn lại là thứ giúp lần mở sau dọn cache thay vì sập tiếp.
     */
    fun canEndSession(engineThread: Thread?): Boolean = engineThread?.isAlive != true
}
