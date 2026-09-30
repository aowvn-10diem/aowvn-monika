package vn.aow.monika.apkinstall

import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import vn.aow.monika.apkinstall.repack.ApkRepacker
import vn.aow.monika.apkinstall.repack.RepackKeyStore
import vn.aow.monika.apkinstall.repack.RepackOptions
import java.io.File

/**
 * Kiểm tra thủ công trên APK THẬT (biên dịch bằng AAPT2): chỉ chạy khi đặt biến môi trường MONIKA_REAL_APK=<file .apk>.
 * Kết quả ghi ra MONIKA_REAL_APK_OUT (mặc định /tmp/repacked.apk) để chạy tiếp `aapt2 dump badging` và `apksigner verify`.
 */
class RealApkRepackTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun repackRealApk() {
        val src = System.getenv("MONIKA_REAL_APK")?.let(::File)
        assumeTrue(src != null && src.isFile)
        val out = File(System.getenv("MONIKA_REAL_APK_OUT") ?: "/tmp/repacked.apk")
        val loader = File("build/generated/loader-assets/monika-loader.dex").readBytes()
        val r = ApkRepacker.repack(src!!, out, RepackOptions("com.aow.monika", loader, RepackKeyStore(tmp.newFolder()).signer(), raiseTargetSdkTo = 24))
        println("REAL_APK targetSdk ${r.targetSdkBefore} -> ${r.targetSdkAfter}, injected=${r.injected}, out=${out.length()} bytes")
    }
}
