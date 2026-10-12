package com.swordfish.libretrodroid

// Aow Monika: không gọi JNI hoặc chờ luồng giả lập từ luồng UI; bỏ chạm trong lúc nạp.
class EmulationTouchInput(
    private val ready: () -> Boolean,
    private val enqueue: (() -> Unit) -> Unit,
    private val deliver: (Float, Float) -> Unit,
    private val canRelease: () -> Boolean = ready,
) {
    @Volatile private var generation = 0
    // Aow Monika: chỉ queue GL đọc/ghi trạng thái chạm đã thật sự chuyển tới JNI.
    private var pressed = false

    fun send(x: Float, y: Float) {
        if (!ready()) return
        val acceptedGeneration = generation
        enqueue {
            if (ready() && acceptedGeneration == generation) {
                deliver(x, y)
                pressed = true
            }
        }
    }

    fun release(x: Float, y: Float) {
        // Aow Monika: nhả không phụ thuộc cổng khung hình; pause vẫn phải nhả chạm đã nhận.
        enqueue {
            if (pressed && canRelease()) deliver(x, y)
            pressed = false
        }
    }

    fun pause(x: Float, y: Float) {
        generation++ // Aow Monika: chạm cũ không được sống lại sau resume nhanh.
        release(x, y)
    }
}
