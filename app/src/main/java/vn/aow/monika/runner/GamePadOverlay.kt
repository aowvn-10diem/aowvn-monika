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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.aow.monika.R
import vn.aow.monika.ui.controls.*
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

enum class PadLayout {
    GB, GBA, NDS, PS, RPG,
    /** SNES: L R + 4 nút vị trí X/Y/A/B (giống NDS nhưng không có màn cảm ứng). */
    SNES,
    /** Mega Drive: 3 nút A B C + hàng X Y Z (tay 6 nút). */
    GEN,
    /** N64: cần analog + A B + Z L R + 4 nút C (gửi qua cần phải). */
    N64,
    /** Dreamcast: cần analog + A B X Y + 2 cò LT RT. */
    DC,
    /** PSP: cần analog (nub) + d-pad + △□○× + L R. */
    PSP,
    /** 3DS: Circle Pad + X Y A B + L R (ZL ZR của New 3DS); màn cảm ứng dưới chạm thẳng vào game như NDS. */
    N3DS;

    /** Có cần analog (chuyển qua lại với D-pad bằng nút nhỏ dưới cụm trái). */
    val hasStick get() = this == N64 || this == DC || this == PSP || this == PS || this == N3DS
    /** Mở game là dùng cần luôn (N64 / Dreamcast / PSP); PS1 mặc định D-pad, bật cần khi game cần. */
    val stickDefault get() = this == N64 || this == DC || this == PSP || this == N3DS
}

/** Mã nguồn cần analog gửi cho lõi. */
const val STICK_LEFT = 0
const val STICK_RIGHT = 1

fun padFor(core: String, override: String?): PadLayout = override?.let { runCatching { PadLayout.valueOf(it.uppercase()) }.getOrNull() }
    ?: when (core) {
        "desmume", "melonds", "melondsds" -> PadLayout.NDS
        "gambatte", "fceumm", "nestopia", "prosystem", "stella2014", "handy", "mednafen_pce_fast", "mednafen_wswan", "mednafen_ngp" -> PadLayout.GB
        "snes9x", "bsnes" -> PadLayout.SNES
        "genesis_plus_gx", "picodrive" -> PadLayout.GEN
        "mupen64plus_next_gles3", "mupen64plus_next", "parallel_n64" -> PadLayout.N64
        "flycast" -> PadLayout.DC
        "ppsspp" -> PadLayout.PSP
        "citra" -> PadLayout.N3DS
        "pcsx_rearmed" -> PadLayout.PS
        "easyrpg" -> PadLayout.RPG
        else -> PadLayout.GBA
    }

class InGameState {
    var menuOpen by mutableStateOf(false)
    /** Hệ số tốc độ game: 1x → 2x → 3x → 4x (bấm "Tốc độ" trong menu để xoay vòng). */
    var speed by mutableStateOf(1)
    val turbo get() = speed > 1
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

    /** Cụm trái đang là cần analog (null = theo mặc định của bố cục). */
    var stickMode by mutableStateOf<Boolean?>(null)

    /** Tên kiểu hiển thị đang dùng (vd. "LCD cổ điển"); null = lõi này không có nhiều kiểu → không hiện nút. */
    var styleLabel by mutableStateOf<String?>(null)

    /** V78b: thanh tiêu đề đang hiện (chỉ có nghĩa khi `autoHideHeader`). Tự ẩn sau [HEADER_HIDE_MS] khi đang chơi. */
    var headerShown by mutableStateOf(true)
    /** Mỗi lần chạm mép trên tăng 1 → hiện lại tiêu đề và đếm lại thời gian tự ẩn. */
    var headerPulse by mutableIntStateOf(0)
    /** Hiện ngay (đặt trạng thái đồng bộ, không chờ hiệu ứng nền) rồi đếm lại giờ tự ẩn. */
    fun revealHeader() { headerShown = true; headerPulse++ }
}

