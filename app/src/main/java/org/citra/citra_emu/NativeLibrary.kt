// Dựa trên NativeLibrary.kt của Azahar Emulator Project (https://github.com/azahar-emu/azahar)
// Copyright 2023-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.
//
// Aow Monika: lớp cầu nối JNI. TÊN LỚP, TÊN HÀM và CHỮ KÝ phải giữ nguyên như Azahar vì mã native (libcitra-android.so)
// gọi ngược theo đúng tên này. Phần giao diện (Dialog/Fragment/Activity) của Azahar đã được thay bằng [AzaharBridge]
// để Monika tự vẽ giao diện thống nhất.
package org.citra.citra_emu

import android.os.ParcelFileDescriptor
import android.view.Surface
import androidx.annotation.Keep
import java.io.File
import java.util.Date
import vn.aow.monika.azahar.AzaharBridge

object NativeLibrary {
    /** Thiết bị cảm ứng mặc định (tên dùng trong onGamePadEvent). */
    const val TOUCHSCREEN_DEVICE = "Touchscreen"

    const val SAVESTATE_SLOT_COUNT = 11
    const val QUICKSAVE_SLOT = 0

    // ===================== Hàm native (giữ nguyên chữ ký gốc) =====================

    external fun onGamePadEvent(device: String, button: Int, action: Int): Boolean

    external fun onGamePadMoveEvent(device: String, axis: Int, xAxis: Float, yAxis: Float): Boolean

    external fun onGamePadAxisEvent(device: String?, axisId: Int, axisVal: Float): Boolean

    external fun onTouchEvent(xAxis: Float, yAxis: Float, pressed: Boolean): Boolean

    external fun onTouchMoved(xAxis: Float, yAxis: Float)

    external fun onSecondaryTouchEvent(xAxis: Float, yAxis: Float, pressed: Boolean): Boolean

    external fun onSecondaryTouchMoved(xAxis: Float, yAxis: Float)

    external fun reloadSettings()

    external fun getTitleId(filename: String): Long

    external fun getIsSystemTitle(path: String): Boolean

    external fun setUserDirectory(directory: String)

    private external fun getInstalledGamePathsImpl(): Array<String?>

    external fun createConfigFile()

    external fun createLogFile()

    external fun logUserDirectory(directory: String)

    external fun setInsertedCartridge(path: String)

    external fun run(path: String)

    external fun surfaceChanged(surf: Surface)

    external fun surfaceDestroyed()

    external fun doFrame()

    external fun secondarySurfaceChanged(secondary_surface: Surface)

    external fun secondarySurfaceDestroyed()

    external fun unPauseEmulation()

    external fun pauseEmulation()

    external fun stopEmulation()

    external fun isRunning(): Boolean

    external fun getRunningTitleId(): Long

    external fun getPerfStats(): DoubleArray

    external fun updateFramebuffer(isPortrait: Boolean)

    external fun swapScreens(swapScreens: Boolean, rotation: Int)

    external fun initializeGpuDriver(
    hookLibDir: String?,
    customDriverDir: String?,
    customDriverName: String?,
    fileRedirectDir: String?
    )

    external fun areKeysAvailable(): Boolean

    external fun getHomeMenuPath(region: Int): String

    external fun getSystemTitleIds(systemType: Int, region: Int): LongArray

    external fun areSystemTitlesInstalled(): BooleanArray

    external fun uninstallSystemFiles(old3DS: Boolean)

    external fun isFullConsoleLinked(): Boolean

    external fun unlinkConsole()

    external fun setTemporaryFrameLimit(speed: Double)

    external fun disableTemporaryFrameLimit()

    external fun playTimeManagerInit()

    external fun playTimeManagerStart(titleId: Long)

    external fun playTimeManagerStop()

    external fun playTimeManagerGetPlayTime(titleId: Long): Long

    external fun playTimeManagerGetCurrentTitleId(): Long

    external fun getSystemUsername(): String

    external fun resetProgramId()

    private external fun uninstallTitle(titleId: Long, mediaType: Int): Boolean

