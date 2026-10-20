package vn.aow.monika.library

import android.app.ActivityManager
import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import vn.aow.monika.AppGraph
import vn.aow.monika.config.MonikaConfig
import vn.aow.monika.config.SaveProfile
import vn.aow.monika.runner.GameLauncher
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Cùng resolver cho trình chạy và xuất/nhập; melonDS DS giữ khóa riêng như trước V134. */
object GameSavePaths {
    fun key(path: String, core: String): String {
        val old = "${File(path).nameWithoutExtension}-${path.hashCode()}"
        return if (core == "melondsds") "$old-melondsds" else old
    }
    fun child(root: File, relative: String): File {
        SaveFormats.checked(SaveFormats.safePath(relative), "Cấu hình đường dẫn save không an toàn.")
        val base = root.canonicalFile
        val result = File(base, relative)
        SaveFormats.checked(result.canonicalFile == result.absoluteFile && result.path.startsWith(base.path + File.separator),
            "Đường dẫn save trỏ ra ngoài kho đã chọn.")
        return result
    }
    fun ram(root: File, path: String, core: String, profile: SaveProfile?): File = child(root,
        "${profile?.saveDirectory ?: "saves"}/${key(path, core)}.${profile?.extension ?: "srm"}")
    fun state(root: File, path: String, core: String, profile: SaveProfile?): File = child(root,
        "${profile?.stateDirectory ?: "states"}/${key(path, core)}.state")

}

@Serializable
internal data class GameSaveManifest(
    val format: Int = 1,
    val system: String,
    val gameSha256: String,
    val core: String,
    val coreVersion: String,
    val files: List<String>,
)

internal data class SaveTarget(
    val game: Game,
    val system: String,
    val core: String,
    val coreVersion: String,
    val profile: SaveProfile,
    val root: File,
    val ram: File?,
    val states: Map<String, File>,
    val suite: String? = null,
) {
    fun destination(name: String): File = when {
        name == "ram.srm" && ram != null -> ram
        name in states -> states.getValue(name)
        suite != null && name.startsWith("rms/") && '/' !in name.removePrefix("rms/") ->
            GameSavePaths.child(root, name.removePrefix("rms/"))
        else -> throw SaveTransferException("Gói save có mục không khớp hệ.")
    }
}

internal data class SaveImportResult(val count: Int, val backup: File)

internal data class SaveImportPlan(val target: SaveTarget, val writes: Map<String, ByteArray>, val warning: String, val gameSha256: String? = null)

object GameSaveTransfer {
    private val json = Json { ignoreUnknownKeys = false }
    private val lock = Any()
    @Volatile var busy: Boolean = false
        private set
    private fun <T> transfer(context: Context, block: () -> T): T = synchronized(lock) {
        requireStopped(context)
        GameSaveGate.acquire(context.filesDir).use {
            busy = true
            try { block() } finally { busy = false }
        }
    }
    fun supported(game: Game, config: MonikaConfig): Boolean = game.system?.id in setOf("java", "nds", "gba") &&
        config.saveTransfer.systems.containsKey(game.system!!.id) && game.entry?.isFile == true && !game.locked && !game.evicted

    fun filename(game: Game, date: LocalDate = LocalDate.now()): String =
        game.name.replace(Regex("[\\p{Cntrl}\\\\/:*?\"<>|]"), "_").take(90).ifBlank { "game" } + "-${game.system?.id}-$date.zip"

