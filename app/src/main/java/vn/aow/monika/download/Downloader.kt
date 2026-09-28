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
import okhttp3.OkHttpClient
import okhttp3.Request
import vn.aow.monika.AppGraph
import vn.aow.monika.Prefs
import vn.aow.monika.library.GameStorage
import vn.aow.monika.library.ImportWorker
import vn.aow.monika.notify.Notifier
import vn.aow.monika.runner.Installer
import java.io.File

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

    /** @param isTool true = app/plugin cần cài ngay, không đưa vào thư viện game. */
    suspend fun enqueue(url: String, isTool: Boolean = false): Long {
        val name = resolveFileName(url)
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
        return dm.enqueue(request).also { if (isTool) prefs.markToolDownload(it) }
    }

    /** Hỏi tên file thật qua Content-Disposition (link kiểu /api/file/xxx không có tên trong URL). */
    private suspend fun resolveFileName(url: String): String = withContext(Dispatchers.IO) {
        val disposition = runCatching {
            http.newCall(Request.Builder().url(url).head().build()).execute().use { it.header("Content-Disposition") }
        }.getOrNull()
        URLUtil.guessFileName(url, disposition, null).replace(Regex("""[\\/:*?"<>|]"""), "_")
    }
}

class DownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
        if (id < 0) return
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
        prefs.clearDownload(id)
        if (file == null || !file.exists()) return

        if (isTool) {
            Notifier.downloadDone(context, "Đã tải ${file.name}", "Bấm để cài đặt", Installer.installIntent(context, file))
            return
        }
        ImportWorker.enqueue(context, file)
    }
}
