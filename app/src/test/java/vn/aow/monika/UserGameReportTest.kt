package vn.aow.monika

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.aow.monika.diag.Diagnostics
import vn.aow.monika.diag.UserGameReport
import vn.aow.monika.ui.TestApp
import kotlinx.serialization.json.*
import okhttp3.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34])
class UserGameReportTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private fun image(): Diagnostics.ReportImage {
        val bitmap = Bitmap.createBitmap(960, 640, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(242,140,40)) }
        return try { requireNotNull(UserGameReport.encode(bitmap)) } finally { bitmap.recycle() }
    }
    private fun report(image: Diagnostics.ReportImage?) = Diagnostics.Report(1,1,"user","Game","test","test", image=image)

    @Test fun jpegIsBoundedAndValidatedWithDimensions() {
        val image=image();assertTrue(image.width<=480);assertTrue(image.height<=480)
        assertTrue(Base64.decode(image.data,Base64.NO_WRAP).size<=24576)
        assertEquals(image,UserGameReport.valid(image))
        assertNull(UserGameReport.valid(image.copy(width=image.width+1)))
        assertNull(UserGameReport.valid(image.copy(data="broken")))
        assertNull(UserGameReport.valid(image.copy(mime="image/png")))
    }
    @Test fun utf8BudgetKeepsImageAndScrubsBeforeSaveAndSend() {
        val image=image()
        val r=report(image).copy(detail="token=fixture-private-secret " + "đ".repeat(50000),
            log=List(250){"đ".repeat(300)},crumbs=List(40){"đ".repeat(300)},env="đ".repeat(2000))
        val text=Diagnostics.reportJson(app,r)
        assertTrue(text.toByteArray(Charsets.UTF_8).size<=60000)
        assertFalse(text.contains("fixture-private-secret"))
        assertEquals(image.data,Json.parseToJsonElement(text).jsonObject["image"]!!.jsonObject["data"]!!.jsonPrimitive.content)
        Diagnostics.save(app,r)
        val saved=Diagnostics.list(app).first { it.id==1L };assertEquals(image,saved.image)
        var wire=""
        val http=OkHttpClient.Builder().addInterceptor { chain ->
            val buffer=okio.Buffer();chain.request().body!!.writeTo(buffer);wire=buffer.readUtf8()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(ResponseBody.create(null,"{}")).build()
        }.build()
        assertTrue(Diagnostics.send(app,http,saved,"https://example.test/report"))
        assertTrue(wire.toByteArray(Charsets.UTF_8).size<=60000);assertTrue(wire.contains(image.data))
    }
    @Test fun imageIsOnlyKeptForExplicitUserReport() {
        val r=report(image()).copy(kind="handled")
        assertNull(Diagnostics.sanitized(app,r).image)
    }
    @Test fun entryFailureHasExplicitGameAndEngineWithoutPretendingSessionStarted() {
        val info = "xp3 data.xp3 2KB startup.tjs@gốc=false\nexe game.exe 3KB"
        val r = Diagnostics.recordUser(app, "Không lên hình", "Thiếu startup.tjs", null,
            gameTitle = "Kara no Shoujo", component = "engine:kirikiri", extraDetail = info)
        assertEquals("Báo lỗi game: Kara no Shoujo", r.title)
        assertEquals("engine:kirikiri", r.component)
        assertTrue(r.detail.contains(info) && r.detail.contains("Thiếu startup.tjs"))
        assertNull(r.session)
        val saved = Diagnostics.list(app).first { it.id == r.id }
        assertEquals(r.detail, saved.detail)
        assertFalse(Diagnostics.reportJson(app, saved).contains(app.filesDir.path))
    }

}
