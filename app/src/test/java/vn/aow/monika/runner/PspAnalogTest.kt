package vn.aow.monika.runner

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.LibretroDroid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.aow.monika.Prefs
import vn.aow.monika.ui.TestApp
import vn.aow.monika.ui.controls.ControllerOptions
import vn.aow.monika.ui.controls.HapticLevel
import vn.aow.monika.ui.controls.stickValue
import vn.aow.monika.ui.theme.MonikaTheme

/**
 * V163: PSP (PPSSPP libretro) nhận núm analog trái qua `RETRO_DEVICE_ANALOG`.
 *
 * Đường đi đã có sẵn từ trước, test này khoá lại từng mắt: bố cục PSP → `VirtualPad` phát
 * `(STICK_LEFT, x, y)` → `RetroActivity.onMotion` đổi thành `MOTION_SOURCE_ANALOG_LEFT` → native
 * (`input.cpp`) nhân `0x7fff` và chỉ trả giá trị khi lõi hỏi `RETRO_DEVICE_ANALOG`.
 * JVM không chạy được native: phần native chỉ khoá bằng hằng số ở đây, còn núm thật trên máy thật là [CHƯA KIỂM].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34], qualifiers = "w851dp-h393dp-land-xxhdpi")
class PspAnalogTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val motion = mutableListOf<Triple<Int, Float, Float>>()

    @org.junit.Before fun setUp() {
        rule.mainClock.autoAdvance = false
        Prefs(rule.activity).controllerOptions = ControllerOptions(haptic = HapticLevel.OFF)
    }

    private fun pad(layout: PadLayout) {
        rule.setContent {
            MonikaTheme {
                Box(Modifier.fillMaxSize()) {
                    VirtualPad(
                        layout, InGameState(), { _, _ -> },
                        { src, x, y -> motion += Triple(src, x, y) },
                        Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
    }

    /** PSP mở game là dùng cần; kéo núm gửi đúng bảng trục âm / giữa / dương, nhả tay về (0,0). */
    @Test fun pspNumAnalogGuiBangTrucTrai() {
        pad(PadLayout.PSP)
        rule.onNodeWithText("Cần → D-pad").assertExists() // mặc định là cần, không phải D-pad
        val stick = rule.onNodeWithContentDescription("Cần analog").assertIsDisplayed()

        stick.performTouchInput {
            down(center) // giữa
            moveTo(center + Offset(width * 0.02f, 0f)) // trôi nhẹ dưới vùng chết
            moveTo(center + Offset(width * 0.45f, 0f)) // hết cỡ sang phải (vượt bán kính → chặn 1)
            moveTo(center + Offset(-width * 0.45f, 0f)) // hết cỡ sang trái
            moveTo(center + Offset(0f, -width * 0.45f)) // hết cỡ lên
            up()
        }
        rule.mainClock.advanceTimeBy(100)

        val expected = listOf(
            0f to 0f, // chạm giữa
            0f to 0f, // dưới vùng chết 0,12 → đứng yên
            1f to 0f, // phải
            -1f to 0f, // trái
            0f to -1f, // lên (y âm = lên, đúng quy ước libretro)
            0f to 0f, // nhả tay
        )
        assertEquals("số lần phát tín hiệu", expected.size, motion.size)
        assertTrue("luôn là cần trái", motion.all { it.first == STICK_LEFT })
        expected.forEachIndexed { i, (x, y) ->
            assertEquals("trục X lần $i", x, motion[i].second, 0.01f)
            assertEquals("trục Y lần $i", y, motion[i].third, 0.01f)
        }
    }

    /** Lõi không khai báo analog (bố cục không có cần) thì không nhận trục nào. */
    @Test fun loiKhongCoAnalogKhongNhanTruc() {
        pad(PadLayout.GB)
        rule.onNodeWithContentDescription("Cần analog").assertDoesNotExist()
        // Kéo ngang cả cụm trái (đúng chỗ cần của PSP) cũng không được phát trục.
        rule.onNodeWithContentDescription("Nút A").performTouchInput {
            down(center); moveTo(center + Offset(width * 0.4f, 0f)); up()
        }
        rule.mainClock.advanceTimeBy(100)
        assertTrue("không có lõi analog thì không gửi trục", motion.isEmpty())
    }

    /** Chỉ hệ có cần mới nhận trục; ppsspp luôn ra bố cục PSP (cả khi config ghi "psp"). */
    @Test fun chiHeCoCanMoiGuiTruc() {
        assertEquals(PadLayout.PSP, padFor("ppsspp", null))
        assertEquals(PadLayout.PSP, padFor("ppsspp", "psp"))
        assertTrue("PSP có cần", PadLayout.PSP.hasStick)
        assertTrue("PSP mặc định dùng cần", PadLayout.PSP.stickDefault)
        listOf("gambatte", "fceumm", "snes9x", "genesis_plus_gx", "easyrpg", "desmume").forEach {
            assertFalse("$it không khai báo analog", padFor(it, null).hasStick)
        }
        assertEquals(
            setOf(PadLayout.N64, PadLayout.DC, PadLayout.PSP, PadLayout.PS, PadLayout.N3DS),
            PadLayout.values().filter { it.hasStick }.toSet(),
        )
    }

    /** Bảng quy đổi giá trị núm → trục: vùng chết 0,12 và chặn trong [-1, 1]. */
    @Test fun bangQuyDoiTrucCoVungChetVaChan() {
        assertEquals(0f, stickValue(0f), 0f)
        assertEquals("trôi nhẹ bị bỏ", 0f, stickValue(0.11f), 0f)
        assertEquals("trôi nhẹ bị bỏ", 0f, stickValue(-0.11f), 0f)
        assertEquals(0.12f, stickValue(0.12f), 0.0001f)
        assertEquals(0.5f, stickValue(0.5f), 0.0001f)
        assertEquals(-0.5f, stickValue(-0.5f), 0.0001f)
        assertEquals(1f, stickValue(1f), 0f)
        assertEquals(-1f, stickValue(-1f), 0f)
        assertEquals("vượt biên bị chặn", 1f, stickValue(1.4f), 0f)
        assertEquals("vượt biên bị chặn", -1f, stickValue(-1.4f), 0f)
    }

    /** Mã nguồn cần của app phải khớp `MOTION_SOURCE_ANALOG_*` bên native (`input.h`). */
    @Test fun maNguonCanKhopVoiNative() {
        assertEquals(0, STICK_LEFT)
        assertEquals(1, STICK_RIGHT)
        assertEquals(1, GLRetroView.MOTION_SOURCE_ANALOG_LEFT) // input.h: MOTION_SOURCE_ANALOG_LEFT = 1
        assertEquals(2, GLRetroView.MOTION_SOURCE_ANALOG_RIGHT)
        assertEquals(LibretroDroid.MOTION_SOURCE_ANALOG_LEFT, GLRetroView.MOTION_SOURCE_ANALOG_LEFT)
        assertEquals(LibretroDroid.MOTION_SOURCE_ANALOG_RIGHT, GLRetroView.MOTION_SOURCE_ANALOG_RIGHT)
    }
}
