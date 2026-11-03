package vn.aow.monika.apkinstall

/** Đặt tên file OBB theo quy ước Android: `main|patch.<versionCode>.<gói>.obb`. */
object ObbNames {
    private val STD = Regex("""^(main|patch)\.\d+\..+\.obb$""", RegexOption.IGNORE_CASE)

    fun isStandard(name: String) = STD.matches(name)

    /** Tên đã chuẩn → giữ; nếu cả gói chỉ có 1 file OBB tên lạ → coi là "main"; nhiều file tên lạ → giữ nguyên tên (không đoán). */
    fun targetName(fileName: String, pkg: String, versionCode: Long, onlyOne: Boolean): String = when {
        isStandard(fileName) -> fileName
        onlyOne && fileName.endsWith(".obb", true) -> "main.$versionCode.$pkg.obb"
        else -> fileName
    }
}