/** V78b: thời gian (ms) tiêu đề chờ trước khi tự ẩn lúc đang chơi. */
const val HEADER_HIDE_MS = 3000L
/** testTag của thanh tiêu đề trong game (test giao diện). */
const val HEADER_TAG = "in-game-header"
/** V78b: dải mép trên (dp) chạm vào thì hiện lại tiêu đề. */
private val HEADER_REVEAL_EDGE = 40.dp

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
    /** Chụp màn hình + tự lưu game + mở Group Facebook để hỏi. */
    onAsk: () -> Unit = {},
    /** Cần analog / nút C: (STICK_LEFT | STICK_RIGHT, x, y) với x,y ∈ [-1,1], y dương = xuống. */
    onMotion: (Int, Float, Float) -> Unit = { _, _, _ -> },
    /** Nút riêng của từng giả lập (vd. "Mã cheat" của 3DS), đặt trước "Chơi tiếp". */
    extraActions: List<SheetAction> = emptyList(),
    /** V78b: ẩn thanh tiêu đề khi đang chơi (NDS); chạm mép trên hoặc mở menu thì hiện lại. */
    autoHideHeader: Boolean = false,
) {
    // Nút Back của máy: mở menu (thay vì thoát ngay, dễ bấm nhầm khi đang chơi); đang chỉnh phím → xong.
    androidx.activity.compose.BackHandler(enabled = !state.menuOpen && state.options == null) {
        if (state.editing) { state.editing = false; onEditDone() } else state.menuOpen = true
    }
    // V78b: tiêu đề hiện lúc đang tải / mở menu / chỉnh phím / bảng tùy chọn; đang chơi thì ẩn sau HEADER_HIDE_MS.
    // Chạm mép trên (headerPulse đổi) hiện lại và đếm lại.
    val pinned = state.menuOpen || state.editing || state.options != null || !showPad
    // Hiện = không tự ẩn, hoặc đang ghim (menu...), hoặc chưa tới giờ ẩn — tính thẳng khi vẽ, không phụ thuộc hiệu ứng nền.
    val headerVisible = !autoHideHeader || pinned || state.headerShown
    LaunchedEffect(autoHideHeader, pinned, state.headerPulse) {
        if (!autoHideHeader || pinned) { state.headerShown = true; return@LaunchedEffect }
        kotlinx.coroutines.delay(HEADER_HIDE_MS)
        state.headerShown = false
    }
    Box(Modifier.fillMaxSize()) {
        // Header kính mờ
        // Né camera / "con nhộng" (display cutout) + thanh trạng thái — lúc chơi game thanh trạng thái bị ẩn nên phải dùng cutout.
        // Hiện/ẩn thẳng bằng điều kiện (AnimatedVisibility không vào lại được trong test Robolectric: 2 test đỏ ở CI).
        if (headerVisible) {
        Row(Modifier.testTag(HEADER_TAG).fillMaxWidth().windowInsetsPadding(SafeTop).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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
        }
        // Tiêu đề đang ẩn: dải mỏng sát mép trên bắt cú chạm để hiện lại (không nằm trên màn cảm ứng dưới của NDS).
        if (autoHideHeader && !headerVisible) {
            Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().height(HEADER_REVEAL_EDGE)
                    .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(); state.revealHeader() } },
            )
        }

        state.toast?.let {
            Text(
                it, style = Monika.type.bodyStrong, color = Color.White,
                modifier = Modifier.align(Alignment.Center).clip(Radius.pill).background(Color(0xCC181719)).padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }

        if (showPad) VirtualPad(layout, state, send, onMotion, Modifier.align(Alignment.BottomCenter))

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
                    // Bản tự lưu (tạo khi bấm "Hỏi nhóm").
                    if (0 in state.filledSlots) SheetChip("Tự lưu", state.slot == 0) { state.slot = 0 }
                }
            },
            actions = listOf(
                SheetAction(if (state.slot == 0) "Lưu bản tự lưu" else "Lưu ô ${state.slot}", R.drawable.ic_fluent_save_24_regular, highlight = true, onClick = onSave),
                SheetAction(if (state.slot == 0) "Tải bản tự lưu" else "Tải ô ${state.slot}", R.drawable.ic_fluent_folder_open_24_regular, enabled = state.slot in state.filledSlots || state.filledSlots.isEmpty(), onClick = onLoad),
                SheetAction("Tốc độ ${state.speed}x", R.drawable.ic_fluent_top_speed_24_regular, highlight = state.turbo, keepOpen = true, onClick = onTurbo),
                SheetAction("Độ mờ phím ${(state.opacity * 100).toInt()}%", R.drawable.ic_fluent_eye_24_regular, keepOpen = true, onClick = onOpacity),
                SheetAction("Chỉnh phím", R.drawable.ic_fluent_xbox_controller_24_regular) { state.editing = true },
                SheetAction("Tùy chọn giả lập", R.drawable.ic_fluent_settings_24_regular, onClick = onOptions),
                SheetAction("Hỏi nhóm FB", R.drawable.ic_fluent_people_community_24_regular, onClick = onAsk),
            ) + extraActions + listOf(
                SheetAction("Chơi tiếp", R.drawable.ic_fluent_play_24_regular) {},
                vn.aow.monika.ui.gameReportAction { state.menuOpen = false },
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
internal fun VirtualPad(layout: PadLayout, state: InGameState, send: (Int, Int) -> Unit, motion: (Int, Float, Float) -> Unit, modifier: Modifier) {
    ControllerOptionsProvider {
    val stored = LocalControllerOptions.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
    val faceWidth = when (layout) { PadLayout.N64 -> 224f; PadLayout.GEN -> 216f; PadLayout.GB, PadLayout.GBA, PadLayout.RPG -> 160f; else -> 180f }
    val fit = ((maxWidth.value - 16f) / (150f + faceWidth)).coerceIn(.7f, 1.4f)
    val options = stored.copy(size = stored.size.coerceAtMost(fit))
    val compact = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    androidx.compose.runtime.CompositionLocalProvider(LocalControllerOptions provides options, LocalControllerTurbo provides state.turbo) {
    val useStick = layout.hasStick && (state.stickMode ?: layout.stickDefault)
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = if (compact) 8.dp else 16.dp).alpha(if (state.editing) 1f else state.opacity * options.opacity)) {
        ShoulderRow(layout, send, compact)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Movable(state, state.dpadOffset, { state.dpadOffset += it }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (useStick) AnalogStick { x, y -> motion(STICK_LEFT, x, y) } else DPad(send)
                    if (layout.hasStick) {
                        Spacer(Modifier.height(6.dp))
                        SwapPill(if (useStick) "Cần → D-pad" else "D-pad → Cần") {
                            state.stickMode = !useStick
                            // Đổi chế độ khi đang nghiêng cần → nhả cần để game không bị kẹt hướng.
                            motion(STICK_LEFT, 0f, 0f)
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Movable(state, state.faceOffset, { state.faceOffset += it }) { FaceButtons(layout, send, motion) }
        }
        Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            PillKey("SELECT", KeyEvent.KEYCODE_BUTTON_SELECT, send)
            Spacer(Modifier.width(12.dp))
            PillKey("START", KeyEvent.KEYCODE_BUTTON_START, send)
        }
    }
    }
    }
    }
}

