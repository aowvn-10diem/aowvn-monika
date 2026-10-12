package vn.aow.monika.runner

import com.swordfish.libretrodroid.EmulationTouchInput
import org.junit.Assert.*
import org.junit.Test

class EmulationTouchInputTest {
    @Test fun loadingDropsTouchesWithoutCallingOrQueuingNative() {
        var queues = 0
        val input = EmulationTouchInput({ false }, { queues++; error("Không xếp chạm khi đang nạp") }, { _, _ -> error("JNI trên UI") })
        repeat(100) { input.send(0.25f, -0.5f) }
        assertEquals(0, queues)
    }

    @Test fun blockedGlThreadCannotBlockUiAndCoordinatesAndReleaseKeepOrder() {
        val queue = mutableListOf<() -> Unit>()
        val calls = mutableListOf<Pair<Float, Float>>()
        val input = EmulationTouchInput({ true }, { queue += it }, { x, y -> calls += x to y })
        // Giả lập GL đang bận: queueEvent chỉ giữ việc, không thực thi JNI hoặc chờ latch trên UI.
        input.send(-0.5f, 0.25f)
        input.send(0.75f, -0.25f)
        input.send(-1f, -1f)
        assertTrue(calls.isEmpty())
        assertEquals(3, queue.size)
        queue.forEach { it() }
        assertEquals(listOf(-0.5f to 0.25f, 0.75f to -0.25f, -1f to -1f), calls)
    }

    @Test fun queuedTouchIsDroppedIfGamePausesBeforeDelivery() {
        var ready = true
        val queue = mutableListOf<() -> Unit>()
        var calls = 0
        val input = EmulationTouchInput({ ready }, { queue += it }, { _, _ -> calls++ })
        input.send(0f, 0f)
        ready = false
        queue.single()()
        assertEquals(0, calls)
        ready = true
        input.send(0.5f, 0.5f)
        queue.last()()
        assertEquals(1, calls)
    }
}
