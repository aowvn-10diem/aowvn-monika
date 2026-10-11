package vn.aow.monika.runner

import android.app.Activity
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import kotlinx.coroutines.delay
import ru.playsoftware.j2meloader.J2meRuntime
import vn.aow.monika.R
import vn.aow.monika.ui.theme.MonikaMenuSheet
import vn.aow.monika.ui.theme.MonikaTheme
import vn.aow.monika.ui.theme.SheetAction
import java.lang.ref.WeakReference
import java.util.function.IntConsumer

/**
 * Menu trong game Java (J2ME Loader) vẽ bằng menu popup của Monika: J2ME gửi danh sách mục của menu gốc,
 * ở đây gắn icon + vẽ thẻ than bo tròn trượt lên từ dưới. Nút Back / phím Menu bấm lần nữa = đóng.
 */
object J2meMenu : J2meRuntime.MenuPresenter {
    private var current: WeakReference<() -> Unit>? = null

    override fun show(activity: Activity, title: String?, subtitle: String?, entries: MutableList<J2meRuntime.MenuEntry>, onPick: IntConsumer) {
        // Đang mở → lần bấm này là "đóng".
        current?.get()?.let { it(); return }
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val view = ComposeView(activity)
        var open by mutableStateOf(false)
        var picked: Int? = null
        var shown = false
        val dismiss = { open = false }
        current = WeakReference(dismiss)
        view.setContent {
            MonikaTheme {
                LaunchedEffect(Unit) { open = true }
                LaunchedEffect(open) {
                    if (open) { shown = true; return@LaunchedEffect }
                    if (shown) {
                        current = null
                        delay(260) // chờ hiệu ứng đóng
                        content.removeView(view)
                        picked?.let(onPick::accept)
                    }
                }
                // ComposeView còn sống khi báo lỗi mở; menu Java chưa tháo view.
                val reportAction = vn.aow.monika.ui.gameReportAction {}.copy(keepOpen = true)
                Box(Modifier.fillMaxSize()) {
                    MonikaMenuSheet(
                        open, dismiss, title = title, subtitle = subtitle,
                        actions = listOf(reportAction) + j2meMenuActions(entries) { picked = it },
                    )
                }
            }
        }
        content.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    override fun askCommunity(activity: Activity, shot: android.graphics.Bitmap?, gameName: String?) =
        vn.aow.monika.community.AskGroup.ask(activity, shot, gameName.orEmpty().ifBlank { "game Java" }, "Java")
}

/** Icon dự phòng cho mục menu J2ME chưa có icon riêng (test giữ cho mọi mục của menu gốc đều có icon riêng). */
internal val J2ME_MENU_FALLBACK_ICON = R.drawable.ic_fluent_more_horizontal_24_regular

/**
 * Mục menu J2ME → nút trong menu Monika: "Chơi tiếp" lên đầu, "Thoát" xuống cuối, còn lại giữ thứ tự J2ME gửi
 * (gồm cả chụp màn hình, bàn phím, giới hạn FPS, tùy chọn phím ảo). [onPick] nhận id mục được chọn.
 */
internal fun j2meMenuActions(entries: List<J2meRuntime.MenuEntry>, onPick: (Int) -> Unit): List<SheetAction> =
    entries.sortedBy { e -> when (e.key) { "monika_continue" -> 0; "action_exit_midlet" -> 2; else -> 1 } }.map { e ->
        SheetAction(e.title, j2meMenuIcon(e.key), highlight = e.key == "monika_continue" || e.checked) { onPick(e.id) }
    }

internal fun j2meMenuIcon(key: String?): Int = when (key) {
    "monika_ask" -> R.drawable.ic_fluent_people_community_24_regular
    "monika_continue" -> R.drawable.ic_fluent_play_24_regular
    "monika_settings" -> R.drawable.ic_fluent_settings_24_regular
    "action_exit_midlet" -> R.drawable.ic_fluent_door_arrow_left_24_regular
    "action_save_log" -> R.drawable.ic_fluent_document_24_regular
    "action_lock_orientation" -> R.drawable.ic_fluent_arrow_rotate_clockwise_24_regular
    "action_ime_keyboard" -> R.drawable.ic_fluent_keyboard_24_regular
    "action_take_screenshot" -> R.drawable.ic_fluent_screenshot_24_regular
    "action_limit_fps" -> R.drawable.ic_fluent_top_speed_24_regular
    "action_layout_edit_mode" -> R.drawable.ic_fluent_xbox_controller_24_regular
    "action_layout_scale_mode" -> R.drawable.ic_fluent_layer_24_regular
    "action_layout_edit_finish" -> R.drawable.ic_fluent_checkmark_circle_24_filled
    "action_layout_switch" -> R.drawable.ic_fluent_keyboard_24_regular
    "action_hide_buttons" -> R.drawable.ic_fluent_eye_24_regular
    else -> J2ME_MENU_FALLBACK_ICON
}
