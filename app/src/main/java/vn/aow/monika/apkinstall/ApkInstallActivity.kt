package vn.aow.monika.apkinstall

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.aow.monika.ui.theme.MonikaTheme
import java.io.File

/** Màn "Cài game Android": hiện dưới dạng menu popup nổi lên trên màn đang mở. Logic ở [ApkInstallFlow]. */
class ApkInstallActivity : ComponentActivity() {
    private var source: File? = null
    private var awaitingUninstall: String? = null

    /** Bộ chọn thư mục của Android, mở sẵn đúng thư mục dữ liệu của game (Cách 2). */
    private val pickFolder = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()) { uri -> onFolderPicked(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        source = intent.getStringExtra(EXTRA_SOURCE)?.let(::File)
        val phase = ApkInstallFlow.state.value.phase
        val busy = phase == UiState.Phase.INSTALLING || phase == UiState.Phase.COPYING_OBB || phase == UiState.Phase.REPACKING || phase == UiState.Phase.PUSHING_DATA
        // Mở từ thông báo (không kèm file) hoặc đang cài → chỉ hiện trạng thái hiện có, không đọc lại gói.
        source?.takeIf { !busy }?.let { inspect(it, null) }
        setContent {
            MonikaTheme {
                val st by ApkInstallFlow.state.collectAsState()
                Box(Modifier.fillMaxSize()) {
                    ApkInstallSheet(
                        st,
                        onInstall = ::install,
                        onChoose = { f -> source?.let { inspect(it, f) } },
                        onRetry = { source?.let { inspect(it, null) } },
                        onUninstallOld = { pkg -> awaitingUninstall = pkg; SessionInstaller.uninstall(this@ApkInstallActivity, pkg) },
                        installed = st.result?.let { SessionInstaller.isInstalled(this@ApkInstallActivity, it.packageName) } == true,
                        canDeleteSource = source?.let { isInAppStorage(it) } == true,
                        onDeleteSource = { deleteSource() },
                        onPlay = { pkg -> packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }; finish() },
                        onHealthStart = ::healthStart,
                        onHealthSkip = { ApkInstallFlow.inspected?.let { r ->
                            ApkInstallFlow.state.value = ApkInstallFlow.confirmWorks(r, true, InstallChecklist.from(this@ApkInstallActivity)) } },
                        onHealthAnswer = { ok -> ApkInstallFlow.inspected?.let { r ->
                            ApkInstallFlow.state.value = ApkInstallFlow.confirmWorks(r, ok, InstallChecklist.from(this@ApkInstallActivity)) } },
                        onMethod = { m -> ApkInstallFlow.chosenMethod = m; if (m == Method.SAF) safStart() else install() },
                        onOpenGame = { ApkInstallFlow.inspected?.let { r -> packageManager.getLaunchIntentForPackage(r.packageName)?.let { startActivity(it) } } },
                        onPickFolder = { ApkInstallFlow.inspected?.let { r -> pickFolder.launch(SafDataAccess.initialUri(r.packageName)) } },
                        onClose = ::finish,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Người chơi vừa gỡ bản cũ xong → tự cài lại.
        val pkg = awaitingUninstall ?: return
        if (!SessionInstaller.isInstalled(this, pkg)) {
            awaitingUninstall = null
            source?.let { s -> inspect(s, null, thenInstall = true) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val phase = ApkInstallFlow.state.value.phase
        val busy = phase == UiState.Phase.INSTALLING || phase == UiState.Phase.COPYING_OBB || phase == UiState.Phase.REPACKING || phase == UiState.Phase.PUSHING_DATA
        if (isFinishing && !busy) {
            ApkInstallFlow.cleanup(ApkInstallFlow.inspected)
            ApkInstallFlow.inspected = null
            ApkInstallFlow.state.value = UiState()
        }
    }

    private fun inspect(src: File, choose: File?, thenInstall: Boolean = false) {
        ApkInstallFlow.state.value = UiState(UiState.Phase.INSPECTING)
        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) {
                val root = File(cacheDir, "apkinstall").apply { mkdirs() }
                // Dọn thư mục tạm cũ (lần cài bị ngắt giữa chừng).
                root.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 3600_000L }?.forEach { it.deleteRecursively() }
                ApkInspector(DeviceInfo.current(this@ApkInstallActivity), root) { apk -> labelOf(apk) }.inspect(src, choose)
            }
            ApkInstallFlow.inspected = r
            val f = r.fatal
            ApkInstallFlow.state.value = if (f is Problem.NeedChoice) UiState(UiState.Phase.NEED_CHOICE, candidates = f.candidates)
            else UiState(UiState.Phase.READY, r, checklist = if (r.packageName.isBlank()) emptyList() else InstallChecklist.from(this@ApkInstallActivity).read(r.packageName, r.versionCode))
            if (thenInstall && r.fatal == null) install()
        }
    }

    private fun labelOf(apk: File): String? = runCatching {
        val info = packageManager.getPackageArchiveInfo(apk.path, 0)?.applicationInfo ?: return null
        info.sourceDir = apk.path; info.publicSourceDir = apk.path
        info.loadLabel(packageManager).toString()
    }.getOrNull()

    /** File nằm trong kho game của Monika (được phép xóa để giải phóng dung lượng). */
    private fun isInAppStorage(f: File): Boolean = runCatching {
        val root = vn.aow.monika.library.GameStorage.root(this).canonicalPath + File.separator
        f.canonicalPath.startsWith(root)
    }.getOrDefault(false)

    /** Xóa file cài (giữ lại .monika.json để Thư viện hiện "Đã dọn · Tải lại"). Game đã cài vẫn chơi bình thường. */
    private fun deleteSource() {
        val s = source ?: return
        if (!isInAppStorage(s)) return
        val freed = if (s.isDirectory) s.walkTopDown().filter { it.isFile }.sumOf { it.length() } else s.length()
        if (s.isDirectory) s.listFiles()?.filter { it.name != ".monika.json" }?.forEach { it.deleteRecursively() } else s.delete()
        Toast.makeText(this, "Đã xóa file cài, giải phóng ${size(freed)}.", Toast.LENGTH_LONG).show()
        finish()
    }

    /** Cách 2, bước 1: cài APK gốc (bỏ qua nếu game gốc đã cài sẵn) rồi chờ người chơi mở game 1 lần + chọn thư mục. */
    private fun safStart() {
        val r = ApkInstallFlow.inspected ?: return
        val skip = SessionInstaller.isInstalled(this, r.packageName) && !RepackRegistry.isKnown(this, r.packageName)
        if (!skip && !checkInstallPermission()) return
        ApkInstallFlow.state.value = UiState(UiState.Phase.INSTALLING, r, percent = 0)
        ApkInstallWorker.enqueueSafInstall(this, skip)
    }

    /** Cách 2, bước 2: nhận thư mục người chơi vừa cấp quyền. */
    private fun onFolderPicked(uri: Uri?) {
        val r = ApkInstallFlow.inspected ?: return
        val checklist = InstallChecklist.from(this)
        if (uri == null) {
            Toast.makeText(this, "Chưa chọn thư mục. Nếu nút \"Dùng thư mục này\" bị mờ thì máy chặn Cách 2.", Toast.LENGTH_LONG).show()
            return
        }
        if (!SafDataAccess.isExpectedTree(uri, r.packageName)) {
            Toast.makeText(this, "Hãy chọn đúng thư mục Android/data/${r.packageName}.", Toast.LENGTH_LONG).show()
            return
        }
        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        SafDataAccess.saveTree(this, r.packageName, uri)
        if (SafDataAccess.root(this, uri) == null) {
            checklist.set(r.packageName, r.versionCode, Method.SAF, State.FAILED, "Máy không cho ghi vào thư mục này")
            ApkInstallFlow.state.value = UiState(UiState.Phase.CHOOSE_METHOD, r, message = "Máy không cho ghi vào thư mục này (đã bị Android chặn). Hãy thử Cách 3.", checklist = checklist.read(r.packageName, r.versionCode))
            return
        }
        ApkInstallFlow.state.value = UiState(UiState.Phase.PUSHING_DATA, r, percent = 0)
        ApkInstallWorker.enqueueSafCopy(this)
    }

    private fun checkInstallPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            Toast.makeText(this, "Hãy bật 'Cho phép cài ứng dụng' cho AowVN Monika rồi bấm lại.", Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }

    /** Mở thử game đã chỉnh, theo dõi tin bộ nạp gửi về, rồi ghi kết quả vào checklist. */
    private fun healthStart() {
        val r = ApkInstallFlow.inspected ?: return
        val pkg = r.packageName
        val launch = packageManager.getLaunchIntentForPackage(pkg) ?: run {
            Toast.makeText(this, "Không mở được game vừa cài.", Toast.LENGTH_LONG).show(); return
        }
        ApkInstallFlow.state.value = UiState(UiState.Phase.HEALTH_RUNNING, r)
        val checklist = InstallChecklist.from(this)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { DataPusher.watch(this@ApkInstallActivity, pkg, 45) }
            val h = HealthCheck.run(pkg, launch = { startActivity(launch) })
            val next = ApkInstallFlow.applyHealth(r, h, checklist)
            ApkInstallFlow.state.value = next
            // Game chạy ổn: không kéo người chơi ra khỏi game; nhắc bằng thông báo để quay lại xác nhận.
            vn.aow.monika.notify.Notifier.downloadDone(
                this@ApkInstallActivity,
                if (h is Health.LikelyOk) "Game chạy ổn?" else "Game chưa chạy được",
                if (h is Health.LikelyOk) "Bấm để xác nhận game đã vào được màn hình chơi." else "Bấm để thử cách khác.",
                Intent(this@ApkInstallActivity, ApkInstallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
    }

    private fun install() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            Toast.makeText(this, "Hãy bật 'Cho phép cài ứng dụng' cho AowVN Monika rồi bấm Cài đặt lại.", Toast.LENGTH_LONG).show()
            return
        }
        if (ApkInstallFlow.inspected == null) return
        ApkInstallFlow.state.value = UiState(UiState.Phase.INSTALLING, ApkInstallFlow.inspected, percent = 0)
        ApkInstallWorker.enqueue(this)
    }

    companion object {
        private const val EXTRA_SOURCE = "source"
        fun intent(context: Context, source: File) = Intent(context, ApkInstallActivity::class.java)
            .putExtra(EXTRA_SOURCE, source.absolutePath).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
