package vn.aow.monika.pack

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files

class PackTransactionTest {
    private fun target() = File(Files.createTempDirectory("pack-tx").toFile(), "test")
    private fun old(target: File) { target.mkdirs(); File(target, "main").writeText("good-old"); File(target, "version").writeText("1") }

    @Test fun installedFilesAreReadOnlyAndUpgradeStillWorks() = runBlocking {
        val target = target(); try {
            PackTransaction.locked(target) { PackTransaction.install(target, readOnly = true) { _, candidate ->
                File(candidate, "main").writeText("v1"); File(candidate, "version").writeText("1")
            } }
            assertTrue(File(target, "main").canRead())
            assertFalse("V204: thư mục lõi sau xác thực phải chỉ-đọc", File(target, "main").canWrite())
            // Nâng cấp lần 2: phải mở quyền ghi để dọn bản .previous, không được ném lỗi.
            PackTransaction.locked(target) { PackTransaction.install(target, readOnly = true) { _, candidate ->
                File(candidate, "main").writeText("v2"); File(candidate, "version").writeText("2")
            } }
            assertEquals("v2", File(target, "main").readText())
        } finally {
            target.parentFile!!.walkTopDown().forEach { it.setWritable(true) }
            target.parentFile!!.deleteRecursively()
        }
    }

    @Test fun packDirectoryStaysWritableByDefault() = runBlocking {
        val target = target(); try {
            PackTransaction.locked(target) { PackTransaction.install(target) { _, candidate ->
                File(candidate, "engine.cfg").writeText("cfg")
            } }
            // Thư mục gói có thể bị engine ghi config → mặc định không đặt chỉ-đọc.
            assertTrue(File(target, "engine.cfg").canWrite())
        } finally {
            target.parentFile!!.deleteRecursively()
        }
    }

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


    @Test fun onsyuriRealFileListInstallsFromNestedOrFlatZipWithoutLosingAssets() {
        val work = Files.createTempDirectory("onsyuri-layout").toFile()
        try {
            val names = checkNotNull(javaClass.getResource("/packs/onsyuri-files.txt")).readText().lineSequence().filter { it.isNotBlank() }.toList()
            assertEquals(7, names.size)
            for (prefix in listOf("onsyuri/", "different-root/", "")) {
                val zip = File(work, "package.zip")
                java.util.zip.ZipOutputStream(zip.outputStream()).use { output ->
                    for (name in names) {
                        output.putNextEntry(java.util.zip.ZipEntry(prefix + name))
                        output.write((if (name == "manifest.json") "{}" else "file:$name").toByteArray())
                        output.closeEntry()
                    }
                }
                val candidate = File(work, "candidate").apply { mkdirs() }
                PackTransaction.unzip(zip, candidate, mainFile = "onsyuri.wasm")
                PackTransaction.validate(candidate, "onsyuri.wasm", "web")
                for (name in names) assertTrue(name, File(candidate, name).isFile)
                assertFalse(File(candidate, "onsyuri").exists()); assertFalse(File(candidate, "different-root").exists())
                candidate.deleteRecursively()
            }
        } finally { work.deleteRecursively() }
    }

    @Test fun rootStrippingRejectsTraversalAndMixedRootDuplicates() {
        val work = Files.createTempDirectory("onsyuri-safe").toFile()
        try {
            for (names in listOf(listOf("bundle/onsyuri.wasm", "bundle/../escaped"), listOf("bundle/onsyuri.wasm", "bundle/./onsyuri.wasm"))) {
                val zip = File(work, "package.zip")
                java.util.zip.ZipOutputStream(zip.outputStream()).use { output ->
                    for (name in names) { output.putNextEntry(java.util.zip.ZipEntry(name)); output.write(1); output.closeEntry() }
                }
                val candidate = File(work, "candidate").apply { mkdirs() }
                assertThrows(IOException::class.java) { PackTransaction.unzip(zip, candidate, mainFile = "onsyuri.wasm") }
                assertFalse(File(work, "escaped").exists())
                candidate.deleteRecursively()
            }
        } finally { work.deleteRecursively() }
    }


    @Test fun multipleRootsAreNotStrippedAndExpectedNestedMainKeepsLayout() {
        val work = Files.createTempDirectory("pack-roots").toFile()
        try {
            val zip = File(work, "package.zip")
            fun archive(names: List<String>) {
                java.util.zip.ZipOutputStream(zip.outputStream()).use { output ->
                    for (name in names) { output.putNextEntry(java.util.zip.ZipEntry(name)); output.write(1); output.closeEntry() }
                }
            }
            val candidate = File(work, "candidate").apply { mkdirs() }
            archive(listOf("bundle/onsyuri.wasm", "other/readme"))
            PackTransaction.unzip(zip, candidate, mainFile = "onsyuri.wasm")
            assertFalse(File(candidate, "onsyuri.wasm").exists())
            assertTrue(File(candidate, "other/readme").isFile)
            assertThrows(IOException::class.java) { PackTransaction.validate(candidate, "onsyuri.wasm", "web") }
            candidate.deleteRecursively(); candidate.mkdirs()
            archive(listOf("lib/main.bin", "lib/dependency.bin"))
            PackTransaction.unzip(zip, candidate, mainFile = "lib/main.bin")
            assertTrue(File(candidate, "lib/main.bin").isFile)
            assertFalse(File(candidate, "main.bin").exists())
        } finally { work.deleteRecursively() }
    }

