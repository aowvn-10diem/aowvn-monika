package vn.aow.monika.donate

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import vn.aow.monika.AppGraph
import vn.aow.monika.R
import vn.aow.monika.config.DonateBankConfig
import vn.aow.monika.config.DonateConfig
import vn.aow.monika.ui.theme.*

@Composable
fun DonationButton(modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    val config by AppGraph.config.config.collectAsState()
    DonationAction({ open = true }, modifier)
    if (open) DonationSheet(config.account.donate, { open = false })
}

/** Gọn trong ô 104dp của MonikaHeader, vẫn giữ chữ và vùng chạm 48dp. */
@Composable
internal fun DonationAction(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Monika.colors
    Row(modifier.heightIn(min = 48.dp).clip(Radius.pill).background(primaryGradient())
        .clickable(onClickLabel = "Ủng hộ AowVN", onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(painterResource(R.drawable.ic_fluent_heart_24_filled), null, Modifier.size(18.dp), tint = c.textOnDark)
        Text("Ủng hộ", style = Monika.type.caption.copy(fontWeight = Monika.type.bodyStrong.fontWeight), color = c.textOnDark, maxLines = 1)
    }
}

/** A78-2: thay toàn bộ QR/STK bằng thông báo trung lập, không chặn chơi game. */
@Composable
internal fun DonationUnavailablePanel(onDismiss: () -> Unit) {
    val c = Monika.colors
    Column(
        Modifier.fillMaxWidth().background(c.bg).testTag("donation-unavailable")
            .padding(horizontal = 20.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(painterResource(R.drawable.ic_fluent_heart_24_filled), null, Modifier.size(26.dp), tint = c.accentCoral)
            Text("Ủng hộ AowVN", style = Monika.type.sectionTitle, color = c.text)
        }
        Text(
            "Bản ứng dụng này không được ký bằng khóa phát hành chính thức của AowVN (bản tự biên dịch " +
                "hoặc đóng gói lại). Để bảo vệ người chơi khỏi tài khoản mạo danh, thông tin Ủng hộ chỉ " +
                "hiển thị trên bản chính thức tại monika.aow.vn.",
            style = Monika.type.body, color = c.textSecondary,
        )
        GradientButton("Đóng", onDismiss)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonationSheet(
    config: DonateConfig,
    onDismiss: () -> Unit,
    loadBanks: suspend (DonateConfig) -> List<DonateBankConfig> = { AppGraph.donationBanks.load(it) },
    qrContent: @Composable (Long) -> Unit = { DonationQr(config, it) },
    officialSha256: String = vn.aow.monika.security.OfficialCertPin.OFFICIAL_APK_CERT_SHA256,
    actualSha256: String? = null,
    debugBuild: Boolean = vn.aow.monika.BuildConfig.DEBUG,
) {
    val context = LocalContext.current
    // A78-2: bản không đúng chứng thư phát hành (hoặc không đọc được chứng thư) không được vẽ QR/STK.
    val allowed = vn.aow.monika.security.OfficialCert.donationAllowed(
        actualSha256 ?: vn.aow.monika.diag.Diagnostics.certSha256(context),
        officialSha256,
        debugBuild,
    )
    if (!allowed) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Monika.colors.bg,
        ) { DonationUnavailablePanel(onDismiss) }
        return
    }
    val banks by produceState(config.fallbackBanks, config) { value = loadBanks(config) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Monika.colors.bg,
    ) {
        DonationPanel(config, banks, onDismiss, { bank, amount ->
            val link = donationPayUrl(config, bank.id, amount)
            try {
                if (link == null) throw ActivityNotFoundException()
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, "Chưa mở được ngân hàng. Bạn có thể quét QR hoặc sao chép thông tin.", Toast.LENGTH_LONG).show()
            } catch (_: SecurityException) {
                Toast.makeText(context, "Chưa mở được ngân hàng. Bạn có thể quét QR hoặc sao chép thông tin.", Toast.LENGTH_LONG).show()
            }
        }, qrContent)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DonationPanel(
    config: DonateConfig,
    banks: List<DonateBankConfig>,
    onDismiss: () -> Unit,
    onBank: (DonateBankConfig, Long) -> Unit,
    qrContent: @Composable (Long) -> Unit,
) {
    val c = Monika.colors
    val amounts = remember(config) { suggestedAmounts(config) }
    var selected by rememberSaveable(config) { mutableLongStateOf(amounts.first()) }
    var custom by rememberSaveable(config) { mutableStateOf("") }
    val amount = donationAmount(custom, selected, config)
    val clipboard = LocalClipboardManager.current
    Column(
        Modifier.fillMaxWidth().background(c.bg).testTag("donation-sheet").imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(painterResource(R.drawable.ic_fluent_heart_24_filled), null, Modifier.size(26.dp), tint = c.accentCoral)
            Text("Ủng hộ AowVN", style = Monika.type.sectionTitle, color = c.text)
        }
        Text(config.appeal, style = Monika.type.body, color = c.textSecondary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(144.dp)) { qrContent(amount.value) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DonationInfo("Ngân hàng", config.bankName)
                DonationInfo("Số tài khoản", config.accountNo, "Sao chép số tài khoản") { clipboard.setText(AnnotatedString(config.accountNo)) }
                DonationInfo("Chủ tài khoản", config.accountName)
                DonationInfo("Nội dung chuyển khoản", config.memo, "Sao chép nội dung") { clipboard.setText(AnnotatedString(config.memo)) }
            }
        }
        Text("Chọn số tiền", style = Monika.type.bodyStrong, color = c.text)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            amounts.forEach { value ->
                FilterChip(
                    selected = value == selected && (custom.isBlank() || amount.invalid),
                    onClick = { selected = value; custom = "" },
                    label = { Text(money(value), style = Monika.type.caption) },
                    shape = Radius.pill,
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.accentCoral, selectedLabelColor = c.text, labelColor = c.text),
                )
            }
        }
        OutlinedTextField(
            value = custom, onValueChange = { custom = it.take(32) },
            label = { Text("Hoặc tự nhập số tiền", style = Monika.type.body) },
            placeholder = { Text("Ví dụ: 30.000", style = Monika.type.body) },
            suffix = { Text("đ", style = Monika.type.body) },
            textStyle = Monika.type.body, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true, isError = amount.invalid, shape = Radius.small,
            modifier = Modifier.fillMaxWidth().testTag("donation-custom-amount"),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.accentCoral, unfocusedBorderColor = c.track,
                focusedTextColor = c.text, unfocusedTextColor = c.text, errorTextColor = c.text, errorBorderColor = c.danger),
        )
        Text(
            if (amount.invalid) "Nhập từ ${money(config.minAmount)} đến ${money(config.maxAmount)} (đang dùng ${money(selected)})"
            else "Chuyển ${money(amount.value)}, cảm ơn bạn nhiều!",
            style = Monika.type.caption, color = if (amount.invalid) c.danger else c.textSecondary,
            modifier = Modifier.testTag("donation-amount-hint"),
        )
        Text("Chọn ngân hàng của bạn để chuyển nhanh", style = Monika.type.bodyStrong, color = c.text)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            banks.forEach { bank -> SoftPillButton(bank.name, { onBank(bank, amount.value) }) }
        }
        SoftPillButton("Để sau, cảm ơn", onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun DonationInfo(label: String, value: String, copyLabel: String? = null, onCopy: () -> Unit = {}) {
    val c = Monika.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = Monika.type.caption, color = c.textSecondary)
            Text(value, style = Monika.type.bodyStrong, color = c.text)
        }
        if (copyLabel != null) IconButton(onClick = onCopy, modifier = Modifier.size(48.dp)) {
            Icon(painterResource(R.drawable.ic_fluent_copy_24_regular), copyLabel, tint = c.accentCoral)
        }
    }
}

