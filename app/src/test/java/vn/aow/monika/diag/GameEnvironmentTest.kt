package vn.aow.monika.diag
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.ui.TestApp
import java.io.File
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class GameEnvironmentTest {
    @Test fun sessionSnapshotAndAudioAreIncludedWithoutPaths() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val pack = File(app.filesDir,"packs/ci-env").apply { mkdirs() }
        try {
            File(pack,"version").writeText("ci-env-v1")
            Diagnostics.begin(app,"rgss","mkxp-z","","test","RGSS")
            val env=Diagnostics.envLine(app)
            for (field in listOf("process64=","nativeBridge=","configVersion=","PerformanceTier=","ci-env=ci-env-v1","GL=","musicVolume=","musicMuted=","audioOutputs=")) assertTrue(field,env.contains(field))
            assertFalse(env.contains(app.filesDir.absolutePath))
            File(pack,"version").writeText("ci-env-v2")
            assertTrue(Diagnostics.envLine(app).contains("ci-env-v1")) // Một lần tại begin, không giả như đã tải bản mới.
        } finally { pack.deleteRecursively(); Diagnostics.end(app) }
    }
    @Test fun cacheGl_khongDocGlKhiChuaCoContext_vaCacheKhiCoContext() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        var reads = 0
        GameEnvironment.cacheGl(app, "test", hasContext = { false }, read = { reads++; "x" })
        assertEquals("chưa có EGL context thì không được gọi glGetString (V56)", 0, reads)
        GameEnvironment.cacheGl(app, "test", hasContext = { true }, read = { if (it == android.opengl.GLES20.GL_RENDERER) "Mali-G715" else "OpenGL ES 3.2" })
        assertTrue(GameEnvironment.current(app).contains("cache(test): Mali-G715 / OpenGL ES 3.2"))
    }
}
