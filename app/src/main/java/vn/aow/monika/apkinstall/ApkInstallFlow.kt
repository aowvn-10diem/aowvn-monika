package vn.aow.monika.apkinstall

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/** Màn hình đang ở bước nào (giao diện chỉ việc vẽ theo [UiState]). */
data class UiState(
    val phase: Phase = Phase.IDLE,
    val result: InspectResult? = null,
    val candidates: List<File> = emptyList(),
    val percent: Int? = null,
    val message: String? = null,
    val failure: InstallOutcome.Failure? = null,
    /** Có dữ liệu (Data) mà bước này không chép được (Android 11+ dùng checklist). */
    val skippedData: Boolean = false,
    /** Nhật ký Cách 1/2/3 của game (khi phase = CHOOSE_METHOD/HEALTH_*). */
    val checklist: List<Attempt> = emptyList(),
) {
    enum class Phase {
        IDLE, INSPECTING, NEED_CHOICE, READY,
        INSTALLING, COPYING_OBB, REPACKING, PUSHING_DATA,
        /** Đã cài xong bằng Cách 1, chờ người chơi bấm để Monika mở thử game. */
        HEALTH_READY, HEALTH_RUNNING, HEALTH_ASK,
        /** Cách 1 không chạy được → chọn Cách 2/3. */
        CHOOSE_METHOD,
        /** Cách 2: game đã cài, chờ người chơi mở game 1 lần rồi chọn thư mục dữ liệu. */
        SAF_PREPARE,
        /** Cách 3: hướng dẫn bật Gỡ lỗi không dây + ghép đôi. */
        ADB_GUIDE,
        DONE, FAILED,
    }
}

/** Cách cài thích hợp cho gói này trên máy này. */
enum class InstallPlan {
    /** APK gốc, không cần gì thêm (kể cả OBB, do Monika tự chép). */
    PLAIN,
    /** Android 9–10: cài gốc rồi chép Data thẳng. */
    PLAIN_DIRECT_DATA,
    /** Android 11+ cần Data hoặc Android 14+ chặn game cũ → checklist 3 cách, mặc định thử Cách 1. */
    CHECKLIST,
}

/**
 * Điều phối cài game Android: đọc gói → cài (PackageInstaller) → chép OBB/Data → báo kết quả.
 * Trạng thái ở [state] (cùng tiến trình, giao diện và worker nền cùng đọc/ghi).
 */
object ApkInstallFlow {
    val state = MutableStateFlow(UiState())

    /** Kết quả đọc gói giữ trong bộ nhớ để worker nền không phải đọc/giải nén lại. */
    @Volatile var inspected: InspectResult? = null

    /** Cách người chơi đã chọn cho lượt cài tới (mặc định Cách 1). */
    @Volatile var chosenMethod: Method = Method.REPACK

    /** Mở màn cài game cho [source] (file .apk/.apks/.xapk hoặc thư mục game đã giải nén). */
    fun start(context: Context, source: File) {
        context.startActivity(ApkInstallActivity.intent(context, source))
    }

    /**
     * Nếu game trong [entry] (file .apk) đã cài trên máy với phiên bản bằng hoặc mới hơn → trả Intent mở game; ngược lại null (đi qua màn cài).
     * .apks/.xapk không đọc nhanh được tên gói nên luôn đi qua màn cài (màn cài có nút Chơi khi đã cài).
     */
    fun installedLaunch(context: Context, entry: File): android.content.Intent? {
        if (!entry.isFile || !entry.extension.equals("apk", true)) return null
        val pm = context.packageManager
        val archive = runCatching { pm.getPackageArchiveInfo(entry.path, 0) }.getOrNull() ?: return null
        val installed = runCatching { pm.getPackageInfo(archive.packageName, 0) }.getOrNull() ?: return null
        val newer = androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(archive) > androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(installed)
        return if (newer) null else pm.getLaunchIntentForPackage(archive.packageName)
    }

    fun plan(result: InspectResult, device: DeviceInfo): InstallPlan = when {
        device.sdkInt >= 30 && (result.needsData || result.lowTargetSdk) -> InstallPlan.CHECKLIST
        result.needsData -> InstallPlan.PLAIN_DIRECT_DATA
        else -> InstallPlan.PLAIN
    }

