package vn.aow.monika.cheats

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import vn.aow.monika.AppGraph
import java.io.File

/** Nơi mã cheat thực sự có tác dụng (lõi libretro, hoặc engine khác). */
interface CheatBackend {
    /** Áp trạng thái bật/tắt của [list] vào game đang chạy; [previous] là danh sách đã áp trước đó (để tắt mã đã bị xóa). */
    fun apply(previous: List<CheatEntry>, list: List<CheatEntry>)
}

@Serializable
private data class Saved(val attempted: Boolean = false, val set: String = "", val list: List<CheatEntry> = emptyList())

/**
 * Hệ thống cheat CHUNG của Monika cho mọi giả lập: lưu theo game trong thư mục do Monika quản lý (nằm trong kho save → sao lưu được),
 * tự nhận diện game và thêm mã từ kho libretro-database, bật/tắt/thêm/xóa/nhập tệp .cht, cùng một giao diện menu popup.
 */
class CheatController(
    private val context: Context,
    private val gameKey: String,
    private val systemId: String,
    private val names: List<String>,
    private val backend: CheatBackend,
    private val db: CheatDb = CheatDb(context, AppGraph.http),
) {
    var open by mutableStateOf(false)
    var items by mutableStateOf<List<CheatEntry>>(emptyList())
    var variants by mutableStateOf<List<CheatDb.Candidate>>(emptyList())
    var currentSet by mutableStateOf("")
    var adding by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
    private var applied: List<CheatEntry> = emptyList()
    private var attempted = false

    private val file = File(File(context.filesDir, "cheats").apply { mkdirs() }, "$gameKey.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun load() {
        val s = runCatching { json.decodeFromString(Saved.serializer(), file.readText()) }.getOrNull() ?: Saved()
        items = s.list; attempted = s.attempted; currentSet = s.set
    }

    private fun save() {
        runCatching { file.writeText(json.encodeToString(Saved.serializer(), Saved(attempted, currentSet, items))) }
    }

    /**
     * Gọi khi game bắt đầu: đọc mã đã lưu; game chưa từng nhận diện thì tự tìm + thêm mã từ kho (chỉ thêm, mặc định TẮT).
     * Trả về true nếu vừa tự thêm mã mới.
     */
    suspend fun prepare(): Boolean = withContext(Dispatchers.IO) {
        load()
        var added = false
        if (!attempted && db.supports(systemId)) {
            val found = db.match(systemId, names)
            variants = found
            if (found.isNotEmpty()) {
                val fetched = db.fetch(found.first())
                if (fetched != null) { items = fetched; currentSet = found.first().file; added = fetched.isNotEmpty() }
                attempted = fetched != null // lỗi mạng → thử lại lần chơi sau
            } else attempted = true
            save()
        } else if (db.supports(systemId)) variants = db.match(systemId, names)
        added
    }

    /** Áp các mã đang bật vào game (gọi sau khi game vẽ khung hình đầu). */
    fun applyAll() { backend.apply(emptyList(), items); applied = items }

    fun show() { message = null; open = true }
    fun close() { open = false; adding = false }

    private fun commit(newList: List<CheatEntry>) {
        items = newList; save()
        backend.apply(applied, newList); applied = newList
    }

    fun toggle(i: Int) = commit(items.toMutableList().also { it[i] = it[i].copy(enabled = !it[i].enabled) })

    fun remove(i: Int) = commit(items.toMutableList().also { it.removeAt(i) })

    /** Trả về null nếu thêm được, hoặc thông báo lỗi. */
    fun add(name: String, code: String): String? {
        if (name.isBlank()) return "Nhập tên cho mã"
        if (code.isBlank()) return "Nhập mã cheat"
        commit(items + CheatEntry(name.trim(), code.trim().lines().joinToString("+") { it.trim() }.replace(Regex("""\++"""), "+")))
        return null
    }

    fun importFile(uri: Uri) {
        val text = runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
        val list = text?.let { ChtFormat.parse(it) }.orEmpty()
        if (list.isEmpty()) { message = "Không đọc được mã trong tệp (cần định dạng .cht)"; return }
        commit(items + list)
        message = "Đã nhập ${list.size} mã"
    }

    /** Đổi sang bộ mã khác của cùng game (vd. GameShark ↔ Code Breaker). */
    suspend fun switchSet(c: CheatDb.Candidate) {
        busy = true
        val fetched = withContext(Dispatchers.IO) { db.fetch(c) }
        busy = false
        if (fetched == null) { message = "Không tải được bộ mã (kiểm tra mạng)"; return }
        // Giữ mã người chơi tự thêm; thay các mã tự động.
        currentSet = c.file
        commit(items.filter { it.source != "auto" } + fetched)
        message = "Đã chọn bộ mã: ${c.label}"
    }
}
