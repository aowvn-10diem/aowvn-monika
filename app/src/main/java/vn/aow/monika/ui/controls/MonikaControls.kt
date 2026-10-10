package vn.aow.monika.ui.controls

import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import vn.aow.monika.Prefs
import vn.aow.monika.ui.theme.Monika
import vn.aow.monika.ui.theme.Radius
import vn.aow.monika.ui.theme.primaryGradient
import kotlin.math.roundToInt

val LocalControllerOptions = staticCompositionLocalOf { ControllerOptions() }
val LocalControllerTurbo = staticCompositionLocalOf { false }

/** Game chỉ đọc file thiết lập chính, không ghi đè SharedPreferences từ tiến trình :game. */
@Composable
fun ControllerOptionsProvider(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val options = remember(context) { Prefs(context).controllerOptions }
    CompositionLocalProvider(LocalControllerOptions provides options, content = content)
}

enum class ControlVisualState { RELEASED, PRESSED, HELD, TURBO, DISABLED }

/** Nút chung nhận cạnh nhấn/nhả; không tự đổi mã phím hay tạo nhịp turbo xuống engine. */
@Composable
fun MonikaKey(
    label: String,
    onPress: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    visualState: ControlVisualState = ControlVisualState.RELEASED,
) = PressControl(label, onPress, modifier, primary, enabled, visualState, pill = false)

@Composable
fun MonikaPill(
    label: String,
    onPress: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    visualState: ControlVisualState = ControlVisualState.RELEASED,
) = PressControl(label, onPress, modifier, false, enabled, visualState, pill = true)

@Composable
private fun PressControl(
    label: String, onPress: (Boolean) -> Unit, modifier: Modifier, primary: Boolean,
    enabled: Boolean, visualState: ControlVisualState, pill: Boolean,
) {
    val c = Monika.colors
    val motion = Monika.motion
    val options = LocalControllerOptions.current
    val view = LocalView.current
    val callback by rememberUpdatedState(onPress)
    var touching by remember { mutableStateOf(false) }
    val usable = enabled && visualState != ControlVisualState.DISABLED
    val pressed = touching || visualState in listOf(ControlVisualState.PRESSED, ControlVisualState.HELD)
    val animate = options.pressAnimation && motion.enabled
    val scale by animateFloatAsState(if (pressed && animate) .92f else 1f,
        tween(if (animate) motion.fast else 0, easing = motion.easing), label = "Lún nút")
    val sink by animateFloatAsState(if (pressed && animate) 2f else 0f,
        tween(if (animate) motion.fast else 0, easing = motion.easing), label = "Dịch nút")
    val shape = Radius.pill
    val turbo = visualState == ControlVisualState.TURBO
    val rotation = if (turbo && motion.enabled) {
        val transition = rememberInfiniteTransition(label = "Viền tua nhanh")
        transition.animateFloat(0f, 360f,
            infiniteRepeatable(tween(motion.slow * 4, easing = LinearEasing)), label = "Viền chạy").value
    } else 0f
    val description = "Nút $label"
    val interaction = Modifier.semantics {
        contentDescription = description
        role = Role.Button
        if (!usable) disabled()
        stateDescription = when {
            !usable -> "Đã tắt"
            visualState == ControlVisualState.TURBO -> "Tua nhanh"
            pressed -> "Đang giữ"
            else -> "Sẵn sàng"
        }
        if (usable) onClick {
            controllerHaptic(view, options.haptic)
            callback(true); callback(false); true
        }
    }.pointerInput(usable, label) {
        if (usable) awaitEachGesture {
            val down = awaitFirstDown(); down.consume()
            touching = true
            controllerHaptic(view, options.haptic)
            val gestureCallback = callback
            try {
                gestureCallback(true)
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    change.consume()
                }
            } finally { touching = false; gestureCallback(false) }
        }
    }
    val diameter = 64.dp * options.size
    val touch = (diameter + 8.dp).coerceAtLeast(72.dp)
    val bounds = if (pill) Modifier.heightIn(min = 72.dp).width((if (label.length > 2) 96.dp else 72.dp) * options.size.coerceAtLeast(1f))
        else Modifier.size(touch)
    Box(modifier.then(bounds).then(interaction), contentAlignment = Alignment.Center) {
        val face = if (pill) Modifier.fillMaxWidth().height(40.dp) else Modifier.size(diameter)
        val surface = when { !usable -> c.surfaceDarkSoft; pressed -> c.surfaceDark; else -> c.controlSurface }
        Box(face.graphicsLayer { scaleX = scale; scaleY = scale; translationY = sink.dp.toPx() }
            .shadow(if (animate && !pressed) 6.dp else 0.dp, shape)
            .clip(shape)
            .background(if (primary && usable && !pressed) primaryGradient() else Brush.linearGradient(listOf(surface, surface)))
            // Hai vòng sáng/tối: ảnh game sáng hoặc tối đều còn một đường bao rõ.
            .border(4.dp, if (turbo) c.accentOrange else c.textOnDark, shape)
            .border(2.dp, c.controlInk, shape)
            .drawWithContent {
                drawContent()
                if (turbo) drawArc(c.accentPink, rotation - 90f, 70f, false,
                    topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                    size = size.copy(width = size.width - 2.dp.toPx(), height = size.height - 2.dp.toPx()), style = Stroke(2.dp.toPx()))
            },
            contentAlignment = Alignment.Center) {
            if (options.labels) Text(label, style = if (pill) Monika.type.caption else Monika.type.sectionTitle,
                color = if (usable && !pressed) c.controlInk else if (usable) c.textOnDark else c.textOnDarkSecondary)
        }
    }
}

