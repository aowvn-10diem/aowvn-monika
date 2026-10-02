package vn.aow.monika.download

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import vn.aow.monika.Prefs
import vn.aow.monika.library.GameMeta
import vn.aow.monika.library.GameStorage
import vn.aow.monika.library.ImportWorker
import vn.aow.monika.notify.Notifier
import vn.aow.monika.runner.Installer
import vn.aow.monika.ui.MainActivity
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

enum class DlState { RUNNING, READY, SAVING, SAVED, FAILED, CANCELED }

/** Nơi lưu file đã tải. */
sealed interface SaveDest {
    /** Thư viện game của Monika (thư mục _TaiVe → tự đưa vào Thư viện, giải nén nếu cần). */
    data object Library : SaveDest
    /** Thư mục Tải xuống chung của máy. */
    data object Downloads : SaveDest
    /** Thư mục người dùng tự chọn (SAF). */
    data class Folder(val treeUri: String, val label: String) : SaveDest
}

/** 1 lượt tải từ trình duyệt nhúng. File ĐÃ được tải về ngay lúc bấm; đặt tên / chọn nơi lưu chỉ quyết định chuyển nó đi đâu. */
class DlJob(val id: Int, val url: String, val userAgent: String?, val referer: String?, val mime: String?, val meta: GameMeta?) {
    var name by mutableStateOf("")
    var total by mutableLongStateOf(-1L)
    var done by mutableLongStateOf(0L)
    var speed by mutableLongStateOf(0L)
    var state by mutableStateOf(DlState.RUNNING)
    var error by mutableStateOf<String?>(null)
    var confirmed by mutableStateOf(false)
    @Volatile var dest: SaveDest? = null
    @Volatile var call: Call? = null
    @Volatile var savedName: String = ""
    val finishing = java.util.concurrent.atomic.AtomicBoolean(false)
    lateinit var part: File

    val progress: Float get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else -1f
    val active get() = state == DlState.RUNNING || state == DlState.SAVING || (state == DlState.READY && confirmed)
}

/**
 * Trình quản lý tải của Monika cho trình duyệt nhúng.
 * Luồng: link tải → BẮT ĐẦU TẢI NGAY vào thư mục tạm cùng ổ với thư viện game → hiện menu popup (tên + nơi lưu) →
 * người dùng đồng ý thì chuyển file sang đúng chỗ (cùng ổ = đổi tên tức thì, không chép) → thông báo hoàn tất.
 * Tải nền có dịch vụ nền + thông báo tiến độ, tiến độ hiện ở góc trình duyệt.
 */
class BrowserDownloads(private val context: Context, private val http: OkHttpClient, private val prefs: Prefs) {
    val jobs = mutableStateListOf<DlJob>()
    /** Lượt tải cần hỏi tên / nơi lưu (hiện menu popup). */
    var sheetFor by mutableStateOf<DlJob?>(null)
    /** Thông báo ngắn "đã tải xong" hiện ở góc. */
    var justSaved by mutableStateOf<String?>(null)
    /** Thư mục vừa chọn từ trình chọn thư mục hệ thống, chờ menu popup nhận. */
    var pickedFolder by mutableStateOf<SaveDest.Folder?>(null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())
    private val ids = AtomicInteger(1)

    private fun staging(): File = runCatching { File(GameStorage.downloads(context), ".tmp").apply { mkdirs() } }
        .getOrNull()?.takeIf { it.isDirectory && it.canWrite() } ?: File(context.cacheDir, "dl").apply { mkdirs() }

    /** Bắt đầu tải ngay. Gọi từ luồng bất kỳ. */
    fun start(url: String, userAgent: String?, contentDisposition: String?, mime: String?, referer: String?, meta: GameMeta? = null): DlJob {
        val job = DlJob(ids.getAndIncrement(), url, userAgent, referer, mime, meta)
        job.name = suggestName(url, contentDisposition, mime)
        job.part = File(staging(), "${System.currentTimeMillis()}-${job.id}.part")
        main.post { jobs.add(job); sheetFor = job; ensureService() }
        scope.launch { vn.aow.monika.library.Prefetch.onDownloadStart(context, meta?.labels.orEmpty(), job.name) }
        scope.launch { download(job) }
        return job
    }

