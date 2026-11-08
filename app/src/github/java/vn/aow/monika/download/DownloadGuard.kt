package vn.aow.monika.download

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * V196: kiểm lại đích tải ở **từng hop** (kể cả hop do redirect 30x sinh ra) trước khi gửi request.
 *
 * Phải gắn bằng `addNetworkInterceptor` (không phải `addInterceptor`): network interceptor chạy cho
 * mọi request thực sự ra mạng, còn application interceptor chỉ chạy một lần cho cả chuỗi redirect.
 */
class DownloadGuard(
    private val allowedHost: (String) -> Boolean,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        if (url.scheme != "https" || !allowedHost(url.host)) {
            throw IOException("Chặn tải: địa chỉ không phải HTTPS hoặc host không nằm trong danh sách cho phép")
        }
        return chain.proceed(chain.request())
    }
}
