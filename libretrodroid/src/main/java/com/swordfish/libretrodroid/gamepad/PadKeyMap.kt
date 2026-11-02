/*
 *     Copyright (C) 2019  Filippo Scognamiglio
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

package com.swordfish.libretrodroid.gamepad

import android.view.KeyEvent

/**
 * Aow Monika (V159): **một chỗ duy nhất** quyết định nút mặt (A/B/X/Y) rơi vào nút nào của lõi.
 *
 * Tay cầm Android đặt nút theo VỊ TRÍ: dưới = `BUTTON_A`, phải = `BUTTON_B`, trái = `BUTTON_X`, trên = `BUTTON_Y`.
 * RetroPad của libretro cũng theo vị trí nhưng lệch một nấc: `JOYPAD_B` ở đáy, `JOYPAD_A` bên phải,
 * `JOYPAD_Y` bên trái, `JOYPAD_X` trên. `input.cpp` (`convertAndroidToLibretroKey`) chỉ đổi tên mã → `JOYPAD_*`,
 * **không hoán**. Vì vậy muốn nút dưới cùng ra `JOYPAD_B` thì phải hoán A↔B và X↔Y ngay trước khi gửi.
 *
 * Các lõi đang dùng trong app đều đặt nút ĐÁY ở `JOYPAD_B`, nên chỉ cần đúng một phép hoán vị trí này:
 * - GB (gambatte): A = `JOYPAD_A`, B = `JOYPAD_B`.
 * - GBA (mgba), NES (fceumm/nestopia), SNES (snes9x), NDS (melonDS/desmume): như trên.
 * - PSP (ppsspp): × = `JOYPAD_B` (đáy), ○ = `JOYPAD_A` (phải), □ = `JOYPAD_Y` (trái), △ = `JOYPAD_X` (trên).
 * - Dreamcast (flycast): A = `JOYPAD_B` (đáy), B = `JOYPAD_A` (phải), X = `JOYPAD_Y` (trái), Y = `JOYPAD_X` (trên).
 * - Mega Drive (genesis_plus_gx): B = `JOYPAD_B` (đáy), C = `JOYPAD_A` (phải), A = `JOYPAD_Y` (trái).
 * - N64 (mupen64plus_next, parallel_n64): A = `JOYPAD_B` (đáy), B = `JOYPAD_Y` (trái).
 * Không hệ nào cần bảng riêng hay "không hoán".
 *
 * Trước V159 việc hoán chỉ nằm ở `GLRetroView.onKeyDown`, còn overlay và tay cầm thật gửi mã thô → ba đường lệch nhau.
 * Nay cả ba đều đi qua `GLRetroView.sendPadKey` dùng hàm thuần này.
 */
object PadKeyMap {
    /**
     * Mã phím gửi cho lõi (không gian `KeyEvent.KEYCODE_*`, native tự đổi sang `RETRO_DEVICE_ID_JOYPAD_*`).
     * Nút ngoài A/B/X/Y (D-pad, L/R, START, SELECT, L3/R3) giữ nguyên.
     */
    fun toNativeKey(keyCode: Int): Int = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_BUTTON_B
        KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BUTTON_A
        KeyEvent.KEYCODE_BUTTON_X -> KeyEvent.KEYCODE_BUTTON_Y
        KeyEvent.KEYCODE_BUTTON_Y -> KeyEvent.KEYCODE_BUTTON_X
        else -> keyCode
    }
}
