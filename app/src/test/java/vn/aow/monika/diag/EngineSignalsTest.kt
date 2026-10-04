package vn.aow.monika.diag
import org.junit.Assert.*
import org.junit.Test
class EngineSignalsTest {
    @Test fun patternsAreOptInAndLiteral() {
        val lines = listOf("Ruby RuntimeError monika-ci", "normal frame", "log A.*B")
        assertTrue(EngineSignals.matched(lines, emptyList()).isEmpty())
        assertEquals(listOf(lines[0]), EngineSignals.matched(lines, listOf("MONIKA-CI")))
        assertEquals(listOf(lines[2]), EngineSignals.matched(lines, listOf("A.*B")))
    }
    @Test fun oneLitOrTransparentPointIsNotBlack() {
        assertTrue(EngineSignals.isBlack(IntArray(64 * 36) { 0xff000000.toInt() }))
        assertFalse(EngineSignals.isBlack(intArrayOf(0xff000000.toInt(), 0xfff28c28.toInt())))
        assertFalse(EngineSignals.isBlack(intArrayOf(0)))
        assertFalse(EngineSignals.isBlack(intArrayOf()))
    }
    @Test fun requiresThirtySecondsContinuousValidFramesAndResetOnResume() {
        val t = BlackFrameTracker()
        assertFalse(t.observe(true, 10_000)); assertFalse(t.observe(true, 30_000))
        assertTrue(t.observe(true, 40_000)); assertFalse(t.observe(true, 50_000))
        t.reset(); assertFalse(t.observe(true, 100_000)); assertFalse(t.observe(null, 120_000))
        assertFalse(t.observe(true, 130_000)); assertFalse(t.observe(false, 160_000))
        assertFalse(t.observe(true, 170_000)); assertTrue(t.observe(true, 200_000))
    }
}
