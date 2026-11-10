package vn.aow.monika.download

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import vn.aow.monika.AppGraph
import vn.aow.monika.Prefs
import vn.aow.monika.config.isAllowedDirectUrlHost
import vn.aow.monika.config.isAllowedDownloadHost
import vn.aow.monika.library.GameStorage
import vn.aow.monika.library.GameMeta
import vn.aow.monika.library.ImportWorker
import vn.aow.monika.notify.Notifier
import vn.aow.monika.runner.Installer
import vn.aow.monika.util.SafeZipPath
import java.io.File
import java.io.IOException

/**
 * Tải bằng DownloadManager của Android: chạy nền, tự nối lại, có thanh tiến độ ở thông báo.
 * Tải xong DownloadReceiver sẽ đưa game vào thư viện (hoặc mở cài đặt nếu là app/plugin).
 */
class Downloader(
    private val context: Context,
    private val http: OkHttpClient,
    private val prefs: Prefs,
) {
    private val dm = context.getSystemService(DownloadManager::class.java)

    /**
     * @param isTool true = app/plugin cần cài ngay, không đưa vào thư viện game.
     * @param meta thông tin bài viết (tên, ảnh bìa) để game tải xong hiển thị đẹp.
     */
    suspend fun enqueue(url: String, isTool: Boolean = false, meta: GameMeta? = null): Long {
        // V196b: kiểm trước khi đưa URL cho trình quản lý tải của hệ thống (luồng này không đi qua OkHttp).
        requireAllowedDownloadUrl(url)
        val name = resolveFileName(url)
        if (!isTool) vn.aow.monika.library.Prefetch.onDownloadStart(context, meta?.labels.orEmpty(), name)
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(name)
            .setDescription("AowVN Monika")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        val sub = "_TaiVe/$name"
        if (GameStorage.isPublic(context)) {
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "${GameStorage.FOLDER}/$sub")
        } else {
            request.setDestinationInExternalFilesDir(context, GameStorage.FOLDER, sub)
        }
        return dm.enqueue(request).also { id ->
            // V204: chỉ xử lý broadcast cho ID do app này đặt.
            prefs.markDownload(id)
            if (isTool) prefs.markToolDownload(id)
            meta?.let { prefs.setDownloadMeta(id, it.toJson()) }
        }
    }

    /** Hỏi tên file thật qua Content-Disposition (link kiểu /api/file/xxx không có tên trong URL). */
    private suspend fun resolveFileName(url: String): String = withContext(Dispatchers.IO) {
        val disposition = runCatching {
            http.newCall(Request.Builder().url(url).head().build()).execute().use { it.header("Content-Disposition") }
        }.getOrNull()
        fileNameFrom(url, disposition)
    }

    companion object {
        /**
         * V196b: URL đưa cho trình quản lý tải phải là HTTPS và host thuộc danh sách đã chốt
         * (host mã/gói hoặc host tệp người dùng). Sai → ném lỗi rõ ràng, không thử tiếp.
         */
        internal fun requireAllowedDownloadUrl(url: String) {
            val parsed = url.toHttpUrlOrNull()
            val host = parsed?.host?.lowercase()
            if (parsed == null || parsed.scheme != "https" || host == null ||
                !(isAllowedDownloadHost(host) || isAllowedDirectUrlHost(host))
            ) {
                throw IOException("Chặn tải: địa chỉ không phải HTTPS hoặc host không nằm trong danh sách cho phép")
            }
        }

        /** Tên file từ URL và Content-Disposition; tách ra để test không cần mạng. */
        internal fun fileNameFrom(url: String, disposition: String?): String {
            // Hỗ trợ cả filename*=UTF-8''... (tên tiếng Việt) mà URLUtil không đọc được.
            val utf8Name = disposition?.let { Regex("""filename\*=(?:UTF-8|utf-8)''([^;]+)""").find(it)?.groupValues?.get(1) }
                // URLDecoder coi "+" là dấu cách (ngữ nghĩa form); ở đây "+" là ký tự thật nên mã hóa trước khi giải.
                ?.let { runCatching { java.net.URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") }.getOrNull() }
            val raw = utf8Name ?: URLUtil.guessFileName(url, disposition, null)
            // V204: làm sạch tên tải về (bỏ đường dẫn/ký tự điều khiển) trước khi ghi ra đĩa.
            return SafeZipPath.sanitizeSingleFileName(raw).orEmpty().ifEmpty { "tai-ve-${System.currentTimeMillis()}" }
        }
    }
}

/**
 * Chỉ xử lý broadcast tải xong của ID do chính app này đặt. `exported=true` là bắt buộc vì
 * `ACTION_DOWNLOAD_COMPLETE` do DownloadProvider (UID `android.media`) gửi; lớp chặn thật là
 * danh sách ID đã đánh dấu khi `enqueue`.
 */
internal fun shouldHandleDownload(action: String?, id: Long, ownId: Boolean): Boolean =
    action == DownloadManager.ACTION_DOWNLOAD_COMPLETE && id >= 0 && ownId

class DownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
        if (!shouldHandleDownload(intent.action, id, id >= 0 && AppGraph.prefs.isOwnDownload(id))) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(context.applicationContext, id)
            } finally {
                pending.finish()
            }
        }
    }

    private fun handle(context: Context, id: Long) {
        val prefs = AppGraph.prefs
        val dm = context.getSystemService(DownloadManager::class.java)
        val file = dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
            if (!c.moveToFirst()) return@use null
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val local = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
            if (status == DownloadManager.STATUS_SUCCESSFUL && local != null) Uri.parse(local).path?.let(::File) else null
        }
        val isTool = prefs.isToolDownload(id)
        val meta = prefs.downloadMeta(id)
        prefs.clearDownload(id)
        if (file == null || !file.exists()) return

        if (isTool) {
            Notifier.downloadDone(context, "Đã tải ${file.name}", "Bấm để cài đặt", Installer.installIntent(context, file))
            return
        }
        ImportWorker.enqueue(context, file, meta)
    }
}
