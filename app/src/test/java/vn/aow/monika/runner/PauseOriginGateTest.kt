/*
 * Aow Monika: test JUnit thuần cho cặp đăng ký/gỡ của đo lường pause (#350/#318).
 * Mục tiêu: `onStart`→`onStop` không nhân đôi / không gỡ hai lần callback
 * `ActivityLifecycleCallbacks` — nguồn rò rỉ Activity đã được tiền duyệt #656 nêu.
 * Không cần Android: gate chỉ gọi hai lambda được tiêm vào.
 */
package vn.aow.monika.runner

import org.junit.Assert.assertEquals
import org.junit.Test

class PauseOriginGateTest {

    private class Recorder {
        val events = mutableListOf<String>()
        val install: () -> Unit = { events += "install" }
        val remove: () -> Unit = { events += "remove" }
        fun gate() = PauseOriginGate(install, remove)
    }

    /** onStart gọi hai lần (start lại sau stop dở) chỉ đăng ký một lần. */
    @Test
    fun startTwiceInstallsOnce() {
        val r = Recorder()
        val gate = r.gate()

        gate.start()
        gate.start()

        assertEquals(listOf("install"), r.events)
    }

    /** stop hai lần chỉ gỡ một lần, và stop khi chưa start là no-op. */
    @Test
    fun stopIsPairedWithStartOnly() {
        val r = Recorder()
        val gate = r.gate()

        gate.stop()
        gate.start()
        gate.stop()
        gate.stop()

        assertEquals(listOf("install", "remove"), r.events)
    }

    /** Nhiều chu kỳ start/stop → đúng một cặp mỗi chu kỳ, đúng thứ tự. */
    @Test
    fun repeatedCyclesStayPaired() {
        val r = Recorder()
        val gate = r.gate()

        repeat(3) {
            gate.start()
            gate.stop()
        }

        assertEquals(listOf("install", "remove", "install", "remove", "install", "remove"), r.events)
    }

    /** install ném (Application từ chối) → onStart không chết, và stop vẫn gỡ để không sót. */
    @Test
    fun throwingInstallStillAllowsCleanup() {
        val events = mutableListOf<String>()
        val gate = PauseOriginGate(
            install = { events += "install"; throw IllegalStateException("Application từ chối") },
            remove = { events += "remove" },
        )

        gate.start() // không được ném
        gate.stop()

        assertEquals(listOf("install", "remove"), events)
    }

    /** remove ném cũng không làm chết onStop, và chu kỳ sau vẫn đăng ký lại được. */
    @Test
    fun throwingRemoveDoesNotBreakNextCycle() {
        val events = mutableListOf<String>()
        var cycles = 0
        val gate = PauseOriginGate(
            install = { events += "install" },
            remove = { cycles++; events += "remove"; if (cycles == 1) throw IllegalStateException("hỏng") },
        )

        gate.start()
        gate.stop() // không được ném
        gate.start()
        gate.stop()

        assertEquals(listOf("install", "remove", "install", "remove"), events)
    }
}
