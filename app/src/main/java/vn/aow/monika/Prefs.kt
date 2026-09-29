package vn.aow.monika

import android.content.Context

/** Thiết lập nhỏ lưu trên máy (SharedPreferences). */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("monika", Context.MODE_PRIVATE)

    /** Mã các bài đã thấy, để biết bài nào mới. Rỗng = chưa chạy lần nào. */
    var seenPostIds: Set<String>
        get() = sp.getStringSet("seen_post_ids", emptySet())!!.toSet()
        set(value) = sp.edit().putStringSet("seen_post_ids", value).apply()

    /** Mốc "published" của bài mới nhất đã xem trong menu Thông báo. Rỗng = chưa mở lần nào. */
    var inboxSeen: String
        get() = sp.getString("inbox_seen", "").orEmpty()
        set(value) = sp.edit().putString("inbox_seen", value).apply()

    /** Lịch sử tìm kiếm (mới nhất trước, tối đa 12). */
    var searchHistory: List<String>
        get() = sp.getString("search_history", "").orEmpty().split('\n').filter { it.isNotBlank() }
        set(value) = sp.edit().putString("search_history", value.take(12).joinToString("\n")).apply()

    fun addSearch(q: String) {
        val t = q.trim().takeIf { it.length >= 2 } ?: return
        searchHistory = listOf(t) + searchHistory.filterNot { it.equals(t, ignoreCase = true) }
    }

    private val postListSer = kotlinx.serialization.builtins.ListSerializer(vn.aow.monika.feed.Post.serializer())
    private val lenientJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    /** Bài/game đã xem gần đây (hiện ở màn Tìm kiếm). */
    val recentPosts: List<vn.aow.monika.feed.Post>
        get() = runCatching { lenientJson.decodeFromString(postListSer, sp.getString("recent_posts", "[]")!!) }.getOrDefault(emptyList())

    fun addRecentPost(p: vn.aow.monika.feed.Post) {
        val lite = p.copy(contentHtml = "")
        val list = (listOf(lite) + recentPosts.filterNot { it.id == p.id }).take(12)
        sp.edit().putString("recent_posts", lenientJson.encodeToString(postListSer, list)).apply()
    }

    /** Nhãn muốn nhận thông báo. Rỗng = nhận tất cả. */
    var subscribedLabels: Set<String>
        get() = sp.getStringSet("subscribed_labels", emptySet())!!.toSet()
        set(value) = sp.edit().putStringSet("subscribed_labels", value).apply()

    /** Lượt tải là app/plugin (cài đặt ngay) chứ không phải game. */
    fun markToolDownload(id: Long) = sp.edit().putBoolean("tool_$id", true).apply()
    fun isToolDownload(id: Long) = sp.getBoolean("tool_$id", false)
    fun clearDownload(id: Long) = sp.edit().remove("tool_$id").remove("meta_$id").apply()

    /** Thông tin bài viết gắn với lượt tải (JSON của GameMeta), để game tải xong có ảnh bìa + tên đẹp. */
    fun setDownloadMeta(id: Long, json: String) = sp.edit().putString("meta_$id", json).apply()
    fun downloadMeta(id: Long): String? = sp.getString("meta_$id", null)

    /** Mức hiệu ứng: "auto" (tự theo cấu hình máy) | "FULL" | "LITE" | "OFF". */
    var motionMode: String
        get() = sp.getString("motion_mode", "auto")!!
        set(value) = sp.edit().putString("motion_mode", value).apply()

    /** Lần chơi gần nhất của từng game (theo đường dẫn thư mục). */
    fun markPlayed(gameDir: String) {
        sp.edit().putLong("played_$gameDir", System.currentTimeMillis())
            .putInt("playcount_$gameDir", playCount(gameDir) + 1)
            // Phiên chơi: game chạy ở app/tiến trình khác (Java, app ngoài) → tính giờ khi quay lại Monika.
            .putString("session_key", gameDir).putLong("session_start", System.currentTimeMillis()).apply()
        playedTick.value++
    }

    /** Tổng thời gian đã chơi (ms). */
    fun playTime(key: String): Long = sp.getLong("playtime_$key", 0L)

    fun addPlayTime(key: String, ms: Long) {
        if (ms < 3_000) return
        sp.edit().putLong("playtime_$key", playTime(key) + ms).apply()
        playedTick.value++
    }

    /** Giả lập tự đếm giờ chính xác (theo lúc màn game hiện) → bỏ phiên ước lượng. */
    fun cancelSession() = sp.edit().remove("session_key").remove("session_start").apply()

    /** Quay lại Monika: cộng thời gian phiên đang mở (tối đa 4 giờ, tránh tính cả lúc bỏ máy). */
    fun endSession() {
        val key = sp.getString("session_key", null) ?: return
        val start = sp.getLong("session_start", 0L)
        cancelSession()
        val ms = System.currentTimeMillis() - start
        if (start > 0 && ms in 10_000..4 * 3_600_000L) addPlayTime(key, ms)
    }
    /** Số lần mở chơi (cho mục "Thường xuyên chơi"). */
    fun playCount(gameDir: String): Int = sp.getInt("playcount_$gameDir", 0)
    /** Tăng mỗi lần chơi 1 game → Trang chủ / Thư viện tự cập nhật "Đang chơi dở". */
    val playedTick = kotlinx.coroutines.flow.MutableStateFlow(0)
    fun lastPlayed(gameDir: String): Long = sp.getLong("played_$gameDir", 0L)

    /**
     * Bộ nhớ đệm (kiểu Telegram). Giới hạn tính bằng byte, -1 = vô hạn. Mặc định 2GB.
     * Số ngày tự xóa khi không dùng, 0 = không bao giờ.
     */
    var cacheLimitBytes: Long
        get() = sp.getLong("cache_limit", 2L * 1024 * 1024 * 1024)
        set(value) = sp.edit().putLong("cache_limit", value).apply()
    var autoDeleteGameDays: Int
        get() = sp.getInt("auto_del_game", 0)
        set(value) = sp.edit().putInt("auto_del_game", value).apply()
    var autoDeleteCoreDays: Int
        get() = sp.getInt("auto_del_core", 30)
        set(value) = sp.edit().putInt("auto_del_core", value).apply()
    var autoDeleteMediaDays: Int
        get() = sp.getInt("auto_del_media", 7)
        set(value) = sp.edit().putInt("auto_del_media", value).apply()

    /** Lõi user chọn cho 1 hệ máy (null = dùng mặc định trong config). */
    fun coreOverride(systemId: String): String? = sp.getString("core_$systemId", null)
    fun setCoreOverride(systemId: String, core: String?) = sp.edit().putString("core_$systemId", core).apply()

    /** Game được "Giữ lại": không bao giờ bị dọn bộ đệm. */
    var pinnedGames: Set<String>
        get() = sp.getStringSet("pinned_games", emptySet())!!.toSet()
        set(value) = sp.edit().putStringSet("pinned_games", value).apply()

    /** Cỡ tay cầm ảo (0.8 / 1.0 / 1.2) và độ lệch vị trí (px) của cụm D-pad và cụm nút. */
    var padScale: Float
        get() = sp.getFloat("pad_scale", 1f)
        set(value) = sp.edit().putFloat("pad_scale", value).apply()
    fun padOffset(group: String): Pair<Float, Float> = sp.getFloat("pad_${group}_x", 0f) to sp.getFloat("pad_${group}_y", 0f)
    fun setPadOffset(group: String, x: Float, y: Float) = sp.edit().putFloat("pad_${group}_x", x).putFloat("pad_${group}_y", y).apply()

    /** Độ mờ tay cầm ảo (0.2–1.0). */
    var padOpacity: Float
        get() = sp.getFloat("pad_opacity", 0.65f)
        set(value) = sp.edit().putFloat("pad_opacity", value).apply()
}
