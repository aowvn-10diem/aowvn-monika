package com.swordfish.libretrodroid

// Aow Monika: không gọi JNI hoặc chờ luồng giả lập từ luồng UI; bỏ chạm trong lúc nạp.
class EmulationTouchInput(
    private val ready: () -> Boolean,
    private val enqueue: (() -> Unit) -> Unit,
    private val deliver: (Float, Float) -> Unit,
) {
    fun send(x: Float, y: Float) {
        if (!ready()) return
        enqueue { if (ready()) deliver(x, y) }
    }
}
