/*
 *     Copyright (C) 2022  Filippo Scognamiglio
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.swordfish.libretrodroid

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Aow Monika: một lời gọi cần chạy trên GLThread rồi trả kết quả về thread gọi (thường là UI Thread).
 * Không dùng Android API nên kiểm được bằng unit test JVM.
 */
internal class EmulationThreadTask<T> {

    private val latch = CountDownLatch(1)

    @Volatile
    private var value: T? = null

    /** Ghi kết quả trước khi mở latch để thread đang chờ luôn đọc được giá trị đã hoàn tất. */
    fun complete(result: T) {
        value = result
        latch.countDown()
    }

    /**
     * Chờ tối đa [timeoutMillis].
     * Trả `false` khi GLThread chưa xong trong hạn — nơi gọi phải tự trả giá trị dự phòng,
     * tuyệt đối không chờ vô hạn trên Main Thread (nguồn ANR khi GLThread biên dịch shader/nạp state).
     * Bị ngắt thì ghi nhận rồi chờ tiếp cho đủ hạn, cuối cùng trả lại cờ ngắt cho thread gọi.
     */
    fun await(timeoutMillis: Long): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        var interrupted = false
        try {
            while (true) {
                if (latch.count == 0L) return true
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0L) return false
                try {
                    return latch.await(remaining, TimeUnit.NANOSECONDS)
                } catch (e: InterruptedException) {
                    interrupted = true
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt()
        }
    }

    /** Chỉ gọi sau khi [await] trả `true`. */
    @Suppress("UNCHECKED_CAST")
    fun result(): T = value as T
}

internal object EmulationThread {
    const val THREAD_NAME_PREFIX = "GLThread"

    /** Hạn chờ tối đa cho một lời gọi đồng bộ sang GLThread. */
    const val CALL_TIMEOUT_MS = 2_000L

    fun isEmulationThread(threadName: String): Boolean = threadName.startsWith(THREAD_NAME_PREFIX)
}
