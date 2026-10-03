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
    /** Mở màn chơi (gói đã sẵn sàng). [entry] = file hoặc thư mục game; null = để engine tự hỏi. */
    val open: (activity: Activity, entry: File?, title: String, key: String?) -> Unit,
)

object EngineRoutes {
    val all: Map<String, EngineRoute> = mapOf(
        "kirikiri" to EngineRoute(PackManager.KIRIKIRI, "Kirikiri") { a, e, t, k -> KirikiriGameActivity.start(a, e?.takeIf { it.isFile }, t, k) },
        "rgss" to EngineRoute(PackManager.RGSS, "RPG Maker XP/VX/Ace") { a, e, t, k -> RgssGameActivity.start(a, e, t, k) },
    )

    /** Engine đã đăng ký VÀ gói của nó có bản cho kiến trúc máy này. */
    fun usable(engine: String?): EngineRoute? =
        engine?.let(all::get)?.takeIf { vn.aow.monika.AppGraph.packs.supported(it.packId) }
}
