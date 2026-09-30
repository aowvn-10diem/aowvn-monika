package vn.aow.monika.apkinstall.adb

import android.content.Context
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbPairingRequiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.aow.monika.apkinstall.repack.AndroidKeystoreWrap
import vn.aow.monika.apkinstall.repack.RepackKeyStore
import java.io.File
import java.io.OutputStream
import java.security.PrivateKey
import java.security.cert.Certificate

/**
 * Kết nối gỡ lỗi không dây vào CHÍNH máy này (localhost). Khóa ghép đôi lưu riêng trong bộ nhớ của Monika (mã hóa bằng Android Keystore).
 * Ghép đôi 1 lần; lần sau chỉ cần bật lại Gỡ lỗi không dây. Có thể thu hồi ở Cài đặt → Tùy chọn nhà phát triển → Gỡ lỗi không dây.
 */
class LocalAdb(private val context: Context) : AbsAdbConnectionManager(), AdbShell {
    private val signer by lazy { RepackKeyStore(File(context.filesDir, "adb"), AndroidKeystoreWrap).signer() }

    init { setTimeout(20, java.util.concurrent.TimeUnit.SECONDS); api = android.os.Build.VERSION.SDK_INT }

    override fun getPrivateKey(): PrivateKey = signer.privateKey
    override fun getCertificate(): Certificate = signer.certificate
    override fun getDeviceName(): String = "Aow Monika"

    sealed class Connect {
        object Ok : Connect()
        object NeedPairing : Connect()
        data class Failed(val text: String) : Connect()
    }

    /** Tự tìm cổng (mDNS) rồi kết nối. Chưa ghép đôi → NeedPairing. */
    suspend fun connect(timeoutMs: Long = 20_000): Connect = withContext(Dispatchers.IO) {
        try {
            if (isConnected || autoConnect(context, timeoutMs)) Connect.Ok else Connect.Failed("Không kết nối được. Hãy bật Gỡ lỗi không dây và kết nối Wi-Fi.")
        } catch (e: AdbPairingRequiredException) {
            Connect.NeedPairing
        } catch (e: Exception) {
            Connect.Failed("Không kết nối được: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    suspend fun pairWith(host: String, port: Int, code: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { pair(host, port, code) }.getOrDefault(false)
    }

    override suspend fun shell(command: String): String = withContext(Dispatchers.IO) {
        openStream("shell:$command").use { s -> s.openInputStream().bufferedReader().readText() }
    }

    override suspend fun execWrite(command: String, size: Long, writer: (OutputStream) -> Unit): String = withContext(Dispatchers.IO) {
        val s = openStream("exec:$command")
        try {
            s.openOutputStream().use { o -> writer(o); o.flush() }
            // Đóng đầu ghi = báo hết dữ liệu; đầu ra (nếu còn đọc được) nói kết quả. Không đọc được thì trả rỗng — nơi gọi tự kiểm lại.
            runCatching { s.openInputStream().bufferedReader().readText() }.getOrDefault("")
        } finally { runCatching { s.close() } }
    }
}
