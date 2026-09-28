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
}
