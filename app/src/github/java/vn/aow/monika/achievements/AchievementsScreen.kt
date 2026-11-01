package vn.aow.monika.achievements

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import vn.aow.monika.AppGraph
import vn.aow.monika.R
import vn.aow.monika.browser.InAppBrowserActivity
import vn.aow.monika.ui.theme.CircleButton
import vn.aow.monika.ui.theme.DockClearance
import vn.aow.monika.ui.theme.GradientButton
import vn.aow.monika.ui.theme.GradientProgress
import vn.aow.monika.ui.theme.Monika
import vn.aow.monika.ui.theme.MonikaCard
import vn.aow.monika.ui.theme.MonikaHeader
import vn.aow.monika.ui.theme.Radius
import vn.aow.monika.ui.theme.Screen
import vn.aow.monika.ui.theme.SoftPillButton
import vn.aow.monika.ui.theme.Spinner
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Ngày giờ RA (UTC, "yyyy-MM-dd HH:mm:ss") → "dd/MM/yyyy HH:mm" theo GMT+7 (Hồ Chí Minh). */
fun raDateVn(utc: String?): String {
    if (utc.isNullOrBlank()) return ""
    return runCatching {
        val src = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val dst = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh") }
        dst.format(src.parse(utc)!!)
    }.getOrDefault(utc)
}

/** Màn "Thành tựu" (RetroAchievements): đăng nhập, hồ sơ, game vừa chơi, thành tựu vừa mở. */
@Composable
fun AchievementsScreen(onBack: () -> Unit) {
    val creds by AppGraph.ra.creds.collectAsState()
    var openGame by remember { mutableStateOf<Int?>(null) }
    Screen {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = DockClearance)) {
            MonikaHeader("Thành tựu", subtitle = "RetroAchievements", left = { CircleButton(R.drawable.ic_fluent_arrow_left_24_regular, "Quay lại", onBack) })
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (creds == null) LoginCard() else SignedIn(creds!!) { openGame = it }
            }
        }
        GameAchievementsSheet(openGame) { openGame = null }
    }
}

@Composable
private fun LoginCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = Monika.colors
    var user by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    MonikaCard(Modifier.fillMaxWidth(), shape = Radius.hero, padding = PaddingValues(18.dp)) {
        Text("Đăng nhập RetroAchievements", style = Monika.type.cardTitle, color = c.text)
        Text(
            "Xem thành tựu, điểm và tiến độ từng game của bạn ngay trong Monika. Cần tên tài khoản RetroAchievements và khóa web API (khóa nằm ở trang cài đặt của tài khoản, mục \"Keys\"). Khóa được mã hóa và chỉ lưu trên máy này.",
            style = Monika.type.caption, color = c.textSecondary, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
        )
        OutlinedTextField(user, { user = it }, Modifier.fillMaxWidth(), singleLine = true, shape = Radius.small, label = { Text("Tên tài khoản RetroAchievements") })
        OutlinedTextField(
            key, { key = it }, Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true, shape = Radius.small, label = { Text("Khóa web API") },
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        error?.let { Text(it, style = Monika.type.caption, color = c.danger, modifier = Modifier.padding(top = 8.dp)) }
        GradientButton(if (busy) "Đang kiểm tra…" else "Đăng nhập", {
            if (busy || user.isBlank() || key.isBlank()) return@GradientButton
            busy = true; error = null
            scope.launch {
                runCatching { AppGraph.ra.signIn(user, key) }
                    .onSuccess { Toast.makeText(context, "Xin chào ${it.user}!", Toast.LENGTH_SHORT).show() }
                    .onFailure { error = if (it is RaAuthException) "Tên hoặc khóa không đúng" else "Không kết nối được RetroAchievements" }
                busy = false
            }
        }, Modifier.fillMaxWidth().padding(top = 14.dp), icon = R.drawable.ic_fluent_person_24_regular, height = 48.dp, enabled = !busy)
        SoftPillButton("Lấy khóa ở đâu?", { InAppBrowserActivity.start(context, "https://retroachievements.org/controlpanel.php") }, R.drawable.ic_fluent_globe_24_regular, Modifier.padding(top = 10.dp))
    }
}

