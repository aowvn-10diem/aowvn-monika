package vn.aow.monika.azahar

import android.view.KeyEvent
import org.citra.citra_emu.NativeLibrary.ButtonType
import vn.aow.monika.runner.GamepadInput

/** Bảng map tay cầm thật → nút 3DS của Azahar (V120). Tách khỏi Activity để test được trên JVM. */
object AzaharPadMap {
    /** Nút theo VỊ TRÍ: nút dưới của Android (A) = B của 3DS. Trả -1 nếu không có nút tương ứng. */
    fun button(keyCode: Int): Int = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> ButtonType.BUTTON_B
        KeyEvent.KEYCODE_BUTTON_B -> ButtonType.BUTTON_A
        KeyEvent.KEYCODE_BUTTON_X -> ButtonType.BUTTON_Y
        KeyEvent.KEYCODE_BUTTON_Y -> ButtonType.BUTTON_X
        KeyEvent.KEYCODE_BUTTON_L1 -> ButtonType.TRIGGER_L
        KeyEvent.KEYCODE_BUTTON_R1 -> ButtonType.TRIGGER_R
        KeyEvent.KEYCODE_BUTTON_L2 -> ButtonType.BUTTON_ZL
        KeyEvent.KEYCODE_BUTTON_R2 -> ButtonType.BUTTON_ZR
        KeyEvent.KEYCODE_BUTTON_START -> ButtonType.BUTTON_START
        KeyEvent.KEYCODE_BUTTON_SELECT -> ButtonType.BUTTON_SELECT
        KeyEvent.KEYCODE_BUTTON_MODE -> ButtonType.BUTTON_HOME
        KeyEvent.KEYCODE_DPAD_UP -> ButtonType.DPAD_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> ButtonType.DPAD_DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> ButtonType.DPAD_LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> ButtonType.DPAD_RIGHT
        else -> -1
    }

    /** Một thay đổi trục → các (nút, đang nhấn) hoặc (cần, x, y) cần gửi cho lõi. */
    sealed interface Out {
        data class Press(val button: Int, val down: Boolean) : Out
        data class Stick(val id: Int, val x: Float, val y: Float) : Out
    }

    /**
     * Hat → D-pad: nhả hướng cũ trước, nhấn hướng mới sau.
     * Cò dạng trục → ZL/ZR. Cần trái = STICK_LEFT, cần phải = STICK_C (cần C của New 3DS).
     */
    fun out(c: GamepadInput.Change): List<Out> = when (c) {
        is GamepadInput.Change.Hat -> buildList {
            if (c.prevX != c.x) {
                if (c.prevX < 0) add(Out.Press(ButtonType.DPAD_LEFT, false))
                if (c.prevX > 0) add(Out.Press(ButtonType.DPAD_RIGHT, false))
                if (c.x < 0) add(Out.Press(ButtonType.DPAD_LEFT, true))
                if (c.x > 0) add(Out.Press(ButtonType.DPAD_RIGHT, true))
            }
            if (c.prevY != c.y) {
                if (c.prevY < 0) add(Out.Press(ButtonType.DPAD_UP, false))
                if (c.prevY > 0) add(Out.Press(ButtonType.DPAD_DOWN, false))
                if (c.y < 0) add(Out.Press(ButtonType.DPAD_UP, true))
                if (c.y > 0) add(Out.Press(ButtonType.DPAD_DOWN, true))
            }
        }
        is GamepadInput.Change.Stick -> listOf(Out.Stick(if (c.right) ButtonType.STICK_C else ButtonType.STICK_LEFT, c.x, c.y))
        is GamepadInput.Change.Trigger -> listOf(Out.Press(if (c.right) ButtonType.BUTTON_ZR else ButtonType.BUTTON_ZL, c.down))
    }
}
