package vn.aow.monika.download

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import vn.aow.monika.config.isAllowedDownloadPath
import vn.aow.monika.config.isRedirectOnlyDownloadHost

/**
 * V196: kiểm lại đích tải đã **từng hop** (kể cả hop do redirect 30x sinh ra) trước khi gửi request.
 *
 * Phải gắn bằng `addNetworkInterceptor` (không phải `addInterceptor`): network interceptor chạy cho
 * mỗi request thực sự ra mạng, còn application interceptor chỉ chạy một lần cho cả chuỗi redirect.
 *
 * V206: thêm ràng buộc đường dẫn — host dùng chung (`github.com`, …) chỉ nhận tiền tố chủ sở hữu đã chốt,
 * và host CDN của GitHub Releases chỉ được là hop redirect từ URL gốc `github.com/aowvn-10diem/...`.
 * [enforceArtifactPaths] = false dành cho luồng người dùng tự chọn link (trình duyệt tải file), nơi chỉ
 * siết HTTPS + danh sách host.
 */
class DownloadGuard(
    private val allowedHost: (String) -> Boolean,
    private val enforceArtifactPaths: Boolean = true,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        if (url.scheme != "https" || !allowedHost(url.host)) {
            throw IOException("Chặn tải: địa chỉ không phải HTTPS hoặc host không nằm trong danh sách cho phép")
        }
        if (enforceArtifactPaths) {
            if (isRedirectOnlyDownloadHost(url.host)) {
                // `Call.request()` là request gốc của cả chuỗi redirect, nên biết được hop đầu có hợp lệ.
                val origin = chain.call().request().url
                val fromOfficialRepo = origin.host == "github.com" &&
                    isAllowedDownloadPath(origin.host, origin.encodedPath)
                if (!fromOfficialRepo) {
                    throw IOException("Chặn tải: host CDN GitHub chỉ nhận redirect từ URL gốc hợp lệ")
                }
            } else if (!isAllowedDownloadPath(url.host, url.encodedPath)) {
                throw IOException("Chặn tải: đường dẫn không nằm trong tiền tố cho phép của host")
            }
        }
        return chain.proceed(chain.request())
    }
}
