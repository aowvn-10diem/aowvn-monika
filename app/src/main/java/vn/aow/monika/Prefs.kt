package vn.aow.monika

import android.content.Context

/** Thiết lập nhỏ lưu trên máy (SharedPreferences). */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("monika", Context.MODE_PRIVATE)

    /** Mã các bài đã thấy, để biết bài nào mới. Rỗng = chưa chạy lần nào. */
    var seenPostIds: Set<String>
        get() = sp.getStringSet("seen_post_ids", emptySet())!!.toSet()
        set(value) = sp.edit().putStringSet("seen_post_ids", value).apply()

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
    fun markPlayed(gameDir: String) = sp.edit().putLong("played_$gameDir", System.currentTimeMillis()).apply()
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