    external fun downloadTitleFromNus(title: Long): InstallStatus

    external fun importZipPass(path: String): Int

    external fun importQueuedZipPass(): Int

    external fun exportZipPass(path: String): Int

    external fun clearStreetPassConfig(): Int

    external fun nativeFileExists(path: String): Boolean

    external fun deleteOpenGLShaderCache(titleId: Long)

    external fun deleteVulkanShaderCache(titleId: Long)

    external fun reloadCameraDevices()

    external fun makeAmiibo(id: String?, filepath: String?): Boolean

    external fun loadAmiibo(path: String?): Boolean

    external fun removeAmiibo()

    external fun getProgramId(): String

    external fun getSavestateInfo(): Array<SaveStateInfo>?

    external fun saveState(slot: Int)

    external fun loadState(slot: Int)

    external fun logDeviceInfo()

    private external fun compressFileNative(inputPath: String?, outputPath: String): Int

    private external fun decompressFileNative(inputPath: String?, outputPath: String): Int

    external fun getRecommendedExtension(inputPath: String?, shouldCompress: Boolean): String

    external fun initMultiplayer()

    // ===================== Hàm Java mà native gọi ngược =====================

    /** Lỗi lõi: true = tiếp tục, false = dừng. */
    @Keep
    @JvmStatic
    fun onCoreError(error: CoreError?, details: String): Boolean = AzaharBridge.onCoreError(error, details)

    @Keep
    @JvmStatic
    fun isPortraitMode(): Boolean = AzaharBridge.isPortrait()

    @Keep
    @JvmStatic
    fun displayAlertMsg(title: String, message: String, yesNo: Boolean): Boolean =
        AzaharBridge.alert(title, message, yesNo)

    @Keep
    @JvmStatic
    fun exitEmulationActivity(resultCode: Int) = AzaharBridge.exit(resultCode)

    // Camera / micro: Monika chưa hỗ trợ (game 3DS dùng camera sẽ thấy hình trống).
    @Keep
    @JvmStatic
    fun requestCameraPermission(): Boolean = false

    @Keep
    @JvmStatic
    fun requestMicPermission(): Boolean = false

    @Keep
    @JvmStatic
    fun onCompressProgress(total: Long, current: Long) {}

    @Keep
    @JvmStatic
    fun addNetPlayMessage(type: Int, message: String) {}

    @Keep
    @JvmStatic
    fun clearChat() {}

    // ---- Tệp: Monika dùng đường dẫn thật (build "vanilla" → native truy cập thẳng hệ tệp, các hàm này ít khi được gọi) ----

    @Keep
    @JvmStatic
    fun createFile(directory: String, filename: String): Boolean = runCatching {
        File(directory).mkdirs(); File(directory, filename).createNewFile()
    }.getOrDefault(false)

    @Keep
    @JvmStatic
    fun createDir(directory: String, directoryName: String): Boolean = runCatching {
        File(directory, directoryName).let { it.isDirectory || it.mkdirs() }
    }.getOrDefault(false)