    /** Cách nào dùng được cho gói này (Cách 2 chỉ để chép Data cho game không bị chặn targetSdk). */
    fun applicable(result: InspectResult): Set<Method> = buildSet {
        add(Method.REPACK)
        if (result.needsData && !result.lowTargetSdk) add(Method.SAF)
        add(Method.ADB)
    }

    private fun st(result: InspectResult, phase: UiState.Phase, pct: Int? = null, failure: InstallOutcome.Failure? = null, message: String? = null, skipped: Boolean = false, checklist: List<Attempt> = emptyList()) =
        UiState(phase, result, percent = pct, failure = failure, message = message, skippedData = skipped, checklist = checklist)

    private fun fail(result: InspectResult, text: String, raw: String, kind: InstallOutcome.Kind = InstallOutcome.Kind.OTHER) =
        st(result, UiState.Phase.FAILED, failure = InstallOutcome.Failure(kind, text, raw))

    /** Cài thẳng APK gốc (+ OBB, + Data thẳng trên Android ≤ 10). Tách tham số để test bằng bản giả. */
    suspend fun execute(
        result: InspectResult,
        installer: ApkInstaller,
        obbDir: File? = null,
        device: DeviceInfo,
        dataDir: File? = null,
        onState: (UiState) -> Unit = { state.value = it },
    ): UiState {
        val plan = plan(result, device)
        onState(st(result, UiState.Phase.INSTALLING, 0))
        val parts = SplitSelector.select(result.parts, device)
        when (val o = installer.install(result.packageName, parts) { onState(st(result, UiState.Phase.INSTALLING, it)) }) {
            is InstallOutcome.Success -> Unit
            is InstallOutcome.UserAborted -> return st(result, UiState.Phase.READY, message = "Bạn đã hủy cài đặt.").also(onState)
            is InstallOutcome.Failure -> return st(result, UiState.Phase.FAILED, failure = o).also(onState)
        }
        copyObb(result, obbDir ?: ObbInstaller.obbDir(result.packageName), onState)?.let { return it }
        if (plan == InstallPlan.PLAIN_DIRECT_DATA) {
            onState(st(result, UiState.Phase.PUSHING_DATA, 0))
            when (val r = DataInstaller.copyDirect(result.packageName, result.dataFiles, dataDir ?: DataInstaller.dataDir(result.packageName)) { onState(st(result, UiState.Phase.PUSHING_DATA, it)) }) {
                is DataInstaller.Result.Ok -> Unit
                is DataInstaller.Result.Failed -> return fail(result, r.text, "data").also(onState)
            }
        }
        return st(result, UiState.Phase.DONE).also(onState)
    }

    private fun copyObb(result: InspectResult, obbDir: File, onState: (UiState) -> Unit): UiState? {
        if (result.obbFiles.isEmpty()) return null
        onState(st(result, UiState.Phase.COPYING_OBB, 0))
        return when (val r = ObbInstaller.copy(result.packageName, result.obbFiles, obbDir) { onState(st(result, UiState.Phase.COPYING_OBB, it)) }) {
            is ObbInstaller.Result.Ok -> null
            is ObbInstaller.Result.Failed -> fail(result, r.text, "obb").also(onState)
        }
    }

    /** Sửa 1 phần APK (base: chèn bộ nạp; split: chỉ ký lại). Trả về file mới. */
    fun interface Repacker { fun repack(part: ApkPart, isBase: Boolean, raiseTargetSdkTo: Int?): File }

