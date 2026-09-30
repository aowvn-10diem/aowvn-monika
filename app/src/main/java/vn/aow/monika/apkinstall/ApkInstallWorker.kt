package vn.aow.monika.apkinstall

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import vn.aow.monika.apkinstall.repack.AndroidKeystoreWrap
import vn.aow.monika.apkinstall.repack.ApkRepacker
import vn.aow.monika.apkinstall.repack.RepackKeyStore
import vn.aow.monika.apkinstall.repack.RepackOptions
import vn.aow.monika.notify.Notifier
import java.io.File

/**
 * Cài game ở nền (game vài GB: chép OBB/Data mất vài phút) — có thông báo tiến độ, không bị ngắt khi tắt màn hình.
 * Kết quả ghi vào [ApkInstallFlow.state] cho màn hình đọc.
 */
class ApkInstallWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val result = ApkInstallFlow.inspected ?: return Result.failure()
        val ctx = applicationContext
        runCatching { setForeground(info(result.label ?: result.packageName, null)) }
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val installer = ApkInstaller { pkg, parts, progress -> SessionInstaller.install(ctx, pkg, parts, progress) }
        val device = DeviceInfo.current(ctx)
        val name = result.label ?: result.packageName
        var last = -1 to ""
        val onState: (UiState) -> Unit = { st ->
            ApkInstallFlow.state.value = st
            val title = when (st.phase) {
                UiState.Phase.COPYING_OBB -> "Đang chép dữ liệu game"
                UiState.Phase.PUSHING_DATA -> "Đang chép dữ liệu (Data) vào game"
                UiState.Phase.REPACKING -> "Đang chuẩn bị game"
                else -> "Đang cài game"
            }
            if ((st.percent ?: -1) to title != last) { last = (st.percent ?: -1) to title; runCatching { nm.notify(NOTIFICATION_ID, Notifier.progress(ctx, title, name, st.percent)) } }
        }

        val end = when {
            ApkInstallFlow.plan(result, device) == InstallPlan.CHECKLIST && ApkInstallFlow.chosenMethod == Method.REPACK -> {
                val signer = RepackKeyStore(File(ctx.filesDir, "repack"), AndroidKeystoreWrap).signer()
                val dex = ctx.assets.open("monika-loader.dex").use { it.readBytes() }
                val out = File(result.workDir ?: ctx.cacheDir, "repacked").apply { mkdirs() }
                ApkInstallFlow.executeRepack(
                    result, installer,
                    repacker = { part, isBase, raise ->
                        val dst = File(out, "${part.file.nameWithoutExtension}_m.apk")
                        ApkRepacker.repack(part.file, dst, RepackOptions(ctx.packageName, dex, signer, raise, injectLoader = isBase), out)
                        dst
                    },
                    pushData = { files, p -> DataPusher.push(ctx, result.packageName, files, onProgress = p) },
                    register = { RepackRegistry.add(ctx, it) },
                    device = device, checklist = InstallChecklist.from(ctx), onState = onState,
                )
            }
            ApkInstallFlow.plan(result, device) == InstallPlan.CHECKLIST -> {
                // Cách 2/3 sẽ có ở bước sau: hiện checklist để người chơi biết.
                val cl = InstallChecklist.from(ctx)
                UiState(UiState.Phase.CHOOSE_METHOD, result, message = "Cách này chưa hỗ trợ ở bản này.", checklist = cl.read(result.packageName, result.versionCode)).also(onState)
            }
            else -> ApkInstallFlow.execute(result, installer, device = device, onState = onState)
        }
        runCatching { nm.cancel(NOTIFICATION_ID) }
        // Giữ thư mục tạm nếu còn phải mở thử/chọn cách khác; dọn khi đã xong hẳn hoặc lỗi.
        if (end.phase == UiState.Phase.DONE || end.phase == UiState.Phase.FAILED) ApkInstallFlow.cleanup(result)
        val toActivity = Intent(ctx, ApkInstallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        when (end.phase) {
            UiState.Phase.DONE -> Notifier.downloadDone(ctx, "Đã cài xong: $name", "Bấm để chơi",
                (ctx.packageManager.getLaunchIntentForPackage(result.packageName) ?: toActivity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            UiState.Phase.HEALTH_READY -> Notifier.downloadDone(ctx, "Đã cài: $name", "Bấm để Monika mở thử game và kiểm tra", toActivity)
            UiState.Phase.FAILED -> Notifier.downloadDone(ctx, "Chưa cài được: $name", end.failure?.text.orEmpty(), toActivity)
            else -> Unit
        }
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = info("Đang cài game", null)

    private fun info(name: String, pct: Int?): ForegroundInfo {
        val n = Notifier.progress(applicationContext, "Đang cài game", name, pct)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ForegroundInfo(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(NOTIFICATION_ID, n)
    }

    companion object {
        private const val NOTIFICATION_ID = 4203
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<ApkInstallWorker>().build())
        }
    }
}
