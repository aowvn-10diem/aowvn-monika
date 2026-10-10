package vn.aow.monika.ui

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.config.ConfigRepository
import vn.aow.monika.feed.FeedRepository

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class V69FeedTimeoutTest {
    @Test fun feedCallsAreBoundedAndCanRetryAfterFailure() = runBlocking {
        var attempts = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            assertEquals(java.util.concurrent.TimeUnit.SECONDS.toNanos(10), chain.call().timeout().timeoutNanos())
            attempts++
            if (attempts == 1) throw java.io.IOException("synthetic timeout")
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("{\"feed\":{\"entry\":[]}}".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val repo = FeedRepository(client, ConfigRepository(ApplicationProvider.getApplicationContext<Context>(), client))
        assertTrue(runCatching { repo.fetchList("v69-fake") }.isFailure)
        assertEquals(emptyList<vn.aow.monika.feed.Post>(), repo.fetchList("v69-fake"))
        assertEquals(2, attempts)
    }
}
