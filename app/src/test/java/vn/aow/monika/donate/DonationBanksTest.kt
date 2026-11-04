package vn.aow.monika.donate

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import vn.aow.monika.config.DonateConfig
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class DonationBanksTest {
    @get:Rule val temp = TemporaryFolder()
    private class MutableClock(var now: Long = 10_000) : Clock() {
        override fun instant(): Instant = Instant.ofEpochMilli(now)
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
    }
    private val config = DonateConfig()
    private val payload = """{"apps":[{"appId":"vcb","appName":"Vietcombank","monthlyInstall":20},{"appId":"icb","appName":"VietinBank","monthlyInstall":5},{"appId":"javascript:bad","appName":"Bad"},{"appId":"vcb","appName":"Duplicate"}]}"""

    @Test fun cacheSurvivesNewRepositoryAndRefreshesAt24Hours() = runBlocking {
        var requests = 0
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            requests++; assertEquals("api.vietqr.io", chain.request().url.host)
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(payload.toResponseBody()).build()
        }.build()
        val clock = MutableClock(); val cache = File(temp.root, "banks.json")
        val first = DonationBanks(http, cache, clock).load(config)
        assertEquals(listOf("vcb", "icb"), first.map { it.id })
        assertEquals(first, DonationBanks(http, cache, clock).load(config)); assertEquals(1, requests)
        clock.now += 24 * 60 * 60 * 1000L - 1
        DonationBanks(http, cache, clock).load(config); assertEquals(1, requests)
        clock.now++
        DonationBanks(http, cache, clock).load(config); assertEquals(2, requests)
    }
    @Test fun serverErrorUsesTwelveBundledBanks() = runBlocking {
        val http = OkHttpClient.Builder().addInterceptor { chain -> Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(503).message("Offline").body("{}".toResponseBody()).build() }.build()
        val banks = DonationBanks(http, File(temp.root, "banks.json"), MutableClock()).load(config)
        assertEquals(12, banks.size); assertEquals(config.fallbackBanks, banks)
    }
    @Test fun brokenPayloadAndCacheUseFallbackInsteadOfCrashing() = runBlocking {
        val cache = File(temp.root, "banks.json").apply { writeText("not json") }
        val http = OkHttpClient.Builder().addInterceptor { chain -> Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body("[]".toResponseBody()).build() }.build()
        assertEquals(config.fallbackBanks, DonationBanks(http, cache, MutableClock()).load(config))
    }
    @Test fun expiredCachedBanksRemainUsableWhenNetworkFails() = runBlocking {
        var offline = false
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            if (offline) throw java.io.IOException("offline")
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(payload.toResponseBody()).build()
        }.build()
        val clock = MutableClock(); val repo = DonationBanks(http, File(temp.root, "banks.json"), clock)
        val banks = repo.load(config); offline = true; clock.now += 24 * 60 * 60 * 1000L
        assertEquals(banks, repo.load(config))
    }
    @Test fun unsafeEndpointCannotCallWorkerOrArbitraryServer() = runBlocking {
        var requests = 0
        val http = OkHttpClient.Builder().addInterceptor { requests++; throw AssertionError("Không được gọi endpoint ngoài VietQR") }.build()
        val cfg = config.copy(banksUrl = "https://example.test/banks")
        assertEquals(cfg.fallbackBanks, DonationBanks(http, File(temp.root, "banks.json"), MutableClock()).load(cfg))
        assertEquals(0, requests)
    }
}