private sealed interface Load<out T> {
    data object Busy : Load<Nothing>
    data class Ok<T>(val v: T) : Load<T>
    data class Fail(val msg: String) : Load<Nothing>
}

@Composable
private fun SignedIn(creds: RaAccount.Creds, onOpenGame: (Int) -> Unit) {
    val c = Monika.colors
    val id = AppGraph.ra.idForApi ?: creds.user
    var data by remember { mutableStateOf<Load<Triple<RaProfile, List<RaRecentGame>, List<RaUnlock>>>>(Load.Busy) }
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(creds, tick) {
        data = Load.Busy
        data = runCatching {
            Triple(
                AppGraph.raApi.profile(id, creds.key),
                AppGraph.raApi.recentGames(id, creds.key, 20),
                AppGraph.raApi.recentUnlocks(id, creds.key),
            )
        }.fold({ Load.Ok(it) }, { Load.Fail(if (it is RaAuthException) "Khóa không còn đúng — hãy đăng xuất rồi đăng nhập lại" else "Không kết nối được RetroAchievements") })
    }
    when (val d = data) {
        Load.Busy -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { Spinner() }
        is Load.Fail -> MonikaCard(Modifier.fillMaxWidth()) {
            Text(d.msg, style = Monika.type.body, color = c.danger)
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftPillButton("Thử lại", { tick++ }, R.drawable.ic_fluent_arrow_sync_24_regular)
                SoftPillButton("Đăng xuất", { AppGraph.ra.signOut() }, R.drawable.ic_fluent_sign_out_24_regular)
            }
        }
        is Load.Ok -> {
            val (profile, games, unlocks) = d.v
            InGameCard()
            MonikaCard(Modifier.fillMaxWidth(), shape = Radius.hero, padding = PaddingValues(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(RaApi.media(profile.userPic), null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp).clip(Radius.pill).background(c.surfaceSoft))
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(profile.user, style = Monika.type.cardTitle, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Điểm hardcore ${profile.totalPoints} · softcore ${profile.totalSoftcorePoints}", style = Monika.type.caption, color = c.textSecondary)
                        profile.richPresence?.takeIf { it.isNotBlank() }?.let { Text(it, style = Monika.type.caption, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    }
                    SoftPillButton("Đăng xuất", { AppGraph.ra.signOut() }, R.drawable.ic_fluent_sign_out_24_regular)
                }
            }
            Text("Game vừa chơi", style = Monika.type.sectionTitle, color = c.text, modifier = Modifier.padding(top = 4.dp))
            if (games.isEmpty()) Text("Chưa có game nào trên RetroAchievements.", style = Monika.type.caption, color = c.textSecondary)
            games.forEach { g ->
                MonikaCard(Modifier.fillMaxWidth(), onClick = { onOpenGame(g.gameId) }, padding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(RaApi.media(g.imageIcon), null, contentScale = ContentScale.Crop, modifier = Modifier.size(52.dp).clip(Radius.small).background(c.surfaceSoft))
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(g.title, style = Monika.type.cardTitle, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${g.consoleName} · chơi ${raDateVn(g.lastPlayed)}", style = Monika.type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${g.numAchieved}/${g.numPossible} thành tựu · ${g.scoreAchieved}/${g.possibleScore} điểm", style = Monika.type.caption, color = c.textSecondary)
                        }
                    }
                    if (g.numPossible > 0) GradientProgress(g.numAchieved / g.numPossible.toFloat(), Modifier.padding(top = 8.dp))
                }
            }
            Text("Thành tựu vừa mở (7 ngày)", style = Monika.type.sectionTitle, color = c.text, modifier = Modifier.padding(top = 8.dp))
            if (unlocks.isEmpty()) Text("Chưa mở thành tựu nào trong 7 ngày qua.", style = Monika.type.caption, color = c.textSecondary)
            unlocks.take(30).forEach { u ->
                MonikaCard(Modifier.fillMaxWidth(), onClick = { onOpenGame(u.gameId) }, padding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(RaApi.badge(u.badgeName), null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(Radius.small).background(c.surfaceSoft))
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(u.title, style = Monika.type.cardTitle, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(u.description, style = Monika.type.caption, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${u.gameTitle} · ${u.points} điểm · ${if (u.hardcore == 1) "hardcore" else "softcore"} · ${raDateVn(u.date)}", style = Monika.type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** Bật thành tựu mở khóa NGAY KHI CHƠI (RetroAchievements). Cần mật khẩu 1 lần để lấy mã đăng nhập; mật khẩu không được lưu. */
@Composable
private fun InGameCard() {
    val c = Monika.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val enabled by AppGraph.ra.inGame.collectAsState()
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var hardcore by remember { mutableStateOf(AppGraph.ra.hardcore) }
    MonikaCard(Modifier.fillMaxWidth(), shape = Radius.hero, padding = PaddingValues(18.dp)) {
        Text("Thành tựu khi chơi game", style = Monika.type.cardTitle, color = c.text)
        if (enabled) {
            Text(
                "Đã bật. Chơi game GB/GBC/GBA/NES/SNES/Genesis/Master System/Game Gear/NGP/WonderSwan/Atari 2600 có thành tựu thì Monika báo ngay lúc mở khóa và đồng bộ lên tài khoản của bạn.",
                style = Monika.type.caption, color = c.textSecondary, modifier = Modifier.padding(top = 6.dp),
            )
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Chế độ Hardcore", style = Monika.type.bodyStrong, color = c.text)
                    Text(
                        if (hardcore) "Bật: khóa nạp state, mã cheat và chạy chậm khi game có thành tựu. Điểm được tính hardcore (cần RetroAchievements duyệt app)."
                        else "Tắt (softcore): được dùng nạp state, cheat, chạy chậm. Điểm tính softcore.",
                        style = Monika.type.caption, color = c.textSecondary,
                    )
                }
                androidx.compose.material3.Switch(hardcore, { hardcore = it; AppGraph.ra.hardcore = it })
            }
            SoftPillButton("Tắt thành tựu trong game", { AppGraph.ra.disableInGame() }, R.drawable.ic_fluent_sign_out_24_regular, Modifier.padding(top = 10.dp))
        } else {
            Text(
                "Để mở khóa thành tựu ngay trong game, nhập mật khẩu RetroAchievements một lần. Monika chỉ dùng nó để lấy mã đăng nhập (lưu mã hóa trên máy), không lưu mật khẩu.",
                style = Monika.type.caption, color = c.textSecondary, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
            )
            OutlinedTextField(
                password, { password = it }, Modifier.fillMaxWidth(), singleLine = true, shape = Radius.small, label = { Text("Mật khẩu RetroAchievements") },
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            error?.let { Text(it, style = Monika.type.caption, color = c.danger, modifier = Modifier.padding(top = 8.dp)) }
            GradientButton(if (busy) "Đang đăng nhập…" else "Bật thành tựu trong game", {
                if (busy || password.isBlank()) return@GradientButton
                busy = true; error = null
                scope.launch {
                    runCatching { AppGraph.ra.enableInGame(password) }
                        .onSuccess { password = ""; Toast.makeText(context, "Đã bật thành tựu trong game", Toast.LENGTH_SHORT).show() }
                        .onFailure { error = if (it is RaAuthException) (it.message ?: "Sai mật khẩu") else "Không kết nối được RetroAchievements" }
                    busy = false
                }
            }, Modifier.fillMaxWidth().padding(top = 12.dp), icon = R.drawable.ic_fluent_star_24_regular, height = 48.dp, enabled = !busy)
        }
    }
}
