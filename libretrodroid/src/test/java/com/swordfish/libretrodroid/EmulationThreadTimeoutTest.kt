package com.swordfish.libretrodroid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * V205 phần 2 — ANR `ffdfa861` (#318): `runOnEmulationThread` không được giữ Main Thread vô hạn
 * khi GLThread đang biên dịch shader/nạp state. Test dùng hàng đợi giả, không cần GL/Android.
 */
class EmulationThreadTimeoutTest {

    @Test fun returnsFallbackWhenGlThreadIsHeldBeyondDeadline() {
        val timeoutMs = AtomicLong(-1)
        val start = System.nanoTime()

        val value = runWithEmulationTimeout(
            enqueue = { /* GLThread bị giữ: việc không bao giờ chạy */ },
            timeoutMs = 120,
            fallback = "fallback",
            onTimeout = { timeoutMs.set(it) },
        ) { "result" }

        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertEquals("Phải trả fallback, không chờ vô hạn", "fallback", value)
        assertEquals("onTimeout phải nhận đúng hạn", 120L, timeoutMs.get())
        assertTrue("Phải trả về gần hạn 120 ms, thực tế $elapsedMs ms", elapsedMs < 1_000)
    }

    @Test fun returnsResultWhenGlThreadFinishesInTime() {
        val value = runWithEmulationTimeout(
            enqueue = { it() },
            timeoutMs = 2_000,
            fallback = "fallback",
            onTimeout = { },
        ) { "result" }

        assertEquals("Xong trong hạn thì trả kết quả thật", "result", value)
    }

    @Test fun lateResultAfterTimeoutDoesNotReplaceFallback() {
        val done = CountDownLatch(1)
        val start = System.nanoTime()

        val value = runWithEmulationTimeout(
            enqueue = { work ->
                thread(isDaemon = true) {
                    Thread.sleep(300) // GLThread xong muộn hơn hạn
                    work()
                    done.countDown()
                }
            },
            timeoutMs = 80,
            fallback = "fallback",
            onTimeout = { },
        ) { "late" }

        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertEquals("Quá hạn thì trả fallback", "fallback", value)
        assertTrue("Không chờ việc muộn, thực tế $elapsedMs ms", elapsedMs < 250)
        assertTrue("Việc muộn vẫn chạy xong, không crash", done.await(3, TimeUnit.SECONDS))
    }

    @Test fun failureFromEmulationThreadIsRethrown() {
        val failure = IllegalStateException("lõi lỗi")
        val thrown = runCatching {
            runWithEmulationTimeout<String>(
                enqueue = { it() },
                timeoutMs = 2_000,
                fallback = "fallback",
                onTimeout = { },
            ) { throw failure }
        }.exceptionOrNull()

        assertTrue("Lỗi phải được ném lại cho caller, nhận: $thrown", thrown === failure)
    }
}
