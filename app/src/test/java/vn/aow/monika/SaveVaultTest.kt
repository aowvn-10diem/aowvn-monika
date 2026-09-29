package vn.aow.monika

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.library.SaveVault
import vn.aow.monika.ui.TestApp
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class SaveVaultTest {
    @Test fun exportThenImportRestoresAndBlocksTraversal() {
        val c = ApplicationProvider.getApplicationContext<android.app.Application>()
        File(c.filesDir, "saves").apply { mkdirs() }.resolve("a.srm").writeText("hello")
        File(c.filesDir, "azahar/states").apply { mkdirs() }.resolve("b.cst").writeText("xy")
        val zip = File(c.cacheDir, "v.zip")
        assertEquals(2, SaveVault.export(c, android.net.Uri.fromFile(zip)))
        File(c.filesDir, "saves/a.srm").delete()
        assertEquals(2, SaveVault.import(c, android.net.Uri.fromFile(zip)))
        assertEquals("hello", File(c.filesDir, "saves/a.srm").readText())
        // Zip độc: đường dẫn thoát ra ngoài phải bị bỏ.
        val evil = File(c.cacheDir, "e.zip")
        java.util.zip.ZipOutputStream(evil.outputStream()).use { z ->
            z.putNextEntry(java.util.zip.ZipEntry("saves/../../evil.txt")); z.write(1); z.closeEntry()
        }
        SaveVault.import(c, android.net.Uri.fromFile(evil))
        assertFalse(File(c.filesDir.parentFile, "evil.txt").exists())
    }
}
