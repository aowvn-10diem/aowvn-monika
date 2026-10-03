package vn.aow.monika.diag

import android.content.Context

/**
 * Đoán thành phần gây lỗi từ stack/log/vệt sự kiện để báo cáo có nhãn rõ, dễ lọc:
 *  pack:<tên> (gói tải thêm) · engine:<tên> (lõi/engine chạy game) · ra (RetroAchievements) · net · app:<mục>.
 * Thứ tự luật: cụ thể trước, chung sau; phiên game (nếu có) chỉ là phương án dự phòng cho mã native không có stack Java.
 */
object Components {
    private val PACK_ID = Regex("""pack:([a-z0-9_-]+)""")

    fun of(c: Context?, session: Diagnostics.Session?, text: String): String {
        val t = text
        PACK_ID.find(t)?.let { return "pack:${it.groupValues[1]}" }
        return when {
            has(t, "SevenZip", "sevenzip", "7-Zip", "7z") -> "pack:sevenzip"
            has(t, "vn.aow.monika.pack", "PackWorker", "PackManager", "SimpleModule") -> "pack"
            has(t, "rcheevos", "RetroAchievements", "vn.aow.monika.achievements", "achievementsjni") -> "ra"
            has(t, "azahar", "citra") -> "engine:azahar"
            has(t, "WebGameActivity", "onsyuri", "chromium", "WebView") -> "engine:onsyuri"
            has(t, "ru.playsoftware.j2meloader", "javax.microedition", "j2me", "dexlib") -> "engine:j2me"
            // RPG Maker (mkxp-z) và Kirikiri nhúng: nhận từ tên lớp/thư viện hoặc vệt "begin: <kind> · <lõi> · …" của tiến trình :game.
            session?.kind == "rgss" || has(t, "RgssGameActivity", "vn.aow.monika.rgss", "mkxp", "libruby") -> "engine:rgss"
            session?.kind == "kirikiri" || has(t, "KirikiriGameActivity", "org.tvp.kirikiri2", "krkr2yuri", "kirikiroid") -> "engine:kirikiri"
            session?.kind == "libretro" || has(t, "com.swordfish.libretrodroid", "libretrodroid", "_libretro_android") ->
                "engine:libretro" + (session?.core?.takeIf { it.isNotBlank() }?.let { ":$it" } ?: "")
            session?.kind == "web" -> "engine:onsyuri"
            session?.kind == "j2me" -> "engine:j2me"
            has(t, "okhttp", "SocketException", "UnknownHostException", "SSLException", "SocketTimeout") -> "net"
            has(t, "androidx.compose", "vn.aow.monika.ui") -> "app:ui"
            else -> Regex("""vn\.aow\.monika\.(\w+)""").find(t)?.let { "app:${it.groupValues[1]}" }.orEmpty()
        }
    }

    private fun has(t: String, vararg keys: String) = keys.any { t.contains(it, ignoreCase = false) }
}
