package vn.aow.monika.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
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
import vn.aow.monika.ui.theme.Fluent
import vn.aow.monika.ui.theme.Fluent3D
import vn.aow.monika.ui.theme.FluentButton
import vn.aow.monika.ui.theme.FluentButtonStyle
import vn.aow.monika.ui.theme.FluentCard
import vn.aow.monika.ui.theme.FluentDivider
import vn.aow.monika.ui.theme.FluentRadius
import vn.aow.monika.ui.theme.FluentSectionHeader
import vn.aow.monika.ui.theme.FluentSpinner
import vn.aow.monika.ui.theme.FluentTag
import vn.aow.monika.ui.theme.FluentTopBar

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cfg by AppGraph.config.config.collectAsState()
    val c = Fluent.colors
    var refreshTick by remember { mutableIntStateOf(0) }
    var subscribed by remember { mutableStateOf(AppGraph.prefs.subscribedLabels) }

    Column(Modifier.fillMaxSize().background(c.background2)) {
        FluentTopBar("Trình chạy", subtitle = "Giả lập, app bổ sung, thông báo")
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 1. Cập nhật app
            if (cfg.app.latestVersionCode > BuildConfig.VERSION_CODE) {
                FluentCard(Modifier.fillMaxWidth()) {
                    Text("Có bản mới ${cfg.app.latestVersionName}", style = Fluent.type.body1Strong, color = c.foreground1)
                    Text(cfg.app.changelog, style = Fluent.type.body2, color = c.foreground2, modifier = Modifier.padding(vertical = 8.dp))
                    if (cfg.app.apkUrl.isNotBlank()) FluentButton("Tải bản mới", {
                        scope.launch { runCatching { AppGraph.downloader.enqueue(cfg.app.apkUrl, isTool = true) } }
                        Toast.makeText(context, "Đang tải bản mới…", Toast.LENGTH_SHORT).show()
                    }, icon = R.drawable.ic_fluent_arrow_download_24_regular)
                }
            }

            // 2. App ngoài
            FluentSectionHeader("APP CHẠY GAME BỔ SUNG")
            cfg.externalApps.forEach { app ->
                val installed = remember(app, refreshTick) { ExternalApps.installedPackage(context, app) != null }
                FluentCard(Modifier.fillMaxWidth()) { ExternalAppDetails(app, installed) }
            }
            FluentButton("Kiểm tra lại trạng thái cài đặt", { refreshTick++ }, style = FluentButtonStyle.Subtle, icon = R.drawable.ic_fluent_arrow_sync_24_regular)

            // 3. Thông báo
            FluentSectionHeader("THÔNG BÁO BÀI MỚI")
            FluentCard(Modifier.fillMaxWidth()) {
                Fluent3D(R.drawable.fluent3d_bell, Modifier.size(40.dp).padding(bottom = 8.dp))
                Text(
                    if (subscribed.isEmpty()) "Đang nhận tất cả bài mới. Chọn nhãn để chỉ nhận loại game bạn thích."
                    else "Chỉ nhận bài có nhãn đã chọn.",
                    style = Fluent.type.body2, color = c.foreground2, modifier = Modifier.padding(bottom = 12.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    cfg.feedLabels().forEach { label ->
                        val on = label in subscribed
                        Box(
                            Modifier.clip(FluentRadius.circular)
                                .background(if (on) c.brandBackground else c.background3)
                                .clickable(role = Role.Checkbox) {
                                    subscribed = if (on) subscribed - label else subscribed + label
                                    AppGraph.prefs.subscribedLabels = subscribed
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        ) { Text(label, style = Fluent.type.caption1, color = if (on) c.onBrand else c.foreground2) }
                    }
                }
            }

            // 4. Lõi giả lập
            FluentSectionHeader("LÕI GIẢ LẬP")
            FluentCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                cfg.cores.entries.forEachIndexed { index, (id, def) ->
                    val installed = remember(id, refreshTick) { AppGraph.cores.installedVersion(id) }
                    if (index > 0) FluentDivider(Modifier.padding(start = 16.dp))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(id, style = Fluent.type.body1, color = c.foreground1, modifier = Modifier.weight(1f))
                        FluentTag(when (installed) { null -> "Tải khi chơi"; def.version -> "Đã có"; else -> "Có bản mới" }, brand = installed != null)
                        if (installed != null) FluentButton("Xóa", { AppGraph.cores.delete(id); refreshTick++ }, style = FluentButtonStyle.Subtle)
                    }
                }
            }

            // 5. Thông tin
            FluentSectionHeader("THÔNG TIN")
            FluentCard(Modifier.fillMaxWidth()) {
                Text("Phiên bản app: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = Fluent.type.body2, color = c.foreground2)
                Text("Phiên bản cấu hình: ${cfg.configVersion}", style = Fluent.type.body2, color = c.foreground2, modifier = Modifier.padding(bottom = 12.dp))
                FluentButton("Cập nhật cấu hình", {
                    scope.launch {
                        val r = AppGraph.config.refresh()
                        Toast.makeText(
                            context,
                            if (r.isSuccess) "Đã cập nhật cấu hình" else "Chưa cập nhật được: ${r.exceptionOrNull()?.message}",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }, style = FluentButtonStyle.Outline, icon = R.drawable.ic_fluent_arrow_sync_24_regular)
            }
        }
    }
}

/**
 * Thông tin 1 app ngoài: trạng thái, nút tải (app + plugin), hướng dẫn cài.
 * Link là trang aow.vn/p/... → app đọc trang qua feed, liệt kê link tải; host tải thẳng thì tải + cài ngay trong app.
 */
@Composable
fun ExternalAppDetails(app: ExternalApp, installed: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = Fluent.colors
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
            Box(Modifier.size(40.dp).clip(FluentRadius.card).background(c.brandSubtle), contentAlignment = Alignment.Center) {
                Fluent3D(R.drawable.fluent3d_toolbox, Modifier.size(28.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(app.name, style = Fluent.type.body1Strong, color = c.foreground1)
                if (app.description.isNotBlank()) Text(app.description, style = Fluent.type.caption1, color = c.foreground3)
            }
            if (installed) Icon(painterResource(R.drawable.ic_fluent_checkmark_circle_24_filled), "Đã cài", Modifier.size(22.dp), tint = c.success)
            else FluentTag("Chưa cài")
        }
        when {
            app.downloadUrl.isBlank() -> Text("Link tải đang được cập nhật.", style = Fluent.type.caption1, color = c.foreground3)
            pageLinks.isNullOrEmpty() -> FluentButton(
                if (installed) "Tải lại / cập nhật" else "Tải ${app.name}",
                { openSource(app.downloadUrl) },
                icon = R.drawable.ic_fluent_arrow_download_24_regular, enabled = !loading,
            )
        }
        if (loading) FluentSpinner()
        pageError?.let { Text(it, style = Fluent.type.caption1, color = c.danger) }
        pageLinks?.forEach { link ->
            val title = listOf(link.label.ifBlank { "Tải" }, link.hostName).distinct().joinToString(" · ")
            FluentButton(
                title,
                { if (link.directUrl != null) startToolDownload(context, scope, link.directUrl) else openUrl(context, link.pageUrl) },
                modifier = Modifier.fillMaxWidth(),
                style = if (link.directUrl != null) FluentButtonStyle.Accent else FluentButtonStyle.Outline,
                icon = if (link.directUrl != null) R.drawable.ic_fluent_arrow_download_24_regular else R.drawable.ic_fluent_open_24_regular,
            )
        }
        app.plugins.forEach { plugin ->
            FluentButton(
                if (plugin.downloadUrl.isBlank()) "${plugin.name} (đang cập nhật link)" else "Tải ${plugin.name}",
                { openSource(plugin.downloadUrl) },
                style = FluentButtonStyle.Outline, enabled = plugin.downloadUrl.isNotBlank(),
            )
        }
        if (app.guide.isNotEmpty()) {
            Text("Hướng dẫn cài", style = Fluent.type.caption1Strong, color = c.foreground2, modifier = Modifier.padding(top = 4.dp))
            app.guide.forEachIndexed { i, step ->
                Row {
                    Text("${i + 1}.", style = Fluent.type.caption1, color = c.brandForeground, modifier = Modifier.padding(end = 6.dp))
                    Text(step, style = Fluent.type.caption1, color = c.foreground2)
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
        openUrl(context, url)
        return
    }
    scope.launch {
        runCatching { AppGraph.downloader.enqueue(direct, isTool = true) }
            .onSuccess { Toast.makeText(context, "Đang tải… xong bấm thông báo để cài", Toast.LENGTH_LONG).show() }
            .onFailure { openUrl(context, url) }
    }
}
