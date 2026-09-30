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
            inputData.getString(KEY_TASK) == TASK_ADB -> runAdb(result, device, onState)
            ApkInstallFlow.plan(result, device) == InstallPlan.CHECKLIST && ApkInstallFlow.chosenMethod == Method.SAF -> {
                val cl = InstallChecklist.from(ctx)
                if (inputData.getString(KEY_TASK) == TASK_SAF_COPY) {
                    val tree = SafDataAccess.savedTree(ctx, result.packageName)
                    val root = tree?.let { SafDataAccess.root(ctx, it) }
                    if (root == null) UiState(UiState.Phase.CHOOSE_METHOD, result, message = "Chưa có quyền ghi vào thư mục dữ liệu của game.", checklist = cl.read(result.packageName, result.versionCode)).also(onState)
                    else ApkInstallFlow.executeSafCopy(result, root, cl, onState)
                } else ApkInstallFlow.executeSafInstall(result, installer, device, cl, alreadyInstalled = SessionInstaller.isInstalled(ctx, result.packageName) && inputData.getBoolean(KEY_SKIP_INSTALL, false), onState = onState)
            }
            ApkInstallFlow.plan(result, device) == InstallPlan.CHECKLIST -> {
                val cl = InstallChecklist.from(ctx)
                UiState(UiState.Phase.CHOOSE_METHOD, result, message = "Hãy chọn một cách để cài.", checklist = cl.read(result.packageName, result.versionCode)).also(onState)
            }
            else -> ApkInstallFlow.execute(result, installer, device = device, onState = onState)
        }
        runCatching { nm.cancel(NOTIFICATION_ID) }
        // Giữ thư mục tạm nếu còn phải mở thử/chọn cách khác; dọn khi đã xong hẳn hoặc lỗi.
        if (end.phase == UiState.Phase.DONE || end.phase == UiState.Phase.FAILED) ApkInstallFlow.cleanup(result)
        if (end.phase == UiState.Phase.SAF_PREPARE) Notifier.downloadDone(ctx, "Đã cài: $name", "Mở game 1 lần rồi quay lại Monika để chọn thư mục dữ liệu.", Intent(ctx, ApkInstallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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

    /** Cách 3: kết nối gỡ lỗi không dây vào chính máy này rồi cài. Chưa ghép đôi/chưa bật → về màn hướng dẫn. */
    private suspend fun runAdb(result: InspectResult, device: DeviceInfo, onState: (UiState) -> Unit): UiState {
        val ctx = applicationContext
        val cl = InstallChecklist.from(ctx)
        val initial = vn.aow.monika.apkinstall.adb.AdbInitialStore.captureOnce(ctx)
        val adb = vn.aow.monika.apkinstall.adb.LocalAdb(ctx)
        try {
            fun guide(msg: String) = UiState(UiState.Phase.ADB_GUIDE, result, message = msg, checklist = cl.read(result.packageName, result.versionCode))
            when (val c = adb.connect()) {
                is vn.aow.monika.apkinstall.adb.LocalAdb.Connect.NeedPairing -> return guide("Chưa ghép đôi. Bấm \"Bắt đầu ghép đôi\" rồi làm theo thông báo.").also(onState)
                is vn.aow.monika.apkinstall.adb.LocalAdb.Connect.Failed -> return guide(c.text).also(onState)
                vn.aow.monika.apkinstall.adb.LocalAdb.Connect.Ok -> Unit
            }
            val end = ApkInstallFlow.executeAdb(result, adb, device, cl, initial, readNow = { vn.aow.monika.apkinstall.adb.DevState.read(ctx) }, onState = onState)
            vn.aow.monika.apkinstall.adb.AdbInitialStore.clear(ctx)
            return end
        } finally {
            runCatching { adb.close() }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = info("Đang cài game", null)

    private fun info(name: String, pct: Int?): ForegroundInfo {
        val n = Notifier.progress(applicationContext, "Đang cài game", name, pct)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ForegroundInfo(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(NOTIFICATION_ID, n)
    }

    companion object {
        private const val NOTIFICATION_ID = 4203
        private const val KEY_TASK = "task"
        private const val KEY_SKIP_INSTALL = "skip_install"
        private const val TASK_SAF_COPY = "saf_copy"
        private const val TASK_ADB = "adb_run"

        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<ApkInstallWorker>().build())
        }

        /** Cách 2, bước 2: chép Data vào thư mục đã được cấp quyền. */
        fun enqueueSafCopy(context: Context) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<ApkInstallWorker>()
                .setInputData(androidx.work.workDataOf(KEY_TASK to TASK_SAF_COPY)).build())
        }

        /** Cách 3: chạy việc cài qua gỡ lỗi không dây (đã ghép đôi). */
        fun enqueueAdb(context: Context) {
            ApkInstallFlow.state.value = UiState(UiState.Phase.INSTALLING, ApkInstallFlow.inspected, percent = 0)
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<ApkInstallWorker>()
                .setInputData(androidx.work.workDataOf(KEY_TASK to TASK_ADB)).build())
        }

        /** Cách 2, bước 1 khi game đã cài sẵn bản gốc (chỉ chuyển sang chờ chọn thư mục). */
        fun enqueueSafInstall(context: Context, skipInstall: Boolean) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<ApkInstallWorker>()
                .setInputData(androidx.work.workDataOf(KEY_SKIP_INSTALL to skipInstall)).build())
        }
    }
}
