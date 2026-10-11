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
        /** Mọi xp3 đều có dấu hiệu tên băm và không có script đọc được; đây là heuristic, không phải xác minh mã hóa. */
        val encrypted: Boolean = false,
        /** Lý do engine chưa hỗ trợ đã xác minh từ định dạng, không suy đoán mã hóa. */
        val unsupportedReason: String? = null,
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
        val isExeEntry = entry.isFile && entry.extension.equals("exe", true)
        if (entry.isFile && !isXp3Entry && !isExeEntry) return EntryResolution.Keep("lối vào không phải xp3 (${entry.extension}), giữ nguyên")

        val patch = patchPatterns.map { Regex(it, RegexOption.IGNORE_CASE) }
        fun isPatch(f: File) = patch.any { it.matches(f.name) }
        val files = dir.listFiles().orEmpty().filter { it.isFile }
        val xp3s = files.filter { it.extension.equals("xp3", true) }
        val exes = files.filter { it.extension.equals("exe", true) }.sortedWith(
            compareByDescending<File> { it.nameWithoutExtension.equals(entry.nameWithoutExtension, true) }.thenBy { it.name.lowercase() })
        // V64: không giữ Result/Names qua lần đọc kế tiếp. Chỉ giữ ứng viên và
        // tối đa 8 dòng chẩn đoán; chỉ mục lớn vẫn chịu cap riêng của Xp3Index.
        var selectedHasStartup = false
        var allReadable = true
        var allEncrypted = true // V26b: mọi xp3 đều có cờ "tên có vẻ băm/mã hóa" (chỉ số bool, không giữ Result)
        var startupCount = 0
        var firstWithStartup: File? = null
        var bestNormal: File? = null
        var bestNormalSize = -1L
        val details = mutableListOf<Pair<String, String>>()
        for (file in xp3s) {
            val summary = readSummary(file)
            if (summary.hasStartup == null) allReadable = false
            if (!summary.looksEncrypted) allEncrypted = false
            if (summary.hasStartup == true) {
                if (file == entry) selectedHasStartup = true
                startupCount++
                if (firstWithStartup == null) firstWithStartup = file
                if (!isPatch(file)) {
                    val size = file.length()
                    if (size > bestNormalSize) {
                        bestNormal = file
                        bestNormalSize = size
                    }
                }
            }
            details.add(file.name to summary.detail)
            details.sortBy { it.first }
            if (details.size > MAX_DETAIL_FILES) details.removeAt(details.lastIndex)
        }

        // 1. Lối vào hiện tại đã có startup.tjs ở gốc → không đổi gì.
        if (isXp3Entry && selectedHasStartup) return EntryResolution.Keep("xp3 đang chọn có startup.tjs ở gốc")

        // 2. Kho khác có startup.tjs: ưu tiên kho thường (lớn nhất); bản vá chỉ khi là kho duy nhất lộ startup.tjs.
        bestNormal?.let { return EntryResolution.Use(it, "xp3 ${it.name} có startup.tjs ở gốc, xp3 đang chọn thì không/không rõ") }

        var embeddedCandidate: File? = null
        var embeddedCount = 0
        var embeddedEncrypted = false
        var embeddedUnknown = false
        var embeddedUnaligned = false
        for (file in exes) {
            val summary = readSummary(file)
            if (summary.offset == null) continue
            embeddedCount++
            details.add(file.name to summary.detail)
            details.sortBy { it.first }
            if (details.size > MAX_DETAIL_FILES) details.removeAt(details.lastIndex)
            if (summary.hasStartup == null) embeddedUnknown = true
            if (summary.looksEncrypted) embeddedEncrypted = true
            if (summary.hasStartup == true) {
                if (summary.offset % 16 != 0L) embeddedUnaligned = true
                else if (embeddedCandidate == null) embeddedCandidate = file
                else if (!embeddedCandidate.nameWithoutExtension.equals(entry.nameWithoutExtension, true) &&
                    !file.nameWithoutExtension.equals(entry.nameWithoutExtension, true)) {
                    return EntryResolution.Keep("nhiều exe có startup.tjs, không tự chọn")
                }
            }
        }

        embeddedCandidate?.let { return EntryResolution.Use(it, "exe ${it.name} có XP3 nhúng chứa startup.tjs ở gốc") }
        if (startupCount == 1) {
            val only = checkNotNull(firstWithStartup)
            return EntryResolution.Use(only, "chỉ ${only.name} (bản vá) lộ startup.tjs ở gốc")
        }
        if (startupCount > 1) return EntryResolution.Keep("${startupCount} bản vá cùng có startup.tjs, không tự chọn")

        // 3. Không xp3 nào lộ startup.tjs: thư mục có startup.tjs rời thì engine nhận được thư mục.
        if (dir.listFiles().orEmpty().any { it.isFile && it.name.equals("startup.tjs", true) }) {
            return EntryResolution.Use(dir, "thư mục có startup.tjs rời")
        }

        if (embeddedUnknown) return EntryResolution.Keep("exe có XP3 nhúng nhưng chỉ mục không đọc đủ; có thể mã hóa/định dạng lạ, chưa xác minh")
        if (embeddedUnaligned) return EntryResolution.NotFound(dir.name, "XP3 nhúng lệch căn 16 byte",
            details.map { it.second }, unsupportedReason = "XP3 nhúng không căn 16 byte. Engine Kirikiri hiện tại chưa hỗ trợ game này.")
        if (embeddedEncrypted && allReadable) return EntryResolution.NotFound(dir.name, "XP3 nhúng có dấu hiệu tên băm/mã hóa, chưa hỗ trợ",
            details.map { it.second }, encrypted = true)

        // 4. Đọc được chỉ mục của mọi xp3 mà không đâu có startup.tjs → báo lỗi rõ. Có xp3 không đọc được (mã hóa/định dạng lạ) → giữ nguyên.
        if ((xp3s.isNotEmpty() || embeddedCount > 0) && allReadable) {
            return EntryResolution.NotFound(dir.name, "đọc chỉ mục ${xp3s.size} xp3, " + (if (embeddedCount > 0) "$embeddedCount XP3 nhúng, " else "") +
                    "không có startup.tjs ở gốc, không có startup.tjs rời",
                details.map { it.second }, encrypted = allEncrypted && xp3s.isNotEmpty())
        }
        return EntryResolution.Keep(if (xp3s.isEmpty()) "thư mục không có xp3" else "có xp3 không đọc được chỉ mục, giữ nguyên")
    }

    private data class IndexSummary(val hasStartup: Boolean?, val detail: String, val looksEncrypted: Boolean, val offset: Long?)

    // Result và toàn bộ tên chỉ sống trong scope này; dòng detail đã cắt ngắn,
    // không trả danh sách tên hoặc closure giữ Result về vòng lặp resolve.
    private fun readSummary(file: File): IndexSummary {
        val result = Xp3Index.read(file)
        val hasStartup = Xp3Index.hasRootStartup(result)
        return IndexSummary(hasStartup, describe(file, result), looksEncrypted(result), result.archiveOffset)
    }

    private const val MAX_DETAIL_FILES = 8
    private const val MAX_SAMPLE_NAMES = 5

    // Chỉ tên cơ sở không có phần mở rộng, 6–64 ký tự hex có cả chữ và số mới là bằng chứng tên băm.
    // Kho chỉ chứa tài nguyên có tên thông thường không đủ để kết luận.
    private val HASHED_NAME = Regex("[0-9a-fA-F]{6,64}")

    /** Chỉ mục đầy đủ, không có script đuôi .tjs hoặc .ks và có tên giống băm. Chỉ là dấu hiệu, không xác minh mã hóa. */
    internal fun looksEncrypted(result: Xp3Index.Result?): Boolean {
        val names = (result as? Xp3Index.Result.Names)?.takeIf { it.complete }?.names ?: return false
        if (names.isEmpty() || names.any { it.endsWith(".tjs", true) || it.endsWith(".ks", true) }) return false
        return names.any { name ->
            val basename = name.replace('\\', '/').substringAfterLast('/')
            HASHED_NAME.matches(basename) && basename.any { it in '0'..'9' } &&
                basename.any { it in 'a'..'f' || it in 'A'..'F' }
        }
    }

    /**
     * Một dòng chẩn đoán cho crumb (≤ 280 ký tự): tên xp3, dung lượng, số mục, 5 tên đầu, và cờ "tên có vẻ băm/mã hóa" khi
     * có dấu hiệu tên băm và không có *.tjs / *.ks nào. Chỉ tên cơ sở của chính xp3 và tên mục trong kho, không đường dẫn máy.
     */
    internal fun describe(file: File, result: Xp3Index.Result?): String {
        val mb = "%.1f".format(java.util.Locale.ROOT, file.length() / 1_048_576.0)
        val embedded = if (file.extension.equals("exe", true)) " xp3 nhúng=${result?.archiveOffset != null} offset=${result?.archiveOffset}" else ""
        val names = (result as? Xp3Index.Result.Names) ?: return "${file.name} ${mb}MB$embedded: startup.tjs@gốc=? chỉ mục chưa hoàn chỉnh (không đọc được chỉ mục)".take(280)
        val sample = names.names.take(MAX_SAMPLE_NAMES).joinToString("|") { it.take(40) }
        val flag = if (looksEncrypted(names)) " [tên có vẻ băm/mã hóa]" else ""
        val more = if (names.complete) "" else "+"
        val startup = Xp3Index.hasRootStartup(names)?.toString() ?: "?"
        val incomplete = if (names.complete) "" else " chỉ mục chưa hoàn chỉnh"
        return "${file.name} ${mb}MB$embedded: ${names.names.size}$more mục, startup.tjs@gốc=$startup$incomplete, mẫu: $sample$flag".take(280)
    }
}
