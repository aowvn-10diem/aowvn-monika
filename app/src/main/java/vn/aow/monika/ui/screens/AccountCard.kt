package vn.aow.monika.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import vn.aow.monika.AppGraph
import vn.aow.monika.R
import vn.aow.monika.account.Profile
import vn.aow.monika.ui.theme.GradientButton
import vn.aow.monika.ui.theme.GradientProgress
import vn.aow.monika.ui.theme.Monika
import vn.aow.monika.ui.theme.MonikaCard
import vn.aow.monika.ui.theme.Radius
import vn.aow.monika.ui.theme.SoftPillButton

/** Thẻ tài khoản AowVN: đăng nhập / điểm danh 14 ngày / điểm tích lũy / đăng xuất (dùng ở Cài đặt). */
@Composable
fun AccountCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = Monika.colors
    val session by AppGraph.account.session.collectAsState()
    var profile by remember { mutableStateOf<Profile?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(session?.uid) {
        profile = null; error = null
        if (session != null) runCatching { AppGraph.aow.profile() }.onSuccess { profile = it }.onFailure { error = it.message }
    }

    MonikaCard(modifier.fillMaxWidth(), shape = Radius.hero, padding = PaddingValues(18.dp)) {
        val s = session
        if (s == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(Radius.pill).background(c.surfaceSoft), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_fluent_person_24_regular), null, Modifier.size(28.dp), tint = c.textSecondary)
                }
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text("Tài khoản AowVN", style = Monika.type.cardTitle, color = c.text)
                    Text("Đăng nhập để điểm danh và đánh giá bản dịch", style = Monika.type.caption, color = c.textSecondary)
                }
            }
            GradientButton("Đăng nhập bằng Google", {
                (context as? Activity)?.let { AppGraph.account.startLogin(it) }
            }, Modifier.fillMaxWidth().padding(top = 14.dp), icon = R.drawable.ic_fluent_person_24_regular, height = 48.dp)
            return@MonikaCard
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(s.photo.ifBlank { null }, null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(52.dp).clip(Radius.pill).background(c.surfaceSoft))
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(s.name.ifBlank { "Thành viên AowVN" }, style = Monika.type.cardTitle, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(s.email, style = Monika.type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            SoftPillButton("Đăng xuất", { AppGraph.account.signOut() }, R.drawable.ic_fluent_sign_out_24_regular)
        }
        val p = profile
        if (p != null) {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("Điểm tích lũy", p.points.toString(), Modifier.weight(1f))
                Stat("Chuỗi ngày", "${p.streak} ngày", Modifier.weight(1f))
            }
            Text("Chu kỳ 14 ngày: ${p.progress}/14 — đủ 14 ngày liên tiếp được +1 điểm", style = Monika.type.caption, color = c.textSecondary, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            GradientProgress(p.progress / 14f)
            val done = p.checkedInToday()
            GradientButton(if (done) "Hôm nay đã điểm danh ✓" else if (busy) "Đang điểm danh…" else "Điểm danh hôm nay", {
                if (done || busy) return@GradientButton
                busy = true
                scope.launch {
                    runCatching { AppGraph.aow.checkin() }
                        .onSuccess { r ->
                            profile = r.profile
                            Toast.makeText(context, when {
                                r.already -> "Hôm nay bạn đã điểm danh rồi"
                                r.pointAwarded -> "Hoàn thành chuỗi 14 ngày! +1 điểm tích lũy"
                                else -> "Điểm danh thành công! Ngày ${r.profile.progress}/14"
                            }, Toast.LENGTH_LONG).show()
                        }
                        .onFailure { Toast.makeText(context, "Không điểm danh được: ${it.message}", Toast.LENGTH_LONG).show() }
                    busy = false
                }
            }, Modifier.fillMaxWidth().padding(top = 14.dp), icon = R.drawable.ic_fluent_calendar_checkmark_24_regular, height = 48.dp, enabled = !done)
        } else if (error != null) {
            Text("Không đọc được hồ sơ: $error", style = Monika.type.caption, color = c.danger, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    val c = Monika.colors
    Column(modifier.clip(Radius.medium).background(c.surfaceSoft).padding(12.dp)) {
        Text(value, style = Monika.type.sectionTitle, color = c.text)
        Text(label, style = Monika.type.caption, color = c.textSecondary)
    }
}
