package vn.aow.monika.runner

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import org.tvp.kirikiri2.KR2Activity
import vn.aow.monika.diag.Diagnostics
import java.io.File

/**
 * Kirikiri (Kirikiroid2Yuri) chạy ngay trong Monika, tiến trình ":game" như các giả lập khác.
 * Mã Java nằm trong module :kirikiri; thư viện native libkrkr2yuri.so + tài nguyên (ui, font) là GÓI tải thêm
 * ([vn.aow.monika.pack.PackManager.KIRIKIRI]) nên APK vẫn nhẹ. Cho phép mở thẳng game theo Intent (bản vá "aow_game_path").
 */
class KirikiriGameActivity : KR2Activity() {
    private var clock: PlayClock? = null

    override fun get_res_sd_operate_step(): Int = vn.aow.monika.kirikiri.R.drawable.sd_operate_step

    private fun packDir() = File(filesDir, "packs/${vn.aow.monika.pack.PackManager.KIRIKIRI}")

    /** Thay cách nạp lõi gốc (System.loadLibrary từ APK) bằng nạp file trong gói đã tải. */
    override fun onLoadNativeLibraries() {
        val lib = File(packDir(), vn.aow.monika.pack.PackManager.KIRIKIRI_LIB)
        try {
            System.load(lib.absolutePath)
            Diagnostics.stage(this, "lib-loaded")
            android.util.Log.i("MonikaGame", "kirikiri-lib-loaded") // CI (scripts/ci-emulator-games.sh) đợi dòng này
        } catch (t: Throwable) {
            android.util.Log.e("MonikaGame", "error kirikiri-lib: $t")
            Diagnostics.recordHandled(this, "engine:kirikiri", "không nạp được ${lib.name}", t)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        sAssetsRoot = File(packDir(), "assets").absolutePath
        val name = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        Diagnostics.begin(this, "kirikiri", "kirikiroid2-yuri", File(packDir(), "manifest.json").takeIf { it.isFile }?.readText().orEmpty(), name, "Kirikiri")
        clock = PlayClock(intent.getStringExtra(PlayClock.EXTRA_KEY))
        super.onCreate(savedInstanceState)
        Diagnostics.stage(this, "created")
    }

    override fun onResume() { super.onResume(); clock?.resume(); Diagnostics.stage(this, "playing") }
    override fun onPause() { clock?.pause(); Diagnostics.heartbeat(this); super.onPause() }

    override fun onDestroy() {
        Diagnostics.end(this) // thoát bình thường → không tạo báo cáo "chết bất thường"
        super.onDestroy() // KR2Activity gọi System.exit(0)
    }

    companion object {
        private const val EXTRA_TITLE = "title"

        /** [entry] = file game (data.xp3, *.xp3, startup.tjs) → mở thẳng; null → vào trình duyệt file của Kirikiri. */
        fun start(activity: Activity, entry: File?, title: String, key: String?) {
            val i = Intent(activity, KirikiriGameActivity::class.java)
                .putExtra(PlayClock.EXTRA_KEY, key)
                .putExtra(EXTRA_TITLE, title)
                // KR2Activity (cocos2d) tự thoát nếu không phải activity gốc của task → mở trong task riêng.
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            entry?.let { i.putExtra("aow_game_path", it.absolutePath) }
            activity.startActivity(i)
        }
    }
}
