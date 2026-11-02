package vn.aow.monika.runner

import android.view.KeyEvent
import com.swordfish.libretrodroid.gamepad.PadKeyMap
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * V159: một bảng ánh xạ nút mặt cho mọi hệ. Overlay và tay cầm thật được kiểm trực tiếp; `onKeyDown` gọi
 * cùng `GLRetroView.sendPadKey` nên đi đúng một đường với tay cầm thật (không lặp assertion giả).
 *
 * Căn cứ thứ tự nút của lõi (đọc mã nguồn từng lõi, xem PR):
 * - gambatte `libgambatte/libretro/libretro.cpp`: `JOYPAD_A` = A, `JOYPAD_B` = B (B ở đáy).
 * - mgba `src/platform/libretro/libretro.c`: mô tả `JOYPAD_A` = "A", `JOYPAD_B` = "B".
 * - fceumm `src/drivers/libretro/libretro.c`: `JOYPAD_A` = JOY_A, `JOYPAD_B` = JOY_B.
 * - snes9x `libretro/libretro.cpp`: `JOYPAD_B` = "B", `JOYPAD_A` = "A", `JOYPAD_Y` = "Y", `JOYPAD_X` = "X".
 * - melonDS `src/libretro/libretro.cpp` / desmume: `JOYPAD_A` = "A", `JOYPAD_B` = "B".
 * - ppsspp `libretro/libretro.cpp`: `JOYPAD_B` = Cross (×, đáy), `JOYPAD_A` = Circle (○, phải),
 *   `JOYPAD_Y` = Square (□, trái), `JOYPAD_X` = Triangle (△, trên).
 * - flycast `core/libretro/libretro.cpp`: `JOYPAD_B` = DC A (đáy), `JOYPAD_A` = DC B (phải),
 *   `JOYPAD_Y` = DC X (trái), `JOYPAD_X` = DC Y (trên).
 * - genesis_plus_gx `libretro/libretro.c`: `JOYPAD_Y` = "A" (trái), `JOYPAD_B` = "B" (đáy), `JOYPAD_A` = "C" (phải).
 * - mupen64plus_next/parallel_n64 `emulate_game_controller_via_libretro.c`: `JOYPAD_B` = "A Button" (đáy),
 *   `JOYPAD_Y` = "B Button" (trái). Không có hệ nào dùng thứ tự ngược lại.
 *
 * Thuần JVM; chưa thử tay cầm thật trên máy [CHƯA KIỂM].
 */
class PadKeyMapTest {

    // ---- Bảng ánh xạ từng hệ: nhãn nút → nút lõi ----

    @Test fun bangAnhXaTungHe() {
        val cases = listOf(
            // GB / NES / GBA: B ở đáy, A bên phải.
            Triple("gambatte", "B", "B"), Triple("gambatte", "A", "A"),
            Triple("fceumm", "B", "B"), Triple("fceumm", "A", "A"),
            Triple("nestopia", "B", "B"), Triple("nestopia", "A", "A"),
            Triple("mgba", "B", "B"), Triple("mgba", "A", "A"),
            // SNES / NDS: đủ 4 nút.
            Triple("snes9x", "B", "B"), Triple("snes9x", "A", "A"),
            Triple("snes9x", "Y", "Y"), Triple("snes9x", "X", "X"),
            Triple("melonds", "B", "B"), Triple("melonds", "A", "A"),
            Triple("melonds", "Y", "Y"), Triple("melonds", "X", "X"),
            Triple("desmume", "B", "B"), Triple("desmume", "A", "A"),
            // PSP: nút đáy là × (JOYPAD_B), bên phải là ○ (JOYPAD_A).
            Triple("ppsspp", "×", "B"), Triple("ppsspp", "○", "A"),
            Triple("ppsspp", "□", "Y"), Triple("ppsspp", "△", "X"),
            // Dreamcast: A ở đáy (JOYPAD_B), B bên phải (JOYPAD_A), X trái (JOYPAD_Y), Y trên (JOYPAD_X).
            Triple("flycast", "A", "B"), Triple("flycast", "B", "A"),
            Triple("flycast", "X", "Y"), Triple("flycast", "Y", "X"),
            // Mega Drive: A trái (JOYPAD_Y), B đáy (JOYPAD_B), C phải (JOYPAD_A).
            Triple("genesis_plus_gx", "A", "Y"), Triple("genesis_plus_gx", "B", "B"),
            Triple("genesis_plus_gx", "C", "A"),
            // N64: A ở đáy (JOYPAD_B), B bên trái (JOYPAD_Y).
            Triple("mupen64plus_next_gles3", "A", "B"), Triple("mupen64plus_next_gles3", "B", "Y"),
            Triple("parallel_n64", "A", "B"), Triple("parallel_n64", "B", "Y"),
        )
        for ((core, label, joypad) in cases) {
            assertEquals("$core nút $label", joypad, joypadOf(padFor(core, null), label))
        }
    }

    /** Mỗi hệ phải được `padFor` gán đúng bố cục — bảng trên chỉ có nghĩa nếu bước này đúng. */
    @Test fun moiHeDungDungBoCuc() {
        assertEquals(PadLayout.GB, padFor("gambatte", null))
        assertEquals(PadLayout.GB, padFor("fceumm", null))   // NES
        assertEquals(PadLayout.GB, padFor("nestopia", null)) // NES
        assertEquals(PadLayout.GBA, padFor("mgba", null))
        assertEquals(PadLayout.SNES, padFor("snes9x", null))
        assertEquals(PadLayout.NDS, padFor("melonds", null))
        assertEquals(PadLayout.NDS, padFor("desmume", null))
        assertEquals(PadLayout.PSP, padFor("ppsspp", null))
        assertEquals(PadLayout.N64, padFor("mupen64plus_next_gles3", null))
        assertEquals(PadLayout.N64, padFor("parallel_n64", null))
    }

