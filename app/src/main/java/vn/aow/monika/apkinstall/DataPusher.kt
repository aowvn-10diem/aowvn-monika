package vn.aow.monika.apkinstall

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * Nhờ bộ nạp trong game (đã chèn ở Cách 1) tự chép dữ liệu vào `Android/data/<gói>/`.
 * Monika kết nối (bind) tới dịch vụ trong game, ra lệnh, rồi nghe tiến độ qua [LoaderEvents].
 */
object DataPusher {
    const val SERVICE = "vn.aow.monika.loader.MonikaLoaderService"
    private const val CMD_COPY = 1
    private const val CMD_WATCH = 2

    sealed class Result {
        data class Ok(val files: Int, val bytes: Long) : Result()
        data class Failed(val text: String) : Result()
    }

    /** [silenceMs]: quá lâu không có tin từ game thì coi là treo. */
    suspend fun push(context: Context, pkg: String, files: List<DataFile>, silenceMs: Long = 60_000, onProgress: (Int) -> Unit = {}): Result = withContext(Dispatchers.IO) {
        if (files.isEmpty()) return@withContext Result.Ok(0, 0)
        val job = DataJob(UUID.randomUUID().toString().take(12), pkg, files)
        DataJobs.register(job)
        val events = Channel<CopyEvent>(Channel.UNLIMITED)
        LoaderEvents.onCopy(job.id) { events.trySend(it) }
        var conn: ServiceConnection? = null
        try {
            val binder = bind(context, pkg, 20_000) { conn = it } ?: return@withContext Result.Failed("Không kết nối được bộ nạp trong game (game chưa cài đúng hoặc bị hệ thống chặn chạy nền).")
            val reply = Parcel.obtain(); val data = Parcel.obtain()
            val ok = try { data.writeString(job.id); binder.transact(CMD_COPY, data, reply, 0); reply.readInt() == 1 } finally { data.recycle(); reply.recycle() }
            if (!ok) return@withContext Result.Failed("Bộ nạp từ chối lệnh (không nhận ra Monika).")
            while (true) {
                val ev = withTimeoutOrNull(silenceMs) { events.receive() } ?: return@withContext Result.Failed("Game không phản hồi khi chép dữ liệu.")
                when (ev) {
                    is CopyEvent.Progress -> if (ev.p.total > 0) onProgress((ev.p.done * 100 / ev.p.total).toInt().coerceIn(0, 100))
                    is CopyEvent.Error -> return@withContext Result.Failed("Chép dữ liệu lỗi: ${ev.message}")
                    is CopyEvent.Done -> return@withContext if (ev.expected != files.size || ev.files != files.size)
                        Result.Failed("Chép thiếu dữ liệu: ${ev.files}/${files.size} file.") else Result.Ok(ev.files, ev.bytes)
                }
            }
            @Suppress("UNREACHABLE_CODE") Result.Failed("")
        } finally {
            LoaderEvents.onCopy(job.id, null); DataJobs.remove(job.id)
            conn?.let { runCatching { context.unbindService(it) } }
        }
    }

    /** Bảo bộ nạp gửi nhịp sống thêm [seconds] giây (dùng khi game đã chạy sẵn trước lúc mở thử). */
    suspend fun watch(context: Context, pkg: String, seconds: Int): Boolean = withContext(Dispatchers.IO) {
        var conn: ServiceConnection? = null
        try {
            val b = bind(context, pkg, 5_000) { conn = it } ?: return@withContext false
            val data = Parcel.obtain(); val reply = Parcel.obtain()
            try { data.writeInt(seconds); b.transact(CMD_WATCH, data, reply, 0); reply.readInt() == 1 } finally { data.recycle(); reply.recycle() }
        } catch (_: Exception) { false } finally { conn?.let { runCatching { context.unbindService(it) } } }
    }

    private suspend fun bind(context: Context, pkg: String, timeoutMs: Long, onConn: (ServiceConnection) -> Unit): IBinder? {
        val got = CompletableDeferred<IBinder?>()
        val c = object : ServiceConnection {
            override fun onServiceConnected(n: ComponentName?, s: IBinder?) { got.complete(s) }
            override fun onServiceDisconnected(n: ComponentName?) {}
            override fun onNullBinding(n: ComponentName?) { got.complete(null) }
            override fun onBindingDied(n: ComponentName?) { got.complete(null) }
        }
        onConn(c)
        val started = runCatching { context.bindService(Intent().setComponent(ComponentName(pkg, SERVICE)), c, Context.BIND_AUTO_CREATE) }.getOrDefault(false)
        if (!started) return null
        return withTimeoutOrNull(timeoutMs) { got.await() }
    }
}
