package vn.aow.monika.runner

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class SystemFilesCacheTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun zip(entries: List<Pair<String, String>>) = ZipInputStream(ByteArrayInputStream(
        ByteArrayOutputStream().also { out -> ZipOutputStream(out).use { z ->
            entries.forEach { (name, data) -> z.putNextEntry(ZipEntry(name)); z.write(data.toByteArray()); z.closeEntry() }
        } }.toByteArray(),
    ))

    @Test fun cacheSurvivesNewInstanceAndRejectsChangedVersionTruncationAndMalformedMarker() {
        val root = tmp.newFolder()
        zip(listOf("nds/bios.bin" to "synthetic bios")).use { SystemFilesCache(root).install("nds", "https://example.test/bios", "one", it) }
        val cache = SystemFilesCache(root)
        assertTrue(cache.ready("nds", "https://example.test/bios", "one"))
        assertFalse(cache.ready("nds", "https://example.test/bios", "two"))
        File(root, "nds/bios.bin").writeText("x")
        assertFalse(cache.ready("nds", "https://example.test/bios", "one"))
        File(root, ".nds-prepared.json").writeText("{broken")
        assertFalse(cache.ready("nds", "https://example.test/bios", "one"))
    }

    @Test fun zipEscapeEmptyAndDuplicateFilesCannotCreateReadyMarker() {
        for (entries in listOf(listOf("../outside.bin" to "x"), emptyList(), listOf("bios.bin" to ""),
            listOf("bios.bin" to "x", "./bios.bin" to "y"))) {
            val root = tmp.newFolder()
            val cache = SystemFilesCache(root)
            assertTrue(runCatching { zip(entries).use { cache.install("nds", "url", "one", it) } }.isFailure)
            assertFalse(cache.ready("nds", "url", "one"))
            assertFalse(File(root.parentFile, "outside.bin").exists())
        }
    }
}
