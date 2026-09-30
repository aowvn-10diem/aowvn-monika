package vn.aow.monika.apkinstall

import android.content.pm.PackageInstaller
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

/** Kết quả 1 lượt cài bằng PackageInstaller, đã dịch ra việc người chơi cần làm. */
sealed class InstallOutcome {
    object Success : InstallOutcome()
    /** Người chơi bấm Hủy ở hộp thoại của Android — không phải lỗi. */
    object UserAborted : InstallOutcome()
    data class Failure(val kind: Kind, val text: String, val raw: String) : InstallOutcome()

    enum class Kind {
        /** Đã có bản cùng tên gói nhưng ký khác chữ ký → phải gỡ bản cũ. */
        SIGNATURE_CONFLICT,
        /** Đã có bản mới hơn. */
        DOWNGRADE,
        /** Android chặn vì targetSdk quá thấp. */
        DEPRECATED_SDK,
        STORAGE,
        ABI,
        BLOCKED,
        OTHER,
    }

    companion object {
        /** Dịch mã trạng thái + thông điệp hệ thống của PackageInstaller. [message] có thể null. */
        fun from(status: Int, message: String?): InstallOutcome {
            val m = message.orEmpty()
            if (status == PackageInstaller.STATUS_SUCCESS) return Success
            if (status == PackageInstaller.STATUS_FAILURE_ABORTED) return UserAborted
            fun f(kind: Kind, text: String) = Failure(kind, text, "status=$status $m".trim())
            return when {
                "INSTALL_FAILED_UPDATE_INCOMPATIBLE" in m || "signatures do not match" in m ->
                    f(Kind.SIGNATURE_CONFLICT, "Máy đang có bản khác của game này (ký khác). Cần gỡ bản cũ rồi cài lại — save trong game cũ sẽ mất.")
                "INSTALL_FAILED_VERSION_DOWNGRADE" in m || "VERSION_DOWNGRADE" in m ->
                    f(Kind.DOWNGRADE, "Máy đang có bản mới hơn. Muốn cài bản cũ này phải gỡ bản hiện tại trước.")
                "DEPRECATED_SDK_VERSION" in m ->
                    f(Kind.DEPRECATED_SDK, "Android chặn game này vì làm cho Android quá cũ.")
                status == PackageInstaller.STATUS_FAILURE_STORAGE || "INSUFFICIENT_STORAGE" in m ->
                    f(Kind.STORAGE, "Không đủ dung lượng để cài. Hãy xóa bớt rồi thử lại.")
                "NO_MATCHING_ABIS" in m -> f(Kind.ABI, "Game không có bản hợp với chip của máy.")
                status == PackageInstaller.STATUS_FAILURE_BLOCKED || "INSTALL_FAILED_USER_RESTRICTED" in m ->
                    f(Kind.BLOCKED, "Máy chặn việc cài này (Play Protect hoặc chính sách của máy). Kiểm tra cài đặt bảo mật rồi thử lại.")
                else -> f(Kind.OTHER, "Không cài được game" + if (m.isNotBlank()) ": $m" else ".")
            }
        }
    }
}

/** Cầu nối giữa [InstallResultReceiver] (nhận kết quả từ hệ thống) và bên đang chờ kết quả. Cùng 1 tiến trình. */
object InstallResults {
    private val waiting = ConcurrentHashMap<Int, CompletableDeferred<InstallOutcome>>()

    fun expect(sessionId: Int): CompletableDeferred<InstallOutcome> = CompletableDeferred<InstallOutcome>().also { waiting[sessionId] = it }

    fun deliver(sessionId: Int, outcome: InstallOutcome) { waiting.remove(sessionId)?.complete(outcome) }

    fun forget(sessionId: Int) { waiting.remove(sessionId) }
}
