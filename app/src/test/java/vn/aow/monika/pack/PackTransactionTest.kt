package vn.aow.monika.pack

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class PackTransactionTest {
    private fun target() = File(Files.createTempDirectory("pack-tx").toFile(), "test")
    private fun old(target: File) { target.mkdirs(); File(target, "main").writeText("good-old"); File(target, "version").writeText("1") }

    @Test fun failureBeforePromotionKeepsOldAndRemovesCandidate() = runBlocking {
        val target = target(); try {
            old(target)
            assertThrows(IOException::class.java) { runBlocking {
                PackTransaction.locked(target) { PackTransaction.install(target) { _, candidate ->
                    File(candidate, "main").writeText("half"); throw IOException("network lost")
                } }
            } }
            assertEquals("good-old", File(target, "main").readText())
            assertFalse(target.parentFile!!.listFiles()!!.any { it.name.startsWith(".test.install-") })
        } finally { target.parentFile!!.deleteRecursively() }
    }

    @Test fun renameFailureRestoresPreviousAndInterruptedSwapRecovers() = runBlocking {
        val target = target(); try {
            old(target)
            assertThrows(IOException::class.java) { runBlocking {
                PackTransaction.locked(target) { PackTransaction.install(target) { _, candidate -> candidate.deleteRecursively() } }
            } }
            assertEquals("good-old", File(target, "main").readText())
            assertTrue(target.renameTo(File(target.parentFile, ".test.previous")))
            PackTransaction.locked(target) { assertEquals("good-old", File(target, "main").readText()) }
        } finally { target.parentFile!!.deleteRecursively() }
    }

    @Test fun concurrentInstallersCannotInterleaveCandidateWrites() = runBlocking {
        val target = target(); try {
            old(target)
            val firstEntered = CompletableDeferred<Unit>(); val allowFirst = CompletableDeferred<Unit>()
            val one = async(Dispatchers.IO) {
                PackTransaction.locked(target) { PackTransaction.install(target) { _, candidate ->
                    File(candidate, "main").writeText("one"); firstEntered.complete(Unit); allowFirst.await()
                } }
            }
            firstEntered.await()
            val two = async(Dispatchers.IO) {
                PackTransaction.locked(target) {
                    assertEquals("one", File(target, "main").readText())
                    PackTransaction.install(target) { _, candidate -> File(candidate, "main").writeText("two") }
                }
            }
            assertEquals("good-old", File(target, "main").readText())
            allowFirst.complete(Unit); one.await(); two.await()
            assertEquals("two", File(target, "main").readText())
        } finally { target.parentFile!!.deleteRecursively() }
    }

    @Test fun realAzaharNeededAndStableNdkLibrariesAreAcceptedButPrivateDependencyIsRequired() {
        val root = Files.createTempDirectory("azahar-needed").toFile()
        try {
            val elf = ByteArray(20).also {
                byteArrayOf(127, 69, 76, 70).copyInto(it)
                it[4] = 2; it[5] = 1; it[18] = 183.toByte()
            }
            File(root, "libazahar.so").writeBytes(elf)
            val fixture = checkNotNull(javaClass.getResource("/packs/azahar-needed.txt")).readText()
            assertEquals(10, fixture.lineSequence().count { it.isNotBlank() })
            File(root, "needed.txt").writeText(fixture)
            PackTransaction.validate(root, "libazahar.so", "arm64-v8a")
            File(root, "needed.txt").appendText("libstdc++.so\nlibsync.so\nlibneuralnetworks.so\nlibOpenMAXAL.so\nlibamidi.so\nlibbinder_ndk.so\n")
            PackTransaction.validate(root, "libazahar.so", "arm64-v8a")
            File(root, "needed.txt").appendText("libc++_shared.so\n")
            assertThrows(IOException::class.java) { PackTransaction.validate(root, "libazahar.so", "arm64-v8a") }
            File(root, "libc++_shared.so").writeBytes(elf)
            PackTransaction.validate(root, "libazahar.so", "arm64-v8a")
        } finally { root.deleteRecursively() }
    }

}
