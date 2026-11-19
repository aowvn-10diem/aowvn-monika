package vn.aow.monika.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnrTraceTest {

    private val baKhungNative = listOf(
        "native: #00 pc 0000000000049d4c  /apex/com.android.runtime/lib64/bionic/libc.so (__epoll_pwait+8)",
        "native: #01 pc 0000000000061f10  /system/lib64/libart.so (art::Monitor::Wait+560)",
        "native: #02 pc 000000000002aa14  /system/lib64/libgui.so (android::Surface::dequeueBuffer+180)",
    )

    private val traceAnr = listOf(
        "----- pid 4242 at 2026-11-18 10:00:00 -----",
        "Cmd line: vn.aow.monika",
        "\"main\" prio=5 tid=1 Native",
        "  | group=\"main\" sCount=1 ucsCount=0 flags=1 obj=0x12c00000 self=0xb4000071",
        "  | sysTid=4242 nice=-10 cgrp=default sched=0/0 handle=0x7ffc0000",
        "  | state=S schedstat=( 1000000 2000000 30 ) utm=10 stm=5 core=0 HZ=100",
        "  | held mutexes=",
        "  native: #00 pc 0000000000049d4c  /apex/com.android.runtime/lib64/bionic/libc.so (__epoll_pwait+8)",
        "  native: #01 pc 0000000000061f10  /system/lib64/libart.so (art::Monitor::Wait+560)",
        "  at android.opengl.GLSurfaceView\$GLThread.guardedRun(GLSurfaceView.java:1723)",
        "  at android.opengl.GLSurfaceView\$GLThread.run(GLSurfaceView.java:1420)",
        "  at java.lang.Thread.run(Thread.java:1012)",
        "",
        "\"GLThread 321\" prio=5 tid=12 Native",
        "  native: #00 pc 0000000000022222  /system/lib64/libGLESv2.so (glDrawArrays+64)",
        "",
        "\"RenderThread\" prio=5 tid=13 Native",
        "  native: #00 pc 0000000000033333  /system/lib64/libEGL.so (eglSwapBuffers+120)",
    )

    /** Ba khung đầu (chữ ký bot) không đổi; khối main nằm sau để đọc được chặng. */
    @Test
    fun appendsMainThreadAfterExistingFrames() {
        val out = AnrTrace.appendMainThread(baKhungNative, traceAnr)

        assertEquals(baKhungNative, out.take(3))
        assertEquals("\"main\" prio=5 tid=1 Native", out[3])
        assertTrue(out.any { it.contains("GLSurfaceView\$GLThread.guardedRun") })
        assertTrue(out.any { it.contains("java.lang.Thread.run") })
    }

    /** Không lẫn khối của luồng khác vào phần nối thêm. */
    @Test
    fun stopsAtNextThread() {
        val out = AnrTrace.appendMainThread(baKhungNative, traceAnr)

        assertFalse(out.any { it.contains("GLThread 321") })
        assertFalse(out.any { it.contains("glDrawArrays") })
        assertFalse(out.any { it.contains("eglSwapBuffers") })
    }

    /** Thiếu khung (bot chưa đủ 3) → không nối, để chữ ký không đổi. */
    @Test
    fun noAppendWhenSignatureNotFull() {
        val hai = baKhungNative.take(2)

        assertEquals(hai, AnrTrace.appendMainThread(hai, traceAnr))
        assertEquals(emptyList<String>(), AnrTrace.appendMainThread(emptyList(), traceAnr))
    }

    /** Trace không có luồng main (bị cắt/không thu được) → giữ nguyên, không ném. */
    @Test
    fun noMainThreadKeepsInput() {
        val khongMain = listOf("----- pid 1 -----", "\"GLThread 7\" prio=5 tid=7 Native")

        assertEquals(baKhungNative, AnrTrace.appendMainThread(baKhungNative, khongMain))
    }

    /** Dòng tiêu đề đã có sẵn thì không lặp lại. */
    @Test
    fun deduplicatesExistingLines() {
        val daCo = baKhungNative + "\"main\" prio=5 tid=1 Native"
        val out = AnrTrace.appendMainThread(daCo, traceAnr)

        assertEquals(1, out.count { it == "\"main\" prio=5 tid=1 Native" })
        assertEquals(daCo, out.take(4))
    }

    /** Khối main dài bị cắt ở trần, vẫn bắt đầu bằng tiêu đề luồng. */
    @Test
    fun capsMainSectionLength() {
        val dai = listOf("\"main\" prio=5 tid=1 Native") +
            (1..50).map { "  at vn.aow.monika.Khung$it.chay(Khung.kt:$it)" }

        val out = AnrTrace.appendMainThread(baKhungNative, dai)

        assertEquals(AnrTrace.MAX_MAIN_LINES, out.size - baKhungNative.size)
        assertEquals("\"main\" prio=5 tid=1 Native", out[3])
    }

    /** Giữ dấu hiệu khoá đang bị giữ (hữu ích khi trace chỉ ra chờ mutex), bỏ dòng nhiễu. */
    @Test
    fun keepsHeldMutexAndDropsNoise() {
        val out = AnrTrace.appendMainThread(baKhungNative, traceAnr)

        assertTrue(out.any { it.trim() == "| held mutexes=" })
        assertFalse(out.any { it.contains("sCount=1") })
        assertFalse(out.any { it.contains("sysTid=4242") })
    }

    /** Mỗi dòng bị cắt ở trần ký tự, tránh phình `detail` (Worker nhận tối đa 20 000 ký tự). */
    @Test
    fun truncatesLongLines() {
        val dai = listOf(
            "\"main\" prio=5 tid=1 Native",
            "  at vn.aow.monika." + "X".repeat(AnrTrace.MAX_LINE_CHARS * 2) + ".chay(Tep.kt:1)",
        )

        val out = AnrTrace.appendMainThread(baKhungNative, dai)

        assertTrue(out.drop(3).all { it.length <= AnrTrace.MAX_LINE_CHARS })
    }
}
