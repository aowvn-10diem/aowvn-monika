package vn.aow.monika.apkinstall.adb

import android.content.Context
import android.provider.Settings

/** Trạng thái 3 công tắc gỡ lỗi của máy (đọc được, không cần quyền đặc biệt). */
data class DevState(val devOptions: Boolean, val adbUsb: Boolean, val adbWifi: Boolean) {
    val anyOn get() = devOptions || adbUsb || adbWifi

    companion object {
        fun read(context: Context): DevState {
            fun on(key: String) = runCatching { Settings.Global.getInt(context.contentResolver, key, 0) == 1 }.getOrDefault(false)
            return DevState(on("development_settings_enabled"), on("adb_enabled"), on("adb_wifi_enabled"))
        }
    }
}

/** Trạng thái công tắc lúc Monika bắt đầu hướng dẫn (ghi 1 lần; xóa sau khi trả lại), để sống sót khi tiến trình bị dừng giữa chừng. */
object AdbInitialStore {
    private const val KEY = "adb_initial"
    fun encode(s: DevState) = "${if (s.devOptions) 1 else 0}${if (s.adbUsb) 1 else 0}${if (s.adbWifi) 1 else 0}"
    fun decode(v: String?): DevState? = v?.takeIf { it.length == 3 && it.all { c -> c == '0' || c == '1' } }?.let { DevState(it[0] == '1', it[1] == '1', it[2] == '1') }

    private fun sp(c: Context) = c.getSharedPreferences("adb_state", Context.MODE_PRIVATE)
    /** Giữ giá trị đầu tiên nếu đã có (lần bấm sau đó công tắc có thể đã do Monika nhờ bật). */
    fun captureOnce(c: Context): DevState = load(c) ?: DevState.read(c).also { sp(c).edit().putString(KEY, encode(it)).apply() }
    fun load(c: Context): DevState? = decode(sp(c).getString(KEY, null))
    fun clear(c: Context) { sp(c).edit().remove(KEY).apply() }
}

/**
 * Sau khi xong việc, trả các công tắc về như lúc trước khi Monika nhờ bật:
 * cái nào Monika bật (lúc đầu đang tắt) thì tắt lại; cái nào người dùng đã bật từ trước thì để nguyên.
 * Lý do: app ngân hàng (Thông tư 77/2025/TT-NHNN) từ chối chạy khi thấy gỡ lỗi/Tùy chọn nhà phát triển đang bật.
 */
object AdbCleanup {
    /**
     * Lệnh shell tắt lại. Tắt Gỡ lỗi không dây ĐỂ CUỐI CÙNG vì lệnh đó sẽ cắt luôn kết nối.
     * Trả null nếu không có gì cần tắt.
     */
    fun command(initial: DevState): String? {
        val parts = buildList {
            if (!initial.adbUsb) add("settings put global adb_enabled 0")
            if (!initial.devOptions) add("settings put global development_settings_enabled 0")
            if (!initial.adbWifi) add("settings put global adb_wifi_enabled 0")
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString("; ")
    }

    /** Đã trả về đúng trạng thái ban đầu chưa (đọc lại từ Monika sau khi tắt). */
    fun restored(initial: DevState, now: DevState): Boolean =
        (initial.adbUsb || !now.adbUsb) && (initial.devOptions || !now.devOptions) && (initial.adbWifi || !now.adbWifi)
}