    /**
     * Cách 1: chỉnh game (chèn bộ nạp + ký lại + nâng targetSdk tối thiểu nếu cần) → cài → chép OBB → bộ nạp trong game chép Data.
     * Xong thì dừng ở HEALTH_READY để giao diện mở thử game (cần đang ở màn hình trước).
     */
    suspend fun executeRepack(
        result: InspectResult,
        installer: ApkInstaller,
        repacker: Repacker,
        pushData: suspend (List<DataFile>, (Int) -> Unit) -> DataPusher.Result,
        register: (String) -> Unit,
        obbDir: File? = null,
        device: DeviceInfo,
        checklist: InstallChecklist,
        onState: (UiState) -> Unit = { state.value = it },
    ): UiState {
        val pkg = result.packageName
        fun list() = checklist.read(pkg, result.versionCode)
        checklist.set(pkg, result.versionCode, Method.REPACK, State.RUNNING)
        fun failed(text: String, raw: String, kind: InstallOutcome.Kind = InstallOutcome.Kind.OTHER, record: Boolean = true): UiState {
            // Xung đột chữ ký/hạ phiên bản không phải lỗi của Cách 1 — người chơi gỡ bản cũ rồi thử lại.
            if (record) checklist.set(pkg, result.versionCode, Method.REPACK, State.FAILED, text)
            return st(result, UiState.Phase.FAILED, failure = InstallOutcome.Failure(kind, text, raw), checklist = list()).also(onState)
        }

        val selected = SplitSelector.select(result.parts, device)
        val raise = if (result.lowTargetSdk) device.minInstallableTargetSdk else null
        val fixed = ArrayList<ApkPart>()
        try {
            selected.forEachIndexed { i, p ->
                onState(st(result, UiState.Phase.REPACKING, (i * 100) / selected.size))
                fixed += ApkPart(repacker.repack(p, p.isBase, raise), p.splitName)
            }
        } catch (e: Exception) {
            return failed("Không chỉnh được gói game: ${e.message ?: e.javaClass.simpleName}", "repack")
        }
        // Đăng ký TRƯỚC khi cài: bộ nạp trong game có thể gọi về Monika ngay khi tiến trình game khởi động.
        register(pkg)
        onState(st(result, UiState.Phase.INSTALLING, 0))
        when (val o = installer.install(pkg, fixed) { onState(st(result, UiState.Phase.INSTALLING, it)) }) {
            is InstallOutcome.Success -> Unit
            is InstallOutcome.UserAborted -> {
                checklist.set(pkg, result.versionCode, Method.REPACK, State.NOT_TRIED)
                return st(result, UiState.Phase.READY, message = "Bạn đã hủy cài đặt.").also(onState)
            }
            is InstallOutcome.Failure -> {
                // Xung đột chữ ký/hạ phiên bản: chưa phải lỗi của Cách 1, người chơi gỡ bản cũ rồi thử lại.
                val conflict = o.kind == InstallOutcome.Kind.SIGNATURE_CONFLICT || o.kind == InstallOutcome.Kind.DOWNGRADE
                if (conflict) checklist.set(pkg, result.versionCode, Method.REPACK, State.NOT_TRIED)
                return failed(o.text, o.raw, o.kind, record = !conflict)
            }
        }
        copyObb(result, obbDir ?: ObbInstaller.obbDir(pkg), onState)?.let { return failed(it.failure?.text.orEmpty(), "obb") }
        if (result.dataFiles.isNotEmpty()) {
            onState(st(result, UiState.Phase.PUSHING_DATA, 0))
            when (val r = pushData(result.dataFiles) { onState(st(result, UiState.Phase.PUSHING_DATA, it)) }) {
                is DataPusher.Result.Ok -> Unit
                is DataPusher.Result.Failed -> return failed(r.text, "data")
            }
        }
        return st(result, UiState.Phase.HEALTH_READY, checklist = list()).also(onState)
    }

