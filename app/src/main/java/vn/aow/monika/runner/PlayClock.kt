package vn.aow.monika.runner

import vn.aow.monika.AppGraph

/** Đếm thời gian chơi thật của 1 game: chỉ tính lúc màn game đang hiện (onResume → onPause). */
class PlayClock(private val key: String?) {
    private var since = 0L

    init {
        // Giả lập của Monika tự đếm chính xác → bỏ phiên ước lượng tạo lúc bấm Chơi.
        if (key != null) AppGraph.prefs.cancelSession()
    }

    fun resume() { since = System.currentTimeMillis() }

    fun pause() {
        val k = key ?: return
        if (since > 0) AppGraph.prefs.addPlayTime(k, System.currentTimeMillis() - since)
        since = 0
    }

    companion object {
        const val EXTRA_KEY = "game_key"
    }
}
