package vn.aow.monika.runner

import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import okhttp3.OkHttpClient
import org.junit.Assert.*
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

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class ExperimentalLaunchTest {
    @Test fun currentConfigFlagAlsoGatesAnOlderCachedGameWithTheShortKirikiriName() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val launcher = GameLauncher(ConfigRepository(activity, OkHttpClient()))
        val cached = Game(File("/synthetic/v81-cached"), "Test",
            SystemDef("kirikiri", "Kirikiri (KAG)", "unsupported-test", experimental = false), null)
        assertEquals(LaunchResult.NeedExperimentalConfirmation("Kirikiri"), launcher.launch(activity, cached))
        assertTrue(launcher.launch(activity, cached, experimentalConfirmed = true) is LaunchResult.Failed)
    }
    @Test fun experimentalFlagGatesBeforeAnyRunnerAndConfirmationOnlyBypassesThatGate() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val launcher = GameLauncher(ConfigRepository(activity, OkHttpClient()))
        val game = Game(File("/synthetic/v81"), "Test", SystemDef("v81-test", "Kirikiri", "unsupported-test", experimental = true), null)
        assertEquals(LaunchResult.NeedExperimentalConfirmation("Kirikiri"), launcher.launch(activity, game))
        assertTrue(launcher.launch(activity, game, experimentalConfirmed = true) is LaunchResult.Failed)
        assertEquals(LaunchResult.NeedExperimentalConfirmation("Kirikiri"), launcher.launch(activity, game))
        assertTrue(launcher.launch(activity, game.copy(system = game.system!!.copy(experimental = false))) is LaunchResult.Failed)
    }
}