@Composable
internal fun DonationStaticQr(config: DonateConfig) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (canUseBundledQr(config)) {
            Image(painterResource(R.drawable.donate_qr), "QR dự phòng ủng hộ", Modifier.fillMaxWidth().aspectRatio(437f / 495f))
            Text("QR dự phòng. Kiểm tra số tiền và nội dung trong app ngân hàng.", style = Monika.type.caption, color = Monika.colors.textSecondary)
        } else Text("Chưa tải được QR. Hãy sao chép tài khoản hoặc chọn ngân hàng.", style = Monika.type.caption, color = Monika.colors.textSecondary)
    }
}

@Composable
private fun DonationQr(config: DonateConfig, amount: Long) {
    val url = donationQrUrl(config, amount)
    var loaded by remember(url) { mutableStateOf(false) }
    if (url == null) { DonationStaticQr(config); return }
    val fallback = if (canUseBundledQr(config)) painterResource(R.drawable.donate_qr) else null
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AsyncImage(model = url, contentDescription = "QR chuyển ${money(amount)}", placeholder = fallback, error = fallback,
            onSuccess = { loaded = true }, onError = { loaded = false },
            modifier = Modifier.fillMaxWidth().aspectRatio(437f / 495f))
        if (!loaded) Text(
            if (fallback != null) "QR dự phòng. Kiểm tra số tiền và nội dung trong app ngân hàng."
            else "Chưa tải được QR. Hãy sao chép tài khoản hoặc chọn ngân hàng.",
            style = Monika.type.caption, color = Monika.colors.textSecondary,
        )
    }
}
