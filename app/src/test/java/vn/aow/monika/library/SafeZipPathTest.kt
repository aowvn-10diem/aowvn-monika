package vn.aow.monika.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

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
}
