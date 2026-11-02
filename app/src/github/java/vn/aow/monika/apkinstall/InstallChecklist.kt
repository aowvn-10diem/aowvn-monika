package vn.aow.monika.apkinstall

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 3 cách cài game cần Data / game cũ (xem docs/plan-apk-installer.md §5). */
enum class Method(val title: String, val note: String) {
    REPACK("Cách 1 — Monika tự chỉnh game", "Khuyên dùng. Tự động, không cần bật gì."),
    SAF("Cách 2 — Cấp quyền thư mục", "Bạn chọn thư mục dữ liệu của game. Chỉ hợp game cần Data, máy chưa vá bảo mật."),
    ADB("Cách 3 — Gỡ lỗi không dây", "Bật tạm gỡ lỗi không dây lúc cài, xong Monika tự tắt."),
}

enum class State { NOT_TRIED, RUNNING, OK, FAILED, NOT_AVAILABLE }

@Serializable
data class Attempt(val method: Method, val state: State, val reason: String? = null, val at: Long = 0)

/** Nhật ký "đã thử cách nào, kết quả ra sao" cho từng game (theo tên gói + phiên bản). Lưu trong SharedPreferences. */
class InstallChecklist(private val get: (String) -> String?, private val put: (String, String) -> Unit, private val clock: () -> Long = { System.currentTimeMillis() }) {
    private val json = Json { ignoreUnknownKeys = true }
    private fun key(pkg: String, versionCode: Long) = "checklist_${pkg}_$versionCode"

    fun read(pkg: String, versionCode: Long): List<Attempt> {
        val saved = get(key(pkg, versionCode))?.let { runCatching { json.decodeFromString<List<Attempt>>(it) }.getOrNull() }.orEmpty()
        // Luôn đủ 3 dòng theo thứ tự Cách 1, 2, 3.
        return Method.values().map { m -> saved.firstOrNull { it.method == m } ?: Attempt(m, State.NOT_TRIED) }
    }

    fun set(pkg: String, versionCode: Long, method: Method, state: State, reason: String? = null) {
        val next = read(pkg, versionCode).map { if (it.method == method) Attempt(method, state, reason, clock()) else it }
        put(key(pkg, versionCode), json.encodeToString(kotlinx.serialization.builtins.ListSerializer(Attempt.serializer()), next))
    }

    fun clear(pkg: String, versionCode: Long) = put(key(pkg, versionCode), "[]")

    /** Cách tiếp theo nên thử: đầu tiên chưa thử và còn dùng được. Null = hết cách. */
    fun next(pkg: String, versionCode: Long, applicable: Set<Method>): Method? =
        read(pkg, versionCode).firstOrNull { it.method in applicable && it.state == State.NOT_TRIED }?.method

    companion object {
        fun from(context: android.content.Context): InstallChecklist {
            val sp = context.getSharedPreferences("install_checklist", android.content.Context.MODE_PRIVATE)
            return InstallChecklist({ sp.getString(it, null) }, { k, v -> sp.edit().putString(k, v).apply() })
        }
    }
}
