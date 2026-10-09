package vn.aow.monika.ui.controls

/** Chỉ là thiết lập chung; bố cục từng hệ/game thuộc V70c. */
data class ControllerOptions(
    val haptic: HapticLevel = HapticLevel.MEDIUM,
    val pressAnimation: Boolean = true,
    val size: Float = 1f,
    val opacity: Float = 1f,
    val labels: Boolean = true,
) {
    fun bounded() = copy(
        size = size.takeIf { it.isFinite() }?.coerceIn(.7f, 1.4f) ?: 1f,
        opacity = opacity.takeIf { it.isFinite() }?.coerceIn(.2f, 1f) ?: 1f,
    )
}

enum class HapticLevel(val amplitude: Int, val durationMillis: Long) {
    OFF(0, 0), LIGHT(60, 8), MEDIUM(130, 12), STRONG(220, 18);
    companion object {
        fun stored(value: String?) = entries.firstOrNull { it.name == value } ?: MEDIUM
    }
}

/** D-pad giữ nguyên ngưỡng/đường chéo và mã Android của tay cầm cũ. */
internal fun directionKeys(dx: Float, dy: Float, width: Float): Set<Int> {
    val dead = width * .12f
    return buildSet {
        if (kotlin.math.abs(dx) > dead && kotlin.math.abs(dx) > kotlin.math.abs(dy) * .4f)
            add(if (dx > 0) android.view.KeyEvent.KEYCODE_DPAD_RIGHT else android.view.KeyEvent.KEYCODE_DPAD_LEFT)
        if (kotlin.math.abs(dy) > dead && kotlin.math.abs(dy) > kotlin.math.abs(dx) * .4f)
            add(if (dy > 0) android.view.KeyEvent.KEYCODE_DPAD_DOWN else android.view.KeyEvent.KEYCODE_DPAD_UP)
    }
}

internal fun stickValue(value: Float) = if (kotlin.math.abs(value) < .12f) 0f else value.coerceIn(-1f, 1f)
