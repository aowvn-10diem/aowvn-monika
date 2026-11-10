package vn.aow.monika.official

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import vn.aow.monika.R
import vn.aow.monika.config.DonateConfig
import vn.aow.monika.config.OfficialConfig
import vn.aow.monika.config.SecurityConfig
import vn.aow.monika.ui.theme.*

/** Cùng dữ liệu với trang web, chỉ hiển thị trong bản GitHub (A68). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficialDistributionSheet(official: OfficialConfig, security: SecurityConfig, donate: DonateConfig, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // A78-3: đối chiếu PackageManager với khối ký APK; chỉ hiện STK ủng hộ khi là bản chính thức.
    val trust = androidx.compose.runtime.remember { vn.aow.monika.security.OfficialCert.verifyOfficialApk(context) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Monika.colors.bg,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        OfficialDistributionPanel(official, vn.aow.monika.security.OfficialCertPin.OFFICIAL_APK_CERT_SHA256, donate, onDismiss, { openOfficialUrl(context, it) }, trust)
    }
}

@Composable
internal fun OfficialDistributionPanel(
    official: OfficialConfig,
    certificate: String,
    donate: DonateConfig,
    onDismiss: () -> Unit,
    openUrl: (String) -> Unit,
    trust: vn.aow.monika.security.OfficialCert.BuildTrust = vn.aow.monika.security.OfficialCert.BuildTrust.OFFICIAL,
) {
    val c = Monika.colors
    val clipboard = LocalClipboardManager.current
    Column(Modifier.fillMaxWidth().background(c.bg).testTag("official-distribution")
        .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Bản chính thức", style = Monika.type.sectionTitle, color = c.text)
        Text("Kiểm tra nơi tải và thông tin ủng hộ trước khi dùng Monika.", style = Monika.type.body, color = c.textSecondary)
        Text("Nơi tải chính thức duy nhất", style = Monika.type.bodyStrong, color = c.text)
        official.downloadSources.forEach { source ->
            SoftPillButton(source.title, { openUrl(source.url) }, R.drawable.ic_fluent_shield_checkmark_24_regular,
                modifier = Modifier.fillMaxWidth())
            Text(source.url, style = Monika.type.caption, color = c.textSecondary)
        }
        MonikaCard(Modifier.fillMaxWidth(), padding = PaddingValues(14.dp)) {
            Text("SHA-256 chứng thư ký APK", style = Monika.type.bodyStrong, color = c.text)
            Text(certificate.chunked(16).joinToString("\n"), style = Monika.type.body, color = c.text,
                modifier = Modifier.padding(vertical = 8.dp).testTag("official-certificate"))
            SoftPillButton("Sao chép chứng thư", { clipboard.setText(AnnotatedString(certificate)) }, R.drawable.ic_fluent_copy_24_regular)
        }
        Text("Tài khoản ủng hộ duy nhất", style = Monika.type.bodyStrong, color = c.text)
        if (trust == vn.aow.monika.security.OfficialCert.BuildTrust.OFFICIAL) {
            Text("${donate.bankName}\n${donate.accountNo}\n${donate.accountName}", style = Monika.type.body, color = c.text)
            SoftPillButton("Sao chép số tài khoản", { clipboard.setText(AnnotatedString(donate.accountNo)) }, R.drawable.ic_fluent_copy_24_regular)
        } else {
            // A78-2/A78-3: bản không đúng chứng thư phát hành không được hiển thị thông tin nhận tiền.
            Text(
                "Bản ứng dụng này không được ký bằng khóa phát hành chính thức của AowVN. " +
                    "Thông tin ủng hộ chỉ hiển thị trên bản chính thức tại monika.aow.vn.",
                style = Monika.type.body, color = c.textSecondary,
                modifier = Modifier.testTag("official-donation-unavailable"),
            )
        }
        Text("Cách tự kiểm", style = Monika.type.bodyStrong, color = c.text)
        official.verificationSteps.forEachIndexed { index, step ->
            Text("${index + 1}. $step", style = Monika.type.body, color = c.textSecondary)
        }
        GradientButton("Báo bản giả", { openUrl(official.reportFakeUrl) }, Modifier.fillMaxWidth(), icon = R.drawable.ic_fluent_info_24_regular, height = 48.dp)
        SoftPillButton("Xem trang Bản chính thức", { openUrl(official.pageUrl) }, modifier = Modifier.fillMaxWidth())
        SoftPillButton("Đóng", onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

/** Chỉ các liên kết web HTTPS và thư liên hệ cấu hình mới được mở. */
internal fun officialIntent(url: String): Intent? {
    val web = url.toHttpUrlOrNull()
    if (web != null && web.isHttps && web.username.isEmpty() && web.password.isEmpty())
        return Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val uri = Uri.parse(url)
    if (uri.scheme == "mailto" && uri.schemeSpecificPart.substringBefore('?').contains('@'))
        return Intent(Intent.ACTION_SENDTO, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return null
}

private fun openOfficialUrl(context: Context, url: String) {
    try {
        val intent = officialIntent(url) ?: throw ActivityNotFoundException()
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Chưa mở được liên kết. Hãy thử lại sau.", Toast.LENGTH_LONG).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, "Chưa mở được liên kết. Hãy thử lại sau.", Toast.LENGTH_LONG).show()
    }
}
