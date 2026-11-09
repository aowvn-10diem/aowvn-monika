package vn.aow.monika.ui.screens

import vn.aow.monika.ui.theme.MonikaIcon
import vn.aow.monika.ui.theme.DockClearance
import android.content.Context
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import vn.aow.monika.AppGraph
import vn.aow.monika.BuildConfig
import vn.aow.monika.R
import vn.aow.monika.ui.theme.ChipBar
import vn.aow.monika.ui.theme.CircleButton
import vn.aow.monika.ui.theme.DarkButton
import vn.aow.monika.ui.theme.GradientButton
import vn.aow.monika.ui.theme.Illustration
import vn.aow.monika.ui.theme.Monika
import vn.aow.monika.ui.theme.MonikaCard
import vn.aow.monika.ui.theme.MonikaHeader
import vn.aow.monika.ui.theme.MotionSetting
import vn.aow.monika.ui.theme.Radius
import vn.aow.monika.ui.theme.Screen
import vn.aow.monika.ui.theme.SoftPillButton
import vn.aow.monika.ui.theme.Spinner
import vn.aow.monika.ui.theme.Tag
import vn.aow.monika.ui.theme.primaryGradient
import vn.aow.monika.ui.theme.secondaryGradient

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: (() -> Unit)? = null, header: (@Composable () -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cfg by AppGraph.config.config.collectAsState()
    val c = Monika.colors
    var open by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableIntStateOf(0) }
    // V193 G1: mặc định tắt; công tắc này là nơi duy nhất bật lại.
    var feedOn by remember { mutableStateOf(AppGraph.feedEnabled) }

    Screen {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = if (onBack == null) DockClearance else 24.dp)) {
            if (header != null) header()
            else MonikaHeader("Cài đặt", subtitle = "Aow Monika", left = onBack?.let { back -> { CircleButton(R.drawable.ic_fluent_arrow_left_24_regular, "Quay lại", back) } })
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // [A95] Bản Play không có câu mời trả tiền ngoài cửa hàng (anti-steering).
                // [A65] Bản Play ẩn thẻ tài khoản: không mời đăng nhập hay sang trang web AowVN.
                // Thẻ tối: app + phiên bản + cập nhật.
                MonikaCard(Modifier.fillMaxWidth(), dark = true, shape = Radius.hero, padding = PaddingValues(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(64.dp).clip(Radius.pill).background(c.textOnDark.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
                            Illustration(R.drawable.fluent3d_joystick, Modifier.size(44.dp))
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text("Aow Monika", style = Monika.type.cardTitle, color = c.textOnDark)
                            Text("Bản ${BuildConfig.VERSION_NAME} · cấu hình v${cfg.configVersion}", style = Monika.type.caption, color = c.textOnDarkSecondary)
                        }
                    }
                }

                SettingGroup("motion", open, { open = it }, R.drawable.ic_fluent_flash_24_regular, primaryGradient(), "Giao diện & hiệu ứng", "Tự điều chỉnh theo cấu hình máy") {
                    val modes = listOf("auto", "FULL", "LITE", "OFF")
                    val label = mapOf("auto" to "Tự động", "FULL" to "Đầy đủ", "LITE" to "Tiết kiệm", "OFF" to "Tắt")
                    ChipBar(modes, MotionSetting.mode.value, { label[it] ?: it }, { MotionSetting.set(it) }, accent = true, contentPadding = PaddingValues(0.dp))
                    Text(
                        "Tự động: máy mạnh dùng hiệu ứng đầy đủ, máy yếu (RAM < 3GB hoặc < 6 nhân) dùng bản tiết kiệm, và tắt hẳn khi bạn tắt hiệu ứng trong Cài đặt Android.",
                        style = Monika.type.caption, color = c.textSecondary,
                    )
                }

                SettingGroup("feed", open, { open = it }, R.drawable.ic_fluent_news_24_regular, primaryGradient(), "Bài viết & tin tức", if (feedOn) "Đang bật" else "Đang tắt") {
                    ChipBar(listOf(true, false), feedOn, { if (it) "Bật" else "Tắt" }, { on ->
                        feedOn = on
                        AppGraph.setFeedEnabled(on)
                    }, accent = true, contentPadding = PaddingValues(0.dp))
                    Text(
                        "Khi bật, app tải bài viết từ aow.vn. Mặc định tắt để bản tự build không gọi dịch vụ AowVN.",
                        style = Monika.type.caption, color = c.textSecondary,
                    )
                }

                SettingGroup("controller", open, { open = it }, R.drawable.ic_fluent_xbox_controller_24_regular, primaryGradient(), "Tay cầm ảo", "Rung, hiệu ứng bấm và cỡ nút") {
                    vn.aow.monika.ui.controls.ControllerSettings(AppGraph.prefs)
                }

                // V78c: mục "Màn hình" cho lõi nhiều màn hình (NDS); nội dung lấy từ config `cores.<id>.screen`.
                cfg.cores.filterValues { it.screen != null }.forEach { (coreId, def) ->
                    SettingGroup("screen-$coreId", open, { open = it }, R.drawable.ic_fluent_phone_24_regular, primaryGradient(), "Màn hình", "Bố cục, tỉ lệ và khoảng cách hai màn hình NDS") {
                        vn.aow.monika.ui.controls.ScreenSettings(coreId, def.screen!!, def.options)
                    }
                }

                SettingGroup("translate", open, { open = it }, R.drawable.ic_fluent_globe_24_regular, primaryGradient(), "Dịch màn hình game", "Dùng khóa API của bạn · gói dịch offline sắp có") {
                    val ts = AppGraph.translateSettings
                    var provider by remember { mutableStateOf(ts.provider) }
                    var lang by remember { mutableStateOf(ts.sourceLang) }
                    var key by remember { mutableStateOf(ts.apiKey) }
                    var base by remember { mutableStateOf(ts.baseUrl) }
                    var model by remember { mutableStateOf(ts.model) }
                    var vision by remember { mutableStateOf(ts.vision) }
                    var testing by remember { mutableStateOf(false) }
                    val names = mapOf("google" to "Google Dịch API", "ai" to "API AI (OpenAI/Gemini…)")
                    ChipBar(listOf("google", "ai"), provider, { names[it] ?: it }, { provider = it; ts.provider = it }, accent = true, contentPadding = PaddingValues(0.dp))
                    ChipBar(listOf("en", "ja"), lang, { if (it == "ja") "Chữ trong game: Nhật" else "Chữ trong game: Anh" }, { lang = it; ts.sourceLang = it }, accent = true, contentPadding = PaddingValues(0.dp))
                    Text(
                        when (provider) {
                            "google" -> "Dùng khóa Google Cloud Translation của bạn. Chữ được đọc trên máy rồi chỉ gửi phần chữ (không gửi ảnh) tới Google; chi phí tính theo tài khoản của bạn."
                            else -> "Dùng API kiểu OpenAI của bạn (OpenAI, Gemini qua đường dẫn tương thích OpenAI, OpenRouter…). Hiểu ngữ cảnh game tốt hơn nhưng tính phí theo lượng chữ; bật \"AI nhìn ảnh\" thì ảnh chụp game cũng được gửi đi."
                        },
                        style = Monika.type.caption, color = c.textSecondary,
                    )
                    Text(
                        "Dịch offline miễn phí đang được làm thành gói tải riêng (chỉ tải khi bạn bật), để app không bị nặng.",
                        style = Monika.type.caption, color = c.textSecondary,
                    )
                    androidx.compose.material3.OutlinedTextField(
                        key, { key = it; ts.apiKey = it }, Modifier.fillMaxWidth(), singleLine = true, shape = Radius.small, label = { Text("Khóa API") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    )
                    Text("Khóa được mã hóa và chỉ lưu trên máy này.", style = Monika.type.caption, color = c.textSecondary)
                    if (provider == "ai") {
                        androidx.compose.material3.OutlinedTextField(base, { base = it; ts.baseUrl = it }, Modifier.fillMaxWidth(), singleLine = true, shape = Radius.small, label = { Text("Địa chỉ API (kết thúc /v1)") })
                        androidx.compose.material3.OutlinedTextField(model, { model = it; ts.model = it }, Modifier.fillMaxWidth(), singleLine = true, shape = Radius.small, label = { Text("Tên mô hình") })
                        ChipBar(listOf(false, true), vision, { if (it) "AI nhìn ảnh: Bật" else "AI nhìn ảnh: Tắt" }, { vision = it; ts.vision = it }, accent = true, contentPadding = PaddingValues(0.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SoftPillButton(if (testing) "Đang thử…" else "Dịch thử", {
                            if (testing) return@SoftPillButton
                            testing = true
                            scope.launch {
                                val r = runCatching { AppGraph.screenTranslator.translateTexts(listOf(if (lang == "ja") "こんにちは、勇者よ。" else "Welcome, hero. Your journey begins here.")).first() }
                                Toast.makeText(context, r.getOrElse { "Lỗi: ${it.message}" }, Toast.LENGTH_LONG).show()
                                testing = false
                            }
                        }, R.drawable.ic_fluent_globe_24_regular)
                        SoftPillButton("Xóa bộ nhớ dịch", {
                            AppGraph.translateMemory.clear(); Toast.makeText(context, "Đã xóa bộ nhớ dịch", Toast.LENGTH_SHORT).show()
                        }, R.drawable.ic_fluent_delete_24_regular)
                    }
                }

                SettingGroup("emu", open, { open = it }, R.drawable.ic_fluent_top_speed_24_regular, primaryGradient(), "Hiệu năng giả lập", "Mức chất lượng theo sức máy · tải trước game") {
                    val tiers = listOf("auto", "lite", "mid", "full")
                    val tierLabel = mapOf("auto" to "Tự động", "lite" to "Tiết kiệm", "mid" to "Cân bằng", "full" to "Mạnh")
                    var emuPerf by remember { mutableStateOf(AppGraph.prefs.emuPerf) }
                    ChipBar(tiers, emuPerf, { tierLabel[it] ?: it }, { emuPerf = it; AppGraph.prefs.emuPerf = it }, accent = true, contentPadding = PaddingValues(0.dp))
                    Text(
                        "Tự động: máy mạnh (RAM ≥ 7 GB) dùng độ phân giải cao hơn cho PSP/Dreamcast/N64, máy yếu (RAM < 3,5 GB, ít nhân hoặc đang tiết kiệm pin) giảm độ phân giải NDS, bật bỏ khung tự động và âm thanh bộ đệm lớn để đỡ giật. Áp dụng từ lần mở game tiếp theo.",
                        style = Monika.type.caption, color = c.textSecondary,
                    )
                    var preload by remember { mutableStateOf(AppGraph.prefs.preloadGame) }
                    ChipBar(listOf(true, false), preload, { if (it) "Tải trước game: Bật" else "Tải trước game: Tắt" }, { preload = it; AppGraph.prefs.preloadGame = it }, accent = true, contentPadding = PaddingValues(0.dp))
                    Text(
                        "Bật: khi sắp chơi (vd. mở Thư viện có game \"Tiếp tục chơi\"), Monika đọc sẵn file game và dựng sẵn trình chạy ở nền để vào game nhanh hơn.",
                        style = Monika.type.caption, color = c.textSecondary,
                    )
                    var autoCrash by remember { mutableStateOf(AppGraph.prefs.autoSendCrash) }
                    ChipBar(listOf(true, false), autoCrash, { if (it) "Tự gửi báo lỗi game: Bật" else "Tự gửi báo lỗi game: Tắt" }, { autoCrash = it; AppGraph.prefs.autoSendCrash = it }, accent = true, contentPadding = PaddingValues(0.dp))
                    Text(
                        "Khi lõi giả lập sập hoặc không chạy được game, Monika tự gửi báo lỗi ẩn danh (tên game, hệ máy, lõi, kiểu máy, log của lõi; không có tên bạn, email hay đường dẫn file) để AowVN sửa nhanh.",
                        style = Monika.type.caption, color = c.textSecondary,
                    )
                }

                SettingGroup(
                    "storage", open, { open = it }, R.drawable.ic_fluent_storage_24_regular,
                    primaryGradient(),
                    "Dung lượng & bộ nhớ đệm", "Tự dọn khi đầy · giới hạn ${cacheLimitLabel()}",
                ) { StorageSettingsContent() }

                if (AppGraph.azahar.available()) SettingGroup(
                    "n3ds", open, { open = it }, R.drawable.ic_fluent_layer_24_regular,
                    primaryGradient(),
                    "Nintendo 3DS", "Cài file .cia · cheat trong menu game",
                ) {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    val scope = androidx.compose.runtime.rememberCoroutineScope()
                    var busy by remember { mutableStateOf(false) }
                    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                        val act = generateSequence(ctx) { (it as? android.content.ContextWrapper)?.baseContext }.filterIsInstance<android.app.Activity>().firstOrNull()
                        if (uris.isNotEmpty() && act != null) {
                            busy = true
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                val paths = runCatching { vn.aow.monika.azahar.AzaharInstallActivity.stage(act, uris) }.getOrNull()
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    busy = false
                                    if (paths != null) vn.aow.monika.azahar.AzaharInstallActivity.launch(act, paths)
                                }
                            }
                        }
                    }
                    Text("File .cia cài vào bộ nhớ máy 3DS do Monika quản lý (game, bản cập nhật, DLC). Cheat: mở game → Menu → Mã cheat.", style = Monika.type.caption, color = c.textSecondary)
                    SoftPillButton(if (busy) "Đang chuẩn bị file…" else "Chọn file .cia để cài", { if (!busy) pick.launch(arrayOf("*/*")) }, R.drawable.ic_fluent_document_24_regular)
                }

                SettingGroup(
                    "vault", open, { open = it }, R.drawable.ic_fluent_storage_24_regular,
                    primaryGradient(),
                    "Save game", "Monika quản lý · sao lưu / chuyển máy",
                ) {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    val scope = androidx.compose.runtime.rememberCoroutineScope()
                    var msg by remember { mutableStateOf<String?>(null) }
                    var stat by remember { mutableStateOf(vn.aow.monika.library.SaveVault.summary(ctx)) }
                    val exp = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip")) { u ->
                        if (u != null) scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val n = runCatching { vn.aow.monika.library.SaveVault.export(ctx, u) }.getOrDefault(-1)
                            msg = if (n >= 0) "Đã xuất $n file save" else "Xuất thất bại"
                        }
                    }
                    val imp = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { u ->
                        if (u != null) scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val n = runCatching { vn.aow.monika.library.SaveVault.import(ctx, u) }.getOrDefault(-1)
                            stat = vn.aow.monika.library.SaveVault.summary(ctx)
                            msg = if (n >= 0) "Đã nhập $n file save" else "Nhập thất bại (file không đúng)"
                        }
                    }
                    Text("Đang giữ ${stat.first} file save · ${stat.second / 1024} KB (save pin, save state mọi giả lập, dữ liệu 3DS).", style = Monika.type.caption, color = c.textSecondary)
                    SoftPillButton("Xuất save ra file .zip", { exp.launch("aow-monika-save.zip") }, R.drawable.ic_fluent_document_24_regular)
                    SoftPillButton("Nhập save từ file .zip", { imp.launch(arrayOf("application/zip", "application/octet-stream")) }, R.drawable.ic_fluent_document_24_regular)
                    msg?.let { Text(it, style = Monika.type.bodyStrong, color = c.text) }
                }

                SettingGroup(
                    "crashlog", open, { open = it }, R.drawable.ic_fluent_info_24_regular,
                    primaryGradient(),
                    "Nhật ký lỗi", "Game sập / lõi lỗi · gửi cho AowVN để sửa",
                ) {
                    Text("Khi game hoặc lõi giả lập sập, Monika tự lưu thông tin (lõi, giai đoạn, log) — không có đường dẫn hay tài khoản của bạn.", style = Monika.type.caption, color = c.textSecondary)
                    SoftPillButton("Mở nhật ký lỗi", { vn.aow.monika.diag.Diagnostics.logOpen.value = true }, R.drawable.ic_fluent_document_24_regular)
                }

                SettingGroup("cores", open, { open = it }, R.drawable.ic_fluent_layer_24_regular, primaryGradient(), "Lõi giả lập", "${cfg.cores.size} lõi · cài qua Google Play") {
                    // Hệ có nhiều lõi (vd. NDS): user tự chọn; game chạy lỗi thì đổi lõi khác thử.
                    cfg.systems.filter { it.altCores.size > 1 }.forEach { sys ->
                        var chosen by remember(sys.id) { mutableStateOf(AppGraph.prefs.coreOverride(sys.id) ?: sys.core.orEmpty()) }
                        Text("Lõi cho ${sys.name}", style = Monika.type.bodyStrong, color = c.text)
                        ChipBar(sys.altCores, chosen, { it }, {
                            chosen = it
                            AppGraph.prefs.setCoreOverride(sys.id, it)
                        }, accent = true, contentPadding = PaddingValues(0.dp))
                    }
                    Text("Game chạy lỗi hoặc giật thì thử đổi lõi khác. Lõi được phân phối qua Google Play.", style = Monika.type.caption, color = c.textSecondary)
                    cfg.cores.forEach { (id, def) ->
                        val installed = remember(id, refreshTick) { AppGraph.cores.installedVersion(id) }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(id, style = Monika.type.body, color = c.text, modifier = Modifier.weight(1f))
                            Tag(when (installed) { null -> "Chưa cài"; def.version -> "Đã có"; else -> "Có bản mới" }, accent = installed == def.version)
                            if (installed != null) SoftPillButton("Xóa", { AppGraph.cores.delete(id); refreshTick++ }, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Mục cài đặt dạng thẻ, bấm để mở rộng (hiệu ứng mở/đóng theo cấu hình máy). */
@Composable
private fun SettingGroup(
    id: String, open: String?, onToggle: (String?) -> Unit,
    @DrawableRes icon: Int, iconBrush: Brush, title: String, subtitle: String,
    content: @Composable () -> Unit,
) {
    val c = Monika.colors
    val motion = Monika.motion
    val expanded = open == id
    MonikaCard(Modifier.fillMaxWidth(), shape = Radius.medium, padding = PaddingValues(14.dp), onClick = { onToggle(if (expanded) null else id) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(Radius.pill).background(iconBrush), contentAlignment = Alignment.Center) {
                MonikaIcon(icon, null, Modifier.size(24.dp), tint = c.textOnDark)
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(title, style = Monika.type.cardTitle, color = c.text)
                Text(subtitle, style = Monika.type.caption, color = c.textSecondary)
            }
            MonikaIcon(R.drawable.ic_fluent_chevron_right_24_regular, null, Modifier.size(22.dp), tint = c.textSecondary)
        }
        AnimatedVisibility(
            expanded,
            enter = if (motion.enabled) expandVertically(tween(motion.normal)) + fadeIn(tween(motion.normal)) else fadeIn(tween(0)),
            exit = if (motion.enabled) shrinkVertically(tween(motion.fast)) + fadeOut(tween(motion.fast)) else fadeOut(tween(0)),
        ) {
            Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
        }
    }
}


fun openUrl(context: Context, url: String) = vn.aow.monika.play.PlayLinks.open(context, url)
