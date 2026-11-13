package vn.aow.monika.azahar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.citra.citra_emu.NativeLibrary
import org.citra.citra_emu.applets.MiiSelector
import org.citra.citra_emu.applets.SoftwareKeyboard
import org.citra.citra_emu.utils.DiskShaderCacheProgress
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Hộp thoại do engine yêu cầu; giao diện Monika ([AzaharActivity]) vẽ, kết quả trả về luồng giả lập đang chờ. */
sealed interface AzDialog {
    val title: String
    data class Message(override val title: String, val text: String, val yesNo: Boolean, val canContinue: Boolean = true, val reply: CompletableFuture<Boolean>) : AzDialog
    data class Keyboard(override val title: String, val config: SoftwareKeyboard.KeyboardConfig, val reply: CompletableFuture<SoftwareKeyboard.KeyboardData>) : AzDialog
    data class Mii(override val title: String, val config: MiiSelector.MiiSelectorConfig, val reply: CompletableFuture<MiiSelector.MiiSelectorData>) : AzDialog
}

/**
 * Cầu nối duy nhất giữa lớp JNI nhúng (org.citra.citra_emu.*, tên bắt buộc khớp thư viện native)
 * và giao diện Monika. Mọi hàm ở đây có thể bị native gọi từ luồng bất kỳ.
 */
object AzaharBridge {
    /** Hộp thoại đang hiển thị (Compose đọc). */
    var dialog by mutableStateOf<AzDialog?>(null)
    var shader by mutableStateOf<String?>(null)
    @Volatile var portrait: Boolean = true
    @Volatile var userDir: String = ""
    @Volatile var onExit: (Int) -> Unit = {}
    @Volatile var onFirstFrame: () -> Unit = {}
    @Volatile var onError: (String) -> Unit = {}
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * Hạn chờ tối đa cho một hộp thoại engine. Người chơi thường trả lời trong vài giây; đây chỉ là lưới an toàn
     * để luồng giả lập không bị treo vĩnh viễn khi không còn ai vẽ/trả lời hộp thoại.
     * `internal` để test hạ xuống vài chục mili giây.
     */
    @Volatile internal var dialogTimeoutMs: Long = 60_000

    fun isPortrait() = portrait
    fun userDirectory() = userDir
    fun exit(code: Int) { onExit(code) }

    private fun <T> ask(build: (CompletableFuture<T>) -> AzDialog, fallback: T): T {
        val f = CompletableFuture<T>()
        // Hộp thoại của riêng lần gọi này: lần gọi khác có thể đã thay `dialog` trong lúc ta chờ.
        val mine = AtomicReference<AzDialog?>(null)
        main.post {
            val d = build(f)
            mine.set(d)
            // Đã hết hạn chờ trước khi kịp vẽ → không hiện hộp thoại mồ côi (không còn ai đợi câu trả lời).
            if (!f.isDone) dialog = d
        }
        val value = try {
            // Native chờ hàm này ở luồng giả lập. Không có hạn chờ thì lúc màn hình không còn ai vẽ hộp thoại
            // (activity đang đóng, người chơi đã rời màn chơi) luồng đó treo vĩnh viễn → game đứng, phải tắt app.
            f.get(dialogTimeoutMs, TimeUnit.MILLISECONDS)
        }
        // diag: bỏ qua ngắt luồng khi rời màn chơi, đã có câu trả lời dự phòng
        catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            fallback
        }
        // diag: bỏ qua hết hạn chờ hộp thoại, đã có câu trả lời dự phòng
        catch (e: Exception) {
            fallback
        } finally {
            // Bỏ chờ (hết hạn/ngắt) thì đóng luôn future: việc vẽ chạy sau đó thấy `isDone` và không hiện
            // hộp thoại mồ côi (không còn ai đợi câu trả lời). Future đã có đáp án thì `cancel` không đổi gì.
            f.cancel(false)
            // Chỉ dọn hộp thoại của mình; hộp thoại của lần gọi khác đang chờ thì để nguyên.
            main.post { if (dialog != null && dialog === mine.get()) dialog = null }
        }
        return value
    }

    fun alert(title: String, message: String, yesNo: Boolean): Boolean =
        ask({ AzDialog.Message(title, message, yesNo, reply = it) }, false)

    /** true = tiếp tục chạy, false = dừng game. */
    fun onCoreError(error: NativeLibrary.CoreError?, details: String): Boolean {
        val (title, text, canContinue) = when (error) {
            NativeLibrary.CoreError.ErrorSystemFiles -> Triple("Thiếu tệp hệ thống 3DS", "Game cần tệp hệ thống mà engine không tìm thấy: $details", true)
            NativeLibrary.CoreError.ErrorSavestate -> Triple("Lỗi lưu/tải trạng thái", details, true)
            NativeLibrary.CoreError.ErrorSavestateBuildMismatch -> Triple("Trạng thái lưu từ bản engine khác", "Bản lưu này tạo bằng phiên bản engine khác nên không nạp được.\n$details", true)
            NativeLibrary.CoreError.ErrorArticDisconnected -> Triple("Mất kết nối", "Mất kết nối máy chủ Artic.", false)
            NativeLibrary.CoreError.ErrorN3DSApplication -> Triple("Sai chế độ máy", "Game này cần New 3DS. Bật \"New 3DS\" trong tùy chọn 3DS rồi mở lại.", false)
            NativeLibrary.CoreError.ErrorCoreExceptionRaised -> Triple("Lỗi nghiêm trọng", "Giả lập gặp lỗi không xử lý được.\n$details", false)
            NativeLibrary.CoreError.ErrorUnknown -> Triple("Lỗi không rõ", details, true)
            NativeLibrary.CoreError.ErrorLoaderErrorEncrypted -> Triple("ROM bị mã hóa", "File game còn mã hóa mà chưa có khóa.\n$details", false)
            else -> return true
        }
        onError("$error: $details")
        return ask({ AzDialog.Message(title, text, yesNo = canContinue, canContinue = canContinue, reply = it) }, false)
    }

    fun keyboard(config: SoftwareKeyboard.KeyboardConfig): SoftwareKeyboard.KeyboardData =
        ask({ AzDialog.Keyboard(config.hintText.orEmpty().ifBlank { "Nhập chữ" }, config, it) }, SoftwareKeyboard.KeyboardData(1, ""))

    fun miiSelect(config: MiiSelector.MiiSelectorConfig): MiiSelector.MiiSelectorData =
        ask({ AzDialog.Mii(config.title.orEmpty().ifBlank { "Chọn Mii" }, config, it) }, MiiSelector.MiiSelectorData(1, 0))

    /** Tiến độ cài .cia (max, progress) cho màn cài đặt. */
    @Volatile var onInstallProgress: (Int, Int) -> Unit = { _, _ -> }
    fun installProgress(max: Int, progress: Int) = onInstallProgress(max, progress)

    private val lastStage = AtomicReference<DiskShaderCacheProgress.LoadCallbackStage?>(null)

    fun shaderProgress(stage: DiskShaderCacheProgress.LoadCallbackStage, progress: Int, max: Int, obj: String) {
        if (stage == DiskShaderCacheProgress.LoadCallbackStage.Complete) {
            main.post { shader = null; onFirstFrame() }
            return
        }
        lastStage.set(stage)
        val label = when (stage) {
            DiskShaderCacheProgress.LoadCallbackStage.Prepare -> "Chuẩn bị bộ đệm shader"
            DiskShaderCacheProgress.LoadCallbackStage.Decompile -> "Giải mã shader"
            else -> "Dựng shader"
        }
        main.post { shader = if (max > 0) "$label $progress/$max" else label }
    }
}
