package vn.aow.monika.apkinstall

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap

/** Danh sách game Monika đã chỉnh (chèn bộ nạp) — chỉ các gói này được gọi vào [LoaderBusProvider]/[GameDataProvider]. */
object RepackRegistry {
    private const val PREFS = "repack_registry"
    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun add(c: Context, pkg: String) { prefs(c).edit().putBoolean(pkg, true).apply() }
    fun remove(c: Context, pkg: String) { prefs(c).edit().remove(pkg).apply() }
    fun isKnown(c: Context, pkg: String?) = pkg != null && prefs(c).getBoolean(pkg, false)
}

/** Sự kiện bộ nạp trong game gửi về. Mốc thời gian dùng đồng hồ máy (ms). */
class LoaderState {
    @Volatile var startedAt = 0L
    @Volatile var screenAt = 0L
    @Volatile var lastBeat = 0L
    @Volatile var crashAt = 0L
    @Volatile var crashSummary: String? = null
}

data class CopyProgress(val done: Long, val total: Long, val files: Int)

sealed class CopyEvent {
    data class Progress(val p: CopyProgress) : CopyEvent()
    data class Done(val files: Int, val expected: Int, val bytes: Long) : CopyEvent()
    data class Error(val message: String) : CopyEvent()
}

/** Kho sự kiện trong bộ nhớ (cùng tiến trình với [LoaderBusProvider]). Đồng hồ nhận vào được để test. */
object LoaderEvents {
    var clock: () -> Long = { System.currentTimeMillis() }
    private val states = ConcurrentHashMap<String, LoaderState>()
    private val copy = ConcurrentHashMap<String, (CopyEvent) -> Unit>()

    fun state(pkg: String): LoaderState = states.getOrPut(pkg) { LoaderState() }

    /** Xóa trạng thái cũ trước khi mở thử game (để không nhầm với lần chạy trước). */
    fun reset(pkg: String) { states[pkg] = LoaderState() }

    fun onCopy(job: String, listener: ((CopyEvent) -> Unit)?) { if (listener == null) copy.remove(job) else copy[job] = listener }

    /** [method]/[arg]/[extras] đúng như bộ nạp gửi (xem loader/Bus.java). */
    fun post(pkg: String, method: String, arg: String?, extras: Bundle?) {
        val now = clock()
        val s = state(pkg)
        when (method) {
            "started" -> { s.startedAt = now; s.lastBeat = now }
            "screen" -> s.screenAt = now
            "beat" -> s.lastBeat = now
            "crash" -> { s.crashAt = now; s.crashSummary = extras?.getString("summary") }
            "progress" -> arg?.let { j -> copy[j]?.invoke(CopyEvent.Progress(CopyProgress(extras?.getLong("done") ?: 0, extras?.getLong("total") ?: 0, extras?.getInt("files") ?: 0))) }
            "done" -> arg?.let { j -> copy[j]?.invoke(CopyEvent.Done(extras?.getInt("files") ?: 0, extras?.getInt("expected") ?: 0, extras?.getLong("bytes") ?: 0)) }
            "error" -> arg?.let { j -> copy[j]?.invoke(CopyEvent.Error(extras?.getString("error").orEmpty())) }
        }
    }
}

/** Nhận tin từ bộ nạp trong game (chỉ từ gói đã đăng ký ở [RepackRegistry]). Không xuất dữ liệu nào ra ngoài. */
class LoaderBusProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        val pkg = callingPackage
        if (!RepackRegistry.isKnown(ctx, pkg)) return null
        LoaderEvents.post(pkg!!, method, arg, extras)
        return Bundle.EMPTY
    }

    override fun query(u: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? = null
    override fun getType(u: Uri): String? = null
    override fun insert(u: Uri, v: ContentValues?): Uri? = null
    override fun delete(u: Uri, s: String?, a: Array<String>?) = 0
    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<String>?) = 0
}

/** Việc chép data đang chạy: gói game + danh sách file. */
class DataJob(val id: String, val pkg: String, val files: List<DataFile>) {
    /** Dòng "chỉ số \t kích thước \t đường dẫn" cho bộ nạp đọc. */
    fun manifest(): String = files.mapIndexed { i, f -> "$i\t${f.payload.size}\t${f.relPath}" }.joinToString("\n")
}

object DataJobs {
    private val jobs = ConcurrentHashMap<String, DataJob>()
    fun register(job: DataJob) { jobs[job.id] = job }
    fun remove(id: String) { jobs.remove(id) }
    fun get(id: String) = jobs[id]
}

/**
 * Bộ nạp trong game đọc dữ liệu từ đây: content://<gói Monika>.gamedata/<job>/manifest và /<job>/<chỉ số>.
 * Chỉ đúng gói game của job đó mới đọc được (kiểm bằng callingPackage).
 */
class GameDataProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val ctx = context ?: throw FileNotFoundException()
        val seg = uri.pathSegments
        if (seg.size != 2) throw FileNotFoundException("bad uri")
        val job = DataJobs.get(seg[0]) ?: throw FileNotFoundException("no job")
        if (callingPackage != job.pkg || !RepackRegistry.isKnown(ctx, job.pkg)) throw SecurityException("not allowed")
        val open: () -> java.io.InputStream = if (seg[1] == "manifest") {
            { job.manifest().byteInputStream(Charsets.UTF_8) }
        } else {
            val f = seg[1].toIntOrNull()?.let { job.files.getOrNull(it) } ?: throw FileNotFoundException("no file")
            ({ f.payload.open() })
        }
        return openPipeHelper(uri, "application/octet-stream", null, null) { out, _, _, _, _ ->
            ParcelFileDescriptor.AutoCloseOutputStream(out).use { o -> open().use { it.copyTo(o) } }
        }
    }

    override fun query(u: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? = null
    override fun getType(u: Uri): String? = null
    override fun insert(u: Uri, v: ContentValues?): Uri? = null
    override fun delete(u: Uri, s: String?, a: Array<String>?) = 0
    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<String>?) = 0
}
