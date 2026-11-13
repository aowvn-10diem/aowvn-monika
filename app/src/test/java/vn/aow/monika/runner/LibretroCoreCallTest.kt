/*
 * Aow Monika: khoá hồi quy cho nhóm `ppsspp / native` (#316, #372, #478, #520 — 206 lượt).
 *
 * Nhóm này được phân tích trong docs/loi/ppsspp-native-1311.md (#620) và các lời gọi lõi ở
 * `GLRetroView` đã được chuyển sang GLThread bằng #606/#619. Tài liệu #620 không sửa mã, nên các
 * hành vi dưới đây là thứ duy nhất giữ được chúng khi refactor.
 *
 * Không cần máy thật: thư viện native `libretrodroid` **không** có trên JVM, nên nếu một setter gọi
 * thẳng JNI trên thread gọi (UI Thread) thì nó **ném ngay** `UnsatisfiedLinkError`. Setter trả về
 * bình thường + nhanh ⇒ lời gọi đã được xếp sang GLThread. Trên máy thật cùng lỗi đó là ANR, vì
 * JNI `setFrameSpeed`/`setShaderConfig`/`updateVariable` giữ `coreLock` suốt `retro_run` của lõi.
 */
package vn.aow.monika.runner

import android.content.Context
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.ShaderConfig
import com.swordfish.libretrodroid.Variable
import com.swordfish.libretrodroid.ViewportAlignment
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.ui.TestApp
import java.util.Collections

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class LibretroCoreCallTest {

    /** Ngưỡng rộng: chỉ để bắt trường hợp chờ lõi (hàng trăm ms → 2 s), không đo hiệu năng. */
    private val khongChoLoi = 300L

    /** Việc đã rời thread gọi: GLThread chạm JNI thật và hỏng vì JVM không có thư viện native. */
    private val loiTrenGlThread = Collections.synchronizedList(mutableListOf<Pair<String, Throwable>>())

    private var handlerCu: Thread.UncaughtExceptionHandler? = null

    /**
     * GLThread gọi JNI thật, mà JVM không có `libretrodroid` ⇒ lỗi nạp thư viện, chính là bằng chứng
     * lời gọi đã rời thread gọi. Chỉ ghi lại và bỏ qua lỗi nạp thư viện; lỗi khác vẫn in ra để không
     * giấu hồi quy thật.
     */
    @Before
    fun ghiLoiTrenGlThread() {
        handlerCu = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            if (error is UnsatisfiedLinkError || error is NoClassDefFoundError) {
                loiTrenGlThread += thread.name to error
            } else {
                handlerCu?.uncaughtException(thread, error) ?: error.printStackTrace()
            }
        }
    }

    @After
    fun traLaiHandler() {
        Thread.setDefaultUncaughtExceptionHandler(handlerCu)
    }

    private fun view(): GLRetroView {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return GLRetroView(context, GLRetroViewData(context))
    }

    private inline fun doNhanh(ten: String, block: () -> Unit) {
        val startedAt = System.nanoTime()
        block()
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        assertTrue("$ten phải trả về ngay, không chờ lõi (thực tế $elapsedMs ms)", elapsedMs < khongChoLoi)
    }

    /** Chờ GLThread xử lý hàng đợi; trả tên thread đã gọi lõi (rỗng nếu không có sau 2 s). */
    private fun threadDaGoiLoi(): String {
        repeat(40) {
            val daGoi = synchronized(loiTrenGlThread) { loiTrenGlThread.firstOrNull() }
            if (daGoi != null) return daGoi.first
            Thread.sleep(50L)
        }
        return ""
    }

    /**
     * Hành vi 1: đổi tốc độ khung / kiểu hình / tuỳ chọn lõi chỉ được **xếp** sang GLThread.
     * Trước #606/#619 các setter này gọi JNI ngay trên UI Thread → ANR khi lõi đang chạy.
     */
    @Test
    fun doiCauHinhHienThiKhongChoLoiTrenThreadGoi() {
        val view = view()

        doNhanh("frameSpeed") { view.frameSpeed = 3 }
        doNhanh("shader") { view.shader = ShaderConfig.CRT }
        doNhanh("updateVariables") {
            view.updateVariables(
                Variable("ppsspp_vertex_cache", "disabled"),
                Variable("ppsspp_auto_frameskip", "enabled"),
            )
        }

        // Giá trị vẫn được ghi nhận để lần vẽ sau dùng đúng cấu hình mới.
        assertEquals("frameSpeed phải nhận giá trị mới", 3, view.frameSpeed)
        assertEquals("shader phải nhận giá trị mới", ShaderConfig.CRT, view.shader)

        // Và việc phải thật sự tới GLThread, không bị bỏ im lặng.
        val thread = threadDaGoiLoi()
        assertTrue("lệnh gửi lõi phải chạy trên GLThread, không phải thread gọi (thấy: '$thread')", thread.startsWith("GLThread"))
    }

    /**
     * Hành vi 2: đổi vùng vẽ / căn vùng vẽ (xoay màn hình, đổi bố cục) cũng không chờ lõi.
     * Đây là nhánh `viewport`/`viewportAlignment` mà #620 nêu; chờ ở đây là ANR khi GLThread đang
     * biên dịch shader hoặc nạp state.
     */
    @Test
    fun doiVungVeKhongChoLoiTrenThreadGoi() {
        val view = view()

        doNhanh("viewport") { view.viewport = RectF(0f, 0f, 0.8f, 1f) }
        doNhanh("viewportAlignment") { view.viewportAlignment = ViewportAlignment.TOP }

        assertEquals("phải nhận đúng vùng vẽ mới", 0.8f, view.viewport.width(), 0.0001f)
        assertEquals("phải nhận đúng căn lề mới", ViewportAlignment.TOP, view.viewportAlignment)
    }

    /**
     * Hành vi 3: `getVariables` có hạn chờ (2000 ms) và hết hạn thì trả mảng rỗng.
     * Không có hạn thì bảng tuỳ chọn lõi treo UI Thread tới mức ANR; trả rỗng để nơi gọi bỏ qua lượt
     * này còn hơn treo (xem `RetroActivity` dựng danh sách tuỳ chọn lõi).
     */
    @Test
    fun docTuyChonLoiHetHanThiTraRongChuKhongTreo() {
        val view = view()

        val startedAt = System.nanoTime()
        val variables = view.getVariables()
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000

        assertEquals("hết hạn phải trả mảng rỗng, không ném", 0, variables.size)
        assertTrue("phải chờ đúng hạn 2000 ms, không bỏ qua sớm ($elapsedMs ms)", elapsedMs >= 1_800L)
        assertTrue("không được chờ quá hạn $elapsedMs ms", elapsedMs <= 4_000L)
    }
}