private val CrossShape = GenericShape { size, _ ->
    val a = size.width / 3f; val b = size.width * 2f / 3f
    moveTo(a, 0f); lineTo(b, 0f); lineTo(b, a); lineTo(size.width, a)
    lineTo(size.width, b); lineTo(b, b); lineTo(b, size.height); lineTo(a, size.height)
    lineTo(a, b); lineTo(0f, b); lineTo(0f, a); lineTo(a, a); close()
}

@Composable
fun MonikaDPad(send: (Int, Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Monika.colors
    val view = LocalView.current
    val options = LocalControllerOptions.current
    val callback by rememberUpdatedState(send)
    var active by remember { mutableStateOf(emptySet<Int>()) }
    val motion = Monika.motion
    val animate = options.pressAnimation && motion.enabled
    val scale by animateFloatAsState(if (active.isNotEmpty() && animate) .92f else 1f,
        tween(if (animate) motion.fast else 0, easing = motion.easing), label = "Lún phím hướng")
    Box(modifier.size(150.dp * options.size).semantics { contentDescription = "Phím hướng, hỗ trợ đi chéo" }
        .pointerInput(options) {
            awaitEachGesture {
                val gestureCallback = callback
                fun update(next: Set<Int>) {
                    (active - next).forEach { gestureCallback(KeyEvent.ACTION_UP, it) }
                    (next - active).forEach { gestureCallback(KeyEvent.ACTION_DOWN, it) }
                    active = next
                }
                val down = awaitFirstDown(); down.consume()
                controllerHaptic(view, options.haptic)
                try {
                    fun at(p: Offset) = directionKeys(p.x - size.width / 2f, p.y - size.height / 2f, size.width.toFloat())
                    update(at(down.position))
                    while (true) {
                        val e = awaitPointerEvent(); val change = e.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        update(at(change.position)); change.consume()
                    }
                } finally { update(emptySet()) }
            }
        }, contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            scaleX = scale; scaleY = scale
            translationY = if (active.isNotEmpty() && animate) 2.dp.toPx() else 0f
        }, contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().shadow(if (animate && active.isEmpty()) 6.dp else 0.dp, CrossShape)
            .clip(CrossShape).background(if (active.isEmpty()) c.controlSurface else c.surfaceDark)
            .border(4.dp, c.textOnDark, CrossShape).border(2.dp, c.controlInk, CrossShape))
        listOf("▲" to KeyEvent.KEYCODE_DPAD_UP, "▼" to KeyEvent.KEYCODE_DPAD_DOWN,
            "◀" to KeyEvent.KEYCODE_DPAD_LEFT, "▶" to KeyEvent.KEYCODE_DPAD_RIGHT).forEachIndexed { index, (text, key) ->
            val distance = 50.dp * options.size
            val offset = when (index) { 0 -> Modifier.offset(y = -distance); 1 -> Modifier.offset(y = distance)
                2 -> Modifier.offset(x = -distance); else -> Modifier.offset(x = distance) }
            Text(text, style = Monika.type.button, color = if (key in active) c.accentOrange else if (active.isNotEmpty()) c.textOnDark else c.controlInk,
                modifier = offset.semantics {
                    contentDescription = when (index) { 0 -> "Hướng lên"; 1 -> "Hướng xuống"; 2 -> "Hướng trái"; else -> "Hướng phải" }
                    role = Role.Button
                    onClick { controllerHaptic(view, options.haptic); callback(KeyEvent.ACTION_DOWN, key); callback(KeyEvent.ACTION_UP, key); true }
                })
        }
        }
    }
}

@Composable
fun MonikaStick(onMove: (Float, Float) -> Unit, modifier: Modifier = Modifier) {
    val c = Monika.colors
    val motion = Monika.motion
    val view = LocalView.current
    val options = LocalControllerOptions.current
    val callback by rememberUpdatedState(onMove)
    var knob by remember { mutableStateOf(Offset.Zero) }
    var touching by remember { mutableStateOf(false) }
    // Chỉ hồi tâm là chuyển động; tín hiệu gửi lõi về 0 ngay khi nhả, không chờ hoạt ảnh.
    val returnSpec = if (!touching && motion.enabled) spring<Float>(dampingRatio = .7f,
        stiffness = 39.48f / (motion.normal / 1000f).let { it * it }) else tween(0)
    val x by animateFloatAsState(knob.x, returnSpec, label = "Hồi tâm X")
    val y by animateFloatAsState(knob.y, returnSpec, label = "Hồi tâm Y")
    Box(modifier.size(148.dp * options.size).clip(Radius.pill).background(c.controlSurface).border(4.dp, c.textOnDark, Radius.pill).border(2.dp, c.controlInk, Radius.pill)
        .semantics { contentDescription = "Cần analog" }
        .pointerInput(options) {
            awaitEachGesture {
                val down = awaitFirstDown(); down.consume(); touching = true
                val gestureCallback = callback
                controllerHaptic(view, options.haptic)
                val radius = (size.width / 2f - (32.dp * options.size).toPx()).coerceAtLeast(1f)
                val center = Offset(size.width / 2f, size.height / 2f)
                fun update(pos: Offset) {
                    val raw = pos - center
                    val distance = raw.getDistance()
                    knob = if (distance > radius) raw * (radius / distance) else raw
                    gestureCallback(stickValue(knob.x / radius), stickValue(knob.y / radius))
                }
                try {
                    update(down.position)
                    while (true) {
                        val e = awaitPointerEvent(); val change = e.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        update(change.position); change.consume()
                    }
                } finally { touching = false; knob = Offset.Zero; gestureCallback(0f, 0f) }
            }
        }, contentAlignment = Alignment.Center) {
        Box(Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }.size(64.dp * options.size)
            .clip(Radius.pill).background(primaryGradient()).border(4.dp, c.textOnDark, Radius.pill).border(2.dp, c.controlInk, Radius.pill))
    }
}
