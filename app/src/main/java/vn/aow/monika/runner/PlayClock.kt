package vn.aow.monika.runner

import vn.aow.monika.AppGraph

/**
 * Đếm thời gian chơi thật của 1 game: chỉ tính lúc màn game đang hiện (onResume → onPause).
 * Chạy ở tiến trình game → không ghi thẳng vào SharedPreferences (2 tiến trình ghi chung sẽ đè nhau),
 * mà gửi sự kiện về cho tiến trình chính ([vn.aow.monika.Prefs.mergeGameEvents]).
 */
class PlayClock(private val key: String?) {
    private var since = 0L

    init {
        // Giả lập của Monika tự đếm chính xác → bỏ phiên ước lượng tạo lúc bấm Chơi.
        if (key != null) AppGraph.prefs.postGameEvent("cancel")
    }

    fun resume() { since = System.currentTimeMillis() }

    fun pause() {
        val k = key ?: return
        if (since > 0) {
            val ms = System.currentTimeMillis() - since
            if (ms >= 3_000) AppGraph.prefs.postGameEvent("play\t$k\t$ms")
        }
        since = 0
    }

    companion object {
        const val EXTRA_KEY = "game_key"
    }
}
