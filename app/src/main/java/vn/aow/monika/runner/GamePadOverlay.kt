package vn.aow.monika.runner

import vn.aow.monika.ui.theme.MonikaMenuSheet
import vn.aow.monika.ui.theme.SheetAction
import vn.aow.monika.ui.theme.SheetChip
import vn.aow.monika.ui.theme.SheetColors
import vn.aow.monika.ui.theme.SheetRow

import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.WindowInsets
import android.view.KeyEvent
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.aow.monika.R
import vn.aow.monika.ui.theme.Monika
import vn.aow.monika.ui.theme.Radius
import vn.aow.monika.ui.theme.primaryGradient
import kotlin.math.abs

/*
 * Giao diện trong game (Compose, nổi trên màn hình giả lập):
 * - Header kính mờ: quay lại · hệ máy + tên game · menu.
 * - Menu nhanh: Lưu / Tải trạng thái, Tăng tốc, Độ mờ phím, Thoát — hiện khi bấm "…" (phóng từ góc nút).
 * - Tay cầm ảo theo từng hệ máy. Vùng trống không chặn chạm (màn cảm ứng NDS vẫn dùng được).
 *
 * Nút theo VỊ TRÍ chuẩn Android: dưới = BUTTON_A, phải = BUTTON_B, trái = BUTTON_X, trên = BUTTON_Y.
 * Nhờ vậy nút "A" của Nintendo (bên phải) và "×" của PlayStation (bên dưới) đều đúng.
 */

enum class PadLayout { GB, GBA, NDS, PS, RPG }

fun padFor(core: String, override: String?): PadLayout = override?.let { runCatching { PadLayout.valueOf(it.uppercase()) }.getOrNull() }
    ?: when (core) {
        "desmume", "melonds" -> PadLayout.NDS
        "gambatte" -> PadLayout.GB
        "pcsx_rearmed", "ppsspp" -> PadLayout.PS
        "easyrpg" -> PadLayout.RPG
        else -> PadLayout.GBA
    }

class InGameState {
    var menuOpen by mutableStateOf(false)
    var turbo by mutableStateOf(false)
    var opacity by mutableStateOf(0.65f)
    var toast by mutableStateOf<String?>(null)

    /** Ô lưu trạng thái đang chọn (1–3) và ô nào đã có dữ liệu. */
    var slot by mutableStateOf(1)
    var filledSlots by mutableStateOf(setOf<Int>())

    /** Chế độ chỉnh tay cầm: kéo cụm phím đổi chỗ, chọn cỡ. */
    var editing by mutableStateOf(false)
    var scale by mutableStateOf(1f)
    var dpadOffset by mutableStateOf(Offset.Zero)
    var faceOffset by mutableStateOf(Offset.Zero)

    /** Khác null = đang mở bảng tùy chọn giả lập. */
    var options by mutableStateOf<List<CoreOption>?>(null)
}

/** Vùng an toàn phía trên: thanh trạng thái ∪ camera/cutout (cả khi thanh trạng thái bị ẩn), và 2 bên cạnh cutout khi máy ngang. */
private val SafeTop: WindowInsets
    @Composable get() = WindowInsets.statusBars.union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

