package vn.aow.monika.runner

import androidx.activity.ComponentActivity
import okhttp3.OkHttpClient
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.config.ConfigRepository
import vn.aow.monika.config.SystemDef
import vn.aow.monika.library.Game
import vn.aow.monika.ui.TestApp
import java.io.File

/**
 * `.cia` là gói cài đặt 3DS, không phải ROM: chỉ đường Azahar cài được.
 * Đưa vào lõi libretro (citra) thì `retro_load_game` thất bại → `core-error` (#301, #610).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class CiaRouteTest {

    private fun reason(path: String, azaharRoute: Boolean = false) =
        GameLauncher.ciaBlockedReason(File(path), azaharRoute)

    @Test fun ciaBiChanKhiChayBangLoiLibretro() {
        val message = reason("/synthetic/Zelda.cia")
        assertTrue(message != null && message.contains(".cia"))
    }

    @Test fun duoiCiaHoaThuongCungBiChan() {
        assertTrue(reason("/synthetic/Zelda.CIA") != null)
    }

    @Test fun rom3dsVanChayQuaLoiLibretro() {
        assertNull(reason("/synthetic/Zelda.3ds"))
        assertNull(reason("/synthetic/Zelda.cci"))
        assertNull(reason("/synthetic/Zelda.cxi"))
        assertNull(reason("/synthetic/Zelda.3dsx"))
    }

    @Test fun ciaVanDuocCaiQuaAzahar() {
        assertNull(reason("/synthetic/Zelda.cia", azaharRoute = true))
    }

    @Test fun launchChanCiaTruocKhiVaoLoiLibretro() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val launcher = GameLauncher(ConfigRepository(activity, OkHttpClient()))
        val system = SystemDef(
            "3ds-cia-test", "Nintendo 3DS", "libretro", core = "citra",
            extensions = listOf("3ds", "cci", "cxi", "3dsx", "cia"), pad = "n3ds",
        )
        val entry = File("/synthetic/zelda.cia")
        val result = launcher.launch(activity, Game(entry, "Zelda", system, entry))
        assertTrue("Phải chặn ở GameLauncher thay vì vào lõi libretro: $result", result is LaunchResult.Failed)
        assertTrue((result as LaunchResult.Failed).message.contains(".cia"))
    }
}
