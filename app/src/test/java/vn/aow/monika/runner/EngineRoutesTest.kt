package vn.aow.monika.runner

import android.app.Application
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import vn.aow.monika.AppGraph
import vn.aow.monika.config.ConfigRepository
import vn.aow.monika.pack.PackManager
import vn.aow.monika.pack.SimpleModule
import vn.aow.monika.ui.TestApp
import java.io.File

/**
 * Chọn route engine nhúng: có gói cho ABI máy → route; thiếu gói / sai ABI / url trống / không khai engine → null
 * (GameLauncher khi đó rơi về JoiPlay nếu hệ còn `allowExternalApp`). Dữ liệu giả, không mạng.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class EngineRoutesTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val delegateField = "packs\$delegate"
    private lateinit var lazyPacks: Any
    private var savedValue: Any? = null
    private var savedInitializer: Any? = null

    // `AppGraph.packs` là `by lazy` (field static final): không gán lại field, chỉ đổi trạng thái bên trong đối tượng Lazy.
    @Before fun saveGraph() {
        app.applicationInfo.nativeLibraryDir = "/fake/arm64" // SimpleModule đọc thư mục thư viện native khi khởi tạo; mặc định của Robolectric là null
        AppGraph.packs // ép khởi tạo để Lazy có `_value`/`initializer` đúng chỗ
        lazyPacks = ReflectionHelpers.getStaticField(AppGraph::class.java, delegateField)
        savedValue = ReflectionHelpers.getField(lazyPacks, "_value")
        savedInitializer = ReflectionHelpers.getField(lazyPacks, "initializer")
        File(app.filesDir, ConfigRepository.ASSET_NAME).delete()
    }

    @After fun restoreGraph() {
        ReflectionHelpers.setField(lazyPacks, "_value", savedValue)
        ReflectionHelpers.setField(lazyPacks, "initializer", savedInitializer)
        File(app.filesDir, ConfigRepository.ASSET_NAME).delete()
    }

    private fun pack(id: String, abis: String = "\"arm64-v8a\"", url: String = "https://example.test/$id.zip") =
        """"$id":{"version":"1","url":"$url","abis":[$abis]}"""

    /** Máy giả với ABI [abi] ("arm64" hoặc "arm" = thư mục thư viện native) và các gói [modules] trong config. */
    private fun device(abi: String, vararg modules: String) {
        val base = ConfigRepository(app, OkHttpClient()).current.configVersion
        File(app.filesDir, ConfigRepository.ASSET_NAME)
            .writeText("""{"configVersion":$base,"modules":{${modules.joinToString(",")}}}""")
        app.applicationInfo.nativeLibraryDir = "/fake/$abi"
        ReflectionHelpers.setStaticField(Build::class.java, "SUPPORTED_ABIS", arrayOf(if (abi == "arm64") "arm64-v8a" else "armeabi-v7a"))
        val packs = SimpleModule(app, OkHttpClient(), ConfigRepository(app, OkHttpClient()))
        ReflectionHelpers.setField(lazyPacks, "_value", packs)
        ReflectionHelpers.setField(lazyPacks, "initializer", null)
    }

    @Test fun routeTableCoversThreeEmbeddedEngines() {
        assertEquals(setOf("kirikiri", "renpy", "rgss"), EngineRoutes.all.keys)
        assertEquals(PackManager.KIRIKIRI, EngineRoutes.all.getValue("kirikiri").packId)
        assertEquals(PackManager.RENPY8, EngineRoutes.all.getValue("renpy").packId)
        assertEquals(PackManager.RGSS, EngineRoutes.all.getValue("rgss").packId)
    }

    @Test fun rgssUsesEmbeddedRouteWhenArm64PackExists() {
        device("arm64", pack(PackManager.RGSS))
        val route = EngineRoutes.usable("rgss")
        assertNotNull(route)
        assertEquals(PackManager.RGSS, route!!.packId)
        assertEquals("RPG Maker XP/VX/Ace", route.label)
    }

    @Test fun kirikiriAndRenpyGoToTheirOwnRoutes() {
        device("arm64", pack(PackManager.KIRIKIRI), pack(PackManager.RENPY8))
        assertEquals(PackManager.KIRIKIRI, EngineRoutes.usable("kirikiri")!!.packId)
        assertEquals(PackManager.RENPY8, EngineRoutes.usable("renpy")!!.packId)
        assertNull("RGSS chưa có gói → không chọn nhầm route khác", EngineRoutes.usable("rgss"))
    }

    @Test fun rgssFallsBackWhenPackMissingFromConfig() {
        device("arm64", pack(PackManager.KIRIKIRI))
        assertNull(EngineRoutes.usable("rgss"))
    }

    @Test fun rgssFallsBackOnWrongAbi() {
        device("arm", pack(PackManager.RGSS, abis = "\"arm64-v8a\""))
        assertNull(EngineRoutes.usable("rgss"))
    }

    @Test fun packWithBlankUrlIsNotUsable() {
        device("arm64", pack(PackManager.RGSS, url = ""))
        assertNull(EngineRoutes.usable("rgss"))
    }

    @Test fun packWithoutAbiListWorksOnAnyAbi() {
        device("arm", pack(PackManager.RGSS, abis = ""))
        assertNotNull(EngineRoutes.usable("rgss"))
    }

    @Test fun noEngineOrUnknownEngineHasNoRoute() {
        device("arm64", pack(PackManager.RGSS), pack(PackManager.KIRIKIRI), pack(PackManager.RENPY8))
        assertNull(EngineRoutes.usable(null))
        assertNull(EngineRoutes.usable(""))
        assertNull(EngineRoutes.usable("joiplay"))
    }

    @Test fun productionConfigRoutesRgssToEmbeddedWithJoiplayFallback() {
        File(app.filesDir, ConfigRepository.ASSET_NAME).delete()
        val cfg = ConfigRepository(app, OkHttpClient()).current
        val system = cfg.systems.first { it.id == "rgss" }
        assertEquals("rgss", system.engine)
        assertTrue("JoiPlay vẫn là đường dự phòng", system.allowExternalApp)
        assertEquals("joiplay", system.externalApp)
        assertTrue(EngineRoutes.all.containsKey(system.engine))
        assertEquals(listOf("arm64-v8a"), cfg.modules.getValue(PackManager.RGSS).abis)
    }
}
