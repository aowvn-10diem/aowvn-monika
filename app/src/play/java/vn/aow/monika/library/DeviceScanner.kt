package vn.aow.monika.library
import android.content.Context
import vn.aow.monika.config.MonikaConfig
import java.io.File
/** Không quét bộ nhớ chung trong bản Play; người dùng chọn tệp bằng SAF. */
class DeviceScanner(context: Context) {
    fun canScanAll() = false
    val lastScan = 0L
    fun found(): List<File> = emptyList()
    fun hide(path: String) = Unit
    fun scan(cfg: MonikaConfig, onProgress: (Int) -> Unit = {}): Int = 0
}
