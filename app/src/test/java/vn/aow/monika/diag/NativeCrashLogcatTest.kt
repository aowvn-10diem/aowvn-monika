package vn.aow.monika.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeCrashLogcatTest {
    private fun line(time: String, pid: Int, tag: String, message: String = "message") =
        time + "   " + pid + "  800 F " + tag + "   : " + message

    private fun select(lines: List<String>, death: Long = 1_000_000L, pid: Int = 42, max: Int = 220) =
        NativeCrashLogcat.select(lines.asSequence(), pid, death, max)

    @Test fun keepsPidLogsAndDebugFromAnotherPid() {
        val own = line("950.000", 42, "libc", "Fatal signal 11")
        val debug = line("1000.001", 800, "DEBUG", "#00 pc 00001234 /data/app/libmkxp-z.so (BuildId: abc)")
        val anotherTag = line("1000.001", 800, "ActivityManager")
        val oldDebug = line("994.999", 800, "DEBUG")
        assertEquals(listOf(own, debug), select(listOf(own, anotherTag, oldDebug, debug)))
    }

    @Test fun includesFiveSecondBoundariesAndExcludesOutsideMilliseconds() {
        val before = line("995.000", 800, "DEBUG")
        val after = line("1005.000", 800, "DEBUG")
        assertEquals(listOf(before, after), select(listOf(
            line("994.999", 800, "DEBUG"), before, after, line("1005.001", 800, "DEBUG"),
        )))
    }

    @Test fun epochWindowCrossesNewYearWithoutGuessingDateOrTimezone() {
        val before = line("1609459199.999", 800, "DEBUG")
        val after = line("1609459200.001", 800, "DEBUG")
        assertEquals(listOf(before, after), select(listOf(before, after), death = 1_609_459_200_000L))
    }

    @Test fun matchesExactPidAndDebugTag() {
        val own = line("1000.000", 42, "Monika")
        assertEquals(listOf(own), select(listOf(
            line("1000.000", 4242, "Monika"),
            line("1000.000", 800, "DEBUGGER"), own,
        )))
    }

    @Test fun ignoresBrokenRowsAndOverflowingTimestamps() {
        assertTrue(select(listOf(
            "--------- beginning of crash",
            "bad timestamp 800 800 F DEBUG : stack",
            line("99999999999999999999.000", 800, "DEBUG"),
            line("9223372036854775.999", 800, "DEBUG"),
        )).isEmpty())
    }

    @Test fun missingDebugAccessStillKeepsPidLogs() {
        val own = line("1000.000", 42, "libc", "Fatal signal 11")
        assertEquals(listOf(own), select(listOf(own)))
        assertEquals(listOf(own), select(listOf(own, line("0.000", 800, "DEBUG")), death = 0))
    }

    @Test fun acceptsFractionPrecisionWithoutFloatingPointBoundaryErrors() {
        val short = line("995.0", 800, "DEBUG")
        val micros = line("1005.000000", 800, "DEBUG")
        assertEquals(listOf(short, micros), select(listOf(short, micros, line("1005.001000", 800, "DEBUG"))))
    }

    @Test fun keepsExistingLineAndCountLimits() {
        val older = line("999.000", 42, "Monika")
        val stack = line("1000.000", 800, "DEBUG", "x".repeat(400))
        val last = line("1001.000", 800, "DEBUG")
        assertEquals(listOf(stack.take(300), last), select(listOf(older, stack, last), max = 2))
        assertTrue(select(listOf(last), max = 0).isEmpty())
    }
}
