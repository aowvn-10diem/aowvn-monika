package vn.aow.monika.donate

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import vn.aow.monika.config.DonateConfig
import java.text.NumberFormat
import java.util.Locale

internal fun money(amount: Long): String = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(amount) + "đ"

internal data class DonationAmount(val value: Long, val invalid: Boolean)

internal fun suggestedAmounts(config: DonateConfig): List<Long> = config.amounts
    .filter { it > 0 && it in config.minAmount..config.maxAmount }.distinct().take(6)
    .ifEmpty { listOf(config.minAmount.coerceAtLeast(1)) }

/** Không giữ số tự nhập cũ khi đầu vào sai; QR và link luôn dùng cùng một kết quả. */
internal fun donationAmount(input: String, selected: Long, config: DonateConfig): DonationAmount {
    val text = input.trim()
    if (text.isEmpty()) return DonationAmount(selected, false)
    val grouped = Regex("[0-9]{1,3}([., ])[0-9]{3}(?:\\1[0-9]{3})*")
    if (text.length > 32 || !(text.all { it in '0'..'9' } || grouped.matches(text))) return DonationAmount(selected, true)
    val amount = text.filter { it in '0'..'9' }.toLongOrNull()
    return if (amount != null && amount > 0 && amount in config.minAmount..config.maxAmount) DonationAmount(amount, false)
    else DonationAmount(selected, true)
}

internal fun donationQrUrl(config: DonateConfig, amount: Long): String? {
    if (!Regex("[A-Za-z0-9]+").matches(config.bankId) || !Regex("[0-9]{4,32}").matches(config.accountNo)) return null
    val base = config.qrBaseUrl.toHttpUrlOrNull()?.takeIf { it.isHttps && it.host == "img.vietqr.io" } ?: return null
    return base.newBuilder().addPathSegment("${config.bankId}-${config.accountNo}-${config.template}.png")
        .addQueryParameter("amount", amount.toString()).addQueryParameter("addInfo", config.memo)
        .addQueryParameter("accountName", config.accountName).build().toString()
}

internal fun donationPayUrl(config: DonateConfig, appId: String, amount: Long): String? {
    if (!Regex("[A-Za-z0-9._-]+").matches(appId) || amount !in config.minAmount..config.maxAmount) return null
    if (!Regex("[A-Za-z0-9]+").matches(config.bankId) || !Regex("[0-9]{4,32}").matches(config.accountNo)) return null
    val base = config.payUrl.toHttpUrlOrNull()?.takeIf { it.isHttps && it.host == "dl.vietqr.io" } ?: return null
    return base.newBuilder().addQueryParameter("app", appId).addQueryParameter("ba", "${config.accountNo}@${config.bankId}")
        .addQueryParameter("am", amount.toString()).addQueryParameter("tn", config.memo)
        .addQueryParameter("bn", config.accountName).addQueryParameter("url", config.returnUrl).build().toString()
}

/** Ảnh đóng gói là QR của tài khoản mặc định trên web. Đổi người nhận từ xa thì không được hiện QR cũ. */
internal fun canUseBundledQr(config: DonateConfig): Boolean {
    val bundled = DonateConfig()
    return config.bankId == bundled.bankId && config.accountNo == bundled.accountNo && config.accountName == bundled.accountName && config.memo == bundled.memo
}
