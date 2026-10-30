package vn.aow.monika

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.playsoftware.j2meloader.config.MonikaScreenDefaults
import vn.aow.monika.config.ConfigRepository
import vn.aow.monika.config.J2meScreenConfig
import vn.aow.monika.runner.J2meScreenDetector
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * V135: game Java tự nhận độ phân giải từ JAD/manifest, không có thì đoán theo ảnh trong jar,
 * cuối cùng dùng mặc định trong config. Game đã có config.json thì KHÔNG ghi đè.
 */
class J2meScreenDetectorTest {
    private val cfg = ConfigRepository
        .parse(File("../config/monika-config.json").readText())
        .j2meScreen
    private val dir = Files.createTempDirectory("j2me-screen").toFile()
    private var index = 0

    private fun jar(vararg entries: Pair<String, ByteArray>): File {
        val file = File(dir, "game${index++}.jar")
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    private fun manifest(vararg lines: String): ByteArray =
        ("Manifest-Version: 1.0\r\n" + lines.joinToString("\r\n") + "\r\n").toByteArray()

    private fun png(width: Int, height: Int): ByteArray {
        val bytes = ByteArray(33)
        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A).copyInto(bytes)
        bytes[16] = (width ushr 24).toByte()
        bytes[17] = (width ushr 16).toByte()
        bytes[18] = (width ushr 8).toByte()
        bytes[19] = width.toByte()
        bytes[20] = (height ushr 24).toByte()
        bytes[21] = (height ushr 16).toByte()
        bytes[22] = (height ushr 8).toByte()
        bytes[23] = height.toByte()
        return bytes
    }

    @Test
    fun `doc kich thuoc tu manifest cua jar`() {
        val game = jar(
            "META-INF/MANIFEST.MF" to manifest(
                "MIDlet-Name: Game",
                "Nokia-MIDlet-Original-Display-Size: 176, 208",
            ),
        )
        val screen = J2meScreenDetector.detect(game, cfg)
        assertEquals(176, screen.width)
        assertEquals(208, screen.height)
        assertEquals("manifest:Nokia-MIDlet-Original-Display-Size", screen.source)
    }

    @Test
    fun `jad uu tien hon manifest va doc duoc kieu x`() {
        val game = jar(
            "META-INF/MANIFEST.MF" to manifest("Nokia-MIDlet-Original-Display-Size: 128,128"),
        )
        File(game.parentFile, game.nameWithoutExtension + ".jad").writeText(
            "MIDlet-Jar-URL: game.jar\r\nNokia-MIDlet-Original-Display-Size: 240x320\r\n",
        )
        val screen = J2meScreenDetector.detect(game, cfg)
        assertEquals(240, screen.width)
        assertEquals(320, screen.height)
        assertTrue(screen.source.startsWith("jad:"))
    }

    @Test
    fun `manifest xuong dong van doc duoc kich thuoc`() {
        val game = jar(
            "META-INF/MANIFEST.MF" to manifest(
                "Nokia-MIDlet-Target-Display-Size: 176,",
                " 220",
            ),
        )
        val screen = J2meScreenDetector.detect(game, cfg)
        assertEquals(176, screen.width)
        assertEquals(220, screen.height)
    }

    @Test
    fun `mo thang file jad van doc duoc`() {
        val game = jar("META-INF/MANIFEST.MF" to manifest("MIDlet-Name: Game"))
        val jad = File(game.parentFile, game.nameWithoutExtension + ".jad")
        jad.writeText("Nokia-MIDlet-Original-Display-Size: 128, 160\r\n")
        val screen = J2meScreenDetector.detect(jad, cfg)
        assertEquals(128, screen.width)
        assertEquals(160, screen.height)
        assertEquals("jad:Nokia-MIDlet-Original-Display-Size", screen.source)
    }

    @Test
    fun `khong khai bao thi doan theo anh lon nhat trung preset`() {
        val game = jar(
            "META-INF/MANIFEST.MF" to manifest("MIDlet-Name: Game"),
            "icon.png" to png(128, 128),
            "splash/bg.png" to png(360, 640),
            "Game.class" to ByteArray(8),
        )
        val screen = J2meScreenDetector.detect(game, cfg)
        assertEquals(360, screen.width)
        assertEquals(640, screen.height)
        assertTrue(screen.source.startsWith("image:"))
    }

    @Test
    fun `khong doan duoc thi dung mac dinh trong config`() {
        val game = jar(
            "META-INF/MANIFEST.MF" to manifest("MIDlet-Name: Game"),
            "icon.png" to png(16, 16),
        )
        val screen = J2meScreenDetector.detect(game, cfg)
        assertEquals(240, screen.width)
        assertEquals(320, screen.height)
        assertEquals("default", screen.source)

        val custom = J2meScreenDetector.detect(game, J2meScreenConfig(defaultWidth = 320, defaultHeight = 240))
        assertEquals(320, custom.width)
        assertEquals(240, custom.height)
    }

    @Test
    fun `file hong khong lam crash`() {
        val broken = File(dir, "broken.jar").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val screen = J2meScreenDetector.detect(broken, cfg)
        assertEquals("default", screen.source)
    }

    @Test
    fun `game da co config thi khong ghi de kich thuoc`() {
        assertNull(MonikaScreenDefaults.screenForNewConfig(true, 176, 208))
        assertArrayEquals(intArrayOf(176, 208), MonikaScreenDefaults.screenForNewConfig(false, 176, 208)!!)
        assertNull(MonikaScreenDefaults.screenForNewConfig(false, 0, 0))
    }

    @Test
    fun `config cu thieu j2meScreen van doc duoc`() {
        val old = ConfigRepository.parse("{}").j2meScreen
        assertTrue(old.attributes.isEmpty())
        assertTrue(old.presets.isEmpty())
        assertEquals(240, old.defaultWidth)
        assertEquals(320, old.defaultHeight)
    }

    @Test
    fun `bang doan trong config that co thuoc tinh va preset`() {
        assertEquals("Nokia-MIDlet-Original-Display-Size", cfg.attributes.first())
        assertTrue(cfg.presets.contains(listOf(176, 208)))
        assertTrue(cfg.presets.contains(listOf(240, 320)))
        assertEquals(200, cfg.maxImages)
        assertEquals(131_072, cfg.maxImageBytes)
    }
}
