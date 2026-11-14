package vn.aow.monika.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import vn.aow.monika.util.SafeZipPath

class SafeZipPathTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun root() = tmp.newFolder("root-${System.nanoTime()}")

    @Test fun rejectsTraversalAbsoluteAndNulNames() {
        val root = root()
        for (name in listOf(
            "../x", "../../x", "a/../../x", "a/..", "..",
            "/etc/x", "/x", "\\etc\\x", "\\\\may-chu\\share\\x",
            "C:\\x", "C:/x",
            "nul\u0000.apk", "a\u0000/b.apk",
            "", ".",
        )) {
            assertFalse(name, SafeZipPath.isSafeName(name))
            assertNull(name, SafeZipPath.resolve(root, name))
        }
    }

    @Test fun keepsPlainRelativeNamesInsideTarget() {
        val root = root()
        for (name in listOf("base.apk", "splits/base-master.apk", "a/b/c.txt", "..hidden/x", "a//b.txt", "a/./b.txt")) {
            assertTrue(name, SafeZipPath.isSafeName(name))
            val out = SafeZipPath.resolve(root, name)
            assertNotNull(name, out)
            assertTrue(name, out!!.canonicalPath.startsWith(root.canonicalFile.path))
        }
    }

    @Test fun backslashIsSeparatorNotEscape() {
        val root = root()
        // Windows coi "\" là dấu phân cách: "a\..\b" phải bị từ chối, không được thành tệp tên lạ trong root.
        assertNull(SafeZipPath.resolve(root, "a\\..\\b"))
        assertNull(SafeZipPath.resolve(root, "..\\b"))
        assertEqualsFile("a/b", SafeZipPath.resolve(root, "a\\b")!!)
    }

    private fun assertEqualsFile(expectedRel: String, actual: java.io.File) {
        assertTrue(actual.path.replace('\\', '/').endsWith(expectedRel))
    }

    @Test fun sanitizeStripsPathAndControlCharacters() {
        assertEquals("game.zip", SafeZipPath.sanitizeSingleFileName("../../etc/game.zip"))
        assertEquals("game.zip", SafeZipPath.sanitizeSingleFileName("C:\\Users\\me\\game.zip"))
        assertEquals("b game.zip", SafeZipPath.sanitizeSingleFileName("a/b\r\n game.zip "))
        assertEquals("game.zip", SafeZipPath.sanitizeSingleFileName("game.zip\u0000"))
        assertNull(SafeZipPath.sanitizeSingleFileName(""))
        assertNull(SafeZipPath.sanitizeSingleFileName("   "))
        assertNull(SafeZipPath.sanitizeSingleFileName(".."))
        assertNull(SafeZipPath.sanitizeSingleFileName("\u0001\u0002"))
    }

    @Test fun sanitizeClampsLongNamesButKeepsExtension() {
        val long = "a".repeat(300) + ".zip"
        val name = SafeZipPath.sanitizeSingleFileName(long)!!
        assertTrue(name.endsWith(".zip"))
        assertTrue(name.toByteArray(Charsets.UTF_8).size <= SafeZipPath.MAX_NAME_BYTES)
        // Tên không có phần mở rộng vẫn bị cắt.
        val plain = SafeZipPath.sanitizeSingleFileName("b".repeat(300))!!
        assertTrue(plain.toByteArray(Charsets.UTF_8).size <= SafeZipPath.MAX_NAME_BYTES)
    }
}
