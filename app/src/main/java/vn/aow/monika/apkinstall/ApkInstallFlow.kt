package vn.aow.monika.apkinstall

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/** Màn hình đang ở bước nào (giao diện chỉ việc vẽ theo [UiState]). */
data class UiState(
    val phase: Phase = Phase.IDLE,
    val result: InspectResult? = null,
    val candidates: List<File> = emptyList(),
    val percent: Int? = null,
    val message: String? = null,
    val failure: InstallOutcome.Failure? = null,
    /** Có dữ liệu (Data) chưa chép được ở bản này. */
    val skippedData: Boolean = false,
) {
    enum class Phase { IDLE, INSPECTING, NEED_CHOICE, READY, INSTALLING, COPYING_OBB, DONE, FAILED }
}

/**
 * Điều phối cài game Android: đọc gói → cài (PackageInstaller) → chép OBB → báo kết quả.
 * Trạng thái ở [state] (cùng tiến trình, giao diện và worker nền cùng đọc/ghi).
 */
object ApkInstallFlow {
    val state = MutableStateFlow(UiState())

    /** Kết quả đọc gói giữ trong bộ nhớ để worker nền không phải đọc/giải nén lại. */
    @Volatile var inspected: InspectResult? = null

    /** Mở màn cài game cho [source] (file .apk/.apks/.xapk hoặc thư mục game đã giải nén). */
    fun start(context: Context, source: File) {
        context.startActivity(ApkInstallActivity.intent(context, source))
    }

    fun update(f: (UiState) -> UiState) = state.value.let { state.value = f(it) }

    /** Phần chạy thật (worker gọi). Tách tham số [installer]/[obbDir] để test bằng bản giả. */
    suspend fun execute(
        result: InspectResult,
        installer: ApkInstaller,
        obbDir: File = ObbInstaller.obbDir(result.packageName),
        device: DeviceInfo,
        onState: (UiState) -> Unit = { state.value = it },
    ): UiState {
        val skipped = result.needsData
        fun st(phase: UiState.Phase, pct: Int? = null, failure: InstallOutcome.Failure? = null, message: String? = null) =
            UiState(phase, result, percent = pct, failure = failure, message = message, skippedData = skipped)

        onState(st(UiState.Phase.INSTALLING, 0))
        val parts = SplitSelector.select(result.parts, device)
        when (val o = installer.install(result.packageName, parts) { onState(st(UiState.Phase.INSTALLING, it)) }) {
            is InstallOutcome.Success -> Unit
            is InstallOutcome.UserAborted -> return st(UiState.Phase.READY, message = "Bạn đã hủy cài đặt.").also(onState)
            is InstallOutcome.Failure -> return st(UiState.Phase.FAILED, failure = o).also(onState)
        }
        if (result.obbFiles.isNotEmpty()) {
            onState(st(UiState.Phase.COPYING_OBB, 0))
            when (val r = ObbInstaller.copy(result.packageName, result.obbFiles, obbDir) { onState(st(UiState.Phase.COPYING_OBB, it)) }) {
                is ObbInstaller.Result.Ok -> Unit
                is ObbInstaller.Result.Failed -> return st(UiState.Phase.FAILED, failure = InstallOutcome.Failure(InstallOutcome.Kind.OTHER, r.text, "obb")).also(onState)
            }
        }
        return st(UiState.Phase.DONE).also(onState)
    }

    /** Dọn thư mục tạm chứa APK đã giải nén. */
    fun cleanup(result: InspectResult?) { result?.workDir?.deleteRecursively() }
}

fun interface ApkInstaller {
    suspend fun install(packageName: String, parts: List<ApkPart>, onProgress: (Int) -> Unit): InstallOutcome
}
