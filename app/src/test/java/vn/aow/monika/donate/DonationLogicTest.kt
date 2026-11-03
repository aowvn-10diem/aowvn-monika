package vn.aow.monika.donate

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test
import vn.aow.monika.config.DonateConfig

class DonationLogicTest {
    private val config = DonateConfig()
    @Test fun amountsAcceptBoundsAndVietnameseGrouping() {
        for ((input, expected) in listOf("1.000" to 1_000L, "500.000.000" to 500_000_000L, "30 000" to 30_000L, "200000" to 200_000L)) {
            val actual = donationAmount(input, 100_000, config)
            assertFalse(input, actual.invalid); assertEquals(input, expected, actual.value)
        }
    }
    @Test fun invalidInputAlwaysRestoresSelectedChipNotLastValidCustomAmount() {
        assertEquals(30_000L, donationAmount("30.000", 200_000, config).value)
        for (input in listOf("999", "500000001", "-1000", "99999999999999999999999", "1e5", "20a000", "1.23.000")) {
            val actual = donationAmount(input, 200_000, config)
            assertTrue(input, actual.invalid); assertEquals(input, 200_000L, actual.value)
        }
        assertEquals(DonationAmount(200_000, false), donationAmount("", 200_000, config))
    }
    @Test fun urlsUseSameAmountAndEncodeMemoNameAndReturnUrl() {
        val cfg = config.copy(memo = "Ủng hộ A&B + Monika", returnUrl = "https://monika.aow.vn/?a=1&b=2")
        val amount = donationAmount("999", 300_000, cfg).value
        val qr = requireNotNull(donationQrUrl(cfg, amount)).toHttpUrl()
        val pay = requireNotNull(donationPayUrl(cfg, "vcb", amount)).toHttpUrl()
        assertEquals("300000", qr.queryParameter("amount")); assertEquals(qr.queryParameter("amount"), pay.queryParameter("am"))
        assertEquals(cfg.memo, qr.queryParameter("addInfo")); assertEquals(cfg.memo, pay.queryParameter("tn"))
        assertEquals(cfg.accountName, pay.queryParameter("bn")); assertEquals(cfg.returnUrl, pay.queryParameter("url"))
        assertEquals("${cfg.accountNo}@${cfg.bankId}", pay.queryParameter("ba")); assertEquals("vcb", pay.queryParameter("app"))
    }
    @Test fun staticQrCannotBeUsedForAnotherRecipientOrMemo() {
        assertTrue(canUseBundledQr(config))
        assertFalse(canUseBundledQr(config.copy(accountNo = "9999999999")))
        assertFalse(canUseBundledQr(config.copy(bankId = "vcb")))
        assertFalse(canUseBundledQr(config.copy(memo = "Nội dung khác")))
        assertNull(donationPayUrl(config, "javascript:bad", 100_000))
        assertNull(donationPayUrl(config, "icb", 999))
        assertNull(donationQrUrl(config.copy(qrBaseUrl = "https://example.test/"), 100_000))
    }
    @Test fun configurationCanChangeAmountsAndLimitsWithoutCodeChanges() {
        val cfg = config.copy(amounts = listOf(10_000, 20_000), minAmount = 5_000, maxAmount = 50_000)
        assertEquals(listOf(10_000L, 20_000L), suggestedAmounts(cfg))
        assertTrue(donationAmount("1000", 20_000, cfg).invalid)
        assertEquals(50_000L, donationAmount("50000", 20_000, cfg).value)
    }
}