    @Keep
    @JvmStatic
    fun openContentUri(path: String, openMode: String): Int = runCatching {
        ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.parseMode(openMode)).detachFd()
    }.getOrDefault(-1)

    @Keep
    @JvmStatic
    fun getFilesName(path: String): Array<String?> = File(path).list()?.map<String, String?> { it }?.toTypedArray() ?: arrayOfNulls(0)

    @Keep
    @JvmStatic
    fun getUserDirectory(): String = AzaharBridge.userDirectory()

    @Keep
    @JvmStatic
    fun getSize(path: String): Long = File(path).length()

    @Keep
    @JvmStatic
    fun getBuildFlavor(): String = "vanilla"

    @Keep
    @JvmStatic
    fun isUsingAngleForOpenGL(): Boolean = false

    @Keep
    @JvmStatic
    fun fileExists(path: String): Boolean = File(path).exists()

    @Keep
    @JvmStatic
    fun isDirectory(path: String): Boolean = File(path).isDirectory

    @Keep
    @JvmStatic
    fun copyFile(source: String, destinationPath: String, destinationFilename: String): Boolean = runCatching {
        File(destinationPath).mkdirs()
        File(source).copyTo(File(destinationPath, destinationFilename), overwrite = true); true
    }.getOrDefault(false)

    @Keep
    @JvmStatic
    fun renameFile(path: String, destinationFilename: String): Boolean = runCatching {
        val f = File(path); f.renameTo(File(f.parentFile, destinationFilename))
    }.getOrDefault(false)

    @Keep
    @JvmStatic
    fun updateDocumentLocation(sourcePath: String, destinationPath: String): Boolean = runCatching {
        File(sourcePath).renameTo(File(destinationPath))
    }.getOrDefault(false)

    @Keep
    @JvmStatic
    fun moveFile(filename: String, sourceDirPath: String, destinationDirPath: String): Boolean = runCatching {
        File(destinationDirPath).mkdirs(); File(sourceDirPath, filename).renameTo(File(destinationDirPath, filename))
    }.getOrDefault(false)

    @Keep
    @JvmStatic
    fun deleteDocument(path: String): Boolean = runCatching { File(path).deleteRecursively() }.getOrDefault(false)

    // ===================== Kiểu dữ liệu native đọc theo tên =====================

    /** Mã lỗi lõi — native tra theo TÊN của từng giá trị. */
    enum class CoreError(val value: Int) {
        Success(0), ErrorNotInitialized(1), ErrorGetLoader(2), ErrorSystemMode(3), ErrorLoader(4),
        ErrorLoaderErrorEncrypted(5), ErrorLoaderErrorInvalidFormat(6), ErrorLoaderErrorGBATitle(7),
        ErrorLoaderErrorPatches(8), ErrorLoaderErrorPatchesInvalidTitle(9), ErrorSystemFiles(10),
        ErrorSavestate(11), ErrorArticDisconnected(12), ErrorN3DSApplication(13), ErrorCoreExceptionRaised(14),
        ErrorSavestateBuildMismatch(15), ShutdownRequested(16), ErrorUnknown(17);

        companion object {
            fun fromInt(value: Int): CoreError = entries.find { it.value == value } ?: ErrorUnknown
        }
    }

    enum class InstallStatus { Success, ErrorFailedToOpenFile, ErrorFileNotFound, ErrorAborted, ErrorInvalid, ErrorEncrypted, Cancelled }

    @Keep
    class SaveStateInfo {
        var slot = 0
        var time: Date? = null
    }

    /** Mã nút dùng trong onGamePadEvent / onGamePadMoveEvent (giữ đúng như Azahar). */
    object ButtonType {
        const val BUTTON_A = 700
        const val BUTTON_B = 701
        const val BUTTON_X = 702
        const val BUTTON_Y = 703
        const val BUTTON_START = 704
        const val BUTTON_SELECT = 705
        const val BUTTON_HOME = 706
        const val BUTTON_ZL = 707
        const val BUTTON_ZR = 708
        const val DPAD_UP = 709
        const val DPAD_DOWN = 710
        const val DPAD_LEFT = 711
        const val DPAD_RIGHT = 712
        const val STICK_LEFT = 713
        const val STICK_LEFT_UP = 714
        const val STICK_LEFT_DOWN = 715
        const val STICK_LEFT_LEFT = 716
        const val STICK_LEFT_RIGHT = 717
        const val STICK_C = 718
        const val STICK_C_UP = 719
        const val STICK_C_DOWN = 720
        const val STICK_C_LEFT = 771
        const val STICK_C_RIGHT = 772
        const val TRIGGER_L = 773
        const val TRIGGER_R = 774
        const val DPAD = 780
        const val BUTTON_DEBUG = 781
        const val BUTTON_GPIO14 = 782
        const val BUTTON_SWAP = 800
        const val BUTTON_TURBO = 801
    }

    object ButtonState {
        const val RELEASED = 0
        const val PRESSED = 1
    }
}
