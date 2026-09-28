package vn.aow.monika.runner

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
    val motion = Monika.motion
    Box(Modifier.fillMaxSize()) {
        // Header kính mờ
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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
            GlassCircle(R.drawable.ic_fluent_more_horizontal_24_regular, "Menu", { state.menuOpen = !state.menuOpen }, active = state.menuOpen)
        }

        // Menu nhanh: phóng + mờ từ góc phải trên (nút "…"). Máy tắt hiệu ứng → hiện ngay.
        AnimatedVisibility(
            state.menuOpen,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 72.dp, end = 16.dp),
            enter = if (motion.enabled) scaleIn(tween(motion.normal, easing = motion.easing), 0.85f, TransformOrigin(1f, 0f)) + fadeIn(tween(motion.normal)) else fadeIn(tween(0)),
            exit = if (motion.enabled) scaleOut(tween(motion.fast), 0.9f, TransformOrigin(1f, 0f)) + fadeOut(tween(motion.fast)) else fadeOut(tween(0)),
        ) {
            Column(
                Modifier.width(220.dp).clip(Radius.large).background(Color(0xE6201F21)).border(1.dp, Color(0x24FFFFFF), Radius.large).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Chọn ô lưu: chấm nhỏ = ô đã có dữ liệu.
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Ô lưu", style = Monika.type.caption, color = Color(0xFFC8C5CB))
                    (1..3).forEach { s ->
                        EditChip(if (s in state.filledSlots) "$s •" else "$s", state.slot == s) { state.slot = s }
                    }
                }
                MenuItem(R.drawable.ic_fluent_save_24_regular, "Lưu vào ô ${state.slot}", highlight = true, onSave)
                MenuItem(R.drawable.ic_fluent_folder_open_24_regular, "Tải từ ô ${state.slot}", false, onLoad)
                MenuItem(R.drawable.ic_fluent_top_speed_24_regular, if (state.turbo) "Tốc độ: 2x" else "Tốc độ: 1x", state.turbo, onTurbo)
                MenuItem(R.drawable.ic_fluent_eye_24_regular, "Độ mờ phím: ${(state.opacity * 100).toInt()}%", false, onOpacity)
                MenuItem(R.drawable.ic_fluent_xbox_controller_24_regular, "Chỉnh vị trí & cỡ phím", false) { state.menuOpen = false; state.editing = true }
                MenuItem(R.drawable.ic_fluent_settings_24_regular, "Tùy chọn giả lập", false) { state.menuOpen = false; onOptions() }
                MenuItem(R.drawable.ic_fluent_dismiss_24_regular, "Thoát trò chơi", false, onBack)
            }
        }

        state.toast?.let {
            Text(
                it, style = Monika.type.bodyStrong, color = Color.White,
                modifier = Modifier.align(Alignment.Center).clip(Radius.pill).background(Color(0xCC181719)).padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }

        if (showPad) VirtualPad(layout, state, send, Modifier.align(Alignment.BottomCenter))

        state.options?.let { opts -> OptionsPanel(opts, onOptionChange, onOptionsReset, { state.options = null }, Modifier.align(Alignment.Center)) }

        // Thanh chỉnh tay cầm: chọn cỡ, về mặc định, xong (lưu lại).
        if (state.editing) {
            Column(
                Modifier.align(Alignment.Center).clip(Radius.large).background(Color(0xE6201F21)).border(1.dp, Color(0x24FFFFFF), Radius.large).padding(16.dp),
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
    }
}

@Composable
private fun EditChip(text: String, on: Boolean, onClick: () -> Unit) {
    Text(
        text, style = Monika.type.bodyStrong, color = Color.White,
        modifier = Modifier.clip(Radius.pill)
            .background(if (on) primaryGradient() else Brush.linearGradient(listOf(Color(0x24FFFFFF), Color(0x24FFFFFF))))
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

/** Bảng tùy chọn lõi: bấm 1 dòng = chuyển sang giá trị kế tiếp, áp ngay vào game. */
@Composable
private fun OptionsPanel(
    options: List<CoreOption>,
    onChange: (CoreOption, String) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier.fillMaxWidth(0.92f).heightIn(max = 520.dp).clip(Radius.large).background(Color(0xF0201F21))
            .border(1.dp, Color(0x24FFFFFF), Radius.large).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Tùy chọn giả lập", style = Monika.type.cardTitle, color = Color.White, modifier = Modifier.padding(4.dp))
        if (options.isEmpty()) {
            Text("Lõi này không có tùy chọn chỉnh được.", style = Monika.type.body, color = Color(0xFFC8C5CB), modifier = Modifier.padding(4.dp))
        }
        LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(options, key = { it.key }) { o ->
                Row(
                    Modifier.fillMaxWidth().clip(Radius.medium).background(Color(0x14FFFFFF))
                        .pointerInput(o) { awaitEachGesture { awaitFirstDown(); if (awaitRelease()) onChange(o, o.next()) } }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(o.label, style = Monika.type.body, color = Color.White, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        o.value, style = Monika.type.bodyStrong, color = Color.White, maxLines = 1,
                        modifier = Modifier.padding(start = 10.dp).clip(Radius.pill).background(primaryGradient()).padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
        Text("Vài tùy chọn chỉ có tác dụng sau khi mở lại game.", style = Monika.type.caption, color = Color(0xFFC8C5CB), modifier = Modifier.padding(horizontal = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { MenuItem(R.drawable.ic_fluent_dismiss_24_regular, "Về mặc định", false, onReset) }
            Box(Modifier.weight(1f)) { MenuItem(R.drawable.ic_fluent_save_24_regular, "Xong", true, onClose) }
        }
    }
}

@Composable
private fun MenuItem(@DrawableRes icon: Int, text: String, highlight: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(Radius.pill)
            .background(if (highlight) primaryGradient() else Brush.linearGradient(listOf(Color(0x14FFFFFF), Color(0x14FFFFFF))))
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(); if (awaitRelease()) onClick() } }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), null, Modifier.size(20.dp), tint = Color.White)
        Text(text, style = Monika.type.body, color = Color.White, modifier = Modifier.padding(start = 12.dp))
    }
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
