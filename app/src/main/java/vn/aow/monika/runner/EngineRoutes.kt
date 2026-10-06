package vn.aow.monika.runner

import android.app.Activity
import vn.aow.monika.pack.PackManager
import java.io.File

/**
 * Engine nhúng kiểu "module Java + gói native tải thêm" (Kirikiri, sau này RGSS/Ren'Py).
 * Khóa của bảng = `system.engine` trong config. Thêm engine: đăng ký 1 dòng ở [EngineRoutes.all] + gói trong [PackManager].
 */
class EngineRoute(
    /** Id gói trong `modules.<id>` và [PackManager]. */
    val packId: String,
    /** Tên hiển thị trên màn chuẩn bị và trong câu báo lỗi. */
    val label: String,
    /**
     * Tùy chọn: kiểm/chọn lại lối vào TRƯỚC khi mở (đọc đĩa, gọi ở luồng nền, có timeout). null = dùng nguyên entry.
     * Lỗi hoặc quá hạn → giữ nguyên lối vào. Đặt trước [open] để cú pháp lambda cuối vẫn gắn vào [open].
     */
    val resolve: ((entry: File?) -> vn.aow.monika.library.EntryResolution)? = null,
    /** Mở màn chơi (gói đã sẵn sàng). [entry] = file hoặc thư mục game; null = để engine tự hỏi. */
    val open: (activity: Activity, entry: File?, title: String, key: String?) -> Unit,
)

object EngineRoutes {
    val all: Map<String, EngineRoute> = mapOf(
        "kirikiri" to EngineRoute(
            PackManager.KIRIKIRI, "Kirikiri",
            // Thư mục chỉ truyền cho engine khi có startup.tjs rời (V26); thư mục khác vẫn để engine tự hỏi như trước.
            open = { a, e, t, k ->
                val entry = e?.takeIf { it.isFile || (it.isDirectory && it.listFiles().orEmpty().any { f -> f.isFile && f.name.equals("startup.tjs", true) }) }
                KirikiriGameActivity.start(a, entry, t, k)
            },
            resolve = { e ->
                val patch = vn.aow.monika.AppGraph.config.current.systems.firstOrNull { it.id == "kirikiri" }?.entryExclude
                vn.aow.monika.library.KirikiriEntryResolver.resolve(e, patch?.takeIf { it.isNotEmpty() } ?: vn.aow.monika.library.KirikiriEntryResolver.DEFAULT_PATCH)
            },
        ),
        "renpy" to EngineRoute(PackManager.RENPY8, "Ren'Py") { a, e, t, k -> RenpyGameActivity.start(a, e, t, k) },
        "rgss" to EngineRoute(PackManager.RGSS, "RPG Maker XP/VX/Ace") { a, e, t, k -> RgssGameActivity.start(a, e, t, k) },
    )

    /** Engine đã đăng ký VÀ gói của nó có bản cho kiến trúc máy này. */
    fun usable(engine: String?): EngineRoute? =
        engine?.let(all::get)?.takeIf { vn.aow.monika.AppGraph.packs.supported(it.packId) }
}