    /** 20 byte đầu đủ để [PackTransaction.validateElf] đọc magic/lớp/machine. */
    private fun elfFile(dir: File, name: String, bits: Int, machine: Int, size: Int = 4 * 1024): File {
        val header = ByteArray(20)
        header[0] = 127; header[1] = 'E'.code.toByte(); header[2] = 'L'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = bits.toByte(); header[5] = 1 // lớp ELF và little-endian
        header[18] = (machine and 255).toByte(); header[19] = ((machine shr 8) and 255).toByte()
        val file = File(dir, name)
        file.outputStream().use { it.write(header); if (size > header.size) it.write(ByteArray(size - header.size)) }
        return file
    }

    @Test fun validateElfAcceptsMatchingAbiAndRejectsWrongOnes() {
        val work = Files.createTempDirectory("core-elf").toFile()
        try {
            PackTransaction.validateElf(elfFile(work, "ok-arm64.so", 2, 183), "arm64-v8a")
            PackTransaction.validateElf(elfFile(work, "ok-v7a.so", 1, 40), "armeabi-v7a")
            // .so 32-bit lọt vào máy arm64 (buildbot trả nhầm thư mục) — phải chặn, không để dlopen lỗi sau.
            assertThrows(IOException::class.java) { PackTransaction.validateElf(elfFile(work, "v7a-on-arm64.so", 1, 40), "arm64-v8a") }
            assertThrows(IOException::class.java) { PackTransaction.validateElf(elfFile(work, "x86-on-arm64.so", 1, 3), "arm64-v8a") }
            // Trang lỗi HTML / ZIP cắt cụt: không có magic ELF.
            val html = File(work, "page.so").apply { writeText("<html><body>502 Bad Gateway</body></html>") }
            assertThrows(IOException::class.java) { PackTransaction.validateElf(html, "arm64-v8a") }
            val cut = elfFile(work, "cut.so", 2, 183).apply { writeBytes(readBytes().copyOf(12)) }
            assertThrows(IOException::class.java) { PackTransaction.validateElf(cut, "arm64-v8a") }
            assertThrows(IOException::class.java) { PackTransaction.validateElf(File(work, "empty.so").apply { createNewFile() }, "arm64-v8a") }
        } finally { work.deleteRecursively() }
    }

    @Test fun buildIdReadsGnuNoteAndIsNullWhenStripped() {
        val work = Files.createTempDirectory("core-build-id").toFile()
        try {
            val withNote = File(work, "with-note.so").apply { writeBytes(ElfFixture.arm64WithBuildId()) }
            assertEquals(ElfFixture.BUILD_ID, PackTransaction.buildId(withNote))
            // Lõi buildbot đã strip: không có PT_NOTE ⇒ null, không ném, không trả chuỗi rỗng.
            assertNull(PackTransaction.buildId(File(work, "stripped.so").apply { writeBytes(ElfFixture.arm64()) }))
            assertNull(PackTransaction.buildId(File(work, "empty.so").apply { createNewFile() }))
            assertNull(PackTransaction.buildId(File(work, "missing.so")))
        } finally { work.deleteRecursively() }
    }

}

/**
 * Dựng byte của một `.so` ELF64/arm64 tối thiểu để test: header hợp lệ, tuỳ chọn một segment `PT_NOTE` chứa
 * note `GNU` build-id — đúng thứ `PackTransaction.validateElf` và `buildId` đọc.
 *
 * Để trong chính tệp này (không tách tệp) vì `tools/pack-check/build.gradle.kts` chỉ biên dịch
 * `PackTransaction.kt` + `PackTransactionTest.kt`; tách tệp thì cổng `real-packs` không thấy fixture.
 */
internal object ElfFixture {
    const val BUILD_ID = "9f2c3b4a5d6e7f8091a2b3c4d5e6f708192a3b4c"

    private const val HEADER = 64
    private const val PHDR = 56
    private const val NOTE_AT = HEADER + PHDR

    /** `.so` arm64 không có note build-id (giống lõi buildbot đã strip). */
    fun arm64(): ByteArray = elf(null)

    /** `.so` arm64 có `NT_GNU_BUILD_ID` với mô tả [id] (mặc định [BUILD_ID]). */
    fun arm64WithBuildId(id: ByteArray = hex(BUILD_ID)): ByteArray = elf(id)

    fun hex(text: String): ByteArray = ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun elf(buildId: ByteArray?): ByteArray {
        val noteLen = if (buildId == null) 0 else 12 + 4 + align4(buildId.size)
        val buffer = ByteBuffer.allocate(NOTE_AT + noteLen).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(byteArrayOf(127, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()))
        buffer.put(2) // ELFCLASS64
        buffer.put(1) // little-endian
        buffer.put(1) // EI_VERSION
        buffer.put(0)
        buffer.position(16); buffer.putShort(3); buffer.putShort(183); buffer.putInt(1) // ET_DYN, EM_AARCH64, EV_CURRENT
        buffer.position(32); buffer.putLong(HEADER.toLong()) // e_phoff
        buffer.position(52); buffer.putShort(HEADER.toShort()); buffer.putShort(PHDR.toShort())
        buffer.putShort((if (buildId == null) 0 else 1).toShort()) // e_ehsize, e_phentsize, e_phnum
        if (buildId != null) {
            buffer.position(HEADER); buffer.putInt(4); buffer.putInt(4) // PT_NOTE, PF_R
            buffer.putLong(NOTE_AT.toLong()) // p_offset
            buffer.position(HEADER + 32); buffer.putLong(noteLen.toLong()); buffer.putLong(noteLen.toLong())
            buffer.position(NOTE_AT); buffer.putInt(4); buffer.putInt(buildId.size); buffer.putInt(3) // NT_GNU_BUILD_ID
            buffer.put(byteArrayOf('G'.code.toByte(), 'N'.code.toByte(), 'U'.code.toByte(), 0))
            buffer.put(buildId)
        }
        return buffer.array()
    }

    private fun align4(n: Int) = (n + 3) and -4
}
