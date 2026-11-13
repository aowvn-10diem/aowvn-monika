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
        // Dấu hiệu lõi libretro thắng dấu hiệu Azahar: đường Azahar hỏng sẽ chuyển sang lõi `citra`
        // trong **cùng tiến trình** (`AzaharActivity.fallback`), `Diagnostics.end()` chỉ xoá tệp phiên
        // nên vệt `[azahar] …` còn nằm trong text của báo cáo lõi libretro.
        val libretro = isLibretro(session, t)
        return when {
            has(t, "SevenZip", "sevenzip", "7-Zip", "7z") -> "pack:sevenzip"
            has(t, "vn.aow.monika.pack", "PackWorker", "PackManager", "SimpleModule") -> "pack"
            has(t, "rcheevos", "RetroAchievements", "vn.aow.monika.achievements", "achievementsjni") -> "ra"
            // Engine Azahar (nhúng) chỉ khi đúng đường Azahar và **không** có dấu hiệu lõi libretro.
            !libretro && (session?.kind == "azahar" || session?.core == "azahar" ||
                has(t, "vn.aow.monika.azahar", "AzaharActivity", "AzaharBridge", "AzaharDialog", "libcitra-android", "org.citra.citra_emu", "azahar")) -> "engine:azahar"
            has(t, "WebGameActivity", "onsyuri", "chromium", "WebView") -> "engine:onsyuri"
            has(t, "ru.playsoftware.j2meloader", "javax.microedition", "j2me", "dexlib") -> "engine:j2me"
            // RPG Maker (mkxp-z) và Kirikiri nhúng: nhận từ tên lớp/thư viện hoặc vệt "begin: <kind> · <lõi> · …" của tiến trình :game.
            session?.kind == "rgss" || has(t, "RgssGameActivity", "vn.aow.monika.rgss", "mkxp", "libruby") -> "engine:rgss"
            session?.kind == "renpy" || has(t, "RenpyGameActivity", "org.renpy.android", "librenpython", "renpy8", "renpy.bootstrap") -> "engine:renpy"
            session?.kind == "kirikiri" || has(t, "KirikiriGameActivity", "org.tvp.kirikiri2", "krkr2yuri", "kirikiroid") -> "engine:kirikiri"
            libretro ->
                "engine:libretro" + libretroCore(session, t).let { if (it.isEmpty()) "" else ":$it" }
            session?.kind == "web" -> "engine:onsyuri"
            session?.kind == "j2me" -> "engine:j2me"
            has(t, "okhttp", "SocketException", "UnknownHostException", "SSLException", "SocketTimeout") -> "net"
            // Chữ "citra" còn lại (không có dấu hiệu engine Azahar, không phải lỗi mạng) = lõi citra libretro:
            // "citra_libretro_android", tên lõi trong vệt "begin: libretro · citra · …". Chỉ khớp chữ thường
            // để không bắt nhầm "Citra/" (cây save).
            has(t, "citra") -> "engine:libretro:citra"
            has(t, "androidx.compose", "vn.aow.monika.ui") -> "app:ui"
            else -> Regex("""vn\.aow\.monika\.(\w+)""").find(t)?.let { "app:${it.groupValues[1]}" }.orEmpty()
        }
    }

    private fun has(t: String, vararg keys: String) = keys.any { t.contains(it, ignoreCase = false) }

    /** Báo cáo đến từ lõi libretro: theo phiên chơi, hoặc theo tên lớp/tệp của `:libretrodroid`. */
    private fun isLibretro(session: Diagnostics.Session?, t: String) =
        session?.kind == "libretro" || has(t, "com.swordfish.libretrodroid", "libretrodroid", "_libretro_android")

    /**
     * Tên lõi để ghép sau `engine:libretro`. Phiên `azahar` còn sót lại (đường Azahar hỏng chuyển sang lõi
     * libretro trong cùng tiến trình) không phải tên lõi, nên khi đó lấy theo dấu hiệu `citra` trong text.
     */
    private fun libretroCore(session: Diagnostics.Session?, t: String): String {
        val fromSession = session?.core?.takeIf { it.isNotBlank() && it != "azahar" }
        if (fromSession != null) return fromSession
        return if (t.contains("citra")) "citra" else ""
    }
}
