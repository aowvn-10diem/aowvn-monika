package vn.aow.monika.apkinstall

import kotlin.math.abs

/**
 * Chọn các phần APK cần cài cho đúng máy này: base + split tính năng + đúng 1 split chip + 1 split mật độ + mọi split ngôn ngữ.
 * Cài dư split chip/mật độ có thể bị Android từ chối, nên chỉ lấy 1.
 */
object SplitSelector {
    private val ABIS = setOf("arm64_v8a", "armeabi_v7a", "armeabi", "x86", "x86_64", "mips", "mips64")
    private val DENSITY = mapOf("ldpi" to 120, "mdpi" to 160, "tvdpi" to 213, "hdpi" to 240, "xhdpi" to 320, "xxhdpi" to 480, "xxxhdpi" to 640)
    private val DENSITY_ANY = setOf("nodpi", "anydpi")
    private val CONFIG = Regex("""(?:^|\.)config\.(.+)$""")

    enum class Kind { BASE, FEATURE, ABI, DENSITY, OTHER }

    /** Phân loại 1 split theo tên; trả về (loại, phần định danh ở sau "config."). */
    fun classify(splitName: String?): Pair<Kind, String?> {
        if (splitName == null) return Kind.BASE to null
        val q = CONFIG.find(splitName)?.groupValues?.get(1) ?: return Kind.FEATURE to null
        return when {
            q in ABIS -> Kind.ABI to q
            q in DENSITY || q in DENSITY_ANY -> Kind.DENSITY to q
            else -> Kind.OTHER to q
        }
    }

    fun select(parts: List<ApkPart>, device: DeviceInfo): List<ApkPart> {
        val keep = ArrayList<ApkPart>()
        val abis = parts.filter { classify(it.splitName).first == Kind.ABI }
        val dens = parts.filter { classify(it.splitName).first == Kind.DENSITY }
        for (p in parts) {
            val k = classify(p.splitName).first
            if (k == Kind.BASE || k == Kind.FEATURE || k == Kind.OTHER) keep += p
        }
        // Chip: ưu tiên theo thứ tự ưa thích của máy.
        for (want in device.supportedAbis.map { it.replace('-', '_') }) {
            val hit = abis.firstOrNull { classify(it.splitName).second == want }
            if (hit != null) { keep += hit; break }
        }
        // Mật độ: gần nhất (split "nodpi"/"anydpi" luôn lấy vì không tốn).
        keep += dens.filter { classify(it.splitName).second in DENSITY_ANY }
        dens.filter { classify(it.splitName).second in DENSITY }
            .minByOrNull { abs(DENSITY.getValue(classify(it.splitName).second!!) - device.densityDpi) }
            ?.let { keep += it }
        return keep.distinct()
    }
}
