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
        input.release(-10f, 10f)
        assertTrue(calls.isEmpty())
        assertEquals(3, queue.size)
        queue.forEach { it() }
        assertEquals(listOf(-0.5f to 0.25f, 0.75f to -0.25f, -10f to 10f), calls)
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

    @Test fun queuedCancelThenPauseReleasesDeliveredTouchBeforeNativePause() {
        var ready = true
        val queue = mutableListOf<() -> Unit>()
        val calls = mutableListOf<String>()
        val input = EmulationTouchInput({ ready }, { queue += it }, { x, y -> calls += "$x,$y" }, { true })
        input.send(0.25f, 0.5f)
        queue.removeAt(0)()
        input.send(0.75f, 0.5f)
        input.release(-10f, 10f) // ACTION_CANCEL đang đợi GL.
        ready = false
        input.pause(-10f, 10f)
        queue += { calls += "native-pause" }
        assertEquals(listOf("0.25,0.5"), calls) // UI chưa gọi JNI hoặc chờ queue.
        queue.forEach { it() }
        assertEquals(listOf("0.25,0.5", "-10.0,10.0", "native-pause"), calls)
    }

    @Test fun pauseWithoutCancelReleasesAndQuickResumeCannotReviveQueuedMove() {
        var ready = true
        val queue = mutableListOf<() -> Unit>()
        val calls = mutableListOf<Pair<Float, Float>>()
        val input = EmulationTouchInput({ ready }, { queue += it }, { x, y -> calls += x to y }, { true })
        input.send(0f, 0f)
        queue.removeAt(0)()
        input.send(0.8f, 0.8f)
        ready = false
        input.pause(-10f, 10f)
        ready = true
        queue.forEach { it() }
        assertEquals(listOf(0f to 0f, -10f to 10f), calls)
    }

    @Test fun releaseAfterAbortOrDestroyNeverCallsNative() {
        var alive = true
        val queue = mutableListOf<() -> Unit>()
        val calls = mutableListOf<Pair<Float, Float>>()
        val input = EmulationTouchInput({ true }, { queue += it }, { x, y -> calls += x to y }, { alive })
        input.send(0f, 0f)
        queue.removeAt(0)()
        input.release(-10f, 10f)
        alive = false
        queue.single()()
        assertEquals(listOf(0f to 0f), calls)
    }
}