/** Hàng nút vai phía trên, theo từng hệ. */
@Composable
private fun ShoulderRow(layout: PadLayout, send: (Int, Int) -> Unit, compact: Boolean) {
    when (layout) {
        PadLayout.GB, PadLayout.RPG -> return
        PadLayout.GEN -> Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            // Tay 6 nút: X = L, Y = nút trên, Z = R (theo cách lõi genesis_plus_gx ánh xạ).
            PillKey("X", KeyEvent.KEYCODE_BUTTON_L1, send)
            PillKey("Y", KeyEvent.KEYCODE_BUTTON_Y, send)
            PillKey("Z", KeyEvent.KEYCODE_BUTTON_R1, send)
        }
        else -> {
            val twoLeft = when (layout) { PadLayout.PS -> "L2" to "L"; PadLayout.N64 -> "Z" to "L"; PadLayout.DC -> "LT" to null; PadLayout.N3DS -> "ZL" to "L"; else -> null to "L" }
            val twoRight = when (layout) { PadLayout.PS -> "R2" to "R"; PadLayout.DC -> "RT" to null; PadLayout.N3DS -> "ZR" to "R"; else -> null to "R" }
            Row(Modifier.fillMaxWidth().padding(bottom = if (compact) 4.dp else 12.dp)) {
                ShoulderGroup(twoLeft, KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_L1, send, compact)
                Spacer(Modifier.weight(1f))
                ShoulderGroup(twoRight, KeyEvent.KEYCODE_BUTTON_R2, KeyEvent.KEYCODE_BUTTON_R1, send, compact)
            }
        }
    }
}

/** Màn ngang xếp hai nút vai cạnh nhau để vùng chạm 72 dp không đẩy phím dưới ra màn. */
@Composable
private fun ShoulderGroup(labels: Pair<String?, String?>, firstKey: Int, secondKey: Int,
    send: (Int, Int) -> Unit, compact: Boolean) {
    val buttons: @Composable () -> Unit = {
        labels.first?.let { PillKey(it, firstKey, send) }
        labels.second?.let { PillKey(it, secondKey, send) }
    }
    if (compact) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { buttons() }
    else Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) { buttons() }
}

