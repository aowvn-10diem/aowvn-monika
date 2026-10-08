package vn.aow.monika

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.azahar.AzaharConfig
import vn.aow.monika.runner.CoreOptions
import vn.aow.monika.ui.TestApp
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class AzaharConfigFileTest {
    @get:Rule val tmp = TemporaryFolder()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Before fun cleanPreferences() {
        CoreOptions.reset(app, AzaharConfig.CORE_ID)
        File(app.filesDir, "azahar").deleteRecursively()
    }

    @Test fun patchesExistingSectionWithoutLosingEngineKeys() {
        val file = tmp.newFile("config.ini")
        file.writeText("# engine note\n[Renderer]\nresolution_factor=2\nunknown = preserve\n[Audio]\nvolume = 73\n")
        AzaharConfig.patch(file, mapOf("Renderer/resolution_factor" to "4", "Renderer/graphics_api" to "2"))
        assertEquals("# engine note\n[Renderer]\nresolution_factor = 4\nunknown = preserve\ngraphics_api = 2\n[Audio]\nvolume = 73\n", file.readText())
        val once = file.readText()
        AzaharConfig.patch(file, mapOf("Renderer/resolution_factor" to "4", "Renderer/graphics_api" to "2"))
        assertEquals(once, file.readText())
    }

    @Test fun createsMissingFileAndNewSectionsWithIndependentKeys() {
        val file = File(tmp.root, "new.ini")
        AzaharConfig.patch(file, mapOf("Core/use_cpu_jit" to "1", "System/is_new_3ds" to "0"))
        assertTrue(file.readText().contains("[Core]\nuse_cpu_jit = 1"))
        assertTrue(file.readText().contains("[System]\nis_new_3ds = 0"))
    }

    @Test fun recognizesWhitespaceButPreservesPrefixKeysAndOtherSections() {
        val file = tmp.newFile("space.ini")
        file.writeText(" [Renderer] \n  graphics_api = 1\ngraphics_api_custom = keep\n[Audio]\ngraphics_api = audio\n")
        AzaharConfig.patch(file, mapOf("Renderer/graphics_api" to "2"))
        assertTrue(file.readText().contains("graphics_api = 2"))
        assertTrue(file.readText().contains("graphics_api_custom = keep"))
        assertTrue(file.readText().endsWith("[Audio]\ngraphics_api = audio\n"))
    }

    @Test fun savedOptionOverridesDefaultAndIsWrittenAlongsideFixedValues() {
        assertEquals("1", AzaharConfig.values(app)["Renderer/resolution_factor"])
        CoreOptions.save(app, AzaharConfig.CORE_ID, "Renderer/resolution_factor", "3")
        val option = AzaharConfig.options(app).single { it.key == "Renderer/resolution_factor" }
        assertEquals("3", option.value)
        assertEquals(listOf("1", "2", "3", "4", "5"), option.values)
        assertEquals("3x", option.display())
        val file = File(AzaharConfig.userDir(app), "config/config.ini")
        file.parentFile!!.mkdirs()
        file.writeText("[Audio]\nvolume = 82\n")
        AzaharConfig.write(app)
        val text = file.readText()
        assertTrue(text.contains("resolution_factor = 3"))
        assertTrue(text.contains("portrait_layout_option = 0"))
        assertTrue(text.contains("use_frame_limit = 1"))
        assertTrue(text.contains("volume = 82"))
        CoreOptions.reset(app, AzaharConfig.CORE_ID)
        AzaharConfig.write(app)
        assertTrue(file.readText().contains("resolution_factor = 1"))
    }
}