    // ---- Overlay và tay cầm thật ra cùng một nút ----

    @Test fun overlayVaTayCamRaCungMotNut() {
        data class Row(val core: String, val label: String, val padKey: Int, val joypad: String)

        // padKey = nút THẬT ở đúng vị trí mà overlay vẽ nhãn đó (dưới = BUTTON_A, phải = BUTTON_B,
        // trái = BUTTON_X, trên = BUTTON_Y).
        val rows = listOf(
            Row("gambatte", "B", KeyEvent.KEYCODE_BUTTON_A, "B"),
            Row("gambatte", "A", KeyEvent.KEYCODE_BUTTON_B, "A"),
            Row("mgba", "B", KeyEvent.KEYCODE_BUTTON_A, "B"),
            Row("mgba", "A", KeyEvent.KEYCODE_BUTTON_B, "A"),
            Row("fceumm", "B", KeyEvent.KEYCODE_BUTTON_A, "B"),
            Row("fceumm", "A", KeyEvent.KEYCODE_BUTTON_B, "A"),
            Row("snes9x", "B", KeyEvent.KEYCODE_BUTTON_A, "B"),
            Row("snes9x", "A", KeyEvent.KEYCODE_BUTTON_B, "A"),
            Row("snes9x", "Y", KeyEvent.KEYCODE_BUTTON_X, "Y"),
            Row("snes9x", "X", KeyEvent.KEYCODE_BUTTON_Y, "X"),
            Row("melonds", "B", KeyEvent.KEYCODE_BUTTON_A, "B"),
            Row("melonds", "A", KeyEvent.KEYCODE_BUTTON_B, "A"),
            Row("melonds", "Y", KeyEvent.KEYCODE_BUTTON_X, "Y"),
            Row("melonds", "X", KeyEvent.KEYCODE_BUTTON_Y, "X"),
            Row("ppsspp", "×", KeyEvent.KEYCODE_BUTTON_A, "B"),
            Row("ppsspp", "○", KeyEvent.KEYCODE_BUTTON_B, "A"),
            Row("ppsspp", "□", KeyEvent.KEYCODE_BUTTON_X, "Y"),
            Row("ppsspp", "△", KeyEvent.KEYCODE_BUTTON_Y, "X"),
            Row("mupen64plus_next_gles3", "A", KeyEvent.KEYCODE_BUTTON_A, "B"),
            Row("mupen64plus_next_gles3", "B", KeyEvent.KEYCODE_BUTTON_X, "Y"),
            Row("flycast", "A", KeyEvent.KEYCODE_BUTTON_A, "B"),
            Row("flycast", "B", KeyEvent.KEYCODE_BUTTON_B, "A"),
            Row("flycast", "X", KeyEvent.KEYCODE_BUTTON_X, "Y"),
            Row("flycast", "Y", KeyEvent.KEYCODE_BUTTON_Y, "X"),
            Row("genesis_plus_gx", "A", KeyEvent.KEYCODE_BUTTON_X, "Y"),
            Row("genesis_plus_gx", "B", KeyEvent.KEYCODE_BUTTON_A, "B"),
            Row("genesis_plus_gx", "C", KeyEvent.KEYCODE_BUTTON_B, "A"),
        )

        for (r in rows) {
            val layout = padFor(r.core, null)
            val overlay = libretroButton(PadKeyMap.toNativeKey(PadFaceKeys.key(layout, r.label)))
            val pad = libretroButton(PadKeyMap.toNativeKey(r.padKey)) // dispatchKeyEvent / nhả phím

            assertEquals("overlay ${r.core} ${r.label}", r.joypad, overlay)
            assertEquals("tay cầm ${r.core} ${r.label}", r.joypad, pad)
            // `onKeyDown` không có entry point thuần chạy được trên JVM (GLRetroView là Android View).
            // Nó gọi `sendPadKey(event.keyCode)` — cùng đúng một biểu thức với tay cầm thật ở trên —
            // nên không thêm assertion lặp lại; đường này được bảo đảm bởi cửa duy nhất `GLRetroView.sendPadKey`.
        }
    }

    // ---- Ngoài nút mặt thì không đổi ----

    @Test fun phimKhacKhongBiHoan() {
        val keys = listOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT,
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2,
            KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR,
        )
        for (k in keys) assertEquals("$k", k, PadKeyMap.toNativeKey(k))
    }

    @Test fun hoanHaiChieuA_B_va_X_Y() {
        for ((from, to) in listOf(
            KeyEvent.KEYCODE_BUTTON_A to KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_B to KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_X to KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_Y to KeyEvent.KEYCODE_BUTTON_X,
        )) {
            assertEquals(to, PadKeyMap.toNativeKey(from))
            assertEquals(from, PadKeyMap.toNativeKey(to))
        }
    }

    // ---- tiện ích ----

    /** Nút lõi sau khi overlay bấm [label] của hệ [layout]. */
    private fun joypadOf(layout: PadLayout, label: String): String? =
        libretroButton(PadKeyMap.toNativeKey(PadFaceKeys.key(layout, label)))

    /** Đổi mã phím đã gửi thành tên nút lõi — khớp `convertAndroidToLibretroKey` trong `src/main/cpp/input.cpp`. */
    private fun libretroButton(nativeKeyCode: Int): String? = when (nativeKeyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> "A"
        KeyEvent.KEYCODE_BUTTON_B -> "B"
        KeyEvent.KEYCODE_BUTTON_X -> "X"
        KeyEvent.KEYCODE_BUTTON_Y -> "Y"
        else -> null
    }
}
