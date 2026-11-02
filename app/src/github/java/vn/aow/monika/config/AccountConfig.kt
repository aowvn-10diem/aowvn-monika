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
    /** Nút "Ủng hộ AowVN" ở Trang chủ mở trang này. Trống = dùng [voteUrl]. */
    val donateUrl: String = "",
    val donate: DonateConfig = DonateConfig(),
)

@Serializable
data class DonateConfig(
    /** Mã BIN ngân hàng (VietQR), số tài khoản, tên, mẫu QR, tiền tố nội dung — giống CONFIG của trang vote. */
    val bankId: String = "",
    val accountNo: String = "",
    val accountName: String = "",
    val template: String = "compact2",
    val memoPrefix: String = "AowVN",
    val amounts: List<Long> = listOf(10_000, 20_000, 50_000, 100_000),
)
