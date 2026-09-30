package vn.aow.monika.apkinstall

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Cài game bằng PackageInstaller session: nhận nhiều file (base + split), không cần bật gỡ lỗi.
 * Android sẽ hiện hộp thoại "Cài đặt?" của hệ thống (việc này do [InstallResultReceiver] mở).
 */
object SessionInstaller {

    suspend fun install(context: Context, packageName: String, parts: List<ApkPart>, onProgress: (Int) -> Unit = {}): InstallOutcome =
        withContext(Dispatchers.IO) {
            val installer = context.packageManager.packageInstaller
            val total = parts.sumOf { it.file.length() }.coerceAtLeast(1)
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(packageName)
                setSize(total)
                if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val id = try { installer.createSession(params) } catch (e: IOException) {
                return@withContext InstallOutcome.from(PackageInstaller.STATUS_FAILURE, e.message)
            }
            val waiting = InstallResults.expect(id)
            try {
                installer.openSession(id).use { session ->
                    var done = 0L
                    parts.forEachIndexed { i, p ->
                        // Tên trong session phải khác nhau và đuôi .apk.
                        val name = "${i}_${p.file.name}".let { if (it.endsWith(".apk", true)) it else "$it.apk" }
                        p.file.inputStream().use { input ->
                            session.openWrite(name, 0, p.file.length()).use { out ->
                                val buf = ByteArray(256 * 1024)
                                while (true) {
                                    val n = input.read(buf); if (n < 0) break
                                    out.write(buf, 0, n); done += n
                                    onProgress((done * 100 / total).toInt().coerceIn(0, 100))
                                }
                                session.fsync(out)
                            }
                        }
                    }
                    val callback = Intent(context, InstallResultReceiver::class.java).setAction(InstallResultReceiver.ACTION)
                        .putExtra(InstallResultReceiver.EXTRA_SESSION, id)
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
                    session.commit(PendingIntent.getBroadcast(context, id, callback, flags).intentSender)
                }
                waiting.await()
            } catch (e: Exception) {
                InstallResults.forget(id)
                runCatching { installer.abandonSession(id) }
                InstallOutcome.from(PackageInstaller.STATUS_FAILURE, e.message)
            }
        }

    /** Gỡ game cũ (mở hộp thoại xác nhận của Android). Trả về khi hộp thoại đã mở, không đợi kết quả. */
    fun uninstall(context: Context, packageName: String) {
        context.startActivity(
            Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun isInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0); true
    } catch (_: android.content.pm.PackageManager.NameNotFoundException) { false }
}
