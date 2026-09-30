package vn.aow.monika.runner

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import vn.aow.monika.AppGraph
import vn.aow.monika.library.Game
import java.io.File
import java.io.FileInputStream

/**
 * Tải trước game (Cài đặt → Hiệu năng giả lập → Tải trước game). Gọi khi sắp chơi (vd. mở Thư viện có sẵn game "Tiếp tục chơi"):
 *  1. Đọc sẵn phần đầu file game để hệ điều hành giữ trong bộ nhớ đệm → lần nạp đầu không phải chờ đọc đĩa (game to: ISO/CSO).
 *  2. Dựng sẵn tiến trình ":game" và nạp thư viện native LibretroDroid → bấm Chơi là vào thẳng, khỏi khởi động tiến trình.
 * Chỉ làm ở nền, lỗi gì cũng bỏ qua (chơi vẫn bình thường). Chưa đo thời gian tiết kiệm trên máy thật.
 */
object GamePreload {
    /** Đọc sẵn tối đa từng này đầu file (game lớn hơn thì phần còn lại đọc khi chơi như cũ). */
    const val MAX_READAHEAD = 64L * 1024 * 1024

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var lastKey: String? = null
    @Volatile private var lastAt = 0L

    /** Hệ chạy bằng lõi libretro mới cần tải trước (J2ME/web/APK có trình chạy riêng). */
    fun applies(game: Game) = game.system?.runner == "libretro" && game.entry?.isFile == true

    fun warm(context: Context, game: Game) {
        if (!AppGraph.prefs.preloadGame || !applies(game)) return
        val now = System.currentTimeMillis()
        // Cùng 1 game trong 2 phút thì không làm lại.
        if (lastKey == game.key && now - lastAt < 120_000) return
        lastKey = game.key; lastAt = now
        val entry = game.entry ?: return
        val app = context.applicationContext
        scope.launch {
            runCatching { readahead(entry) }
            // Đang ở nền thì Android không cho khởi động service → bỏ qua, không sao.
            runCatching { app.startService(Intent(app, GameWarmService::class.java)) }
        }
    }

    /** Đọc (và bỏ) phần đầu file để hệ điều hành giữ nó trong bộ nhớ đệm. Trả số byte đã đọc. Chạy ở luồng nền. */
    fun readahead(file: File, max: Long = MAX_READAHEAD): Long {
        if (!file.isFile) return 0
        val limit = minOf(file.length(), max)
        if (limit <= 0) return 0
        val buf = ByteArray(1 shl 20)
        var total = 0L
        FileInputStream(file).use { input ->
            while (total < limit) {
                val n = input.read(buf, 0, minOf(buf.size.toLong(), limit - total).toInt())
                if (n < 0) break
                total += n
            }
        }
        return total
    }
}

/** Chạy trong tiến trình ":game": chỉ để tiến trình được dựng sẵn và nạp sẵn thư viện native rồi tự tắt service (tiến trình vẫn còn trong bộ nhớ đệm). */
class GameWarmService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Thread {
            runCatching {
                // Khởi tạo lớp → System.loadLibrary("libretrodroid") chạy sẵn ở đây thay vì lúc vào game.
                Class.forName("com.swordfish.libretrodroid.LibretroDroid")
                Class.forName("com.swordfish.libretrodroid.GLRetroView")
            }
            stopSelf(startId)
        }.start()
        return START_NOT_STICKY
    }
}
