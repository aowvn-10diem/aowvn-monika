package vn.aow.monika.azahar

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.citra.citra_emu.NativeLibrary
import org.citra.citra_emu.utils.CiaInstallWorker
import vn.aow.monika.AppGraph
import vn.aow.monika.diag.Diagnostics
import vn.aow.monika.runner.EmuTier
import vn.aow.monika.ui.theme.MonikaMenuSheet
import vn.aow.monika.ui.theme.MonikaTheme
import vn.aow.monika.ui.theme.SheetChip
import vn.aow.monika.ui.theme.SheetColors
import vn.aow.monika.ui.theme.SheetRow
import java.io.File

/**
 * Cài file .cia (game, bản cập nhật, DLC) vào "bộ nhớ máy 3DS" do Monika quản lý.
 * Chạy ở tiến trình :game như màn chơi (engine native nằm ở đó); giao diện là menu popup dưới đáy của Monika.
 */
class AzaharInstallActivity : ComponentActivity() {
    private val lines = mutableStateListOf<Pair<String, String>>() // tên file → trạng thái
    private var status by mutableStateOf("Đang chuẩn bị engine 3DS…")
    private var done by mutableStateOf(false)
    private var pct by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val files = intent.getStringArrayExtra(EXTRA_FILES).orEmpty().map(::File)
        if (files.isEmpty()) return finish()
        Diagnostics.begin(this, "azahar-install", "azahar", AppGraph.azahar.info(), "${files.size} cia", "3DS")
        AzaharBridge.onInstallProgress = { max, p -> pct = if (max > 0) (p * 100L / max).toInt() else 0 }
        setContent {
            MonikaTheme {
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF141315))) {
                    MonikaMenuSheet(
                        true, { if (done) finish() }, actions = emptyList(),
                        title = "Cài file .cia (3DS)", subtitle = status,
                        header = {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                lines.forEach { (n, s) -> SheetRow(n, subtitle = s) }
                                if (!done) Text("Đang cài… $pct%", color = SheetColors.textSecondary)
                                else SheetChip("Xong", true) { finish() }
                                // V205: .cia là bộ cài đặt (game/cập nhật/DLC), không phải ROM chạy trực tiếp.
                                Text("File .cia là bộ cài đặt. Cài xong, mở ROM .3ds của game để chơi.", color = SheetColors.textSecondary)
                            }
                        },
                    )
                }
            }
        }
        lifecycleScope.launch {
            // diag: bỏ qua lỗi nạp engine tại đây — nhánh dưới dùng `ok` để báo Diagnostics và hiện lỗi cho người dùng.
            val ok = runCatching {
                AppGraph.azahar.ensure { status = it }
                withContext(Dispatchers.Default) { AppGraph.azahar.load() }
                Diagnostics.stage(this@AzaharInstallActivity, "native-loaded")
                val dir = AzaharConfig.userDir(this@AzaharInstallActivity)
                AzaharBridge.userDir = dir.absolutePath + "/"
                NativeLibrary.setUserDirectory(dir.absolutePath)
                NativeLibrary.createLogFile()
                NativeLibrary.createConfigFile()
                AzaharConfig.write(this@AzaharInstallActivity, EmuTier.detect(this@AzaharInstallActivity, AppGraph.prefs.emuPerf))
                NativeLibrary.reloadSettings()
            }
            if (ok.isFailure) {
                status = "Engine 3DS chưa chạy được trên máy này: ${ok.exceptionOrNull()?.message ?: ""}"
                // Người chơi vừa bấm cài .cia mà engine không nạp được → để lại vệt lỗi (không nuốt).
                Diagnostics.recordHandled(this@AzaharInstallActivity, "app:azahar-cia", "Không cài được .cia, engine 3DS không nạp", ok.exceptionOrNull())
                done = true
                return@launch
            }
            status = "Đang cài ${files.size} file…"
            val worker = CiaInstallWorker()
            val staging = AzaharCia.stagingDir(cacheDir)
            for (f in files) {
                lines.add(f.name to "Đang cài…")
                val idx = lines.lastIndex
                pct = 0
                Diagnostics.stage(this@AzaharInstallActivity, "install ${f.name.take(40)}")
                val res = withContext(Dispatchers.IO) {
                    runCatching { worker.installCIA("!" + f.absolutePath) }
                        .onFailure { Diagnostics.recordHandled(this@AzaharInstallActivity, "app:azahar-cia", "Không cài được ${f.name}", it) }
                        .getOrNull()
                }
                lines[idx] = f.name to when (res) {
                    NativeLibrary.InstallStatus.Success -> "Đã cài xong"
                    NativeLibrary.InstallStatus.ErrorEncrypted -> "Lỗi: file còn mã hóa (cần khóa)"
                    NativeLibrary.InstallStatus.ErrorInvalid -> "Lỗi: file .cia không hợp lệ"
                    NativeLibrary.InstallStatus.ErrorAborted -> "Lỗi: bị hủy giữa chừng"
                    NativeLibrary.InstallStatus.ErrorFailedToOpenFile, NativeLibrary.InstallStatus.ErrorFileNotFound -> "Lỗi: không mở được file"
                    else -> "Lỗi không rõ"
                }
                // Chỉ xoá bản sao Monika tự chép vào bộ đệm; `.cia` gốc người chơi mở từ thư viện phải còn nguyên.
                if (AzaharCia.isStagedCopy(staging, f) && !f.delete()) {
                    Diagnostics.crumb(this@AzaharInstallActivity, "azahar-cia", "Không xoá được bản sao .cia trong bộ đệm")
                }
            }
            status = "Xong ${lines.count { it.second.startsWith("Đã") }}/${files.size} file"
            done = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AzaharBridge.onInstallProgress = { _, _ -> }
        if (isFinishing) {
            Diagnostics.end(this)
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    companion object {
        private const val EXTRA_FILES = "files"

        fun launch(activity: Activity, paths: List<String>) {
            activity.startActivity(Intent(activity, AzaharInstallActivity::class.java).putExtra(EXTRA_FILES, paths.toTypedArray()))
        }

        /** Chép các .cia người chơi chọn vào bộ nhớ đệm (gọi ở luồng nền, file có thể vài trăm MB). */
        fun stage(activity: Activity, uris: List<android.net.Uri>): List<String> {
            val dir = AzaharCia.stagingDir(activity.cacheDir).apply { mkdirs() }
            return uris.mapIndexed { i, u ->
                val name = activity.contentResolver.query(u, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "game$i.cia"
                File(dir, AzaharCia.stagedName(i, name)).also { f ->
                    activity.contentResolver.openInputStream(u)?.use { ins -> f.outputStream().use { ins.copyTo(it) } }
                }.absolutePath
            }
        }
    }
}
