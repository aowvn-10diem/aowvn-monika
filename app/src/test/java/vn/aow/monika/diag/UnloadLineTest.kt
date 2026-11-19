package vn.aow.monika.diag

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Hợp đồng dòng vệt `[unload]` do `libretrodroid/src/main/cpp/diag.cpp` dựng.
 *
 * Dòng đó vào báo cáo theo hai đường:
 *  - `crumbs` (`Breadcrumbs.kt`): giữ nguyên cả dòng;
 *  - `log` (`Diagnostics.logcat`, `Diagnostics.kt:380`): mỗi dòng bị cắt còn **300** ký tự.
 *
 * Nên hai điều phải luôn đúng, nếu không thì mất đúng trường quyết định của
 * `docs/loi/ppsspp-do-go-1911.md` §4.2:
 *  1. `core=` và `bid=` đứng **trước** danh sách thread ⇒ dù bị cắt vẫn còn;
 *  2. dòng tự giới hạn ≤ `kMaxLineChars` (290) ⇒ bình thường không bị cắt, và khi
 *     danh sách thread dài thì thread của lõi (đánh dấu `*`) vẫn nằm ở đầu.
 *
 * Test chạy trên JVM, không gọi mã C++; phần đọc `diag.cpp` để bám đúng hằng số
 * và định dạng thật (không phải bản sao trong đầu người viết test).
 */
class UnloadLineTest {

    private val bid = "cb9f14894ab06a4128ea252d7732b985f5ec3eab"
    private val core = "libppsspp_libretro.so"

    /** 300 là giới hạn cắt thật của `Diagnostics.logcat` (`Diagnostics.kt:380`). */
    private val logcatLineLimit = 300

    private fun nativeSource(): String? = listOf(
        "libretrodroid/src/main/cpp/diag.cpp",
        "../libretrodroid/src/main/cpp/diag.cpp",
    ).map(::File).firstOrNull { it.isFile }?.readText()

    // ---- Bản mô phỏng định dạng của diag.cpp (giữ đúng thứ tự và ngân sách) ----

    private data class Thread(val tid: String, val name: String, val isNew: Boolean)

    private fun looksLikeCoreThread(name: String) =
        name in setOf("Emu", "GPU", "Audio", "Render", "PPSSPP", "video", "Video") ||
            name.startsWith("core") || name.startsWith("Core")

    private fun head(stage: String) =
        "12:34:56.789 [unload] $stage core=$core bid=$bid dl=0x7f0a1c2d30 t=812345 "

    /** Dựng dòng đúng như `threadField()` trong `diag.cpp`: ưu tiên thread mới/tên lõi, cắt phần đuôi, ghi `(+N)`. */
    private fun buildLine(threads: List<Thread>, budgetChars: Int = 290): String {
        val prefix = head("dlclose-before")
        val sorted = threads.sortedWith(
            compareBy(
                { if (it.isNew) 0 else if (looksLikeCoreThread(it.name)) 1 else 2 },
                { it.tid.length },
                { it.tid },
            ),
        )
        if (sorted.isEmpty()) return prefix + "thr=0 []"

        val budget = (budgetChars - prefix.length - 1).coerceAtLeast(0)
        if (budget < 16) return prefix + "thr=${sorted.size} (+${sorted.size})"

        val out = StringBuilder("thr=${sorted.size} [")
        var used = out.length
        var included = 0
        for ((i, thread) in sorted.withIndex()) {
            val item = (if (thread.isNew) "*" else "") + thread.tid + ":" + thread.name
            val reserve = if (i + 1 < sorted.size) 10 else 0
            val extra = (if (included > 0) 1 else 0) + item.length
            if (used + extra + reserve > budget) break
            if (included > 0) out.append(',')
            out.append(item)
            used += extra
            included++
        }
        if (included < sorted.size) out.append(" (+${sorted.size - included})")
        out.append(']')
        return prefix + out
    }

    // ---- Test ----

    @Test
    fun `native source keeps core and build id ahead of thread list`() {
        val source = nativeSource()
        assumeTrue("không thấy libretrodroid/src/main/cpp/diag.cpp từ thư mục chạy test", source != null)

        // Trần danh sách đủ cho tiến trình :game (~30–40 thread).
        assertTrue("phải nâng trần thread lên 48", source!!.contains("kMaxThreads = 48"))
        // Dòng tự cắt dưới giới hạn 300 của Diagnostics.logcat.
        assertTrue("phải tự giới hạn dòng ở 290", source.contains("kMaxLineChars = 290"))
        // core= và bid= đứng liền nhau, trước dl=/t=/thr=.
        val headFormat = Regex("""snprintf\(head,[\s\S]{0,600}?\);""").find(source)?.value.orEmpty()
        assertTrue("không đọc được snprintf(head) trong diag.cpp", headFormat.contains("core=%s bid=%s"))
        assertTrue("core=/bid= phải đứng trước t=", headFormat.indexOf("core=%s") < headFormat.indexOf(" t="))
    }

    @Test
    fun `line with 40 threads keeps build id and core threads`() {
        val appThreads = (0 until 38).map { Thread("13%02d".format(it), "worker$it", isNew = false) }
        val coreThreads = listOf(
            Thread("1338", "Emu", isNew = true),
            Thread("1339", "GPU", isNew = true),
        )
        val line = buildLine(appThreads + coreThreads)

        assertTrue("dòng dài ${line.length} ký tự", line.length <= logcatLineLimit)
        assertTrue(line, line.contains("core=$core"))
        assertTrue(line, line.contains("bid=$bid"))
        // Thread lõi nằm ở đầu danh sách dù tid lớn nhất.
        assertTrue(line, line.contains("*1338:Emu"))
        assertTrue(line, line.contains("*1339:GPU"))
        // Phần bị cắt phải nói rõ số lượng.
        assertTrue(line, Regex("""\(\+\d+\)]""").containsMatchIn(line))
    }

    @Test
    fun `cut long line still keeps core and build id`() {
        // 48 thread (trần mới) nhưng không tự cắt: dòng vượt 300 ký tự đúng như khi danh
        // sách dài hơn ngân sách, rồi bị Diagnostics.logcat cắt. core=/bid= ở đầu nên còn.
        val threads = (0 until 46).map { Thread("14%02d".format(it), "worker$it", isNew = false) } +
            listOf(Thread("1446", "Emu", isNew = true), Thread("1447", "GPU", isNew = true))
        val longLine = buildLine(threads, budgetChars = 100_000)
        assertTrue("dòng phải dài hơn giới hạn: ${longLine.length}", longLine.length > logcatLineLimit)

        val cut = longLine.take(logcatLineLimit)
        assertTrue(cut, cut.length == logcatLineLimit)
        assertTrue("bị cắt mất bid=", cut.contains("bid=$bid"))
        assertTrue("bị cắt mất core=", cut.contains("core=$core"))
    }
}
