package vn.aow.monika

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.azahar.AzaharSession
import vn.aow.monika.ui.TestApp
import java.io.File

/**
 * V205: chỉ xoá bộ đệm shader khi phiên trước sập **trước khung hình đầu**;
 * phiên đã có hình (hoặc thoát sạch) phải giữ nguyên cache lành và never đụng save/NAND.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class AzaharSessionTest {
    @get:Rule val tmp = TemporaryFolder()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    /** Thư mục người dùng của engine: có NAND, config.ini và bộ đệm shader (2 tệp). */
    private fun userDir(): File = tmp.newFolder("azahar").apply {
        File(this, "nand").mkdirs()
        File(this, "nand/save.bin").writeText("save")
        File(this, "config").mkdirs()
        File(this, "config/config.ini").writeText("[Renderer]\nresolution_factor = 1\n")
        File(this, "cache/shaders").mkdirs()
        File(this, "cache/shaders/opengl.cache").writeText("cache")
        File(this, "cache/vulkan").mkdirs()
        File(this, "cache/vulkan/pipeline.bin").writeText("cache")
    }

    @Test fun decisionOnlyTreatsUnfinishedSessionAsBrokenCache() {
        assertFalse(AzaharSession.shaderCacheBroken(null)) // lần trước thoát sạch
        assertFalse(AzaharSession.shaderCacheBroken(AzaharSession.FIRST_FRAME))
        assertFalse(AzaharSession.shaderCacheBroken("giá trị lạ"))
        assertTrue(AzaharSession.shaderCacheBroken(AzaharSession.STARTED))
    }

    @Test fun cleanExitRemovesMarkerAndLeavesCacheAlone() {
        val dir = userDir()
        AzaharSession.begin(app, dir)
        assertEquals(AzaharSession.STARTED, AzaharSession.read(app, dir))
        AzaharSession.end(app, dir)
        assertNull(AzaharSession.read(app, dir))
        assertFalse(AzaharSession.shaderCacheBroken(AzaharSession.read(app, dir)))
        assertTrue(File(dir, "cache/shaders/opengl.cache").isFile)
    }

    @Test fun sessionThatReachedFirstFrameKeepsCache() {
        val dir = userDir()
        AzaharSession.begin(app, dir)
        AzaharSession.firstFrame(app, dir)
        assertEquals(AzaharSession.FIRST_FRAME, AzaharSession.read(app, dir))
        assertFalse(AzaharSession.shaderCacheBroken(AzaharSession.read(app, dir)))
        assertTrue(File(dir, "cache/shaders/opengl.cache").isFile)
        assertTrue(File(dir, "cache/vulkan/pipeline.bin").isFile)
    }

    @Test fun brokenSessionPurgesOnlyShaderCacheNotSaves() {
        val dir = userDir()
        AzaharSession.begin(app, dir) // phiên trước sập trước first-frame để lại dấu "started"
        assertTrue(AzaharSession.shaderCacheBroken(AzaharSession.read(app, dir)))

        assertEquals(2, AzaharSession.purgeShaderCache(app, dir))

        assertFalse(File(dir, "cache").exists())
        assertTrue(File(dir, "nand/save.bin").isFile)
        assertTrue(File(dir, "config/config.ini").isFile)
    }

    @Test fun purgeWithoutCacheDirectoryDoesNothing() {
        assertEquals(0, AzaharSession.purgeShaderCache(app, tmp.newFolder("azahar-trong")))
    }
}
