package vn.aow.monika.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.playsoftware.j2meloader.J2meRuntime.MenuEntry
import vn.aow.monika.R
import vn.aow.monika.runner.J2ME_MENU_FALLBACK_ICON
import vn.aow.monika.runner.j2meMenuActions
import vn.aow.monika.ui.theme.*

/** V77: menu trong game Java = menu Monika, gom đủ chức năng của menu J2ME gốc (kể cả chụp màn hình). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class J2meMenuUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Before fun clock() { rule.mainClock.autoAdvance = false }

    /** Thứ tự và khóa y như `MicroActivity.showMonikaMenu` gửi sang (id số tự đặt, không phụ thuộc R của J2ME). */
    private fun entries() = listOf(
        MenuEntry(-2, "Chơi tiếp", "monika_continue", false),
        MenuEntry(-4, "Hỏi nhóm FB", "monika_ask", false),
        MenuEntry(1, "Thoát", "action_exit_midlet", false),
        MenuEntry(2, "Lưu nhật ký", "action_save_log", false),
        MenuEntry(3, "Khóa xoay màn hình", "action_lock_orientation", true),
        MenuEntry(4, "Bàn phím hệ thống", "action_ime_keyboard", false),
        MenuEntry(5, "Chụp màn hình", "action_take_screenshot", false),
        MenuEntry(6, "Giới hạn FPS", "action_limit_fps", false),
        MenuEntry(7, "Sửa bố cục phím", "action_layout_edit_mode", false),
        MenuEntry(8, "Đổi bố cục phím", "action_layout_switch", false),
        MenuEntry(-3, "Cài đặt game", "monika_settings", false),
    )

    @Test fun continueFirstExitLastAndEveryFunctionKept() {
        val picked = mutableListOf<Int>()
        val actions = j2meMenuActions(entries()) { picked += it }
        assertEquals(entries().size, actions.size)
        assertEquals("Chơi tiếp", actions.first().label)
        assertEquals("Thoát", actions.last().label)
        assertTrue(actions.none { it.icon == J2ME_MENU_FALLBACK_ICON })
        val shot = actions.single { it.label == "Chụp màn hình" }
        assertEquals(R.drawable.ic_fluent_screenshot_24_regular, shot.icon)
        shot.onClick()
        assertEquals(listOf(5), picked)
        // Mục đang bật (khóa xoay) và "Chơi tiếp" được nhấn màu.
        assertTrue(actions.single { it.label == "Khóa xoay màn hình" }.highlight)
        assertTrue(actions.first().highlight)
        assertFalse(shot.highlight)
    }

    @Test fun menuSheetShowsEveryJ2meFunction() {
        val actions = j2meMenuActions(entries()) {}
        rule.setContent {
            MonikaTheme {
                Box(Modifier.fillMaxSize().background(Monika.colors.surfaceDark)) {
                    MonikaMenuSheet(true, {}, actions, title = "Game Java mẫu", subtitle = "Game Java (J2ME)")
                }
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithText("Game Java mẫu").assertExists()
        for (e in entries()) rule.onNodeWithText(e.title).assertExists()
        rule.shot("v77-j2me-menu")
    }
}
