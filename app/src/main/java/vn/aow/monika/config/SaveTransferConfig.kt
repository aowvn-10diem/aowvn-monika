package vn.aow.monika.config

import kotlinx.serialization.Serializable

@Serializable
data class SaveTransferConfig(val systems: Map<String, SaveProfile> = defaultSaveProfiles()) {
    fun validate() {
        require(systems.size <= 32) { "Quá nhiều cấu hình save" }
        fun safe(path: String) = path.isNotEmpty() && path.length <= 120 && path.none { it.code < 32 || it in "\\:" } &&
            path.split('/').all { it.isNotEmpty() && it != "." && it != ".." }
        systems.values.forEach { p ->
            require(p.kind in setOf("raw", "rms") && safe(p.saveDirectory)) { "Cấu hình thư mục save không hợp lệ" }
            require(p.imports.isNotEmpty() && p.imports.size <= 16 && p.imports.keys.all { it.matches(Regex("[a-z0-9]{1,8}")) }) { "Đuôi save không hợp lệ" }
            if (p.kind == "raw") {
                require(safe(p.stateDirectory) && p.extension.matches(Regex("[a-z0-9]{1,8}"))) { "Thư mục state/đuôi save không hợp lệ" }
                require(p.imports.values.all { it in setOf("raw", "desmume") }) { "Bộ chuyển save chưa được hỗ trợ" }
                require(p.sizes.isNotEmpty() && p.sizes.size <= 32 && p.sizes.distinct().size == p.sizes.size &&
                    p.sizes.all { it in 1..33554432 } && (p.targetBytes == 0 || p.targetBytes in p.sizes)) { "Kích thước save không hợp lệ" }
            } else require(p.imports.all { (ext, converter) -> ext in setOf("rsh", "rsr") && converter == "rms" }) { "Bộ chuyển RMS không hợp lệ" }
        }
    }
}

@Serializable
data class SaveProfile(
    val kind: String = "raw",
    val saveDirectory: String = "saves",
    val stateDirectory: String = "states",
    val extension: String = "srm",
    /** Khóa là đuôi file ngoài; giá trị chỉ chọn bộ chuyển đổi đã kiểm, không chạy mã từ config. */
    val imports: Map<String, String> = mapOf("sav" to "raw", "srm" to "raw"),
    val sizes: List<Int> = emptyList(),
    /** Lõi có RAM cố định có thể đệm FF, không bao giờ cắt dữ liệu khác FF. 0 = giữ kích thước hợp lệ. */
    val targetBytes: Int = 0,
)

fun defaultSaveProfiles(): Map<String, SaveProfile> = linkedMapOf(
    "java" to SaveProfile("rms", "data", "", "", mapOf("rsh" to "rms", "rsr" to "rms")),
    "nds" to SaveProfile(imports = mapOf("sav" to "raw", "srm" to "raw", "dsv" to "desmume"),
        sizes = listOf(512, 8192, 32768, 65536, 131072, 262144, 524288, 1048576, 2097152, 4194304, 8388608)),
    "gba" to SaveProfile(sizes = listOf(512, 8192, 32768, 65536, 131072)),
)
