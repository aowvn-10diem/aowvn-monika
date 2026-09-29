package vn.aow.monika.azahar

import android.content.Context
import vn.aow.monika.runner.CoreOption
import vn.aow.monika.runner.CoreOptions
import java.io.File

/**
 * Cấu hình engine 3DS do Monika quản lý: người chơi chỉnh trong menu "Tùy chọn giả lập" (cùng bảng với mọi giả lập khác),
 * Monika ghi vào config.ini của engine. Khóa dạng "Phần/tên" (vd. "Renderer/resolution_factor").
 */
object AzaharConfig {
    const val CORE_ID = "azahar"

    private class Def(val key: String, val label: String, val values: List<String>, val default: String, val text: Map<String, String> = emptyMap())

    private val defs = listOf(
        Def("Renderer/resolution_factor", "Độ phân giải", listOf("1", "2", "3", "4", "5"), "1", mapOf("1" to "1x (gốc)", "2" to "2x", "3" to "3x", "4" to "4x", "5" to "5x")),
        Def("Renderer/graphics_api", "Đồ họa", listOf("1", "2"), "1", mapOf("1" to "OpenGL ES", "2" to "Vulkan")),
        Def("Renderer/async_shader_compilation", "Biên dịch shader nền", listOf("1", "0"), "1", mapOf("1" to "Bật", "0" to "Tắt")),
        Def("Renderer/use_disk_shader_cache", "Bộ đệm shader trên đĩa", listOf("1", "0"), "1", mapOf("1" to "Bật", "0" to "Tắt")),
        Def("Renderer/use_skip_duplicate_frames", "Bỏ khung trùng (game 30fps)", listOf("1", "0"), "1", mapOf("1" to "Bật", "0" to "Tắt")),
        Def("Renderer/texture_sampling", "Lọc kết cấu", listOf("0", "1", "2"), "0", mapOf("0" to "Theo game", "1" to "Điểm ảnh", "2" to "Mịn")),
        Def("Core/use_cpu_jit", "CPU JIT", listOf("1", "0"), "1", mapOf("1" to "Bật (nhanh)", "0" to "Tắt (chậm)")),
        Def("Core/cpu_clock_percentage", "Xung nhịp CPU", listOf("100", "75", "150", "200"), "100", mapOf("100" to "100%", "75" to "75% (nhẹ máy)", "150" to "150%", "200" to "200%")),
        Def("System/is_new_3ds", "Máy New 3DS", listOf("1", "0"), "1", mapOf("1" to "Bật", "0" to "Tắt")),
        Def("Layout/layout_option", "Bố cục màn ngang", listOf("2", "3", "0"), "2", mapOf("2" to "Màn lớn", "3" to "Hai màn cạnh nhau", "0" to "Hai màn dọc")),
    )

    fun userDir(c: Context) = File(c.filesDir, "azahar").apply { mkdirs() }

    /** Giá trị đang dùng = mặc định của Monika, đè bằng lựa chọn của người chơi. */
    fun values(c: Context): Map<String, String> = defs.associate { it.key to it.default } + CoreOptions.saved(c, CORE_ID)

    fun options(c: Context): List<CoreOption> {
        val v = values(c)
        return defs.map { CoreOption(it.key, it.label, it.values, v[it.key] ?: it.default, it.text) }
    }

    /** Ghi các giá trị cố định của Monika + lựa chọn người chơi vào config.ini (giữ nguyên các khóa khác của engine). */
    fun write(c: Context) {
        val file = File(userDir(c), "config/config.ini")
        file.parentFile?.mkdirs()
        val fixed = mapOf(
            // Màn dọc: 2 màn xếp dọc lấp đầy khung game; màn ngang theo lựa chọn.
            "Layout/portrait_layout_option" to "0",
            "Layout/screen_orientation" to "2",
            "Renderer/use_frame_limit" to "1",
            "Renderer/use_vsync" to "0",
            "Utility/use_custom_storage" to "0",
        )
        patch(file, fixed + values(c))
    }

    fun patch(file: File, kv: Map<String, String>) {
        val lines = if (file.exists()) file.readLines().toMutableList() else mutableListOf()
        for ((k, v) in kv) {
            val (section, key) = k.split('/', limit = 2)
            var i = lines.indexOfFirst { it.trim() == "[$section]" }
            if (i < 0) { lines += ""; lines += "[$section]"; i = lines.lastIndex }
            var end = i + 1
            var found = -1
            while (end < lines.size && !lines[end].trim().startsWith("[")) {
                val t = lines[end].trim()
                if (t.startsWith("$key =") || t.startsWith("$key=")) found = end
                end++
            }
            if (found >= 0) lines[found] = "$key = $v" else lines.add(end, "$key = $v")
        }
        file.writeText(lines.joinToString("\n") + "\n")
    }
}
