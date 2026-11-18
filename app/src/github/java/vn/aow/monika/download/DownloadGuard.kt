package vn.aow.monika.download

import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.WeakHashMap
import vn.aow.monika.config.isAllowedDownloadPath
import vn.aow.monika.config.isAllowedRedirectHop
import vn.aow.monika.config.isR2RedirectDownloadHost
import vn.aow.monika.config.isRedirectOnlyDownloadHost

/**
 * V196: kiểm lại đích tải ở **từng hop** (kể cả hop do redirect 30x sinh ra) trước khi gửi request.
 *
 * Phải gắn bằng `addNetworkInterceptor` (không phải `addInterceptor`): network interceptor chạy cho
 * mỗi request thực sự ra mạng, còn application interceptor chỉ chạy một lần cho cả chuỗi redirect.
 *
 * V206 & A85-C06:
 * - Host dùng chung (`github.com`, …) chỉ nhận tiền tố chủ sở hữu đã chốt (`/aowvn-10diem/`).
 * - Host CDN của GitHub Releases chỉ được là hop redirect 1 bước từ `github.com/aowvn-10diem/...`.
 * - Host Cloudflare R2 (`<32-hex>.r2.cloudflarestorage.com/monika-packs/...`) chỉ được là hop redirect
 *   1 bước từ cổng G3 (`api.monika.aow.vn/pack/...` hoặc `monika.aow.vn/pack/...`) kèm chữ ký `X-Amz-Signature`.
 * - Chặn mọi chuỗi redirect từ 2 bước trở lên (`maxRedirectHops = 1`).
 */
class DownloadGuard(
    private val allowedHost: (String) -> Boolean,
    private val enforceArtifactPaths: Boolean = true,
) : Interceptor {

    private val redirectHopsByCall = WeakHashMap<Call, Int>()

    private fun recordHop(call: Call, isRedirectHop: Boolean): Int = synchronized(redirectHopsByCall) {
        if (!isRedirectHop) {
            redirectHopsByCall[call] = 0
            0
        } else {
            val next = (redirectHopsByCall[call] ?: 0) + 1
            redirectHopsByCall[call] = next
            next
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        val host = url.host.lowercase()
        if (url.scheme != "https" || !allowedHost(host)) {
            throw IOException("Chặn tải: địa chỉ không phải HTTPS hoặc host không nằm trong danh sách cho phép")
        }
        if (enforceArtifactPaths) {
            val origin = chain.call().request().url
            val originHost = origin.host.lowercase()
            val isRedirect = url != origin
            val hopCount = recordHop(chain.call(), isRedirect)
            if (isRedirect) {
                if (hopCount > 1) {
                    throw IOException("Chặn tải: vượt quá 1 bước chuyển hướng cho phép")
                }
                if (!isRedirectOnlyDownloadHost(host) ||
                    !isAllowedRedirectHop(originHost, origin.encodedPath, host, url.encodedPath)
                ) {
                    throw IOException("Chặn tải: đích chuyển hướng không khớp nguồn gốc hợp lệ")
                }
                if (isR2RedirectDownloadHost(host) && url.queryParameter("X-Amz-Signature").isNullOrBlank()) {
                    throw IOException("Chặn tải: URL R2 thiếu chữ ký SigV4")
                }
            } else {
                if (isRedirectOnlyDownloadHost(host)) {
                    throw IOException("Chặn tải: host CDN/R2 chỉ nhận redirect từ URL gốc hợp lệ")
                }
                if (!isAllowedDownloadPath(host, url.encodedPath)) {
                    throw IOException("Chặn tải: đường dẫn không nằm trong tiền tố cho phép của host")
                }
            }
        }
        return chain.proceed(chain.request())
    }
}

