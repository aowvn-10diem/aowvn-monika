package vn.aow.monika.config
import kotlinx.serialization.Serializable

@Serializable
data class AccountConfig(
    val enabled: Boolean = true,
    /** Firebase Web API key (công khai, giống trên web). */
    val apiKey: String = "",
    val databaseUrl: String = "",
    /** Trang đăng nhập trên web (docs/web/device-login.html). */
    val loginUrl: String = "https://www.aow.vn/p/device-login.html",
    /** Trang vote/donate trên web (mở khi cần tính năng app chưa có). */
    val voteUrl: String = "https://www.aow.vn/p/vote-game.html",
    /** URL tương thích bản cũ. Bảng ủng hộ native dùng [donate]. */
    val donateUrl: String = "",
    val donate: DonateConfig = DonateConfig(),
)

@Serializable
data class DonateBankConfig(val id: String = "", val name: String = "")

@Serializable
data class DonateConfig(
    val bankId: String = "icb",
    val bankName: String = "VietinBank",
    val accountNo: String = "101867601261",
    val accountName: String = "VU DOAN BINH MINH",
    val template: String = "compact2",
    val memoPrefix: String = "AowVN",
    val memo: String = "Ung ho AowVN Monika",
    val amounts: List<Long> = listOf(100_000, 200_000, 300_000),
    val minAmount: Long = 1_000,
    val maxAmount: Long = 500_000_000,
    val qrBaseUrl: String = "https://img.vietqr.io/image/",
    val payUrl: String = "https://dl.vietqr.io/pay",
    val banksUrl: String = "https://api.vietqr.io/v2/android-app-deeplinks",
    val returnUrl: String = "https://monika.aow.vn/",
    val appeal: String = "Chúng mình cố gắng phát triển Monika hoàn toàn miễn phí, nhưng AowVN vẫn cần phải chi trả chi phí cho máy chủ, AI. Đừng ngần ngại donate ủng hộ chúng mình nha, 200k, 100k hay 10k đều rất đáng trân trọng á.",
    val fallbackBanks: List<DonateBankConfig> = listOf(
        DonateBankConfig("icb", "VietinBank"), DonateBankConfig("vcb", "Vietcombank"),
        DonateBankConfig("mb", "MB Bank"), DonateBankConfig("tcb", "Techcombank"),
        DonateBankConfig("bidv", "BIDV"), DonateBankConfig("acb", "ACB"),
        DonateBankConfig("vpb", "VPBank"), DonateBankConfig("tpb", "TPBank"),
        DonateBankConfig("shb", "SHB"), DonateBankConfig("ocb", "OCB"),
        DonateBankConfig("lpb", "LPBank"), DonateBankConfig("seab", "SeABank"),
    ),
)
