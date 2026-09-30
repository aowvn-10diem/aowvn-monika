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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        source = intent.getStringExtra(EXTRA_SOURCE)?.let(::File)
        val phase = ApkInstallFlow.state.value.phase
        val busy = phase == UiState.Phase.INSTALLING || phase == UiState.Phase.COPYING_OBB
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
                        onPlay = { pkg -> packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }; finish() },
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
        if (isFinishing && phase != UiState.Phase.INSTALLING && phase != UiState.Phase.COPYING_OBB) {
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
            else UiState(UiState.Phase.READY, r)
            if (thenInstall && r.fatal == null) install()
        }
    }

    private fun labelOf(apk: File): String? = runCatching {
        val info = packageManager.getPackageArchiveInfo(apk.path, 0)?.applicationInfo ?: return null
        info.sourceDir = apk.path; info.publicSourceDir = apk.path
        info.loadLabel(packageManager).toString()
    }.getOrNull()

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
