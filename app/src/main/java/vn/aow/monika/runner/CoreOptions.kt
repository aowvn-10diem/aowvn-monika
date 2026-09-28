package vn.aow.monika.runner

import android.content.Context

/** 1 tùy chọn của lõi libretro, vd. "Độ phân giải trong: 1x|2x|3x". */
data class CoreOption(
    val key: String,
    val label: String,
    val values: List<String>,
    val value: String,
    /** Bản dịch giá trị để HIỂN THỊ (giá trị gửi cho lõi vẫn là bản gốc). */
    val valueText: Map<String, String> = emptyMap(),
) {
    fun display(v: String = value): String = valueText[v] ?: valueText[v.lowercase()] ?: v

    /** Giá trị kế tiếp (bấm để xoay vòng). */
    fun next(): String = values.getOrNull((values.indexOf(value) + 1) % values.size.coerceAtLeast(1)) ?: value
}

/**
 * Tùy chọn lõi giả lập.
 * - Mặc định lấy từ config `cores.<id>.options` (sửa config là đổi cho mọi user, không cần APK mới).
 * - User chỉnh trong game (menu … → Tùy chọn giả lập) → lưu riêng từng lõi, đè lên mặc định.
 */
object CoreOptions {

    /**
     * Lõi libretro mô tả tùy chọn dạng "Tên; giá trị1|giá trị2|…" (giá trị đầu là mặc định).
     * Mô tả không đúng dạng → bỏ qua (không có danh sách để chọn).
     */
    fun parse(key: String, description: String?, current: String?, text: vn.aow.monika.config.CoreOptionText = vn.aow.monika.config.CoreOptionText()): CoreOption? {
        val d = description ?: return null
        val sep = d.indexOf(';')
        if (sep < 0) return null
        val values = d.substring(sep + 1).trim().split('|').map { it.trim() }.filter { it.isNotEmpty() }
        if (values.size < 2) return null
        val value = current?.takeIf { it in values } ?: values.first()
        val label = d.substring(0, sep).trim()
        return CoreOption(key, text.labels[label] ?: label, values, value, text.values)
    }

    /** Giá trị áp khi mở game = mặc định trong config, đè bằng giá trị user đã chọn. */
    fun initial(context: Context, coreId: String, configDefaults: Map<String, String>): Map<String, String> =
        configDefaults + saved(context, coreId)

    fun saved(context: Context, coreId: String): Map<String, String> =
        prefs(context, coreId).all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()

    fun save(context: Context, coreId: String, key: String, value: String) {
        prefs(context, coreId).edit().putString(key, value).apply()
    }

    fun reset(context: Context, coreId: String) {
        prefs(context, coreId).edit().clear().apply()
    }

    private fun prefs(context: Context, coreId: String) =
        context.getSharedPreferences("core_options_$coreId", Context.MODE_PRIVATE)
}
