package vn.aow.monika.runner

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Nhận diện và quy đổi tay cầm thật (V120). Thuần logic, không giữ Context nên test được trên JVM.
 * Dùng chung cho `RetroActivity` và `AzaharActivity`; J2ME có bảng riêng trong `KeyMapper`.
 */
object GamepadInput {
    /** Dưới ngưỡng này cần analog coi như đứng yên (chống trôi cần). */
    const val STICK_DEADZONE = 0.2f
    private const val TRIGGER_ON = 0.55f
    private const val TRIGGER_OFF = 0.35f

    private fun hasSource(source: Int, flag: Int) = source and flag == flag

    /** Nguồn là tay cầm: nút, D-pad hoặc cần analog. Tay cầm thường báo `GAMEPAD` hoặc `JOYSTICK` (có khi cả hai). */
    fun isPadSource(source: Int): Boolean =
        hasSource(source, InputDevice.SOURCE_GAMEPAD) ||
            hasSource(source, InputDevice.SOURCE_DPAD) ||
            hasSource(source, InputDevice.SOURCE_JOYSTICK)

    /** Mã `KEYCODE_BUTTON_A`..`KEYCODE_BUTTON_MODE`: luôn là nút tay cầm, kể cả khi tay cầm rẻ tiền báo nguồn bàn phím. */
    fun isPadButtonCode(keyCode: Int): Boolean =
        keyCode in KeyEvent.KEYCODE_BUTTON_A..KeyEvent.KEYCODE_BUTTON_MODE

    /** Phím cần chuyển vào game: nút tay cầm, hoặc bất kỳ phím nào từ nguồn tay cầm. BACK luôn để hệ thống xử lý. */
    fun isPadKey(source: Int, keyCode: Int): Boolean =
        keyCode != KeyEvent.KEYCODE_BACK && (isPadSource(source) || isPadButtonCode(keyCode))

    /** Sự kiện chuyển động từ cần/hat của tay cầm (khác chuột, cảm ứng). */
    fun isPadMotion(source: Int): Boolean = hasSource(source, InputDevice.SOURCE_JOYSTICK)

    /** Giá trị trục lúc một MotionEvent đến. */
    data class Axes(
        val hatX: Float = 0f, val hatY: Float = 0f,
        val leftX: Float = 0f, val leftY: Float = 0f,
        val rightX: Float = 0f, val rightY: Float = 0f,
        val triggerL: Float = 0f, val triggerR: Float = 0f,
    )

    /** Cần phải = `AXIS_Z`/`AXIS_RZ` (cách Android gán cho Xbox, PS4, 8BitDo). Cò = `LTRIGGER`/`RTRIGGER`, có tay cầm báo `BRAKE`/`GAS`. */
    fun axesOf(e: MotionEvent): Axes = Axes(
        hatX = e.getAxisValue(MotionEvent.AXIS_HAT_X), hatY = e.getAxisValue(MotionEvent.AXIS_HAT_Y),
        leftX = e.getAxisValue(MotionEvent.AXIS_X), leftY = e.getAxisValue(MotionEvent.AXIS_Y),
        rightX = e.getAxisValue(MotionEvent.AXIS_Z), rightY = e.getAxisValue(MotionEvent.AXIS_RZ),
        triggerL = maxOf(e.getAxisValue(MotionEvent.AXIS_LTRIGGER), e.getAxisValue(MotionEvent.AXIS_BRAKE)),
        triggerR = maxOf(e.getAxisValue(MotionEvent.AXIS_RTRIGGER), e.getAxisValue(MotionEvent.AXIS_GAS)),
    )

    /** Thay đổi trạng thái sau khi lọc: chỉ phát khi giá trị đổi để khỏi tràn hàng đợi GL (tay cầm báo >100 lần/giây). */
    sealed interface Change {
        /** D-pad dạng hat: x, y ∈ {-1, 0, 1}, y dương = xuống; [prevX]/[prevY] là hướng trước đó. */
        data class Hat(val x: Int, val y: Int, val prevX: Int = 0, val prevY: Int = 0) : Change
        data class Stick(val right: Boolean, val x: Float, val y: Float) : Change
        /** Cò L2/R2 dạng trục, quy về nút. */
        data class Trigger(val right: Boolean, val down: Boolean) : Change
    }

    class Tracker {
        private var hatX = 0
        private var hatY = 0
        private var left = 0f to 0f
        private var right = 0f to 0f
        private var triggerL = false
        private var triggerR = false

        fun update(a: Axes): List<Change> {
            val out = ArrayList<Change>(4)
            val hx = a.hatX.roundToInt().coerceIn(-1, 1)
            val hy = a.hatY.roundToInt().coerceIn(-1, 1)
            if (hx != hatX || hy != hatY) { out += Change.Hat(hx, hy, hatX, hatY); hatX = hx; hatY = hy }
            val l = deadzone(a.leftX, a.leftY)
            if (l != left) { left = l; out += Change.Stick(false, l.first, l.second) }
            val r = deadzone(a.rightX, a.rightY)
            if (r != right) { right = r; out += Change.Stick(true, r.first, r.second) }
            val tl = hysteresis(triggerL, a.triggerL)
            if (tl != triggerL) { triggerL = tl; out += Change.Trigger(false, tl) }
            val tr = hysteresis(triggerR, a.triggerR)
            if (tr != triggerR) { triggerR = tr; out += Change.Trigger(true, tr) }
            return out
        }

        /** Tay cầm rút/mất tiêu điểm: đưa mọi trục về giữa và nhả cò, trả những thay đổi cần phát. */
        fun reset(): List<Change> = update(Axes())

        private fun deadzone(x: Float, y: Float): Pair<Float, Float> =
            if (hypot(x, y) < STICK_DEADZONE) 0f to 0f else x to y

        private fun hysteresis(down: Boolean, v: Float): Boolean = if (down) v > TRIGGER_OFF else v > TRIGGER_ON
    }
}
