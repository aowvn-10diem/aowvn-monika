package vn.aow.monika.donate

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import vn.aow.monika.config.DonateBankConfig
import vn.aow.monika.config.DonateConfig
import java.io.File
import java.time.Clock
import java.util.concurrent.TimeUnit

/** Chỉ hỏi VietQR khi mở bảng và cache hết 24 giờ; không đi qua Worker. */
class DonationBanks(private val http: OkHttpClient, private val cache: File, private val clock: Clock) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    @Serializable private data class Cached(val at: Long, val endpoint: String, val banks: List<DonateBankConfig>)

    suspend fun load(config: DonateConfig): List<DonateBankConfig> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val saved = runCatching { json.decodeFromString<Cached>(cache.readText()) }.getOrNull()
                ?.takeIf { it.endpoint == config.banksUrl && it.banks.isNotEmpty() }
            val age = saved?.let { clock.millis() - it.at }
            if (age != null && age >= 0 && age < TimeUnit.HOURS.toMillis(24)) return@withLock saved.banks
            try {
                val endpoint = config.banksUrl.toHttpUrlOrNull()
                require(endpoint != null && endpoint.isHttps && endpoint.host == "api.vietqr.io")
                val call = http.newCall(Request.Builder().url(endpoint).build())
                call.timeout().timeout(10, TimeUnit.SECONDS)
                val banks = call.execute().use { response ->
                    check(response.isSuccessful)
                    val body = response.body ?: error("VietQR thiếu dữ liệu")
                    check(body.contentLength() <= 1_048_576)
                    val source = body.source()
                    source.request(1_048_577)
                    check(source.buffer.size <= 1_048_576)
                    parseBanks(source.buffer.readUtf8())
                }
                check(banks.isNotEmpty())
                runCatching {
                    cache.parentFile?.mkdirs()
                    val temp = File(cache.path + ".new")
                    temp.writeText(json.encodeToString(Cached(clock.millis(), config.banksUrl, banks)))
                    check(temp.renameTo(cache))
                }
                banks
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                saved?.banks ?: config.fallbackBanks
            }
        }
    }

    private fun parseBanks(body: String): List<DonateBankConfig> {
        val apps = json.parseToJsonElement(body).jsonObject["apps"]?.jsonArray ?: return emptyList()
        return apps.sortedByDescending { it.jsonObject["monthlyInstall"]?.jsonPrimitive?.longOrNull ?: 0 }
            .mapNotNull { element ->
                val app = element.jsonObject
                val id = app["appId"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val name = app["appName"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
                if (!Regex("[A-Za-z0-9._-]+").matches(id) || name.isBlank()) null
                else DonateBankConfig(id, name.take(80))
            }.distinctBy { it.id }.take(100)
    }
}
