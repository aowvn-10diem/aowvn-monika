/*
 * Aow Monika (L1-anr): hai kiểu gọi sang GLThread, tách khỏi GLRetroView để kiểm được bằng JUnit thường.
 */
package com.swordfish.libretrodroid

/**
 * - [post]: việc **không cần kết quả** (đổi biến lõi, shader, tốc độ khung) → chỉ xếp hàng, KHÔNG chờ.
 *   JNI tương ứng giữ `coreLock` suốt `retro_run` của melondsds, nên chờ ở UI Thread là nguồn ANR.
 * - [runBounded]: việc **cần kết quả** → chờ tối đa [timeoutMs]; hết hạn trả [fallback], không chờ vô hạn.
 */
internal object EmulationCall {

    fun post(deliver: (() -> Unit) -> Unit, block: () -> Unit) {
        deliver(block)
    }

    fun <T> runBounded(
        deliver: (() -> Unit) -> Unit,
        timeoutMs: Long,
        fallback: () -> T,
        block: () -> T,
    ): T {
        val task = EmulationThreadTask<T>()
        deliver { task.complete(block()) }
        return if (task.await(timeoutMs)) task.result() else fallback()
    }
}