@Composable
fun InGameOverlay(
    state: InGameState,
    system: String,
    title: String,
    layout: PadLayout,
    showPad: Boolean,
    send: (action: Int, key: Int) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onTurbo: () -> Unit,
    onOpacity: () -> Unit,
    onEditDone: () -> Unit,
    onOptions: () -> Unit = {},
    onOptionChange: (CoreOption, String) -> Unit = { _, _ -> },
    onOptionsReset: () -> Unit = {},
) {
    // Nút Back của máy: mở menu (thay vì thoát ngay, dễ bấm nhầm khi đang chơi); đang chỉnh phím → xong.
    androidx.activity.compose.BackHandler(enabled = !state.menuOpen && state.options == null) {
        if (state.editing) { state.editing = false; onEditDone() } else state.menuOpen = true
    }
    Box(Modifier.fillMaxSize()) {
        // Header kính mờ
        // Né camera / "con nhộng" (display cutout) + thanh trạng thái — lúc chơi game thanh trạng thái bị ẩn nên phải dùng cutout.
        Row(Modifier.fillMaxWidth().windowInsetsPadding(SafeTop).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassCircle(R.drawable.ic_fluent_arrow_left_24_regular, "Thoát", onBack)
            Spacer(Modifier.width(10.dp))
            Row(
                Modifier.weight(1f).height(52.dp).clip(Radius.pill).background(Color(0xB8181719)).border(1.dp, Color(0x24FFFFFF), Radius.pill).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_fluent_xbox_controller_24_regular), null, Modifier.size(22.dp), tint = Color.White)
                Column(Modifier.padding(start = 10.dp)) {
                    Text(system, style = Monika.type.bodyStrong, color = Color.White, maxLines = 1)
                    Text(title, style = Monika.type.caption, color = Color(0xFFC8C5CB), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(10.dp))
            GlassCircle(R.drawable.ic_fluent_grid_24_regular, "Menu", { state.menuOpen = !state.menuOpen }, active = state.menuOpen)
        }

        state.toast?.let {
            Text(
                it, style = Monika.type.bodyStrong, color = Color.White,
                modifier = Modifier.align(Alignment.Center).clip(Radius.pill).background(Color(0xCC181719)).padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }

        if (showPad) VirtualPad(layout, state, send, Modifier.align(Alignment.BottomCenter))

        // Thanh chỉnh tay cầm: chọn cỡ, về mặc định, xong (lưu lại). Giữa màn hình để vẫn thấy phím khi kéo.
        if (state.editing) {
            Column(
                Modifier.align(Alignment.Center).padding(16.dp).clip(Radius.hero).background(SheetColors.background).border(1.dp, SheetColors.border, Radius.hero).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Kéo cụm D-pad / cụm nút để đổi chỗ", style = Monika.type.bodyStrong, color = Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Nhỏ" to 0.85f, "Vừa" to 1f, "Lớn" to 1.2f).forEach { (label, v) ->
                        EditChip(label, state.scale == v) { state.scale = v }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditChip("Mặc định", false) { state.scale = 1f; state.dpadOffset = Offset.Zero; state.faceOffset = Offset.Zero }
                    EditChip("Xong", true) { state.editing = false; onEditDone() }
                }
            }
        }

        // Menu trong game: CÙNG menu popup dưới đáy như Aow Monika (thẻ than bo tròn trượt lên).
        MonikaMenuSheet(
            state.menuOpen, { state.menuOpen = false },
            title = title, subtitle = system,
            header = {
                // Chọn ô lưu: chấm = ô đã có dữ liệu.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Ô lưu", style = Monika.type.bodyStrong, color = SheetColors.textSecondary, modifier = Modifier.padding(start = 4.dp, end = 4.dp))
                    (1..3).forEach { s -> SheetChip(if (s in state.filledSlots) "$s •" else "$s", state.slot == s, Modifier.weight(1f)) { state.slot = s } }
                }
            },
            actions = listOf(
                SheetAction("Lưu ô ${state.slot}", R.drawable.ic_fluent_save_24_regular, highlight = true, onClick = onSave),
                SheetAction("Tải ô ${state.slot}", R.drawable.ic_fluent_folder_open_24_regular, enabled = state.slot in state.filledSlots || state.filledSlots.isEmpty(), onClick = onLoad),
                SheetAction(if (state.turbo) "Tốc độ 2x" else "Tốc độ 1x", R.drawable.ic_fluent_top_speed_24_regular, highlight = state.turbo, keepOpen = true, onClick = onTurbo),
                SheetAction("Độ mờ phím ${(state.opacity * 100).toInt()}%", R.drawable.ic_fluent_eye_24_regular, keepOpen = true, onClick = onOpacity),
                SheetAction("Chỉnh phím", R.drawable.ic_fluent_xbox_controller_24_regular) { state.editing = true },
                SheetAction("Tùy chọn giả lập", R.drawable.ic_fluent_settings_24_regular, onClick = onOptions),
                SheetAction("Chơi tiếp", R.drawable.ic_fluent_play_24_regular) {},
                SheetAction("Thoát game", R.drawable.ic_fluent_door_arrow_left_24_regular, onClick = onBack),
            ),
        )

        // Tùy chọn lõi: bấm 1 dòng = chuyển sang giá trị kế tiếp, áp ngay vào game.
        val opts = state.options
        MonikaMenuSheet(
            opts != null, { state.options = null },
            title = "Tùy chọn giả lập",
            subtitle = if (opts.isNullOrEmpty()) "Lõi này không có tùy chọn chỉnh được." else "Vài tùy chọn chỉ có tác dụng sau khi mở lại game.",
            header = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    opts.orEmpty().forEach { o ->
                        SheetRow(
                            o.label,
                            trailing = {
                                Text(
                                    o.display(), style = Monika.type.bodyStrong, color = Color.White, maxLines = 1,
                                    modifier = Modifier.padding(start = 10.dp).clip(Radius.pill).background(primaryGradient()).padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            },
                            onClick = { onOptionChange(o, o.next()) },
                        )
                    }
                }
            },
            actions = listOf(
                SheetAction("Về mặc định", R.drawable.ic_fluent_arrow_counterclockwise_24_regular, onClick = onOptionsReset),
                SheetAction("Xong", R.drawable.ic_fluent_checkmark_circle_24_filled, highlight = true) {},
            ),
        )
    }
}