    /** Cách 2, bước 1: cài APK gốc (+ OBB), chưa chép Data — chờ người chơi mở game 1 lần để Android tạo thư mục dữ liệu. */
    suspend fun executeSafInstall(
        result: InspectResult, installer: ApkInstaller, device: DeviceInfo, checklist: InstallChecklist,
        obbDir: File? = null, alreadyInstalled: Boolean = false, onState: (UiState) -> Unit = { state.value = it },
    ): UiState {
        val pkg = result.packageName
        checklist.set(pkg, result.versionCode, Method.SAF, State.RUNNING)
        if (!alreadyInstalled) {
            onState(st(result, UiState.Phase.INSTALLING, 0))
            val parts = SplitSelector.select(result.parts, device)
            when (val o = installer.install(pkg, parts) { onState(st(result, UiState.Phase.INSTALLING, it)) }) {
                is InstallOutcome.Success -> Unit
                is InstallOutcome.UserAborted -> { checklist.set(pkg, result.versionCode, Method.SAF, State.NOT_TRIED); return st(result, UiState.Phase.CHOOSE_METHOD, message = "Bạn đã hủy cài đặt.", checklist = checklist.read(pkg, result.versionCode)).also(onState) }
                is InstallOutcome.Failure -> {
                    val conflict = o.kind == InstallOutcome.Kind.SIGNATURE_CONFLICT || o.kind == InstallOutcome.Kind.DOWNGRADE
                    checklist.set(pkg, result.versionCode, Method.SAF, if (conflict) State.NOT_TRIED else State.FAILED, if (conflict) null else o.text)
                    return st(result, UiState.Phase.FAILED, failure = o, checklist = checklist.read(pkg, result.versionCode)).also(onState)
                }
            }
            copyObb(result, obbDir ?: ObbInstaller.obbDir(pkg), onState)?.let {
                checklist.set(pkg, result.versionCode, Method.SAF, State.FAILED, it.failure?.text)
                return it.copy(checklist = checklist.read(pkg, result.versionCode))
            }
        }
        return st(result, UiState.Phase.SAF_PREPARE, checklist = checklist.read(pkg, result.versionCode)).also(onState)
    }

    /**
     * Cách 3: cài APK GỐC qua quyền shell (bỏ chặn targetSdk nếu cần), chép OBB và Data, rồi luôn trả công tắc gỡ lỗi về như ban đầu.
     * [readNow] đọc lại công tắc sau khi tắt để báo nếu chưa trả được.
     */
    suspend fun executeAdb(
        result: InspectResult, shell: vn.aow.monika.apkinstall.adb.AdbShell, device: DeviceInfo, checklist: InstallChecklist,
        initial: vn.aow.monika.apkinstall.adb.DevState, readNow: () -> vn.aow.monika.apkinstall.adb.DevState,
        obbDir: File? = null, onState: (UiState) -> Unit = { state.value = it },
    ): UiState {
        val pkg = result.packageName
        fun list() = checklist.read(pkg, result.versionCode)
        checklist.set(pkg, result.versionCode, Method.ADB, State.RUNNING)
        var end: UiState
        try {
            val parts = SplitSelector.select(result.parts, device)
            onState(st(result, UiState.Phase.INSTALLING, 0))
            val o = vn.aow.monika.apkinstall.adb.AdbInstaller.install(shell, pkg, parts, bypassLowTargetSdk = device.sdkInt >= 34 && result.lowTargetSdk) { onState(st(result, UiState.Phase.INSTALLING, it)) }
            end = when (o) {
                is InstallOutcome.Success -> {
                    val obbFail = copyObb(result, obbDir ?: ObbInstaller.obbDir(pkg), onState)
                    if (obbFail != null) { checklist.set(pkg, result.versionCode, Method.ADB, State.FAILED, obbFail.failure?.text); obbFail.copy(checklist = list()) }
                    else {
                        var dataFail: String? = null
                        if (result.dataFiles.isNotEmpty()) {
                            onState(st(result, UiState.Phase.PUSHING_DATA, 0))
                            val r = vn.aow.monika.apkinstall.adb.AdbInstaller.pushData(shell, pkg, result.dataFiles) { onState(st(result, UiState.Phase.PUSHING_DATA, it)) }
                            if (r is vn.aow.monika.apkinstall.adb.AdbInstaller.DataResult.Failed) dataFail = r.text
                        }
                        if (dataFail != null) { checklist.set(pkg, result.versionCode, Method.ADB, State.FAILED, dataFail); st(result, UiState.Phase.CHOOSE_METHOD, message = dataFail, checklist = list()) }
                        else { checklist.set(pkg, result.versionCode, Method.ADB, State.OK); st(result, UiState.Phase.DONE, checklist = list()) }
                    }
                }
                is InstallOutcome.UserAborted -> { checklist.set(pkg, result.versionCode, Method.ADB, State.NOT_TRIED); st(result, UiState.Phase.CHOOSE_METHOD, message = "Đã hủy cài đặt.", checklist = list()) }
                is InstallOutcome.Failure -> {
                    val conflict = o.kind == InstallOutcome.Kind.SIGNATURE_CONFLICT || o.kind == InstallOutcome.Kind.DOWNGRADE
                    checklist.set(pkg, result.versionCode, Method.ADB, if (conflict) State.NOT_TRIED else State.FAILED, if (conflict) null else o.text)
                    st(result, UiState.Phase.FAILED, failure = o, checklist = list())
                }
            }
        } catch (e: Exception) {
            checklist.set(pkg, result.versionCode, Method.ADB, State.FAILED, e.message)
            end = st(result, UiState.Phase.FAILED, failure = InstallOutcome.Failure(InstallOutcome.Kind.OTHER, "Cách 3 bị ngắt giữa chừng: ${e.message ?: e.javaClass.simpleName}", e.toString()), checklist = list())
        } finally {
            vn.aow.monika.apkinstall.adb.AdbInstaller.cleanup(shell, initial)
        }
        val restored = runCatching { vn.aow.monika.apkinstall.adb.AdbCleanup.restored(initial, readNow()) }.getOrDefault(true)
        if (!restored) end = end.copy(message = "Monika chưa tắt lại được Gỡ lỗi. Nếu dùng app ngân hàng, hãy tắt Tùy chọn nhà phát triển trong Cài đặt.")
        return end.also(onState)
    }

