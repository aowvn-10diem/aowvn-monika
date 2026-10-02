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
import vn.aow.monika.config.ExternalApp
import vn.aow.monika.download.DownloadLink
import vn.aow.monika.download.LinkResolver
import vn.aow.monika.runner.ExternalApps
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
    var subscribed by remember { mutableStateOf(AppGraph.prefs.subscribedLabels) }

    Screen {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = if (onBack == null) DockClearance else 24.dp)) {
            if (header != null) header()
            else MonikaHeader("Cài đặt", subtitle = "Aow Monika", left = onBack?.let { back -> { CircleButton(R.drawable.ic_fluent_arrow_left_24_regular, "Quay lại", back) } })
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SupportStrip()
                if (cfg.account.enabled) AccountCard()
                // Thẻ tối: app + phiên bản + cập nhật.
                MonikaCard(Modifier.fillMaxWidth(), dark = true, shape = Radius.hero, padding = PaddingValues(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(64.dp).clip(Radius.pill).background(Color(0x22FFFFFF)), contentAlignment = Alignment.Center) {
                            Illustration(R.drawable.fluent3d_joystick, Modifier.size(44.dp))
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text("Aow Monika", style = Monika.type.cardTitle, color = Color.White)
                            Text("Bản ${BuildConfig.VERSION_NAME} · cấu hình v${cfg.configVersion}", style = Monika.type.caption, color = c.textOnDarkSecondary)
                        }
                    }
                    if (cfg.app.latestVersionCode > BuildConfig.VERSION_CODE) {
                        Text("Có bản mới ${cfg.app.latestVersionName}: ${cfg.app.changelog}", style = Monika.type.caption, color = c.accentYellow, modifier = Modifier.padding(top = 12.dp))
                        if (cfg.app.apkUrl.isNotBlank()) GradientButton("Tải bản mới", {
                            scope.launch { runCatching { AppGraph.downloader.enqueue(cfg.app.apkUrl, isTool = true) } }
                            Toast.makeText(context, "Đang tải bản mới…", Toast.LENGTH_SHORT).show()
                        }, Modifier.padding(top = 10.dp), icon = R.drawable.ic_fluent_arrow_download_24_regular, height = 48.dp)
                    }
                    Row(Modifier.padding(top = 12.dp)) {
                        SoftPillButton("Cập nhật cấu hình", {
                            scope.launch {
                                val r = AppGraph.config.refresh(force = true)
                                Toast.makeText(context, if (r.isSuccess) "Đã cập nhật cấu hình" else "Chưa cập nhật được: ${r.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                            }
                        }, R.drawable.ic_fluent_arrow_sync_24_regular)
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

                SettingGroup("apps", open, { open = it }, R.drawable.ic_fluent_games_24_regular, secondaryGradient(), "App chạy game bổ sung", cfg.externalApps.joinToString(", ") { it.name }) {
                    cfg.externalApps.forEach { app ->
                        val installed = remember(app, refreshTick) { ExternalApps.installedPackage(context, app) != null }
                        MonikaCard(Modifier.fillMaxWidth(), shape = Radius.medium) { ExternalAppDetails(app, installed) }
                    }
                    SoftPillButton("Kiểm tra lại trạng thái cài đặt", { refreshTick++ }, R.drawable.ic_fluent_arrow_sync_24_regular)
                }

                SettingGroup("notify", open, { open = it }, R.drawable.ic_fluent_alert_24_regular, Brush.linearGradient(listOf(Color(0xFFFF806E), Color(0xFFFFC95C))), "Thông báo bài mới", if (subscribed.isEmpty()) "Nhận tất cả" else "${subscribed.size} nhãn") {
                    Text("Chọn nhãn muốn nhận. Không chọn gì = nhận tất cả.", style = Monika.type.caption, color = c.textSecondary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        cfg.feedLabels().forEach { label ->
                            val on = label in subscribed
                            Text(
                                shortLabel(label), style = Monika.type.caption, color = if (on) Color.White else c.chipText,
                                modifier = Modifier.clip(Radius.pill)
                                    .background(if (on) primaryGradient() else Brush.linearGradient(listOf(c.chip, c.chip)))
                                    .clickable {
                                        subscribed = if (on) subscribed - label else subscribed + label
                                        AppGraph.prefs.subscribedLabels = subscribed
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                }

                SettingGroup(
                    "storage", open, { open = it }, R.drawable.ic_fluent_storage_24_regular,
                    Brush.linearGradient(listOf(Color(0xFF638EFF), Color(0xFF66CFF3))),
                    "Dung lượng & bộ nhớ đệm", "Tự dọn khi đầy · giới hạn ${cacheLimitLabel()}",
                ) { StorageSettingsContent() }

                if (AppGraph.azahar.available()) SettingGroup(
                    "n3ds", open, { open = it }, R.drawable.ic_fluent_layer_24_regular,
                    Brush.linearGradient(listOf(Color(0xFFE95CC8), Color(0xFFFF7A32))),
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
                    Brush.linearGradient(listOf(Color(0xFFFFB347), Color(0xFFFF7A32))),
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
                    Brush.linearGradient(listOf(Color(0xFFFF806E), Color(0xFFE95CC8))),
                    "Nhật ký lỗi", "Game sập / lõi lỗi · gửi cho AowVN để sửa",
                ) {
                    Text("Khi game hoặc lõi giả lập sập, Monika tự lưu thông tin (lõi, giai đoạn, log) — không có đường dẫn hay tài khoản của bạn.", style = Monika.type.caption, color = c.textSecondary)
                    SoftPillButton("Mở nhật ký lỗi", { vn.aow.monika.diag.Diagnostics.logOpen.value = true }, R.drawable.ic_fluent_document_24_regular)
                }

                SettingGroup("cores", open, { open = it }, R.drawable.ic_fluent_layer_24_regular, Brush.linearGradient(listOf(Color(0xFF63D68A), Color(0xFF66CFF3))), "Lõi giả lập", "${cfg.cores.size} lõi · tải khi chơi lần đầu") {
                    // Hệ có nhiều lõi (vd. NDS): user tự chọn; game chạy lỗi thì đổi lõi khác thử.
                    cfg.systems.filter { it.altCores.size > 1 }.forEach { sys ->
                        var chosen by remember(sys.id) { mutableStateOf(AppGraph.prefs.coreOverride(sys.id) ?: sys.core.orEmpty()) }
                        Text("Lõi cho ${sys.name}", style = Monika.type.bodyStrong, color = c.text)
                        ChipBar(sys.altCores, chosen, { it }, {
                            chosen = it
                            AppGraph.prefs.setCoreOverride(sys.id, it)
                        }, accent = true, contentPadding = PaddingValues(0.dp))
                    }
                    Text("Game chạy lỗi hoặc giật thì thử đổi lõi khác. Lõi mới tự tải khi chơi lần đầu.", style = Monika.type.caption, color = c.textSecondary)
                    cfg.cores.forEach { (id, def) ->
                        val installed = remember(id, refreshTick) { AppGraph.cores.installedVersion(id) }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(id, style = Monika.type.body, color = c.text, modifier = Modifier.weight(1f))
                            Tag(when (installed) { null -> "Chưa tải"; def.version -> "Đã có"; else -> "Có bản mới" }, accent = installed == def.version)
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
                MonikaIcon(icon, null, Modifier.size(24.dp), tint = Color.White)
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

/**
 * Thông tin 1 app ngoài: trạng thái, nút tải (app + plugin), hướng dẫn cài.
 * Link là trang aow.vn/p/... → app đọc trang qua feed, liệt kê link tải; host tải thẳng thì tải + cài trong app.
 */
@Composable
fun ExternalAppDetails(app: ExternalApp, installed: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = Monika.colors
    var pageLinks by remember(app) { mutableStateOf<List<DownloadLink>?>(null) }
    var loading by remember(app) { mutableStateOf(false) }
    var pageError by remember(app) { mutableStateOf<String?>(null) }

    fun openSource(url: String) {
        if (LinkResolver.isBlogPage(url)) {
            loading = true
            pageError = null
            scope.launch {
                runCatching { AppGraph.feed.fetchPage(url, forceRefresh = true) }
                    .onSuccess { page ->
                        val links = page?.let { LinkResolver.extract(it.contentHtml, AppGraph.config.current.downloadHosts) }.orEmpty()
                        pageLinks = links
                        if (page == null) pageError = "Không tìm thấy trang trên aow.vn."
                        else if (links.isEmpty()) pageError = "Trang chưa có link tải. Admin đang cập nhật."
                    }
                    .onFailure { pageError = "Không đọc được trang: ${it.message}" }
                loading = false
            }
        } else startToolDownload(context, scope, url)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Illustration(R.drawable.fluent3d_toolbox, Modifier.size(36.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(app.name, style = Monika.type.cardTitle, color = c.text)
                if (app.description.isNotBlank()) Text(app.description, style = Monika.type.caption, color = c.textSecondary)
            }
            if (installed) MonikaIcon(R.drawable.ic_fluent_checkmark_circle_24_filled, "Đã cài", Modifier.size(24.dp), tint = c.success)
            else Tag("Chưa cài")
        }
        when {
            app.downloadUrl.isBlank() -> Text("Link tải đang được cập nhật.", style = Monika.type.caption, color = c.textSecondary)
            pageLinks.isNullOrEmpty() -> GradientButton(
                if (installed) "Tải lại / cập nhật" else "Tải ${app.name}", { openSource(app.downloadUrl) },
                Modifier.fillMaxWidth(), icon = R.drawable.ic_fluent_arrow_download_24_regular, enabled = !loading, height = 48.dp,
            )
        }
        if (loading) Spinner()
        pageError?.let { Text(it, style = Monika.type.caption, color = c.danger) }
        pageLinks?.forEach { link ->
            val title = listOf(link.label.ifBlank { "Tải" }, link.hostName).distinct().joinToString(" · ")
            if (link.directUrl != null) GradientButton(title, { startToolDownload(context, scope, link.directUrl) }, Modifier.fillMaxWidth(), icon = R.drawable.ic_fluent_arrow_download_24_regular, height = 48.dp)
            else DarkButton(title, { openInApp(context, link.pageUrl) }, Modifier.fillMaxWidth(), icon = R.drawable.ic_fluent_open_24_regular)
        }
        app.plugins.forEach { plugin ->
            DarkButton(
                if (plugin.downloadUrl.isBlank()) "${plugin.name} (đang cập nhật link)" else "Tải ${plugin.name}",
                { openSource(plugin.downloadUrl) }, Modifier.fillMaxWidth(), enabled = plugin.downloadUrl.isNotBlank(),
            )
        }
        if (app.guide.isNotEmpty()) {
            Text("Hướng dẫn cài", style = Monika.type.bodyStrong, color = c.text, modifier = Modifier.padding(top = 4.dp))
            app.guide.forEachIndexed { i, step ->
                Row {
                    Text("${i + 1}.", style = Monika.type.caption, color = c.accentCoral, modifier = Modifier.padding(end = 6.dp))
                    Text(step, style = Monika.type.caption, color = c.textSecondary)
                }
            }
        }
    }
}

/** Tải app/plugin: host tải thẳng hoặc link .apk → tải trong app, xong bấm thông báo để cài; còn lại mở trình duyệt. */
private fun startToolDownload(context: Context, scope: CoroutineScope, url: String) {
    val direct = LinkResolver.resolve(url, AppGraph.config.current.downloadHosts)?.directUrl
        ?: url.takeIf { it.substringBefore('?').endsWith(".apk", ignoreCase = true) }
    if (direct == null) {
        openInApp(context, url)
        return
    }
    scope.launch {
        runCatching { AppGraph.downloader.enqueue(direct, isTool = true) }
            .onSuccess { Toast.makeText(context, "Đang tải… xong bấm thông báo để cài", Toast.LENGTH_LONG).show() }
            .onFailure { openInApp(context, url) }
    }
}
