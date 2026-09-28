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
    fun clearDownload(id: Long) = sp.edit().remove("tool_$id").apply()

    /** Mức hiệu ứng: "auto" (tự theo cấu hình máy) | "FULL" | "LITE" | "OFF". */
    var motionMode: String
        get() = sp.getString("motion_mode", "auto")!!
        set(value) = sp.edit().putString("motion_mode", value).apply()

    /** Lần chơi gần nhất của từng game (theo đường dẫn thư mục). */
    fun markPlayed(gameDir: String) = sp.edit().putLong("played_$gameDir", System.currentTimeMillis()).apply()
    fun lastPlayed(gameDir: String): Long = sp.getLong("played_$gameDir", 0L)

    /** Độ mờ tay cầm ảo (0.2–1.0). */
    var padOpacity: Float
        get() = sp.getFloat("pad_opacity", 0.65f)
        set(value) = sp.edit().putFloat("pad_opacity", value).apply()
}