    private fun download(job: DlJob) {
        try {
            val b = Request.Builder().url(job.url)
            job.userAgent?.let { b.header("User-Agent", it) }
            job.referer?.let { b.header("Referer", it) }
            runCatching { CookieManager.getInstance().getCookie(job.url) }.getOrNull()?.takeIf { it.isNotBlank() }?.let { b.header("Cookie", it) }
            val call = http.newBuilder().readTimeout(0, java.util.concurrent.TimeUnit.SECONDS).callTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
                .build().newCall(b.build())
            job.call = call
            call.execute().use { r ->
                if (!r.isSuccessful) throw IOException("Máy chủ trả lỗi ${r.code}")
                // Kiểm "đã xác nhận" LẠI trên luồng chính lúc áp tên: nếu người dùng đặt tên đúng lúc tiêu đề máy chủ về (race), tên người dùng đặt không được bị đè.
                r.header("Content-Disposition")?.let { cd -> main.post { if (!job.confirmed) job.name = suggestName(job.url, cd, job.mime) } }
                val len = r.body!!.contentLength()
                main.post { job.total = len }
                var last = System.nanoTime(); var lastBytes = 0L; var got = 0L
                job.part.parentFile?.mkdirs()
                r.body!!.byteStream().use { ins ->
                    job.part.outputStream().buffered(1 shl 16).use { out ->
                        val buf = ByteArray(1 shl 16)
                        while (true) {
                            val n = ins.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n); got += n
                            val now = System.nanoTime()
                            if (now - last > 250_000_000L) {
                                val sp = (got - lastBytes) * 1_000_000_000L / (now - last)
                                val g = got
                                main.post { job.done = g; job.speed = sp }
                                last = now; lastBytes = got
                            }
                        }
                    }
                }
                main.post { job.done = got; if (job.total < 0) job.total = got; job.speed = 0 }
            }
            if (job.state == DlState.CANCELED) { runCatching { job.part.delete() }; return }
            // Hủy có thể xen vào giữa lúc kiểm tra ở trên và lúc chạy trên luồng chính → không được ghi đè trạng thái "đã hủy".
            // Đặt READY và quyết định chuyển file CÙNG MỘT chỗ trên luồng chính: nếu người dùng xác nhận đúng lúc tải vừa xong
            // (trước khi READY được gắn) thì confirm() không thấy READY → file kẹt ở thư mục tạm. Giờ cả hai nhánh đều gọi startFinish (chỉ chạy 1 lần).
            main.post { if (job.state != DlState.CANCELED) { job.state = DlState.READY; if (job.confirmed) startFinish(job) } }
        } catch (e: Exception) {
            runCatching { job.part.delete() }
            if (job.state == DlState.CANCELED) return
            main.post { job.error = e.message ?: "Lỗi mạng"; job.state = DlState.FAILED }
            Notifier.downloadDone(context, "Tải thất bại", "${job.name}: ${e.message ?: "lỗi mạng"}", Intent(context, MainActivity::class.java))
        }
    }

    /** Người dùng đã đặt tên + chọn nơi lưu: nếu file đã tải xong thì chuyển đi ngay, chưa xong thì tự chuyển khi tải xong. */
    fun confirm(job: DlJob, name: String, dest: SaveDest) {
        job.name = sanitize(name).ifBlank { job.name }
        job.dest = dest
        job.confirmed = true
        when (dest) {
            SaveDest.Library -> prefs.dlDest = "lib"
            SaveDest.Downloads -> prefs.dlDest = "dl"
            is SaveDest.Folder -> { prefs.dlDest = "folder"; prefs.dlFolder = dest.treeUri; prefs.dlFolderLabel = dest.label }
        }
        if (sheetFor === job) sheetFor = null
        if (job.state == DlState.READY) startFinish(job)
    }

    private fun startFinish(job: DlJob) {
        if (job.finishing.compareAndSet(false, true)) scope.launch { finish(job) }
    }

    /** Lượt tải bị bỏ dở chưa chọn nơi lưu (đóng trình duyệt) → lưu theo lựa chọn lần trước, không để mất. */
    fun autoConfirmPending() {
        jobs.filter { !it.confirmed && (it.state == DlState.RUNNING || it.state == DlState.READY) }.forEach { confirm(it, it.name, defaultDest()) }
    }

    fun defaultDest(): SaveDest = when (prefs.dlDest) {
        "dl" -> SaveDest.Downloads
        "folder" -> prefs.dlFolder?.let { SaveDest.Folder(it, prefs.dlFolderLabel ?: "Thư mục đã chọn") } ?: SaveDest.Library
        else -> SaveDest.Library
    }

    fun cancel(job: DlJob) {
        job.state = DlState.CANCELED
        runCatching { job.call?.cancel() }
        scope.launch { runCatching { job.part.delete() } }
        // Đặt sau lệnh thêm vào danh sách (đã post từ start) để không sót lượt tải đã hủy.
        main.post { if (sheetFor === job) sheetFor = null; jobs.remove(job) }
    }

    fun dismiss(job: DlJob) { if (job.state == DlState.SAVED || job.state == DlState.FAILED) jobs.remove(job) }

    // ---- Chuyển file sang đúng nơi ----

    private fun finish(job: DlJob) {
        main.post { job.state = DlState.SAVING }
        val name = job.name
        try {
            val dest = job.dest ?: SaveDest.Library
            var openIntent: Intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            var place = ""
            when (dest) {
                SaveDest.Library -> {
                    val target = unique(File(GameStorage.downloads(context), name))
                    if (!job.part.renameTo(target)) { job.part.inputStream().use { i -> target.outputStream().use { i.copyTo(it) } }; job.part.delete() }
                    job.savedName = target.name
                    place = "Thư viện game"
                    if (target.extension.lowercase() in setOf("apk", "apks", "xapk", "apkm")) openIntent = vn.aow.monika.apkinstall.ApkInstallActivity.intent(context, target)
                    else ImportWorker.enqueue(context, target, job.meta?.toJson())
                }
                SaveDest.Downloads -> {
                    val uri = saveToMediaStore(job, name)
                    place = "Tải xuống"
                    openIntent = viewIntent(uri, mimeOf(name))
                }
                is SaveDest.Folder -> {
                    val tree = DocumentFile.fromTreeUri(context, Uri.parse(dest.treeUri)) ?: throw IOException("Không mở được thư mục đã chọn")
                    val doc = tree.createFile(mimeOf(name), name) ?: throw IOException("Không tạo được file trong thư mục đã chọn")
                    context.contentResolver.openOutputStream(doc.uri)!!.use { o -> job.part.inputStream().use { it.copyTo(o) } }
                    job.part.delete()
                    job.savedName = doc.name ?: name
                    place = dest.label
                    openIntent = viewIntent(doc.uri, mimeOf(name))
                }
            }
            main.post {
                job.state = DlState.SAVED
                justSaved = "Đã tải xong · ${job.savedName.ifBlank { name }}"
                jobs.remove(job)
            }
            Notifier.downloadDone(context, "Đã tải xong", "${job.savedName.ifBlank { name }} · lưu ở $place", openIntent)
        } catch (e: Exception) {
            main.post { job.error = e.message ?: "Không lưu được"; job.state = DlState.FAILED }
            Notifier.downloadDone(context, "Không lưu được file", "$name: ${e.message}", Intent(context, MainActivity::class.java))
        }
    }

    private fun saveToMediaStore(job: DlJob, name: String): Uri {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mimeOf(name))
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IOException("Không ghi được vào Tải xuống")
            context.contentResolver.openOutputStream(uri)!!.use { o -> job.part.inputStream().use { it.copyTo(o) } }
            context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            job.part.delete()
            job.savedName = name
            return uri
        }
        val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        val target = unique(File(dir.apply { mkdirs() }, name))
        if (!job.part.renameTo(target)) { job.part.inputStream().use { i -> target.outputStream().use { i.copyTo(it) } }; job.part.delete() }
        job.savedName = target.name
        return androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", target)
    }

    private fun viewIntent(uri: Uri, mime: String) =
        Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)

    // ---- Dịch vụ nền: giữ tiến trình sống + thông báo tiến độ khi ra khỏi trình duyệt ----

    private fun ensureService() {
        runCatching { ContextCompat.startForegroundService(context, Intent(context, BrowserDownloadService::class.java)) }
    }

    fun activeJobs(): List<DlJob> = jobs.filter { it.active }

    companion object {
        fun sanitize(n: String) = n.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().trim('.').take(180)

        fun suggestName(url: String, disposition: String?, mime: String?): String {
            val utf8 = disposition?.let { Regex("""filename\*=(?:UTF-8|utf-8)''([^;]+)""").find(it)?.groupValues?.get(1) }
                ?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrNull() }
            return sanitize(utf8 ?: URLUtil.guessFileName(url, disposition, mime)).ifBlank { "tai-ve" }
        }

        fun mimeOf(name: String): String =
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"

        fun unique(f: File): File {
            if (!f.exists()) return f
            val base = f.nameWithoutExtension; val ext = f.extension.let { if (it.isEmpty()) "" else ".$it" }
            var i = 1
            while (true) { val c = File(f.parentFile, "$base ($i)$ext"); if (!c.exists()) return c; i++ }
        }
    }
}