@Composable
private fun EditChip(text: String, on: Boolean, onClick: () -> Unit) {
    Text(
        text, style = Monika.type.bodyStrong, color = Color.White,
        modifier = Modifier.clip(Radius.pill)
            .background(if (on) primaryGradient() else Brush.linearGradient(listOf(SheetColors.tile, SheetColors.tile)))
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(); if (awaitRelease()) onClick() } }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** Cụm phím kéo được khi đang chỉnh; lúc chơi thì phím hoạt động bình thường. */
@Composable
private fun Movable(state: InGameState, offset: Offset, onMove: (Offset) -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .graphicsLayer { scaleX = state.scale; scaleY = state.scale },
    ) {
        content()
        if (state.editing) {
            Box(
                Modifier.matchParentSize().clip(Radius.large).border(2.dp, Color(0xFFFF7F78), Radius.large).background(Color(0x22FF7F78))
                    .pointerInput(Unit) { detectDragGestures { ch, drag -> ch.consume(); onMove(drag) } },
            )
        }
    }
}

@Composable
private fun GlassCircle(@DrawableRes icon: Int, desc: String, onClick: () -> Unit, active: Boolean = false) {
    Box(
        Modifier.size(52.dp).clip(Radius.pill)
            .background(if (active) primaryGradient() else Brush.linearGradient(listOf(Color(0xB8181719), Color(0xB8181719))))
            .border(1.dp, Color(0x24FFFFFF), Radius.pill)
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(); val up = awaitRelease(); if (up) onClick() } },
        contentAlignment = Alignment.Center,
    ) { Icon(painterResource(icon), desc, Modifier.size(24.dp), tint = Color.White) }
}

/** Chờ nhả tay; true nếu nhả (không bị hủy). */
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.awaitRelease(): Boolean {
    while (true) {
        val e = awaitPointerEvent()
        if (e.changes.all { !it.pressed }) return true
    }
}

@Composable
private fun VirtualPad(layout: PadLayout, state: InGameState, send: (Int, Int) -> Unit, modifier: Modifier) {
    val shoulders = layout == PadLayout.GBA || layout == PadLayout.NDS || layout == PadLayout.PS
    Column(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 16.dp).alpha(if (state.editing) 1f else state.opacity)) {
        if (shoulders) {
            Row(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (layout == PadLayout.PS) PillKey("L2", KeyEvent.KEYCODE_BUTTON_L2, send)
                    PillKey("L", KeyEvent.KEYCODE_BUTTON_L1, send)
                }
                Spacer(Modifier.weight(1f))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
                    if (layout == PadLayout.PS) PillKey("R2", KeyEvent.KEYCODE_BUTTON_R2, send)
                    PillKey("R", KeyEvent.KEYCODE_BUTTON_R1, send)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Movable(state, state.dpadOffset, { state.dpadOffset += it }) { DPad(send) }
            Spacer(Modifier.weight(1f))
            Movable(state, state.faceOffset, { state.faceOffset += it }) { FaceButtons(layout, send) }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            PillKey("SELECT", KeyEvent.KEYCODE_BUTTON_SELECT, send)
            Spacer(Modifier.width(12.dp))
            PillKey("START", KeyEvent.KEYCODE_BUTTON_START, send)
        }
    }
}

