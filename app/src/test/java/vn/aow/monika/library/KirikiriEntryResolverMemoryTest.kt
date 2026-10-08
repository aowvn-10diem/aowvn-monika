package vn.aow.monika.library

import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** V64: ngân sách heap cố định, XP3 hoàn toàn tự sinh; không đo GC/WeakReference. */
class KirikiriEntryResolverMemoryTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun motVaHaiMuoiBonXp3DungCungNganSachHeap_vaGiuChanDoan() {
        val dir = tmp.newFolder("synthetic")
        val names = List(30_000) { "asset/%05d/".format(it) + "x".repeat(110) + ".png" }
        val archive = Xp3Fixture.xp3(dir, names, compressed = true)
        val first = File(dir, "part00.xp3")
        assertTrue(archive.renameTo(first))
        runProbe(dir, 1)
        // Cùng chỉ mục riêng lẻ hợp lệ, tăng số kho 24 lần trong cùng heap 64 MiB.
        for (i in 23 downTo 1) first.copyTo(File(dir, "part%02d.xp3".format(i)))
        runProbe(dir, 24)
    }

    private fun runProbe(dir: File, count: Int) {
        // Gradle worker không đặt toàn bộ test runtime vào java.class.path.
        // Lấy đúng các location cần cho probe, gồm classes app/test và Kotlin runtime.
        val classpath = listOf(Xp3HeapProbe::class.java, KirikiriEntryResolver::class.java,
            kotlin.Unit::class.java).map { File(it.protectionDomain.codeSource.location.toURI()).path }
            .distinct().joinToString(File.pathSeparator)
        val log = File(tmp.root, "heap-$count.log")
        val child = ProcessBuilder(File(System.getProperty("java.home"), "bin/java").path,
            "-Xmx64m", "-cp", classpath, Xp3HeapProbe::class.java.name, dir.path, count.toString())
            .redirectErrorStream(true).redirectOutput(log).start()
        val finished = child.waitFor(60, TimeUnit.SECONDS)
        if (!finished) child.destroyForcibly()
        assertTrue("probe timeout ($count XP3)", finished)
        assertEquals(log.readText(), 0, child.exitValue())
        assertTrue(log.readText().contains("HEAP_OK $count"))
    }
}

/** Chạy trong JVM con để giới hạn heap áp dụng cho resolver, không cho JVM test/fixture. */
object Xp3HeapProbe {
    @JvmStatic fun main(args: Array<String>) {
        val dir = File(args[0])
        val count = args[1].toInt()
        val result = KirikiriEntryResolver.resolve(dir) as EntryResolution.NotFound
        check(result.why == "đọc chỉ mục $count xp3, không có startup.tjs ở gốc, không có startup.tjs rời")
        check(result.details.size == minOf(count, 8))
        result.details.forEachIndexed { i, detail ->
            check(detail.startsWith("part%02d.xp3 ".format(i)))
            check(detail.contains("30000 mục"))
            check(detail.length <= 280)
        }
        println("HEAP_OK $count")
    }
}
