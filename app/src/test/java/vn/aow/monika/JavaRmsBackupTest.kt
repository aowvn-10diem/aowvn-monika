package vn.aow.monika

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Implementation
import vn.aow.monika.library.*
import vn.aow.monika.ui.TestApp
import java.io.*
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34], shadows = [JavaRmsBackupTest.JavaDirectories::class])
class JavaRmsBackupTest {
    /** Chỉ giả bộ cung cấp thư mục; target/export/prepare/commit/ZIP/RMS đều chạy mã app thật.
     * Robolectric 4.16.1 không acquire javax.*, nên không khởi tạo ContextHolder/J2ME runtime.
     */
    @Implements(value = ru.playsoftware.j2meloader.config.Config::class, isInAndroidSdk = false)
    class JavaDirectories {
        companion object {
            lateinit var root: File
            @JvmStatic @Implementation fun __staticInitializer__() = Unit
            @JvmStatic @Implementation fun getEmulatorDir(): String = root.absolutePath
            @JvmStatic @Implementation fun getDataDir(): String = File(root,"data").absolutePath+File.separator
            @JvmStatic @Implementation fun getAppDir(): String = File(root,"converted").absolutePath+File.separator
        }
    }
    private fun withInstalledJavaFixture(block: (android.app.Application, Game, SaveTarget) -> Unit) {
        val app=ApplicationProvider.getApplicationContext<android.app.Application>()
        val dir=Files.createTempDirectory("save-java-installed").toFile()
        JavaDirectories.root=dir
        val backupRoot=File(app.filesDir,"save-backups")
        val known=backupRoot.listFiles().orEmpty().toSet()
        try {
            val installed=File(dir,"converted/v134-fixture").apply { mkdirs() }
            val manifest="Manifest-Version: 1.0\r\nMIDlet-Name: RMS fixture\r\nMIDlet-Vendor: Synthetic fixture\r\n\r\n"
            File(installed,"converted.dex.conf").writeText(manifest)
            val jad=File(dir,"Fixture.jad").apply { writeText(manifest) }
            val game=Game(dir,"RMS fixture",AppGraph.config.current.system("java"),jad)
            val t=GameSaveTransfer.target(app,game)
            assertEquals(File(dir,"data/v134-fixture").canonicalFile,t.root)
            t.root.mkdirs()
            block(app,game,t)
        } finally {
            dir.deleteRecursively()
            backupRoot.listFiles().orEmpty().filter { it !in known }.forEach { it.delete() }
        }
    }
    private fun rmsZip(files: Map<String,ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
        java.util.zip.ZipOutputStream(out).use { zip -> files.forEach { (name,bytes) ->
            zip.putNextEntry(java.util.zip.ZipEntry(name));zip.write(bytes);zip.closeEntry()
        } }
    }.toByteArray()

    @Test fun corruptExistingJavaRmsBlocksCommitBeforeBackupOrAnyWrite() = withInstalledJavaFixture { app,game,t ->
        val valid=SaveFormatsTest().rms()
        val incoming=rmsZip(valid)
        val backupRoot=File(app.filesDir,"save-backups")
        val known=backupRoot.listFiles().orEmpty().toSet()
        val rom=game.entry!!.readBytes()
        for ((name,bytes) in listOf("progress.rsh" to byteArrayOf(),"progress.1.rsr" to byteArrayOf(),
            "orphan.99.rsr" to byteArrayOf(),"progress.1.rsr" to byteArrayOf(1,2))) {
            t.root.listFiles().orEmpty().forEach { it.delete() }
            valid.forEach { (key,value) -> File(t.root,key).writeBytes(value) }
            File(t.root,name).writeBytes(bytes)
            val before=GameSaveTransfer.snapshot(t)
            val plan=GameSaveTransfer.prepare(app,game,"valid-rms.zip",incoming.inputStream())
            val error=assertThrows(IOException::class.java) { GameSaveTransfer.commit(app,plan) }
            assertTrue(error.message.orEmpty().contains("RMS hiện tại bị hỏng"))
            val after=GameSaveTransfer.snapshot(t)
            assertEquals(before.keys,after.keys)
            before.forEach { (key,value) -> assertArrayEquals(value,after.getValue(key)) }
            assertEquals(known,backupRoot.listFiles().orEmpty().toSet())
            assertFalse(t.root.walkTopDown().any { it.name.startsWith(".monika-") })
            assertArrayEquals(rom,game.entry.readBytes())
        }
    }

    @Test fun validZeroPayloadJavaRecordSurvivesExportImportAndBackupRestore() = withInstalledJavaFixture { app,game,t ->
        val regular=SaveFormatsTest().rms()
        val zeroRecord=ByteArrayOutputStream().also { out -> DataOutputStream(out).use {
            it.writeInt(1);it.writeInt(0);it.writeInt(0)
        } }.toByteArray()
        val zero=regular+("progress.1.rsr" to zeroRecord)
        SaveFormats.validateRms(zero)
        zero.forEach { (name,bytes) -> File(t.root,name).writeBytes(bytes) }
        val exported=ByteArrayOutputStream()
        assertEquals(2,GameSaveTransfer.export(app,game,exported))
        val archive=SaveFormats.archive(exported.toByteArray().inputStream())
        assertArrayEquals(zeroRecord,archive.getValue("rms/progress.1.rsr"))
        val plan=GameSaveTransfer.prepare(app,game,"zero-payload.zip",exported.toByteArray().inputStream())
        regular.forEach { (name,bytes) -> File(t.root,name).writeBytes(bytes) }
        val result=GameSaveTransfer.commit(app,plan)
        assertArrayEquals(zeroRecord,File(t.root,"progress.1.rsr").readBytes())
        val undo=result.backup.inputStream().use { GameSaveTransfer.prepare(app,game,"backup.zip",it) }
        GameSaveTransfer.commit(app,undo)
        regular.forEach { (name,bytes) -> assertArrayEquals(bytes,File(t.root,name).readBytes()) }
        val again=result.backup.inputStream().use { SaveFormats.archive(it) }
        SaveFormats.validateRms((again-"monika-save.json").mapKeys { it.key.removePrefix("rms/") })
        assertTrue(game.entry!!.isFile)
    }

}