@Composable
private fun SwapPill(text: String, onClick: () -> Unit) {
    Text(
        text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(Radius.pill).background(Color(0x99181719)).border(1.dp, Color(0x33FFFFFF), Radius.pill)
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(); if (awaitRelease()) onClick() } }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** Cần analog kính mờ: kéo núm trong vòng tròn, nhả tay về giữa. Gửi (x, y) ∈ [-1,1]; y dương = xuống. */
@Composable
private fun AnalogStick(onMove: (Float, Float) -> Unit) = MonikaStick(onMove)

@Composable
private fun DPad(send: (Int, Int) -> Unit) = MonikaDPad(send)

@Composable
private fun FaceButtons(layout: PadLayout, send: (Int, Int) -> Unit, motion: (Int, Float, Float) -> Unit) {
    val bottom = KeyEvent.KEYCODE_BUTTON_A
    val right = KeyEvent.KEYCODE_BUTTON_B
    val left = KeyEvent.KEYCODE_BUTTON_X
    val top = KeyEvent.KEYCODE_BUTTON_Y
    when (layout) {
        PadLayout.GB, PadLayout.GBA, PadLayout.RPG -> Box(Modifier.size(width = 160.dp, height = 130.dp)) {
            RoundKey("B", bottom, send, Modifier.align(Alignment.BottomStart))
            RoundKey("A", right, send, Modifier.align(Alignment.TopEnd))
        }
        PadLayout.NDS, PadLayout.SNES, PadLayout.N3DS -> Diamond(send, listOf("X" to top, "Y" to left, "A" to right, "B" to bottom))
        PadLayout.PS, PadLayout.PSP -> Diamond(send, listOf("△" to top, "□" to left, "○" to right, "×" to bottom))
        PadLayout.DC -> Diamond(send, listOf("Y" to top, "X" to left, "B" to right, "A" to bottom))
        PadLayout.GEN -> Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(0.dp), verticalAlignment = Alignment.Bottom) {
            RoundKey("A", left, send)
            RoundKey("B", bottom, send, Modifier.padding(bottom = 18.dp))
            RoundKey("C", right, send, Modifier.padding(bottom = 36.dp))
        }
        PadLayout.N64 -> Box(Modifier.size(width = 224.dp, height = 190.dp)) {
            Box(Modifier.align(Alignment.TopEnd)) { CKeys(motion) }
            // C giữ ma trận 2×2 bên phải; A/B bên trái, không chồng vùng chạm 72 dp.
            RoundKey("B", left, send, Modifier.align(Alignment.TopStart))
            RoundKey("A", bottom, send, Modifier.align(Alignment.BottomStart))
        }
    }
}

/** 4 nút C của N64: nhấn = đẩy cần phải về hướng đó. */
@Composable
private fun CKeys(motion: (Int, Float, Float) -> Unit) {
    @Composable fun key(label: String, x: Float, y: Float) {
        MonikaPill(label, { down -> motion(STICK_RIGHT, if (down) x else 0f, if (down) y else 0f) }, Modifier.width(72.dp))
    }
    Column {
        Row { key("C ▲", 0f, -1f); key("C ◀", -1f, 0f) }
        Row { key("C ▼", 0f, 1f); key("C ▶", 1f, 0f) }
    }
}

@Composable
private fun Diamond(send: (Int, Int) -> Unit, keys: List<Pair<String, Int>>) {
    val s = 60.dp * LocalControllerOptions.current.size.coerceAtLeast(1f)
    Box(Modifier.size(s * 3)) {
        val (top, left, right, bottom) = keys
        RoundKey(top.first, top.second, send, Modifier.align(Alignment.TopCenter))
        RoundKey(left.first, left.second, send, Modifier.align(Alignment.CenterStart))
        RoundKey(right.first, right.second, send, Modifier.align(Alignment.CenterEnd))
        RoundKey(bottom.first, bottom.second, send, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun RoundKey(label: String, key: Int, send: (Int, Int) -> Unit, modifier: Modifier = Modifier) {
    MonikaKey(label, { down -> send(if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, key) },
        modifier, primary = label == "A" || label == "C" || label == "×",
        visualState = if (LocalControllerTurbo.current) ControlVisualState.TURBO else ControlVisualState.RELEASED)
}

@Composable
private fun PillKey(label: String, key: Int, send: (Int, Int) -> Unit) {
    MonikaPill(label, { down -> send(if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, key) },
        visualState = if (LocalControllerTurbo.current) ControlVisualState.TURBO else ControlVisualState.RELEASED)
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