    private fun requireStopped(context: Context) {
        val processes = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).runningAppProcesses
            ?: throw SaveTransferException("Không kiểm tra được màn chơi. Hãy đóng game và mở lại Monika.")
        SaveFormats.checked(processes.none { it.processName == context.packageName + ":midlet" },
            "Hãy thoát hoàn toàn màn chơi trước khi xuất hoặc nhập save.")
    }

    internal fun target(context: Context, game: Game, config: MonikaConfig = AppGraph.config.current): SaveTarget {
        SaveFormats.checked(supported(game, config), "Hệ này chưa hỗ trợ chuyển save hoặc game chưa sẵn sàng.")
        config.saveTransfer.validate()
        val id = game.system!!.id
        val profile = config.saveTransfer.systems.getValue(id)
        SaveFormats.checked((id == "java" && profile.kind == "rms") || (id != "java" && profile.kind == "raw"), "Kiểu save trong cấu hình chưa được hỗ trợ.")
        if (id == "java") {
            // Không đụng mã J2ME: đối chiếu manifest suite với bản đã cài, không đoán theo tên file JAR.
            val entry = game.entry!!
            val manifest = if (entry.extension.equals("jad", true)) entry.inputStream().use { Manifest(SaveFormats.read(it, 65536).inputStream()) } else ZipFile(entry).use { zip ->
                val mf = zip.getEntry("META-INF/MANIFEST.MF") ?: throw SaveTransferException("Game Java thiếu manifest.")
                Manifest(SaveFormats.read(zip.getInputStream(mf), 65536).inputStream())
            }
            val name = manifest.mainAttributes.getValue("MIDlet-Name")
            val vendor = manifest.mainAttributes.getValue("MIDlet-Vendor")
            SaveFormats.checked(!name.isNullOrBlank() && !vendor.isNullOrBlank(), "Game Java thiếu tên hoặc nhà phát hành.")
            val engineBase = File(ru.playsoftware.j2meloader.config.Config.getEmulatorDir())
            val data = GameSavePaths.child(engineBase, profile.saveDirectory)
            SaveFormats.checked(data.canonicalFile == File(ru.playsoftware.j2meloader.config.Config.getDataDir()).canonicalFile,
                "Thư mục RMS trong cấu hình không khớp J2ME Loader.")
            val installed = File(ru.playsoftware.j2meloader.config.Config.getAppDir()).listFiles().orEmpty()
            SaveFormats.checked(installed.size <= 4096, "Kho Java quá lớn để đối chiếu an toàn.")
            val suites = installed.filter { dir ->
                val mf = File(dir, "converted.dex.conf")
                if (!dir.isDirectory || !mf.isFile || mf.length() > 65536) false else {
                    val attrs = mf.inputStream().use { Manifest(it).mainAttributes }
                    attrs.getValue("MIDlet-Name") == name && attrs.getValue("MIDlet-Vendor") == vendor
                }
            }
            SaveFormats.checked(suites.size == 1, "Hãy mở game Java một lần để cài, hoặc kiểm tra suite bị trùng.")
            val suite = suites.single().name
            return SaveTarget(game, id, "j2me", "rms-v3", profile, GameSavePaths.child(data, suite), null, emptyMap(), suite)
        }
        val system = config.system(id) ?: throw SaveTransferException("Không tìm thấy hệ trong cấu hình hiện hành.")
        val core = GameLauncher.pickCore(AppGraph.prefs.coreOverride(id), config.cores.keys, system.core, system.altCores) {
            AppGraph.cores.supports(it)
        } ?: throw SaveTransferException("Hệ chưa có lõi sử dụng được.")
        val ram = GameSavePaths.ram(context.filesDir, game.entry!!.path, core, profile)
        val state = GameSavePaths.state(context.filesDir, game.entry.path, core, profile)
        val states = linkedMapOf("state/slot1.state" to state,
            "state/auto.state" to File(state.parentFile, state.nameWithoutExtension + ".auto.state"),
            "state/slot2.state" to File(state.parentFile, state.nameWithoutExtension + ".s2.state"),
            "state/slot3.state" to File(state.parentFile, state.nameWithoutExtension + ".s3.state"))
        return SaveTarget(game, id, core, config.cores.getValue(core).version, profile, context.filesDir, ram, states)
    }

    internal fun snapshot(target: SaveTarget): Map<String, ByteArray> {
        val paths = if (target.suite != null) target.root.listFiles().orEmpty().filter { it.isFile &&
            (it.extension == "rsh" || it.extension == "rsr") }.associate { "rms/${it.name}" to it }
        else linkedMapOf("ram.srm" to target.ram!!).plus(target.states)
        SaveFormats.checked(paths.size < SaveFormats.MAX_FILES, "Quá nhiều file save.")
        var total = 0L
        return paths.filterValues { it.exists() }.mapValues { (name, file) ->
            SaveFormats.checked(target.destination(name).canonicalFile == file.absoluteFile, "Save có liên kết không an toàn.")
            total += file.length()
            SaveFormats.checked(file.length() <= SaveFormats.MAX_FILE && total <= SaveFormats.MAX_TOTAL - 65536, "Save vượt giới hạn dung lượng.")
            file.inputStream().use { SaveFormats.read(it, minOf(SaveFormats.MAX_FILE, file.length().toInt())) }
        }
    }

    // Lõi không hỗ trợ serializeState có thể để lại file 0 byte. Không xuất placeholder
    // vào ZIP; snapshot gốc vẫn giữ nó để rollback khôi phục đúng file trước giao dịch.
    private fun archiveFiles(files: Map<String, ByteArray>): Map<String, ByteArray> =
        files.filter { (name, bytes) -> !name.startsWith("state/") || bytes.isNotEmpty() }

    private fun hash(game: Game): String {
        val entry = game.entry ?: throw SaveTransferException("Game không có file để đối chiếu.")
        SaveFormats.checked(entry.length() in 1..(1024L * 1024 * 1024), "File game không hợp lệ để đối chiếu save.")
        val expectedSize = entry.length()
        val modified = entry.lastModified()
        var read = 0L
        val digest = MessageDigest.getInstance("SHA-256")
        entry.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                val n = input.read(buffer); if (n < 0) break
                read += n
                SaveFormats.checked(read <= expectedSize, "File game đổi trong lúc đối chiếu save.")
                digest.update(buffer, 0, n)
            }
        }
        SaveFormats.checked(read == expectedSize && entry.length() == expectedSize && entry.lastModified() == modified,
            "File game đổi trong lúc đối chiếu save.")
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun export(context: Context, game: Game, output: OutputStream) = transfer(context) {
        val target = target(context, game)
        val files = archiveFiles(snapshot(target))
        SaveFormats.checked(files.isNotEmpty(), "Game chưa có save hoặc state để xuất.")
        if (target.suite != null) SaveFormats.validateRms(files.mapKeys { it.key.removePrefix("rms/") })
        val manifest = GameSaveManifest(system = target.system, gameSha256 = hash(game), core = target.core,
            coreVersion = target.coreVersion, files = files.keys.toList())
        writeZip(output, files + ("monika-save.json" to json.encodeToString(manifest).toByteArray()))
        files.size
    }

    internal fun prepare(context: Context, game: Game, filename: String, input: InputStream): SaveImportPlan = transfer(context) {
        val target = target(context, game)
        val buffered = input.buffered()
        buffered.mark(4); val signature = ByteArray(4); val count = buffered.read(signature); buffered.reset()
        val zipped = count == 4 && signature[0] == 0x50.toByte() && signature[1] == 0x4b.toByte()
        val files = if (zipped) SaveFormats.archive(buffered) else mapOf(filename to SaveFormats.read(buffered))
        val digest = hash(game)
        plan(target, files) { digest }.copy(gameSha256 = digest)
    }

    internal fun plan(target: SaveTarget, files: Map<String, ByteArray>, gameHash: () -> String): SaveImportPlan {
        val meta = files["monika-save.json"]
        val writes: Map<String, ByteArray>
        val warning: String
        if (meta != null) {
            SaveFormats.checked(meta.size <= 65536, "Thông tin gói save quá lớn.")
            val manifest = json.decodeFromString<GameSaveManifest>(meta.toString(Charsets.UTF_8))
            SaveFormats.checked(manifest.format == 1 && manifest.system == target.system, "Gói save không khớp hệ của game.")
            SaveFormats.checked(manifest.gameSha256 == gameHash(), "Gói save không khớp file game này.")
            writes = files - "monika-save.json"
            SaveFormats.checked(manifest.files.toSet() == writes.keys && manifest.files.size == writes.size, "Danh sách file trong gói save không khớp.")
            if (writes.keys.any { it.startsWith("state/") }) SaveFormats.checked(manifest.core == target.core && manifest.coreVersion == target.coreVersion,
                "State không khớp lõi hoặc phiên bản lõi hiện tại; hãy nhập file SRAM riêng.")
            // SRAM của melonDS DS không áp lên lõi NDS khác khi chưa có bằng chứng tương thích.
            SaveFormats.checked(manifest.core == target.core, "Gói save được tạo bằng lõi khác. Chọn đúng lõi trước khi nhập.")
            warning = "Đã đối chiếu đúng hệ và mã SHA-256 của file game." +
                if (writes.keys.any { it.startsWith("state/") }) " State cần đúng lõi/phiên bản lúc xuất; state cũ chưa có dấu phiên bản lúc tạo nên chưa bảo đảm nạp được." else ""
        } else if (target.suite != null) {
            SaveFormats.checked(files.keys.map { it.substringBeforeLast('/', "") }.distinct().size == 1, "ZIP có RMS của nhiều thư mục/game.")
            val parent = files.keys.first().substringBeforeLast('/', "")
            SaveFormats.checked(parent.isEmpty() || parent.substringAfterLast('/') == target.suite, "Thư mục RMS không khớp suite Java đã cài.")
            var rms = files.mapKeys { it.key.substringAfterLast('/') }
            SaveFormats.checked(rms.keys.all { target.profile.imports[File(it).extension] == "rms" }, "Đuôi RMS không được cấu hình hệ này cho phép.")
            if (rms.size == 1 && rms.keys.single().endsWith(".rsr")) {
                rms = snapshot(target).mapKeys { it.key.removePrefix("rms/") } + rms
            }
            SaveFormats.validateRms(rms)
            writes = rms.mapKeys { "rms/${it.key}" }
            warning = "RMS ngoài không chứa mã game. Chỉ tiếp tục khi đây là save của ${target.game.name}; cần cùng bản game/nhà phát hành."
        } else {
            SaveFormats.checked(files.size == 1, "ZIP ngoài phải có đúng một save của game; không nhận state ngoài.")
            val (name, bytes) = files.entries.single()
            val leaf = File(name)
            val expectedNames = listOf(target.game.entry!!.nameWithoutExtension, target.game.name).map { it.lowercase(Locale.ROOT) }
            SaveFormats.checked(leaf.nameWithoutExtension.lowercase(Locale.ROOT) in expectedNames, "Tên file save không khớp game đã chọn.")
            writes = mapOf("ram.srm" to SaveFormats.convert(bytes, leaf.extension, target.profile))
            warning = "Tên file khớp game; save thô không chứa mã game/hệ để xác minh chắc chắn. Chỉ tiếp tục nếu đúng bản game."
        }
        SaveFormats.checked(writes.isNotEmpty() && writes.size < SaveFormats.MAX_FILES && writes.values.sumOf { it.size.toLong() } <= SaveFormats.MAX_TOTAL - 65536,
            "Gói save rỗng hoặc vượt giới hạn.")
        writes.forEach { (name, bytes) -> target.destination(name); SaveFormats.checked(bytes.size in 1..SaveFormats.MAX_FILE, "File save rỗng hoặc quá lớn.") }
        if (target.suite != null) SaveFormats.validateRms(writes.mapKeys { it.key.removePrefix("rms/") })
        else writes["ram.srm"]?.let { bytes ->
            SaveFormats.checked(bytes.size in target.profile.sizes, "SRAM trong gói có kích thước không khớp hệ.")
        }
        return SaveImportPlan(target, writes, warning)
    }

    internal fun commit(context: Context, plan: SaveImportPlan): SaveImportResult = transfer(context) {
        val current = target(context, plan.target.game)
        SaveFormats.checked(current.core == plan.target.core && current.coreVersion == plan.target.coreVersion && current.profile == plan.target.profile && current.root == plan.target.root,
            "Cấu hình save đã đổi. Hãy chọn lại file để nhập.")
        SaveFormats.checked(plan.gameSha256 != null && plan.gameSha256 == hash(current.game), "File game đã đổi. Hãy chọn lại save.")
        val before = snapshot(current)
        val backupFiles = archiveFiles(before)
        // Sao lưu TOÀN BỘ save/state cũ trước lần thay đổi đầu tiên, kể cả file sẽ bị thay thế/xóa trong RMS.
        val backupDir = GameSavePaths.child(context.filesDir, "save-backups").apply { check(mkdirs() || isDirectory) }
        val backup = File(backupDir, "${UUID.randomUUID()}.zip")
        val oldManifest = GameSaveManifest(system = current.system, gameSha256 = hash(current.game), core = current.core,
            coreVersion = current.coreVersion, files = backupFiles.keys.toList())
        val backupTemp = File.createTempFile(".save-backup-", ".tmp", backupDir)
        try {
            backupTemp.outputStream().use { writeZip(it, backupFiles + ("monika-save.json" to json.encodeToString(oldManifest).toByteArray())) }
            Files.move(backupTemp.toPath(), backup.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } finally { backupTemp.delete() }
        val deletes = if (current.suite == null) emptySet() else {
            val stores = plan.writes.keys.filter { it.endsWith(".rsh") }.map { it.removeSuffix(".rsh") }
            before.keys.filter { old -> old !in plan.writes && stores.any { store -> old.startsWith("$store.") && old.endsWith(".rsr") && old.removePrefix("$store.").removeSuffix(".rsr").toIntOrNull() != null } }.toSet()
        }
        replace(current, plan.writes, deletes, before)
        SaveImportResult(plan.writes.size, backup)
    }

    /** Staging cùng thư mục + atomic rename từng file; nếu một bước lỗi, khôi phục mọi đích từ snapshot cũ. */
    internal fun replace(target: SaveTarget, writes: Map<String, ByteArray>, deletes: Set<String>, before: Map<String, ByteArray>,
        move: (File, File) -> Unit = { from, to -> Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); Unit },
    ) {
        val staged = linkedMapOf<String, File>()
        val touched = mutableListOf<String>()
        try {
            writes.forEach { (name, bytes) ->
                val dest = target.destination(name)
                check(dest.parentFile!!.mkdirs() || dest.parentFile!!.isDirectory)
                val temp = File.createTempFile(".monika-save-", ".tmp", dest.parentFile)
                staged[name] = temp
                temp.outputStream().use { it.write(bytes); it.fd.sync() }
            }
            staged.forEach { (name, temp) -> touched += name; move(temp, target.destination(name)) }
            deletes.forEach { name -> touched += name; Files.deleteIfExists(target.destination(name).toPath()) }
        // diag: bỏ qua giao dịch thất bại được ném lại sau khôi phục; không nuốt lỗi lưu game.
        } catch (error: Throwable) {
            touched.asReversed().forEach { name ->
                val dest = target.destination(name)
                val bytes = before[name]
                try {
                    if (bytes == null) Files.deleteIfExists(dest.toPath()) else {
                        val recovery = File.createTempFile(".monika-restore-", ".tmp", dest.parentFile)
                        try { recovery.outputStream().use { it.write(bytes); it.fd.sync() }
                            Files.move(recovery.toPath(), dest.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                        } finally { recovery.delete() }
                    }
                // diag: bỏ qua lỗi khôi phục được gắn vào lỗi giao dịch và ném lại cho giao diện.
                } catch (restore: Throwable) { error.addSuppressed(restore) }
            }
            throw error
        } finally { staged.values.forEach { it.delete() } }
    }

    private fun writeZip(output: OutputStream, files: Map<String, ByteArray>) {
        ZipOutputStream(output.buffered()).use { zip -> files.forEach { (name, bytes) ->
            zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
        } }
    }
}
