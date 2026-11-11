package vn.aow.monika.download

import okhttp3.Call
import okhttp3.Connection
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.concurrent.TimeUnit

/**
 * Chain giả cho test guard: ghi lại URL thực sự được gửi đi, không chạm mạng.
 * [proceeded] == null nghĩa là guard đã chặn trước khi request ra ngoài.
 * [origin] mô phỏng request gốc của cả chuỗi redirect (`Call.request()`).
 */
internal class FakeChain(url: String, origin: String = url) : Interceptor.Chain {
    private val urlText = url
    private val call = OkHttpClient().newCall(Request.Builder().url(origin.toHttpUrl()).build())
    var proceeded: HttpUrl? = null

    override fun request(): Request = Request.Builder().url(urlText.toHttpUrl()).build()

    override fun proceed(request: Request): Response {
        proceeded = request.url
        return Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body("".toResponseBody(null)).build()
    }

    override fun connection(): Connection? = null
    override fun call(): Call = call
    override fun connectTimeoutMillis(): Int = 0
    override fun readTimeoutMillis(): Int = 0
    override fun writeTimeoutMillis(): Int = 0
    override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
}