    /** Cách 2, bước 2: chép Data vào thư mục người chơi đã cấp quyền. */
    fun executeSafCopy(result: InspectResult, root: DocDir, checklist: InstallChecklist, onState: (UiState) -> Unit = { state.value = it }): UiState {
        val pkg = result.packageName
        onState(st(result, UiState.Phase.PUSHING_DATA, 0))
        return when (val r = SafDataAccess.copy(root, result.dataFiles) { onState(st(result, UiState.Phase.PUSHING_DATA, it)) }) {
            is SafDataAccess.Result.Ok -> {
                checklist.set(pkg, result.versionCode, Method.SAF, State.OK)
                st(result, UiState.Phase.DONE, checklist = checklist.read(pkg, result.versionCode))
            }
            is SafDataAccess.Result.Failed -> {
                checklist.set(pkg, result.versionCode, Method.SAF, State.FAILED, r.text)
                st(result, UiState.Phase.CHOOSE_METHOD, message = r.text, checklist = checklist.read(pkg, result.versionCode))
            }
        }.also(onState)
    }

    /** Ghi kết quả tự kiểm tra + xác nhận của người chơi vào checklist và trả trạng thái kế tiếp. */
    fun applyHealth(result: InspectResult, health: Health, checklist: InstallChecklist): UiState = when (health) {
        is Health.LikelyOk -> st(result, UiState.Phase.HEALTH_ASK, checklist = checklist.read(result.packageName, result.versionCode))
        is Health.Failed -> {
            checklist.set(result.packageName, result.versionCode, Method.REPACK, State.FAILED, health.reason)
            st(result, UiState.Phase.CHOOSE_METHOD, message = health.reason, checklist = checklist.read(result.packageName, result.versionCode))
        }
    }

    fun confirmWorks(result: InspectResult, works: Boolean, checklist: InstallChecklist): UiState {
        checklist.set(result.packageName, result.versionCode, Method.REPACK, if (works) State.OK else State.FAILED, if (works) null else "Người chơi báo không vào được màn hình chơi")
        val list = checklist.read(result.packageName, result.versionCode)
        return if (works) st(result, UiState.Phase.DONE, checklist = list) else st(result, UiState.Phase.CHOOSE_METHOD, checklist = list)
    }

    /** Dọn thư mục tạm chứa APK đã giải nén + APK đã chỉnh. */
    fun cleanup(result: InspectResult?) { result?.workDir?.deleteRecursively() }
}

fun interface ApkInstaller {
    suspend fun install(packageName: String, parts: List<ApkPart>, onProgress: (Int) -> Unit): InstallOutcome
}
