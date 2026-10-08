package vn.aow.monika.library

import java.io.File

/** Kết quả chọn lối vào cho engine nhúng (V26). */
sealed class EntryResolution {
    /** Giữ nguyên lối vào đang có (không đủ bằng chứng để đổi); [why] ghi vào breadcrumb. */
    class Keep(val why: String) : EntryResolution()
    /** Dùng [entry] (file xp3 hoặc thư mục có `startup.tjs` rời) thay cho lối vào cũ; [why] ghi vào breadcrumb. */
    class Use(val entry: File, val why: String) : EntryResolution()
    /** Đã đọc được chỉ mục mọi xp3 và không có `startup.tjs` ở đâu → không gọi engine, báo lỗi rõ ràng. */
    class NotFound(
        val dirName: String,
        val why: String,
        val details: List<String> = emptyList(),
        /** Mọi xp3 đều có tên mục không phải .tjs/.ks (xem [KirikiriEntryResolver.looksEncrypted]) → nhiều khả năng kho mã hóa (V26b). */
        val encrypted: Boolean = false,
    ) : EntryResolution()
}

/**
 * V26: Kirikiri báo "Cannot find storage startup.tjs" khi kho truyền vào không có `startup.tjs` ở gốc. Trước khi gọi engine,
 * đọc chỉ mục các xp3 cùng thư mục ([Xp3Index], chỉ seek tới chỉ mục) để chọn kho có `startup.tjs`. Không chắc → [EntryResolution.Keep]
 * (giữ hành vi cũ). Danh sách ứng viên gồm cả bản vá (`patch*.xp3`), nhưng bản vá chỉ được chọn khi là kho DUY NHẤT lộ `startup.tjs`.
 * Hàm chặn (đọc đĩa): gọi ở luồng nền, có timeout.
 */
object KirikiriEntryResolver {
    /** Mặc định giống `entryExclude` của hệ Kirikiri trong config; nơi gọi nên truyền giá trị từ config. */
    val DEFAULT_PATCH = listOf("^patch\\d*\\.xp3$")

    fun resolve(entry: File?, patchPatterns: List<String> = DEFAULT_PATCH): EntryResolution = try {
        resolveUnsafe(entry, patchPatterns)
    } catch (e: Exception) {
        EntryResolution.Keep("lỗi ${e.javaClass.simpleName}")
    }

    private fun resolveUnsafe(entry: File?, patchPatterns: List<String>): EntryResolution {
        if (entry == null) return EntryResolution.Keep("không có lối vào (để engine tự hỏi)")
        val dir = if (entry.isDirectory) entry else entry.parentFile ?: return EntryResolution.Keep("không có thư mục cha")
        val isXp3Entry = entry.isFile && entry.extension.equals("xp3", true)
        if (entry.isFile && !isXp3Entry) return EntryResolution.Keep("lối vào không phải xp3 (${entry.extension}), giữ nguyên")

        val patch = patchPatterns.map { Regex(it, RegexOption.IGNORE_CASE) }
        fun isPatch(f: File) = patch.any { it.matches(f.name) }
        val xp3s = dir.listFiles().orEmpty().filter { it.isFile && it.extension.equals("xp3", true) }
        val idx: Map<File, Xp3Index.Result> = xp3s.associateWith { Xp3Index.read(it) }
        val has: Map<File, Boolean?> = idx.mapValues { (_, r) -> (r as? Xp3Index.Result.Names)?.let { Xp3Index.hasRootStartup(it.names) } }

        // 1. Lối vào hiện tại đã có startup.tjs ở gốc → không đổi gì.
        if (isXp3Entry && has[entry] == true) return EntryResolution.Keep("xp3 đang chọn có startup.tjs ở gốc")

        // 2. Kho khác có startup.tjs: ưu tiên kho thường (lớn nhất); bản vá chỉ khi là kho duy nhất lộ startup.tjs.
        val withStartup = xp3s.filter { has[it] == true }
        val normal = withStartup.filter { !isPatch(it) }.sortedByDescending { it.length() }
        if (normal.isNotEmpty()) return EntryResolution.Use(normal.first(), "xp3 ${normal.first().name} có startup.tjs ở gốc, xp3 đang chọn thì không/không rõ")
        if (withStartup.size == 1) return EntryResolution.Use(withStartup.first(), "chỉ ${withStartup.first().name} (bản vá) lộ startup.tjs ở gốc")
        if (withStartup.size > 1) return EntryResolution.Keep("${withStartup.size} bản vá cùng có startup.tjs, không tự chọn")

        // 3. Không xp3 nào lộ startup.tjs: thư mục có startup.tjs rời thì engine nhận được thư mục.
        if (dir.listFiles().orEmpty().any { it.isFile && it.name.equals("startup.tjs", true) }) {
            return EntryResolution.Use(dir, "thư mục có startup.tjs rời")
        }

        // 4. Đọc được chỉ mục của mọi xp3 mà không đâu có startup.tjs → báo lỗi rõ. Có xp3 không đọc được (mã hóa/định dạng lạ) → giữ nguyên.
        if (xp3s.isNotEmpty() && xp3s.all { has[it] == false }) {
            return EntryResolution.NotFound(dir.name, "đọc chỉ mục ${xp3s.size} xp3, không có startup.tjs ở gốc, không có startup.tjs rời",
                xp3s.sortedBy { it.name }.take(MAX_DETAIL_FILES).map { describe(it, idx[it]) },
                encrypted = xp3s.all { looksEncrypted(idx[it]) })
        }
        return EntryResolution.Keep(if (xp3s.isEmpty()) "thư mục không có xp3" else "có xp3 không đọc được chỉ mục, giữ nguyên")
    }

    private const val MAX_DETAIL_FILES = 8
    private const val MAX_SAMPLE_NAMES = 5

    /** Kho có mục nhưng không tên nào là *.tjs / *.ks → tên có vẻ băm/mã hóa. Chỉ mục bị cắt ([Xp3Index.Result.Names.complete] = false) hoặc không đọc được thì false. */
    internal fun looksEncrypted(result: Xp3Index.Result?): Boolean {
        val names = (result as? Xp3Index.Result.Names)?.takeIf { it.complete }?.names ?: return false
        return names.isNotEmpty() && names.none { it.endsWith(".tjs", true) || it.endsWith(".ks", true) }
    }

    /**
     * Một dòng chẩn đoán cho crumb (≤ 280 ký tự): tên xp3, dung lượng, số mục, 5 tên đầu, và cờ "tên có vẻ băm/mã hóa" khi
     * không có tên dạng *.tjs / *.ks nào (V26/G7B). Chỉ tên cơ sở của chính xp3 và tên mục trong kho, không đường dẫn máy.
     */
    internal fun describe(file: File, result: Xp3Index.Result?): String {
        val mb = "%.1f".format(java.util.Locale.ROOT, file.length() / 1_048_576.0)
        val names = (result as? Xp3Index.Result.Names) ?: return "${file.name} ${mb}MB: không đọc được chỉ mục"
        val sample = names.names.take(MAX_SAMPLE_NAMES).joinToString("|") { it.take(40) }
        val flag = if (looksEncrypted(names)) " [tên có vẻ băm/mã hóa]" else ""
        val more = if (names.complete) "" else "+"
        return "${file.name} ${mb}MB: ${names.names.size}$more mục, mẫu: $sample$flag".take(280)
    }
}
