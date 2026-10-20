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
    val treeSnapshot: Boolean = false,
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
    val directory: File? = null,
    val identity: String? = null,
) {
    fun destination(name: String): File = when {
        name == "ram.srm" && ram != null -> ram
        name in states -> states.getValue(name)
        directory != null && name.startsWith("save/") -> {
            val relative = name.removePrefix("save/")
            SaveFormats.checked(ConsoleSaveTrees.allowed(this, relative), "Cây save không khớp game đã chọn.")
            GameSavePaths.child(directory, relative)
        }
        suite != null && name.startsWith("rms/") && '/' !in name.removePrefix("rms/") ->
            GameSavePaths.child(root, name.removePrefix("rms/"))
        else -> throw SaveTransferException("Gói save có mục không khớp hệ.")
    }
}

internal data class SaveImportResult(val count: Int, val backup: File)

internal data class SaveImportPlan(val target: SaveTarget, val writes: Map<String, ByteArray>, val warning: String, val gameSha256: String? = null, val replaceTree: Boolean = false)

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
    fun supported(game: Game, config: MonikaConfig): Boolean = game.system?.id in setOf("java", "nds", "gba", "3ds", "psp", "ps1") &&
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
        val kind = when (id) { "java" -> "rms"; "3ds" -> "3ds"; "psp" -> "psp"; else -> "raw" }
        SaveFormats.checked(profile.kind == kind, "Kiểu save trong cấu hình chưa được hỗ trợ.")
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
        if (id == "3ds" && system.engine == vn.aow.monika.azahar.AzaharModule.ID && AppGraph.azahar.available() && AppGraph.prefs.coreOverride(id) == null) {
            SaveFormats.checked(game.system.engine == system.engine, "Cấu hình game đã đổi; mở lại Thư viện trước khi chuyển save.")
            val userDir = GameSavePaths.child(context.filesDir, profile.nativeDirectory)
            SaveFormats.checked(userDir == File(context.filesDir, "azahar"), "Thư mục save cấu hình không khớp engine Azahar.")
            SaveFormats.checked(profile.stateDirectory == "states", "Thư mục state cấu hình không khớp engine Azahar.")
            val title = ConsoleSaveFormats.title3ds(game.entry!!)
            val states = (0..3).associate { slot -> "state/slot$slot.cst" to GameSavePaths.child(userDir, "${profile.stateDirectory}/${title.uppercase(Locale.ROOT)}.%02d.cst".format(slot)) }
            return SaveTarget(game, id, "azahar", config.modules.getValue("azahar").version, profile, context.filesDir, null, states,
                directory = ConsoleSaveTrees.titleRoot(userDir, title), identity = title)
        }
        val core = GameLauncher.pickCore(AppGraph.prefs.coreOverride(id), config.cores.keys, system.core, system.altCores) {
            AppGraph.cores.supports(it)
        } ?: throw SaveTransferException("Hệ chưa có lõi sử dụng được.")
        val ram = GameSavePaths.ram(context.filesDir, game.entry!!.path, core, profile)
        val state = GameSavePaths.state(context.filesDir, game.entry.path, core, profile)
        val states = linkedMapOf("state/slot1.state" to state,
            "state/auto.state" to File(state.parentFile, state.nameWithoutExtension + ".auto.state"),
            "state/slot2.state" to File(state.parentFile, state.nameWithoutExtension + ".s2.state"),
            "state/slot3.state" to File(state.parentFile, state.nameWithoutExtension + ".s3.state"))
        if (id in setOf("3ds", "psp")) {
            SaveFormats.checked(core == if (id == "psp") "ppsspp" else "citra", "Cây save chưa được kiểm với lõi này.")
            val identity = if (id == "psp") ConsoleSaveFormats.pspDisc(game.entry) else ConsoleSaveFormats.title3ds(game.entry)
            val base = GameSavePaths.child(context.filesDir, profile.saveDirectory)
            val directory = if (id == "psp") GameSavePaths.child(base, profile.treeDirectory) else {
                val pathOption = vn.aow.monika.runner.CoreOptions.saved(context, core)["citra_use_libretro_save_path"]
                    ?: config.cores.getValue(core).options["citra_use_libretro_save_path"] ?: "LibRetro Default"
                SaveFormats.checked(pathOption == "LibRetro Default", "Citra đang chọn đường dẫn save ngoài mặc định; chưa thể chuyển an toàn.")
                ConsoleSaveTrees.titleRoot(GameSavePaths.child(base, profile.treeDirectory), identity)
            }
            return SaveTarget(game, id, core, config.cores.getValue(core).version, profile, context.filesDir, null, states,
                directory = directory, identity = identity)
        }
        if (id == "ps1") SaveFormats.checked(core == "pcsx_rearmed", "Thẻ PS1 chưa được kiểm với lõi này.")
        return SaveTarget(game, id, core, config.cores.getValue(core).version, profile, context.filesDir, ram, states)
    }

    internal fun snapshot(target: SaveTarget): Map<String, ByteArray> {
        val paths = if (target.suite != null) target.root.listFiles().orEmpty().filter { it.isFile &&
            (it.extension == "rsh" || it.extension == "rsr") }.associate { "rms/${it.name}" to it }
        else if (target.directory != null) ConsoleSaveTrees.snapshot(target).plus(target.states)
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

    // Lõi có thể để lại RAM/state placeholder 0 byte mà bước nhập sẽ từ chối.
    // Chỉ bỏ placeholder này khỏi ZIP; không lọc mất RMS/cây save. RMS phải hợp lệ trước backup.
    // Snapshot gốc không lọc để rollback khôi phục đúng file trước giao dịch.
    private fun archiveFiles(files: Map<String, ByteArray>): Map<String, ByteArray> =
        files.filter { (name, bytes) -> bytes.isNotEmpty() || (name != "ram.srm" && !name.startsWith("state/")) }

    private fun hash(game: Game): String {
        val entry = game.entry ?: throw SaveTransferException("Game không có file để đối chiếu.")
        SaveFormats.checked(entry.length() in 1..(4L * 1024 * 1024 * 1024), "File game không hợp lệ để đối chiếu save (tối đa 4 GiB).")
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
        if (target.directory != null) ConsoleSaveTrees.validate(target, files)
        val manifest = GameSaveManifest(system = target.system, gameSha256 = hash(game), core = target.core,
            coreVersion = target.coreVersion, files = files.keys.toList(), treeSnapshot = target.directory != null)
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
        plan(target, files, filename) { digest }.copy(gameSha256 = digest)
    }

    internal fun plan(target: SaveTarget, files: Map<String, ByteArray>, filename: String = "", gameHash: () -> String): SaveImportPlan {
        val meta = files["monika-save.json"]
        val writes: Map<String, ByteArray>
        val warning: String
        var replaceTree = false
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
            SaveFormats.checked(manifest.treeSnapshot == (target.directory != null), "Kiểu bản sao cây save không khớp hệ.")
            replaceTree = manifest.treeSnapshot
            warning = "Đã đối chiếu đúng hệ và mã SHA-256 của file game." +
                (if (writes.keys.any { it.startsWith("state/") }) " State cần đúng lõi/phiên bản lúc xuất; state cũ chưa có dấu phiên bản lúc tạo nên chưa bảo đảm nạp được." else "") +
                (if (replaceTree && writes.keys.none { it.startsWith("save/") }) " Bản sao không có save thường: cây save game này sẽ về trống sau khi sao lưu hiện trạng." else "")
        } else if (target.directory != null) {
            SaveFormats.checked(target.profile.imports[File(filename).extension.lowercase(Locale.ROOT)] in setOf("psp-tree", "3ds-tree"), "Hệ này cần ZIP của một cây save đã giải mã, không nhận file container lẻ.")
            writes = ConsoleSaveTrees.external(target, files, filename)
            replaceTree = target.system == "3ds"
            warning = "Đã đối chiếu mã ${target.identity}; dump ngoài không có chữ ký xác nhận nội dung. Chỉ tiếp tục khi đúng game/bản vùng; không chuyển save mã hóa."
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
        SaveFormats.checked((writes.isNotEmpty() || replaceTree) && writes.size < SaveFormats.MAX_FILES && writes.values.sumOf { it.size.toLong() } <= SaveFormats.MAX_TOTAL - 65536,
            "Gói save rỗng hoặc vượt giới hạn.")
        writes.forEach { (name, bytes) -> target.destination(name); SaveFormats.checked(bytes.size in (if (target.directory != null && name.startsWith("save/")) 0 else 1)..SaveFormats.MAX_FILE, "File save rỗng hoặc quá lớn.") }
        if (target.directory != null) ConsoleSaveTrees.validate(target, writes)
        if (target.suite != null) SaveFormats.validateRms(writes.mapKeys { it.key.removePrefix("rms/") })
        else writes["ram.srm"]?.let { bytes ->
            SaveFormats.checked(bytes.size in target.profile.sizes, "SRAM trong gói có kích thước không khớp hệ.")
            if (target.system == "ps1") ConsoleSaveFormats.ps1(bytes)
        }
        return SaveImportPlan(target, writes, warning, replaceTree = replaceTree)
    }

    internal fun commit(context: Context, plan: SaveImportPlan): SaveImportResult = transfer(context) {
        val current = target(context, plan.target.game)
        SaveFormats.checked(current.core == plan.target.core && current.coreVersion == plan.target.coreVersion && current.profile == plan.target.profile && current.root == plan.target.root &&
            current.directory == plan.target.directory && current.identity == plan.target.identity,
            "Cấu hình save đã đổi. Hãy chọn lại file để nhập.")
        SaveFormats.checked(plan.gameSha256 != null && plan.gameSha256 == hash(current.game), "File game đã đổi. Hãy chọn lại save.")
        val before = snapshot(current)
        // RMS hỏng không được bỏ file để làm backup có vẻ hợp lệ. Dừng trước tạo ZIP/ghi save.
        if (current.suite != null && before.isNotEmpty()) {
            try {
                SaveFormats.validateRms(before.mapKeys { it.key.removePrefix("rms/") })
            // diag: bỏ qua ghi thêm vì lỗi RMS được ném tiếp kèm nguyên nhân để UI báo và không ghi dữ liệu.
            } catch (e: java.io.IOException) {
                throw SaveTransferException("RMS hiện tại bị hỏng; chưa nhập để giữ nguyên dữ liệu và tránh bản sao lưu không khôi phục được.").apply { initCause(e) }
            }
        }
        val backupFiles = archiveFiles(before)
        // Sao lưu TOÀN BỘ save/state cũ trước lần thay đổi đầu tiên, kể cả file sẽ bị thay thế/xóa trong RMS.
        val backupDir = GameSavePaths.child(context.filesDir, "save-backups").apply { check(mkdirs() || isDirectory) }
        val backup = File(backupDir, "${UUID.randomUUID()}.zip")
        val oldManifest = GameSaveManifest(system = current.system, gameSha256 = hash(current.game), core = current.core,
            coreVersion = current.coreVersion, files = backupFiles.keys.toList(), treeSnapshot = current.directory != null)
        val backupTemp = File.createTempFile(".save-backup-", ".tmp", backupDir)
        try {
            backupTemp.outputStream().use { writeZip(it, backupFiles + ("monika-save.json" to json.encodeToString(oldManifest).toByteArray())) }
            Files.move(backupTemp.toPath(), backup.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } finally { backupTemp.delete() }
        val deletes = if (current.directory != null) {
            val groups = plan.writes.keys.filter { it.startsWith("save/") }.map { it.removePrefix("save/").substringBefore('/') }.toSet()
            before.keys.filter { it.startsWith("save/") && it !in plan.writes &&
                (plan.replaceTree || it.removePrefix("save/").substringBefore('/') in groups) }.toSet()
        } else if (current.suite == null) emptySet() else {
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
