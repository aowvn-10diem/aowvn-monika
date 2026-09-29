package vn.aow.monika.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource

/**
 * Icon toàn app: icon Fluent (nét đơn sắc) nào có emoji 3D tương ứng thì hiện emoji 3D của Microsoft (Fluent Emoji, MIT),
 * icon điều khiển (mũi tên, đóng, chevron…) giữ nguyên nét vector. Đổi/ thêm ánh xạ: sửa [MAP] — một chỗ duy nhất.
 * Chữ ký giống `Icon(painterResource(..))` để thay thế 1-1.
 */
@Composable
fun MonikaIcon(@DrawableRes icon: Int, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    val context = LocalContext.current
    val emoji = remember(icon) {
        val entry = runCatching { context.resources.getResourceEntryName(icon) }.getOrNull()
        MAP[entry?.removePrefix("ic_fluent_")?.replace(Regex("_24_(regular|filled)$"), "")]
            ?.let { context.resources.getIdentifier("fluent3d_$it", "drawable", context.packageName) }?.takeIf { it != 0 }
    }
    if (emoji != null) Image(painterResource(emoji), contentDescription, modifier)
    else Icon(painterResource(icon), contentDescription, modifier, tint = tint)
}

/** Tên icon Fluent (bỏ tiền tố/hậu tố) → tên emoji 3D (`fluent3d_<tên>`). */
private val MAP = mapOf(
    "home" to "house",
    "games" to "video_game",
    "xbox_controller" to "joystick",
    "library" to "books",
    "grid" to "puzzle_piece",
    "people_community" to "bust_in_silhouette",
    "person" to "bust_in_silhouette",
    "settings" to "gear",
    "search" to "magnifying_glass_tilted_left",
    "arrow_download" to "inbox_tray",
    "document" to "clipboard",
    "delete" to "wastebasket",
    "checkmark_circle" to "check_mark_button",
    "globe" to "globe_with_meridians",
    "star" to "star",
    "heart" to "red_heart",
    "lock_closed" to "locked",
    "shield_checkmark" to "shield",
    "top_speed" to "rocket",
    "storage" to "floppy_disk",
    "save" to "floppy_disk",
    "alert" to "warning",
    "folder_open" to "open_file_folder",
    "folder_add" to "file_folder",
    "flash" to "high_voltage",
    "thumb_like" to "thumbs_up",
    "news" to "newspaper",
    "calendar_checkmark" to "calendar",
    "keyboard" to "keyboard",
    "link" to "link",
    "archive" to "package",
    "layer" to "puzzle_piece",
    "chat_multiple" to "speech_balloon",
    "screenshot" to "camera",
    "info" to "light_bulb",
)