/** D-pad tròn kính mờ; kéo ngón để đổi hướng, hỗ trợ đi chéo. */
@Composable
private fun DPad(send: (Int, Int) -> Unit) {
    val size = 148.dp
    var active by remember { mutableStateOf(setOf<Int>()) }
    Box(
        Modifier.size(size).clip(Radius.pill).background(Color(0x99181719)).border(1.dp, Color(0x33FFFFFF), Radius.pill)
            .pointerInput(Unit) {
                fun keysAt(x: Float, y: Float): Set<Int> {
                    val cx = this.size.width / 2f
                    val cy = this.size.height / 2f
                    val dx = x - cx
                    val dy = y - cy
                    val dead = this.size.width * 0.12f
                    val out = mutableSetOf<Int>()
                    if (abs(dx) > dead && abs(dx) > abs(dy) * 0.4f) out += if (dx > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
                    if (abs(dy) > dead && abs(dy) > abs(dx) * 0.4f) out += if (dy > 0) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP
                    return out
                }
                fun update(next: Set<Int>) {
                    (active - next).forEach { send(KeyEvent.ACTION_UP, it) }
                    (next - active).forEach { send(KeyEvent.ACTION_DOWN, it) }
                    active = next
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    update(keysAt(down.position.x, down.position.y))
                    while (true) {
                        val e = awaitPointerEvent()
                        val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        if (ch.positionChange() != androidx.compose.ui.geometry.Offset.Zero) update(keysAt(ch.position.x, ch.position.y))
                        ch.consume()
                    }
                    update(emptySet())
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val arm = Color(0x40FFFFFF)
        Box(Modifier.size(width = 46.dp, height = 124.dp).clip(Radius.small).background(arm))
        Box(Modifier.size(width = 124.dp, height = 46.dp).clip(Radius.small).background(arm))
        Text("▲", color = if (KeyEvent.KEYCODE_DPAD_UP in active) Color.White else Color(0xCCFFFFFF), modifier = Modifier.offset(y = (-42).dp))
        Text("▼", color = if (KeyEvent.KEYCODE_DPAD_DOWN in active) Color.White else Color(0xCCFFFFFF), modifier = Modifier.offset(y = 42.dp))
        Text("◀", color = if (KeyEvent.KEYCODE_DPAD_LEFT in active) Color.White else Color(0xCCFFFFFF), modifier = Modifier.offset(x = (-42).dp))
        Text("▶", color = if (KeyEvent.KEYCODE_DPAD_RIGHT in active) Color.White else Color(0xCCFFFFFF), modifier = Modifier.offset(x = 42.dp))
    }
}

@Composable
private fun FaceButtons(layout: PadLayout, send: (Int, Int) -> Unit) {
    val bottom = KeyEvent.KEYCODE_BUTTON_A
    val right = KeyEvent.KEYCODE_BUTTON_B
    val left = KeyEvent.KEYCODE_BUTTON_X
    val top = KeyEvent.KEYCODE_BUTTON_Y
    val coral = Brush.linearGradient(listOf(Color(0xFFFF806E), Color(0xFFE95CC8)))
    val orange = Brush.linearGradient(listOf(Color(0xFFFFB052), Color(0xFFFF806E)))
    val glass = Brush.linearGradient(listOf(Color(0x99181719), Color(0x99181719)))
    when (layout) {
        PadLayout.GB, PadLayout.GBA, PadLayout.RPG -> Box(Modifier.size(width = 160.dp, height = 130.dp)) {
            RoundKey("B", bottom, coral, send, Modifier.align(Alignment.BottomStart))
            RoundKey("A", right, orange, send, Modifier.align(Alignment.TopEnd))
        }
        PadLayout.NDS -> Diamond(send, glass, listOf("X" to top, "Y" to left, "A" to right, "B" to bottom), highlight = right, accent = orange)
        PadLayout.PS -> Diamond(send, glass, listOf("△" to top, "□" to left, "○" to right, "×" to bottom), highlight = -1, accent = orange)
    }
}

@Composable
private fun Diamond(send: (Int, Int) -> Unit, glass: Brush, keys: List<Pair<String, Int>>, highlight: Int, accent: Brush) {
    val s = 56.dp
    Box(Modifier.size(s * 3)) {
        val (top, left, right, bottom) = keys
        RoundKey(top.first, top.second, if (top.second == highlight) accent else glass, send, Modifier.align(Alignment.TopCenter), s)
        RoundKey(left.first, left.second, if (left.second == highlight) accent else glass, send, Modifier.align(Alignment.CenterStart), s)
        RoundKey(right.first, right.second, if (right.second == highlight) accent else glass, send, Modifier.align(Alignment.CenterEnd), s)
        RoundKey(bottom.first, bottom.second, if (bottom.second == highlight) accent else glass, send, Modifier.align(Alignment.BottomCenter), s)
    }
}

@Composable
private fun RoundKey(label: String, key: Int, brush: Brush, send: (Int, Int) -> Unit, modifier: Modifier = Modifier, size: Dp = 72.dp) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier.size(size).graphicsLayer { val sc = if (pressed) 0.92f else 1f; scaleX = sc; scaleY = sc }
            .clip(Radius.pill).background(brush).border(1.dp, Color(0x40FFFFFF), Radius.pill)
            .keyInput(key, send) { pressed = it },
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Color.White, fontSize = if (size > 60.dp) 26.sp else 20.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun PillKey(label: String, key: Int, send: (Int, Int) -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        Modifier.height(40.dp).width(if (label.length > 2) 96.dp else 72.dp).clip(Radius.pill)
            .background(if (pressed) Color(0xCC2A292C) else Color(0x99181719)).border(1.dp, Color(0x33FFFFFF), Radius.pill)
            .keyInput(key, send) { pressed = it },
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
}

/** Nhấn giữ = ACTION_DOWN, nhả = ACTION_UP. Mỗi nút tự theo dõi ngón của nó → bấm nhiều nút cùng lúc được. */
private fun Modifier.keyInput(key: Int, send: (Int, Int) -> Unit, onPressed: (Boolean) -> Unit) = pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown()
        down.consume()
        onPressed(true)
        send(KeyEvent.ACTION_DOWN, key)
        while (true) {
            val e = awaitPointerEvent()
            val ch = e.changes.firstOrNull { it.id == down.id }
            if (ch == null || !ch.pressed) break
            ch.consume()
        }
        onPressed(false)
        send(KeyEvent.ACTION_UP, key)
    }
}

/**
 * Nút mở menu dùng chung cho mọi giả lập không có thanh tiêu đề (game web, J2ME):
 * tròn kính mờ 44dp ở góc phải trên, né camera / tai thỏ; mờ bớt khi không dùng để không che game.
 */
@Composable
fun GameMenuButton(active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.windowInsetsPadding(SafeTop).padding(10.dp).size(44.dp).alpha(if (active) 1f else 0.7f).clip(Radius.pill)
            .background(if (active) primaryGradient() else Brush.linearGradient(listOf(Color(0xB8181719), Color(0xB8181719))))
            .border(1.dp, Color(0x24FFFFFF), Radius.pill)
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(); if (awaitRelease()) onClick() } },
        contentAlignment = Alignment.Center,
    ) { Icon(painterResource(R.drawable.ic_fluent_grid_24_regular), "Menu", Modifier.size(22.dp), tint = Color.White) }
}
