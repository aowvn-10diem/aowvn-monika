package vn.aow.monika.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.aow.monika.library.GameSaveEntry
import vn.aow.monika.ui.screens.GameSaveSection
import vn.aow.monika.ui.screens.SaveListState
import vn.aow.monika.ui.screens.saveTime
import vn.aow.monika.ui.theme.MonikaTheme
import java.time.ZoneId

/**
 * V140 — mục Save ở trang game: danh sách save (tên · thời điểm · dung lượng), trạng thái rỗng
 * tiếng Việt, lỗi có loại, và hai nút Nhập/Chia sẻ. Chỉ dùng thành phần Monika.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class GameSaveSectionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun section(
        state: SaveListState,
        busy: Boolean = false,
        onImport: () -> Unit = {},
        onExport: () -> Unit = {},
    ) = rule.setContent {
        MonikaTheme {
            Column(Modifier.fillMaxWidth()) {
                GameSaveSection(state = state, busy = busy, onImport = onImport, onExport = onExport)
            }
        }
    }

    private fun settle() {
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
    }

    @Test fun existingSavesAreListedWithNameTimeAndSize() {
        val saves = listOf(
            GameSaveEntry("State: lưu nhanh 1", 1_800_000_000_000, 300),
            GameSaveEntry("Save trong game (SRAM)", 1_700_000_000_000, 8192),
        )
        section(SaveListState.Ready(saves))
        settle()
        rule.onNodeWithText("Save").assertExists()
        rule.onNodeWithText("2 mục").assertExists()
        rule.onNodeWithText("State: lưu nhanh 1").assertExists()
        rule.onNodeWithText("Save trong game (SRAM)").assertExists()
        rule.onNodeWithText("300 B").assertExists()
        rule.onNodeWithText("8,0 KB").assertExists()
        rule.onNodeWithText("Chưa có save nào.").assertDoesNotExist()
        rule.onNodeWithText("Bản cũ", substring = true).assertDoesNotExist()
        rule.shot("v140-muc-save-co-du-lieu")
    }

    /** Chưa có save → trạng thái rỗng đúng tiếng Việt, không phải lỗi. */
    @Test fun emptyStateSpeaksVietnameseAndStillOffersImport() {
        section(SaveListState.Ready(emptyList()))
        settle()
        rule.onNodeWithText("Chưa có save nào.").assertExists()
        rule.onNodeWithText("Chơi game rồi lưu", substring = true).assertExists()
        rule.onNodeWithText("Nhập save từ tệp").assertExists().assertIsEnabled()
        rule.shot("v140-muc-save-trong")
    }

    /** Đọc danh sách lỗi → hiện câu lỗi có loại, vẫn cho nhập tệp. */
    @Test fun failedStateShowsTypedMessageInsteadOfCrashing() {
        section(SaveListState.Failed("Hãy thoát hoàn toàn màn chơi trước khi xuất hoặc nhập save."))
        settle()
        rule.onNodeWithText("Hãy thoát hoàn toàn màn chơi", substring = true).assertExists()
        rule.onNodeWithText("Nhập save từ tệp").assertIsEnabled()
    }

    /** Hệ chưa hỗ trợ chuyển save → nói rõ, hai nút không còn hành động thay vì bấm rồi lỗi. */
    @Test fun unsupportedSystemExplainsAndDisablesActions() {
        section(SaveListState.Unsupported)
        settle()
        rule.onNodeWithText("Hệ này chưa hỗ trợ chuyển save", substring = true).assertExists()
        rule.onNodeWithText("Chia sẻ save").assertHasNoClickAction()
        rule.onNodeWithText("Nhập save từ tệp").assertHasNoClickAction()
    }

    /** Danh sách còn đang đọc → nói "Đang đọc save…" thay vì nháy trạng thái rỗng. */
    @Test fun loadingStateDoesNotClaimThereAreNoSaves() {
        section(SaveListState.Loading)
        settle()
        rule.onNodeWithText("Đang đọc save…").assertExists()
        rule.onNodeWithText("Chưa có save nào.").assertDoesNotExist()
    }

    @Test fun importAndShareButtonsCallTheirActions() {
        var imported = 0
        var exported = 0
        section(SaveListState.Ready(emptyList()), onImport = { imported++ }, onExport = { exported++ })
        settle()
        rule.onNodeWithText("Chia sẻ save").performClick()
        rule.onNodeWithText("Nhập save từ tệp").performClick()
        assertEquals(1, exported)
        assertEquals(1, imported)
    }

    /** Đang chuyển save thì khoá hai nút (không nhập hai lần). */
    @Test fun busyStateDisablesBothButtons() {
        section(SaveListState.Ready(emptyList()), busy = true)
        settle()
        rule.onNodeWithText("Đang chuyển save…").assertExists()
        rule.onNodeWithText("Chia sẻ save").assertHasNoClickAction()
        rule.onNodeWithText("Nhập save từ tệp").assertHasNoClickAction()
    }

    /** Thời điểm: định dạng ngày/tháng giờ:phút; không có metadata thì nói rõ, không hiện năm 1970. */
    @Test fun saveTimeIsReadableAndNeverShowsTheEpoch() {
        assertEquals("Không rõ thời điểm", saveTime(0))
        assertEquals("Không rõ thời điểm", saveTime(-1))
        // 1.700.000.000.000 ms = 14/11/2023 22:13 UTC → 05:13 ngày 15/11 giờ Việt Nam.
        assertEquals("15/11/2023 05:13", saveTime(1_700_000_000_000, ZoneId.of("Asia/Ho_Chi_Minh")))
    }
}
