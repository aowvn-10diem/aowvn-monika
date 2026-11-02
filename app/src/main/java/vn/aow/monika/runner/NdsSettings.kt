package vn.aow.monika.runner

import vn.aow.monika.config.CoreOptionText

/**
 * V188: cách trình bày tùy chọn lõi NDS (melonDS DS) cho dễ đọc — 3 nhóm cố định
 * **Màn hình · Bố cục · Hiệu năng** và nhãn tiếng Việt theo *khoá tùy chọn*.
 *
 * - Nhãn: bản dịch trong config (`coreOptionText.labels`, keyed theo tên lõi đưa ra) thắng; thiếu thì dùng bảng
 *   theo khoá ở đây; khoá lạ giữ nguyên tên gốc — không đoán nghĩa.
 * - Nhóm: theo khoá của các tùy chọn đã biết, còn lại theo từ khoá trong tên gốc của lõi. Khoá không xếp được
 *   nhóm vẫn được hiển thị (ở cuối, tiêu đề "Khác") — không ẩn bớt tùy chọn của lõi.
 * - Danh sách bố cục KHÔNG nằm ở đây: vẫn lấy từ config `cores.<id>.screen.layouts` (nguồn duy nhất).
 */
object NdsSettings {
    /** 3 nhóm theo thứ tự cố định trong bảng tùy chọn. */
    enum class Group(val title: String) { SCREEN("Màn hình"), LAYOUT("Bố cục"), PERFORMANCE("Hiệu năng") }

    /** Nhãn tiếng Việt theo khoá tùy chọn của melonDS DS (khoá lạ → giữ nguyên tên lõi/config). */
    private val labels = mapOf(
        "melonds_screen_layout1" to "Bố cục 2 màn hình",
        "melonds_number_of_screen_layouts" to "Số bố cục màn hình đang dùng",
        "melonds_screen_gap" to "Khoảng cách giữa 2 màn hình",
        "melonds_hybrid_ratio" to "Bố cục lai: Tỉ lệ màn lớn/nhỏ",
        "melonds_jit_enable" to "Bật JIT – tăng tốc (mở lại game)",
        "melonds_render_mode" to "Cách vẽ 3D (phần mềm / OpenGL)",
        "melonds_threaded_renderer" to "Vẽ 3D đa luồng (mượt hơn)",
        "melonds_sysfile_mode" to "Nguồn BIOS/Firmware của máy",
    )

    /** Giá trị tiếng Việt cho các lựa chọn NDS hay gặp mà config chưa dịch. */
    private val values = mapOf(
        "software" to "Phần mềm",
        "Software" to "Phần mềm",
        "builtin" to "Có sẵn trong lõi",
        "external" to "Tệp ngoài",
    )

    /** Tên gốc đã biết nhưng khoá khác nhau giữa các bản lõi (dịch theo tên gốc). */
    private val knownLabels = mapOf(
        "Number of Screen Layouts" to "Số bố cục màn hình đang dùng",
        "Screen Layout 1" to "Bố cục 2 màn hình",
    )

    /** Tùy chọn này có phải của lõi NDS không (theo mã lõi hoặc theo khoá). */
    fun isNds(coreId: String, options: List<CoreOption>): Boolean =
        coreId.startsWith("melonds") || (options.isNotEmpty() && options.all { it.key.startsWith("melonds_") })

    /** Nhãn hiển thị: config thắng → bảng theo khoá → tên gốc đã biết → tên gốc của lõi. */
    fun label(key: String, original: String, text: CoreOptionText): String =
        text.labels[original] ?: labels[key] ?: knownLabels[original] ?: original

    /** Bản dịch giá trị để hiển thị: config thắng, bổ sung các giá trị NDS còn thiếu. */
    fun valueText(text: CoreOptionText): Map<String, String> = values + text.values

    /**
     * Cùng một lựa chọn bố cục? Config ghi "left-right", lõi có thể báo "Left/Right" — so sau khi bỏ
     * dấu gạch/xiên/khoảng trắng và không phân biệt hoa thường để chip hiện đúng lựa chọn đang dùng.
     */
    fun sameChoice(configValue: String, coreValue: String): Boolean = normalize(configValue) == normalize(coreValue)

    private fun normalize(v: String) = v.lowercase().replace("-", "").replace("/", "").replace(" ", "")

    private val layoutKeys = setOf(
        "melonds_screen_layout1", "melonds_number_of_screen_layouts", "melonds_screen_gap", "melonds_hybrid_ratio",
    )
    private val performanceKeys = setOf("melonds_jit_enable", "melonds_render_mode", "melonds_threaded_renderer")

    /**
     * Nhóm của một tùy chọn; null = chưa xếp nhóm (vẫn hiện ở cuối bảng).
     * Ưu tiên khoá đã biết, sau đó tới từ khoá trong tên gốc (lõi đặt tên tiếng Anh) — nhãn đã dịch không dùng để đoán.
     */
    fun groupOf(option: CoreOption): Group? {
        if (option.key in layoutKeys) return Group.LAYOUT
        if (option.key in performanceKeys) return Group.PERFORMANCE
        val name = option.original.ifBlank { option.label }.lowercase()
        return when {
            name.contains("layout") || name.contains("screen gap") || name.contains("swap screen") -> Group.LAYOUT
            name.contains("jit") || name.contains("cpu cores") || name.contains("threaded") ||
                name.contains("frameskip") || name.contains("bus-level") || name.contains("fast memory") ||
                name.contains("optimisation") -> Group.PERFORMANCE
            name.contains("resolution") || name.contains("renderer") || name.contains("rasterizer") ||
                name.contains("opengl") || name.contains("soft3d") || name.contains("texture") ||
                name.contains("edge marking") || name.contains("multisampling") || name.contains("shadow") ||
                name.contains("color depth") || name.contains("filtering") || name.contains("deposter") ||
                name.contains("polygon") -> Group.SCREEN
            else -> null
        }
    }

    /**
     * Chia [options] thành các nhóm theo thứ tự cố định Màn hình → Bố cục → Hiệu năng (bỏ nhóm rỗng),
     * tùy chọn chưa xếp nhóm nằm cuối với group = null. Giữ nguyên thứ tự trong từng nhóm.
     */
    fun sections(options: List<CoreOption>): List<Pair<Group?, List<CoreOption>>> {
        val byGroup = options.groupBy { groupOf(it) }
        val known = Group.entries.mapNotNull { g -> byGroup[g]?.let { g to it } }
        val rest = byGroup[null].orEmpty()
        return if (rest.isEmpty()) known else known + (null to rest)
    }
}
