package vn.aow.monika.apkinstall

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File
import java.io.InputStream

/** Thông số máy dùng để quyết định game cài được không. Tách riêng để test giả lập được mọi loại máy. */
data class DeviceInfo(
    val sdkInt: Int,
    val supportedAbis: List<String>,
    /** Rỗng = máy chỉ chạy app 64-bit (Pixel 7+, Galaxy S24+...). */
    val supported32BitAbis: List<String>,
    val densityDpi: Int,
    val freeBytes: Long,
) {
    /** Mức targetSdk thấp nhất hệ thống cho cài (Android 14: 23; 15+: 24; cũ hơn: không chặn). */
    val minInstallableTargetSdk: Int get() = when { sdkInt >= 35 -> 24; sdkInt >= 34 -> 23; else -> 0 }

    companion object {
        fun current(context: Context) = DeviceInfo(
            sdkInt = Build.VERSION.SDK_INT,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            supported32BitAbis = Build.SUPPORTED_32_BIT_ABIS.toList(),
            densityDpi = context.resources.displayMetrics.densityDpi,
            freeBytes = runCatching { Environment.getExternalStorageDirectory().usableSpace }.getOrDefault(0L)
                .takeIf { it > 0 } ?: context.filesDir.usableSpace,
        )
    }
}

/** Dữ liệu 1 file: nằm trên đĩa hoặc trong 1 file zip (không giải nén trước để khỏi tốn gấp đôi dung lượng). */
class Payload(val name: String, val size: Long, private val opener: () -> InputStream) {
    fun open(): InputStream = opener()
}

/** 1 file APK (base hoặc split). [splitName] null = base. */
data class ApkPart(val file: File, val splitName: String?) {
    val isBase get() = splitName == null
}

/** File OBB: [targetName] là tên đúng quy ước `main|patch.<versionCode>.<pkg>.obb`. */
data class ObbFile(val targetName: String, val payload: Payload)

/** File dữ liệu game: [relPath] tính từ `Android/data/<pkg>/`. */
data class DataFile(val relPath: String, val payload: Payload)

enum class PackKind { APK, APKS, XAPK, FOLDER, APKM_UNSUPPORTED }

/** Vấn đề phát hiện TRƯỚC khi cài. [fatal] = chặn hẳn, không có nút Cài. */
sealed class Problem(val fatal: Boolean) {
    /** Câu nói với người chơi (tiếng Việt, kèm việc nên làm). */
    abstract val text: String

    /** Game chỉ có thư viện 32-bit mà máy chỉ chạy app 64-bit. */
    object Only32Bit : Problem(true) {
        override val text = "Máy bạn chỉ chạy được app 64-bit, còn game này chỉ có bản 32-bit. " +
            "Hãy dùng app máy ảo Android (ví dụ VPhoneGaGa) hoặc một điện thoại Android cũ để chơi game này."
    }
    object NoMatchingAbi : Problem(true) {
        override val text = "Game này không có bản dành cho chip của máy bạn. Hãy thử trên máy khác."
    }
    data class MinSdkTooHigh(val minSdk: Int) : Problem(true) {
        override val text = "Game cần Android ${androidName(minSdk)} trở lên, máy bạn thấp hơn."
    }
    data class LowSpace(val need: Long, val free: Long) : Problem(true) {
        override val text = "Không đủ dung lượng: cần khoảng ${gb(need)}, máy còn ${gb(free)}. Hãy xóa bớt rồi thử lại."
    }
    /** Android 14+ chặn cài game làm cho Android quá cũ → dùng checklist 3 cách. */
    data class LowTargetSdk(val targetSdk: Int, val minInstallable: Int) : Problem(false) {
        override val text = "Game này làm cho Android đời cũ (targetSdk $targetSdk), Android của máy chặn cài thường."
    }
    object Unsupported : Problem(true) {
        override val text = "Định dạng file này (APKM) chưa hỗ trợ. Hãy dùng file .apk, .apks hoặc .xapk."
    }
    /** Trong thư mục có nhiều game khác nhau → người dùng chọn 1. */
    data class NeedChoice(val candidates: List<File>) : Problem(true) {
        override val text = "Trong thư mục có ${candidates.size} file cài đặt khác nhau, hãy chọn file cần cài."
    }
    object NoApk : Problem(true) {
        override val text = "Không tìm thấy file APK trong gói này."
    }

    companion object {
        private fun gb(b: Long) = "%.1f GB".format(b / 1_073_741_824.0).replace('.', ',')
        fun androidName(sdk: Int) = when (sdk) {
            26 -> "8.0"; 27 -> "8.1"; 28 -> "9"; 29 -> "10"; 30 -> "11"; 31 -> "12"; 32 -> "12L"
            33 -> "13"; 34 -> "14"; 35 -> "15"; 36 -> "16"; else -> "(API $sdk)"
        }
    }
}

data class InspectResult(
    val kind: PackKind,
    val packageName: String,
    val label: String?,
    val versionCode: Long,
    val versionName: String?,
    val minSdk: Int,
    val targetSdk: Int,
    val parts: List<ApkPart>,
    val nativeAbis: Set<String>,
    val obbFiles: List<ObbFile>,
    val dataFiles: List<DataFile>,
    val problems: List<Problem>,
    /** Thư mục tạm chứa APK đã giải nén (xóa khi xong). Null nếu không giải nén gì. */
    val workDir: File? = null,
) {
    val fatal: Problem? get() = problems.firstOrNull { it.fatal }
    val lowTargetSdk: Boolean get() = problems.any { it is Problem.LowTargetSdk }
    val needsData: Boolean get() = dataFiles.isNotEmpty()
    val totalBytes: Long get() = parts.sumOf { it.file.length() } + obbFiles.sumOf { it.payload.size } + dataFiles.sumOf { it.payload.size }

    companion object {
        fun failed(kind: PackKind, problem: Problem) = InspectResult(kind, "", null, 0, null, 0, 0, emptyList(), emptySet(), emptyList(), emptyList(), listOf(problem))
    }
}
