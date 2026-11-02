package vn.aow.monika.runner

import android.view.KeyEvent

/**
 * V159: bảng nhãn nút mặt → mã phím theo VỊ TRÍ, dùng chung cho overlay và test.
 *
 * Tay cầm Android đặt nút theo vị trí: đáy = `BUTTON_A`, phải = `BUTTON_B`, trái = `BUTTON_X`, trên = `BUTTON_Y`.
 * Overlay vẽ nhãn theo hệ (B/A của Nintendo, ×/○ của PSP…) nhưng gửi đúng mã của vị trí đó; việc hoán sang
 * nút của lõi nằm ở một chỗ duy nhất: `GLRetroView.sendPadKey` + `PadKeyMap`.
 *
 * Nhờ vậy bấm nút trên màn hình và bấm nút thật ở cùng vị trí luôn ra cùng một nút trong game.
 */
object PadFaceKeys {
    private const val BOTTOM = KeyEvent.KEYCODE_BUTTON_A
    private const val RIGHT = KeyEvent.KEYCODE_BUTTON_B
    private const val LEFT = KeyEvent.KEYCODE_BUTTON_X
    private const val TOP = KeyEvent.KEYCODE_BUTTON_Y

    /** Nintendo/RetroPad: B ở đáy, A bên phải, Y bên trái, X trên. */
    private val NINTENDO = mapOf("B" to BOTTOM, "A" to RIGHT, "Y" to LEFT, "X" to TOP)

    /** PlayStation/PSP: × ở đáy, ○ bên phải, □ bên trái, △ trên. */
    private val PLAYSTATION = mapOf("×" to BOTTOM, "○" to RIGHT, "□" to LEFT, "△" to TOP)

    /** Dreamcast: A ở đáy, B bên phải, X bên trái, Y trên (lõi flycast: A = JOYPAD_B, B = JOYPAD_A…). */
    private val DREAMCAST = mapOf("A" to BOTTOM, "B" to RIGHT, "X" to LEFT, "Y" to TOP)

    /** Mega Drive 3 nút: A trái, B giữa, C phải (lõi genesis_plus_gx: A = JOYPAD_Y, B = JOYPAD_B, C = JOYPAD_A). */
    private val GENESIS = mapOf("A" to LEFT, "B" to BOTTOM, "C" to RIGHT)

    /** N64: A ở đáy, B bên trái (mupen64plus_next/parallel_n64: A = JOYPAD_B, B = JOYPAD_Y). */
    private val N64 = mapOf("A" to BOTTOM, "B" to LEFT)

    /** Mã phím overlay gửi khi bấm nút có nhãn [label]. */
    fun key(layout: PadLayout, label: String): Int = table(layout).getValue(label)

    private fun table(layout: PadLayout): Map<String, Int> = when (layout) {
        PadLayout.GB, PadLayout.GBA, PadLayout.RPG -> NINTENDO
        PadLayout.NDS, PadLayout.SNES, PadLayout.N3DS -> NINTENDO
        PadLayout.PS, PadLayout.PSP -> PLAYSTATION
        PadLayout.DC -> DREAMCAST
        PadLayout.GEN -> GENESIS
        PadLayout.N64 -> N64
    }
}
