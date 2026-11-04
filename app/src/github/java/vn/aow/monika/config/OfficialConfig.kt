package vn.aow.monika.config
import kotlinx.serialization.Serializable

@Serializable
data class OfficialSource(val title: String = "", val url: String = "")

@Serializable
data class OfficialConfig(
    val pageUrl: String = "https://monika.aow.vn/chinh-thuc/",
    val downloadSources: List<OfficialSource> = listOf(
        OfficialSource("monika.aow.vn", "https://monika.aow.vn/"),
        OfficialSource("GitHub Releases", "https://github.com/aowvn-10diem/aowvn-monika/releases"),
    ),
    val reportFakeUrl: String = "https://github.com/aowvn-10diem/aowvn-monika/issues/new?template=bao-ban-gia.yml",
    val verificationSteps: List<String> = listOf(
        "Đối chiếu địa chỉ nơi tải với danh sách bên trên. Tên và biểu tượng giống nhau chưa đủ để xác nhận bản chính thức.",
        "Trên máy tính có Android SDK Build-Tools, đổi tên tệp APK thành app.apk rồi chạy: apksigner verify --print-certs app.apk. Đối chiếu dòng Signer #1 certificate SHA-256 digest với chứng thư bên trên.",
        "Trước khi ủng hộ, đối chiếu ngân hàng, số tài khoản và chủ tài khoản trong mục Ủng hộ. Gặp thông tin khác hoặc nguồn tải đáng ngờ, hãy bấm Báo bản giả.",
    ),
)
