package vn.aow.monika.pack

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PackManifestIntegrityTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun main(root: File) = File(root, "main.bin").apply { writeBytes(byteArrayOf(1, 2, 3)) }
    private fun manifest(root: File, body: String) { File(root, "manifest.json").writeText(body) }

    @Test fun acceptsPerFileManifestSizeHashAndLoadOrder() {
        val root = tmp.newFolder(); val main = main(root)
        File(root, "dep.bin").writeText("dependency")
        manifest(root, """{"abi":"web","loadOrder":["dep.bin"],"files":{"main.bin":{"size":3,"sha256":"${hash(main.readBytes()).uppercase()}"}}}""")
        PackTransaction.validate(root, "main.bin", "web")
        assertArrayEquals(byteArrayOf(1, 2, 3), main.readBytes())
    }

    @Test fun rejectsWrongAbiSizeHashAndMissingLoadOrder() {
        val root = tmp.newFolder(); main(root)
        val cases = listOf(
            """{"abi":"arm64-v8a"}""",
            """{"files":{"main.bin":{"size":4}}}""",
            """{"files":{"main.bin":{"sha256":"${"0".repeat(64)}"}}}""",
            """{"loadOrder":["missing.bin"]}""",
            """{"files":{"missing.bin":{"size":1}}}"""
        )
        for (body in cases) {
            manifest(root, body)
            assertThrows(IOException::class.java) { PackTransaction.validate(root, "main.bin", "web") }
        }
    }

    @Test fun refusesManifestPathsOutsideCandidateAndEmptyDependencies() {
        val root = tmp.newFolder(); main(root)
        File(tmp.root, "outside.bin").writeText("outside")
        for (body in listOf("""{"loadOrder":["../outside.bin"]}""", """{"files":{"../outside.bin":{}}}""")) {
            manifest(root, body)
            assertThrows(IOException::class.java) { PackTransaction.validate(root, "main.bin", "web") }
        }
        File(root, "empty.bin").writeBytes(byteArrayOf())
        manifest(root, """{"loadOrder":["empty.bin"]}""")
        assertThrows(IOException::class.java) { PackTransaction.validate(root, "main.bin", "web") }
    }

    @Test fun downloadVerifiesSizeHashAndProgressWithoutNetwork() = runBlocking {
        val bytes = ByteArray(70000) { (it % 251).toByte() }
        val file = tmp.newFile(); val progress = mutableListOf<Long>()
        PackTransaction.download(ByteArrayInputStream(bytes), file, hash(bytes).uppercase(), bytes.size.toLong()) { progress += it }
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(bytes.size.toLong(), progress.last())
        assertTrue(progress.zipWithNext().all { (a, b) -> a < b })
        assertThrows(IOException::class.java) { runBlocking { PackTransaction.download(ByteArrayInputStream(bytes), file, hash(bytes), bytes.size + 1L) } }
        assertThrows(IOException::class.java) { runBlocking { PackTransaction.download(ByteArrayInputStream(bytes), file, "0".repeat(64)) } }
    }

    @Test fun flattenRejectsCollidingLibraryNames() {
        val archive = tmp.newFile("pack.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            for (name in listOf("a/libsame.so", "b/libsame.so")) {
                zip.putNextEntry(ZipEntry(name)); zip.write(1); zip.closeEntry()
            }
        }
        assertThrows(IOException::class.java) { PackTransaction.unzip(archive, tmp.newFolder(), flatten = true) }
    }

    @Test fun failedManifestValidationPreservesInstalledVersion() = runBlocking {
        val target = File(tmp.newFolder(), "engine").apply { mkdirs() }
        File(target, "main.bin").writeText("known-good")
        File(target, "version").writeText("old")
        assertThrows(IOException::class.java) { runBlocking {
            PackTransaction.locked(target) { PackTransaction.install(target) { _, candidate ->
                main(candidate)
                manifest(candidate, """{"files":{"main.bin":{"sha256":"${"0".repeat(64)}"}}}""")
                PackTransaction.validate(candidate, "main.bin", "web")
            } }
        } }
        assertEquals("known-good", File(target, "main.bin").readText())
        assertEquals("old", File(target, "version").readText())
        assertFalse(target.parentFile!!.listFiles()!!.any { it.name.startsWith(".engine.install-") })
    }
}
